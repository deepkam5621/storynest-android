package com.storynest.android.data.remote

import android.util.Base64
import android.util.Log
import com.storynest.android.data.model.AgeBand
import com.storynest.android.data.model.CreateBookRequest
import com.storynest.android.data.model.GeneratedPage
import com.storynest.android.data.model.GeneratedStory
import com.storynest.android.data.model.StoryMood
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // Never log bodies — may contain story content; API key is in header/query only
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        private const val TAG = "GeminiApi"
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"

        /** Text story writing — current flash. */
        const val TEXT_MODEL = "gemini-3.5-flash"
        val TEXT_MODELS = listOf(
            "gemini-3.5-flash",
            "gemini-3.6-flash",
            "gemini-flash-latest",
            "gemini-3.1-flash-lite"
        )

        /**
         * Image models tried in order (2026 Google AI Developer API).
         * Prefer gemini-3.1-flash-lite-image / gemini-3.1-flash-image; fall back to 2.5 and pro.
         */
        val IMAGE_MODELS = listOf(
            "gemini-3.1-flash-lite-image",
            "gemini-3.1-flash-image",
            "gemini-2.5-flash-image",
            "gemini-3-pro-image"
        )

        val STYLE_LOCK = """
            Soft Western children's picture-book illustration style.
            Warm watercolor and soft gouache textures, gentle rounded shapes,
            cozy pastel lighting, friendly expressive faces with NORMAL human/animal eyes
            (not oversized anime dot-eyes, not manga, not photorealistic, not 3D CGI).
            Suitable for a printed bedtime picture book. No text overlays in the image.
            Age-appropriate, calm, wholesome, cartoon-only.
        """.trimIndent().replace('\n', ' ')
    }

    sealed class GeminiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class MissingApiKey : GeminiException("Add a Gemini API key in Settings to create stories.")
        class QuotaExceeded(detail: String) : GeminiException("Gemini quota or rate limit hit. $detail")
        class ApiError(detail: String) : GeminiException(detail)
        class ParseError(detail: String) : GeminiException(detail)
    }

    data class ImageResult(val bytes: ByteArray, val modelUsed: String)

    suspend fun generateStory(apiKey: String, request: CreateBookRequest): GeneratedStory =
        withContext(Dispatchers.IO) {
            requireKey(apiKey)
            val system = buildSystemPrompt(request.ageBand, request.mood, request.length.pages)
            val user = """
                Story idea from parent: ${request.idea.trim()}

                Write a complete picture book with exactly ${request.length.pages} pages.
                Return ONLY valid JSON matching this schema (no markdown fences):
                {
                  "title": "string",
                  "characterCard": "detailed consistent description of main characters (appearance, clothing, colors) for illustration locking",
                  "pages": [
                    {
                      "pageNumber": 1,
                      "text": "page story text for the child",
                      "imagePrompt": "short visual scene description for this page only (no style words — style is applied separately)"
                    }
                  ]
                }
            """.trimIndent()

            val text = generateTextWithFallback(apiKey, system, user)
            parseStoryJson(text, request.length.pages)
        }

    suspend fun regeneratePageText(
        apiKey: String,
        title: String,
        characterCard: String,
        pageNumber: Int,
        previousText: String,
        ageBand: String,
        mood: String
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        requireKey(apiKey)
        val system = """
            You rewrite a single page of a children's bedtime picture book.
            Keep age-appropriate, gentle, no gore/horror/romance. Tone down action.
            Return ONLY JSON: {"text":"...","imagePrompt":"short scene description"}
        """.trimIndent()
        val user = """
            Book title: $title
            Characters: $characterCard
            Age/mood context: $ageBand / $mood
            Page number: $pageNumber
            Current text: $previousText
            Rewrite this page's text and imagePrompt, keeping continuity with the same characters.
        """.trimIndent()
        val raw = generateTextWithFallback(apiKey, system, user)
        val cleaned = stripFences(raw)
        val obj = json.parseToJsonElement(cleaned).jsonObject
        val text = obj["text"]?.jsonPrimitive?.contentOrNull
            ?: throw GeminiException.ParseError("Missing text in page rewrite")
        val imagePrompt = obj["imagePrompt"]?.jsonPrimitive?.contentOrNull ?: previousText
        text to imagePrompt
    }

    /**
     * Tries image-capable models. Returns null if all fail (caller uses placeholder).
     */
    suspend fun generatePageImage(
        apiKey: String,
        characterCard: String,
        pageScene: String,
        pageNumber: Int
    ): ImageResult? = withContext(Dispatchers.IO) {
        requireKey(apiKey)
        val prompt = buildImagePrompt(characterCard, pageScene, pageNumber)
        var lastError: String? = null
        for (model in IMAGE_MODELS) {
            try {
                val bytes = generateImageBytes(apiKey, model, prompt)
                if (bytes != null && bytes.isNotEmpty()) {
                    Log.i(TAG, "Image ok with model=$model page=$pageNumber size=${bytes.size}")
                    return@withContext ImageResult(bytes, model)
                }
                lastError = "Empty image from $model"
            } catch (e: GeminiException.QuotaExceeded) {
                throw e
            } catch (e: Exception) {
                lastError = "${e.message}"
                Log.w(TAG, "Image model $model failed: ${e.message}")
            }
        }
        Log.w(TAG, "All image models failed: $lastError")
        null
    }

    private fun buildImagePrompt(characterCard: String, pageScene: String, pageNumber: Int): String =
        """
        Create one children's picture-book illustration for page $pageNumber.
        STYLE LOCK (must follow): $STYLE_LOCK
        CHARACTER CARD (keep consistent): $characterCard
        SCENE: $pageScene
        Single full-bleed illustration, no speech bubbles, no written words, no watermarks.
        """.trimIndent()

    private fun buildSystemPrompt(age: AgeBand, mood: StoryMood, pages: Int): String = """
        You are StoryNest, a children's bedtime picture-book author.
        Write age-appropriate stories for ${age.promptHint}.
        Mood: ${mood.promptHint}.
        Exactly $pages pages. Gentle endings. No gore, horror, romance, bullying, or scary villains.
        Tone down action; keep everything soft and reassuring for bedtime.
        Each page text should be short enough to read aloud in one breath or two.
        characterCard must be detailed enough to keep illustrations consistent across pages.
    """.trimIndent()

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) throw GeminiException.MissingApiKey()
    }


    private suspend fun generateTextWithFallback(
        apiKey: String,
        system: String,
        user: String
    ): String {
        var last: Exception? = null
        for (model in TEXT_MODELS) {
            try {
                return generateText(apiKey, model, system, user)
            } catch (e: GeminiException.QuotaExceeded) {
                throw e
            } catch (e: Exception) {
                last = e
                Log.w(TAG, "Text model $model failed: ${e.message}")
            }
        }
        throw last ?: GeminiException.ApiError("All text models failed")
    }

    private suspend fun generateText(
        apiKey: String,
        model: String,
        system: String,
        user: String
    ): String {
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", system) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", user) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.85)
                put("responseMimeType", "application/json")
            })
        }
        val response = postGenerate(apiKey, model, body.toString())
        return extractText(response)
            ?: throw GeminiException.ParseError("No text in Gemini response")
    }

    private suspend fun generateImageBytes(
        apiKey: String,
        model: String,
        prompt: String
    ): ByteArray? {
        // Primary: generateContent with IMAGE response modality
        val body = buildJsonObject {
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseModalities", buildJsonArray {
                    add(JsonPrimitive("TEXT"))
                    add(JsonPrimitive("IMAGE"))
                })
            })
        }
        val responseJson = postGenerate(apiKey, model, body.toString())
        return extractInlineImage(responseJson)
    }

    private suspend fun postGenerate(apiKey: String, model: String, jsonBody: String): String {
        var attempt = 0
        var lastError: Exception? = null
        while (attempt < 3) {
            attempt++
            val url = "$BASE/$model:generateContent"
            val request = Request.Builder()
                .url(url)
                .addHeader("x-goog-api-key", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toRequestBody(mediaType))
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    when {
                        resp.isSuccessful -> return bodyStr
                        resp.code == 429 || resp.code == 503 -> {
                            val msg = extractErrorMessage(bodyStr)
                            if (attempt >= 3) {
                                throw GeminiException.QuotaExceeded(msg.ifBlank { "HTTP ${resp.code}" })
                            }
                            delay(1500L * attempt)
                        }
                        resp.code == 400 || resp.code == 404 -> {
                            throw GeminiException.ApiError(
                                extractErrorMessage(bodyStr).ifBlank { "HTTP ${resp.code} for $model" }
                            )
                        }
                        else -> {
                            throw GeminiException.ApiError(
                                extractErrorMessage(bodyStr).ifBlank { "HTTP ${resp.code}" }
                            )
                        }
                    }
                }
            } catch (e: GeminiException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                delay(800L * attempt)
            }
        }
        throw GeminiException.ApiError(lastError?.message ?: "Network error talking to Gemini")
    }

    private fun extractErrorMessage(body: String): String = try {
        json.parseToJsonElement(body).jsonObject["error"]
            ?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            ?: body.take(200)
    } catch (_: Exception) {
        body.take(200)
    }

    private fun extractText(responseJson: String): String? {
        val root = json.parseToJsonElement(responseJson).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return null
        val parts = candidates.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray ?: return null
        val texts = parts.mapNotNull { part ->
            part.jsonObject["text"]?.jsonPrimitive?.contentOrNull
        }
        return texts.joinToString("\n").ifBlank { null }
    }

    private fun extractInlineImage(responseJson: String): ByteArray? {
        val root = json.parseToJsonElement(responseJson).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return null
        for (candidate in candidates) {
            val parts = candidate.jsonObject["content"]?.jsonObject
                ?.get("parts")?.jsonArray ?: continue
            for (part in parts) {
                val inline = part.jsonObject["inlineData"]?.jsonObject
                    ?: part.jsonObject["inline_data"]?.jsonObject
                if (inline != null) {
                    val data = inline["data"]?.jsonPrimitive?.contentOrNull ?: continue
                    return Base64.decode(data, Base64.DEFAULT)
                }
            }
        }
        return null
    }

    private fun parseStoryJson(raw: String, expectedPages: Int): GeneratedStory {
        val cleaned = stripFences(raw)
        return try {
            val story = json.decodeFromString(GeneratedStory.serializer(), cleaned)
            val pages = story.pages
                .sortedBy { it.pageNumber }
                .mapIndexed { idx, p ->
                    p.copy(pageNumber = idx + 1)
                }
                .take(expectedPages)
                .toMutableList()
            while (pages.size < expectedPages) {
                pages.add(
                    GeneratedPage(
                        pageNumber = pages.size + 1,
                        text = "And then everyone snuggled close, safe and warm.",
                        imagePrompt = "Characters resting peacefully together at bedtime"
                    )
                )
            }
            story.copy(
                title = story.title.ifBlank { "Bedtime Story" },
                characterCard = story.characterCard.ifBlank { "Friendly soft cartoon characters in cozy clothes" },
                pages = pages
            )
        } catch (e: Exception) {
            throw GeminiException.ParseError("Could not parse story JSON: ${e.message}")
        }
    }

    private fun stripFences(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) {
            t = t.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
            if (t.endsWith("```")) t = t.removeSuffix("```").trim()
        }
        // Sometimes model wraps with prose — find first { last }
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start >= 0 && end > start) t = t.substring(start, end + 1)
        return t
    }
}
