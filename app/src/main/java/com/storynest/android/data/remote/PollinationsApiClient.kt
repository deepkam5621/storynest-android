package com.storynest.android.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

/**
 * Free anonymous image fallback via Pollinations.ai (no API key).
 * Anonymous tier is roughly 1 request / 15s — callers should space calls ~16s apart.
 *
 * Important: Pollinations caches by prompt. Shared style/character prefixes alone
 * often return the SAME image for every page unless SCENE leads and [seed] differs.
 */
class PollinationsApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val TAG = "PollinationsApi"
        const val CALL_GAP_MS = 16_000L
        private const val USER_AGENT = "StoryNest/1.0 (Android; children's picture books)"
    }

    data class ImageResult(val bytes: ByteArray, val contentType: String?)

    fun buildPrompt(characterCard: String, pageScene: String, pageNumber: Int, pageText: String = ""): String {
        val style = GeminiApiClient.STYLE_LOCK
        val scene = pageScene.ifBlank { pageText }.ifBlank { "page $pageNumber of a children's bedtime story" }
        val textHint = pageText.trim().take(180)
        // SCENE FIRST so each page differs; keep character lock shorter.
        return buildString {
            append("UNIQUE SCENE FOR PAGE $pageNumber ONLY (do not reuse other pages): ")
            append(scene)
            append(". ")
            if (textHint.isNotBlank()) {
                append("Story text on this page: ")
                append(textHint)
                append(". ")
            }
            append("Characters (same cast, this moment only): ")
            append(characterCard.take(280))
            append(". ")
            append("Style: children's soft watercolor picture-book, kid-friendly, wholesome, pastel, ")
            append(style.take(200))
            append(". Full-bleed illustration, no text letters logos watermarks, not scary.")
        }.replace(Regex("\\s+"), " ").trim()
    }

    /** Stable but different seed per page+scene so Pollinations won't return a cached twin. */
    fun seedFor(pageNumber: Int, pageScene: String, pageText: String): Int {
        val material = "$pageNumber|${pageScene.trim()}|${pageText.trim()}"
        return (material.hashCode().absoluteValue % 1_000_000_000) + pageNumber * 9973
    }

    suspend fun generateImage(
        characterCard: String,
        pageScene: String,
        pageNumber: Int,
        pageText: String = ""
    ): ImageResult? = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(characterCard, pageScene, pageNumber, pageText)
        val seed = seedFor(pageNumber, pageScene, pageText)
        Log.i(TAG, "page=$pageNumber seed=$seed promptChars=${prompt.length} scene=${pageScene.take(80)}")

        val url = "https://image.pollinations.ai/".toHttpUrl().newBuilder()
            .addPathSegment("prompt")
            .addPathSegment(prompt)
            .addQueryParameter("width", "1024")
            .addQueryParameter("height", "1024")
            .addQueryParameter("model", "flux")
            .addQueryParameter("nologo", "true")
            // enhance can rewrite different scenes toward the same generic look — keep off
            .addQueryParameter("enhance", "false")
            .addQueryParameter("seed", seed.toString())
            .addQueryParameter("private", "true")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "image/jpeg,image/png,image/*,*/*")
            .header("Cache-Control", "no-cache")
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Pollinations HTTP ${resp.code} page=$pageNumber")
                    return@withContext null
                }
                val body = resp.body ?: return@withContext null
                val bytes = body.bytes()
                if (bytes.isEmpty()) {
                    Log.w(TAG, "Empty body from Pollinations page=$pageNumber")
                    return@withContext null
                }
                if (bytes.size < 1024) {
                    val sniff = bytes.take(80).joinToString("") { b -> (b.toInt() and 0xFF).toChar().toString() }
                    if (sniff.contains("<", ignoreCase = true) || sniff.contains("{")) {
                        Log.w(TAG, "Non-image payload from Pollinations page=$pageNumber")
                        return@withContext null
                    }
                }
                val type = body.contentType()?.toString() ?: resp.header("Content-Type")
                Log.i(TAG, "Pollinations ok page=$pageNumber size=${bytes.size} type=$type seed=$seed")
                ImageResult(bytes, type)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pollinations failed page=$pageNumber: ${e.message}")
            null
        }
    }
}
