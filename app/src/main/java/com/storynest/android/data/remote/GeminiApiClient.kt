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

        /** Preferred text models (skip retired 2.5-pro for new keys). */
        val TEXT_MODELS = listOf(
            "gemini-3.5-flash",
            "gemini-flash-latest",
            "gemini-3.6-flash",
            "gemini-3.1-flash-lite",
            "gemini-3.7-flash"
        )

        /**
         * Image models tried in order. Free-tier image quota is often exhausted (HTTP 429);
         * callers should still save story text with placeholders.
         */
        val IMAGE_MODELS = listOf(
            "gemini-3.1-flash-lite-image",
            "gemini-3.1-flash-image",
            "gemini-2.5-flash-image",
            "gemini-3-pro-image-preview",
            "nano-banana-pro-preview"
        )

        /** Pause between page illustration calls to reduce burst 429s. */
        const val IMAGE_CALL_GAP_MS = 1750L

        val STYLE_LOCK = """
            Soft Western children's picture-book illustration style.
            Warm watercolor and soft gouache textures, gentle rounded shapes,
            cozy pastel lighting, friendly expressive faces with NORMAL human/animal eyes
            (not oversized anime dot-eyes, not manga, not photorealistic, not 3D CGI).
            Suitable for a printed bedtime picture book. No text overlays in the image.
            Age-appropriate, calm, wholesome, cartoon-only.
        """.trimIndent().replace('\n', ' ')

        const val QUOTA_IMAGE_HELP =
            "Free-tier Gemini image quota is exhausted or rate-limited. " +
                "Story text is saved. To get real pictures, enable billing / raise image limits at " +
                "https://aistudio.google.com or https://ai.dev/rate-limit"
    }

    sealed class GeminiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
        class MissingApiKey : GeminiException("Add a Gemini API key in Settings to create stories.")
        class QuotaExceeded(detail: String) : GeminiException(
            if (detail.contains("aistudio.google.com") || detail.contains("ai.dev/rate-limit")) {
                detail
            } else {
                "$QUOTA_IMAGE_HELP (${detail.take(160)})"
            }
        )
        class ApiError(detail: String) : GeminiException(detail)
        class ParseError(detail: String) : GeminiException(detail)
    }

    data class ImageResult(val bytes: ByteArray, val modelUsed: String)

    suspend fun generateStory(apiKey: String, request: CreateBookRequest): GeneratedStory =
        withContext(Dispatchers.IO) {
            requireKey(apiKey)
            val system = buildSystemPrompt(request.ageBand, request.mood, request.length.pages)
            val user = buildUserPrompt(request)
            val text = generateTextWithFallback(apiKey, system, user, temperature = 1.05)
            var story = parseStoryJson(text, request.length.pages)
            if (isThinStory(story, request.ageBand)) {
                Log.i(TAG, "Story text looks thin — running one enrich rewrite pass")
                try {
                    val enrichSystem = buildEnrichSystemPrompt(request.ageBand, request.mood, request.length.pages)
                    val enrichUser = buildEnrichUserPrompt(story, request)
                    val enriched = generateTextWithFallback(apiKey, enrichSystem, enrichUser, temperature = 1.0)
                    story = parseStoryJson(enriched, request.length.pages)
                } catch (e: Exception) {
                    Log.w(TAG, "Enrich pass skipped: ${e.message}")
                }
            }
            story
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
            You rewrite a single page of an award-winning children's bedtime picture book.
            Keep age-appropriate, gentle, no gore/horror/romance. Tone down action.
            Use vivid sensory detail, concrete actions, and emotional warmth.
            Page text: 2–4 short sentences (a bit more if ages 6–8).
            imagePrompt must be a concrete visual scene matching THIS page's text
            (setting, pose, lighting, props — no style words).
            Return ONLY JSON: {"text":"...","imagePrompt":"concrete scene for this page"}
        """.trimIndent()
        val user = """
            Book title: $title
            Characters (keep fixed look): $characterCard
            Age/mood context: $ageBand / $mood
            Page number: $pageNumber
            Current text: $previousText
            Rewrite this page's text and imagePrompt, keeping continuity with the same characters.
        """.trimIndent()
        val raw = generateTextWithFallback(apiKey, system, user, temperature = 1.05)
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
        var hitQuota = false
        for (model in IMAGE_MODELS) {
            try {
                val bytes = generateImageBytes(apiKey, model, prompt)
                if (bytes != null && bytes.isNotEmpty()) {
                    Log.i(TAG, "Image ok with model=$model page=$pageNumber size=${bytes.size}")
                    return@withContext ImageResult(bytes, model)
                }
                lastError = "Empty image from $model"
            } catch (e: GeminiException.QuotaExceeded) {
                hitQuota = true
                lastError = e.message
                Log.w(TAG, "Image model $model quota: ${e.message}")
                // Try remaining models once; free tier often blocks all image models.
            } catch (e: Exception) {
                lastError = "${e.message}"
                Log.w(TAG, "Image model $model failed: ${e.message}")
            }
        }
        Log.w(TAG, "All image models failed: $lastError")
        if (hitQuota) {
            throw GeminiException.QuotaExceeded(lastError ?: QUOTA_IMAGE_HELP)
        }
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

    private fun buildSystemPrompt(age: AgeBand, mood: StoryMood, pages: Int): String {
        val pageLength = when (age) {
            AgeBand.AGES_3_5 ->
                "For ages 3–5: each page has 2–4 short, clear sentences with concrete words a toddler can picture. Readable aloud with warmth — not one-breath stubs."
            AgeBand.AGES_6_8 ->
                "For ages 6–8: each page has 3–5 short sentences with slightly richer vocabulary, clear feelings, and a little more plot detail."
        }
        return """
            You are StoryNest, an award-winning author-illustrator team that writes children's bedtime picture books
            in the spirit of classic gem-quality picture books (think vivid scenes, heart, and a clear arc —
            never vague filler like "they felt happy" with no action).

            Audience: ${age.promptHint}.
            Mood: ${mood.promptHint}.
            Exactly $pages pages. Gentle endings. No gore, horror, romance, bullying, or scary villains.
            Soften conflict; keep everything safe and reassuring for bedtime.

            STORYCRAFT (required):
            - Clear plot arc across the book: setup → a gentle wish or small problem → soft adventure / trying → cozy resolution and bedtime comfort.
            - Every page has a distinct purpose (introduce world, show the wish, first step, discovery, turning point, help from a friend, quiet win, snuggle home).
            - Vivid sensory detail: what they see, hear, touch, smell — concrete settings and actions, not abstract emotion-only lines.
            - Emotional warmth and memorable moments a parent will enjoy reading aloud.
            - Memorable title (specific and charming, not generic like "A Fun Day").
            - characterCard: FIXED look lock for illustrations — species/kind, hair/fur color & style, clothing colors & items, size, signature props. Same details every page.
            - Each imagePrompt: a concrete visual scene that MATCHES that page's text (who, where, pose, props, time-of-day/lighting). No style words — style is applied separately.

            PAGE TEXT LENGTH:
            $pageLength
            Do NOT use vague filler. Prefer specific verbs and settings ("climbed the mossy garden wall", "whispered to the sleepy moon").

            Output ONLY valid JSON matching the schema the user provides. No markdown fences, no commentary.
        """.trimIndent()
    }

    private fun buildUserPrompt(request: CreateBookRequest): String = """
        Story idea from parent: ${request.idea.trim()}

        Write a complete picture book with exactly ${request.length.pages} pages.
        Make it feel like a real bedtime picture book: clear arc, sensory detail, distinct page purposes, cozy ending.
        Invent a memorable title and a detailed characterCard (hair/fur, clothes, colors, species/kind, props).

        Return ONLY valid JSON matching this schema (no markdown fences):
        {
          "title": "memorable specific title",
          "characterCard": "fixed look details: species/kind, hair or fur, clothing colors, signature props — enough to keep every illustration consistent",
          "pages": [
            {
              "pageNumber": 1,
              "text": "2–4 short sentences (or a bit more for ages 6–8) of vivid page story text",
              "imagePrompt": "concrete visual scene for THIS page only matching the text (setting, characters, action, lighting) — no style words"
            }
          ]
        }
    """.trimIndent()

    private fun buildEnrichSystemPrompt(age: AgeBand, mood: StoryMood, pages: Int): String = """
        You improve a children's bedtime picture-book draft that is too thin or vague.
        Keep the same characters, title idea, and overall plot — but enrich every page with
        vivid sensory detail, concrete actions/settings, and a clear arc (setup → wish/problem →
        gentle adventure → cozy resolution). Mood: ${mood.promptHint}. Audience: ${age.promptHint}.
        Exactly $pages pages. Keep characterCard fixed-look details. Each imagePrompt must match its page text.
        Return ONLY the same JSON schema. No markdown.
    """.trimIndent()

    private fun buildEnrichUserPrompt(story: GeneratedStory, request: CreateBookRequest): String {
        val pagesJson = story.pages.joinToString(",\n") { p ->
            """{"pageNumber":${p.pageNumber},"text":${JsonPrimitive(p.text)},"imagePrompt":${JsonPrimitive(p.imagePrompt)}}"""
        }
        return """
            Parent idea: ${request.idea.trim()}
            Current draft (enrich, do not shrink):
            {
              "title": ${JsonPrimitive(story.title)},
              "characterCard": ${JsonPrimitive(story.characterCard)},
              "pages": [
                $pagesJson
              ]
            }
            Rewrite so each page has richer readable-aloud text and a concrete imagePrompt. Same page count (${request.length.pages}).
        """.trimIndent()
    }

    private fun isThinStory(story: GeneratedStory, age: AgeBand): Boolean {
        if (story.pages.isEmpty()) return true
        val minChars = when (age) {
            AgeBand.AGES_3_5 -> 60
            AgeBand.AGES_6_8 -> 90
        }
        val shortPages = story.pages.count { it.text.trim().length < minChars }
        val avg = story.pages.map { it.text.trim().length }.average()
        return shortPages >= (story.pages.size + 1) / 2 || avg < minChars
    }

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) throw GeminiException.MissingApiKey()
    }

    private suspend fun generateTextWithFallback(
        apiKey: String,
        system: String,
        user: String,
        temperature: Double = 1.05
    ): String {
        var last: Exception? = null
        for (model in TEXT_MODELS) {
            try {
                return generateText(apiKey, model, system, user, temperature)
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
        user: String,
        temperature: Double
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
                put("temperature", temperature)
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
                        text = "And then everyone snuggled close, safe and warm under the soft blanket.",
                        imagePrompt = "Characters resting peacefully together at bedtime in a cozy room with soft lamp light"
                    )
                )
            }
            story.copy(
                title = story.title.ifBlank { "Bedtime Story" },
                characterCard = story.characterCard.ifBlank {
                    "Friendly soft cartoon child with rounded cheeks, warm brown hair, striped pajamas, and a small stuffed animal"
                },
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
        val start = t.indexOf('{')
        val end = t.lastIndexOf('}')
        if (start >= 0 && end > start) t = t.substring(start, end + 1)
        return t
    }
}
