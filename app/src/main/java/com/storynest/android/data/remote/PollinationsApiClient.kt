package com.storynest.android.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Free anonymous image fallback via Pollinations.ai (no API key).
 * Anonymous tier is roughly 1 request / 15s — callers should space calls ~16s apart.
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

    fun buildPrompt(characterCard: String, pageScene: String, pageNumber: Int): String {
        val style = GeminiApiClient.STYLE_LOCK
        return """
            Children's picture book illustration for page $pageNumber.
            Soft watercolor and gouache, wholesome kid-friendly bedtime story art,
            warm pastel lighting, gentle rounded shapes, friendly expressive faces.
            STYLE: $style
            CHARACTERS (keep consistent): $characterCard
            SCENE: $pageScene
            Full-bleed single illustration. No text in the image, no letters, no speech bubbles,
            no watermarks, no logos, no scary content, no horror, no gore, cartoon-only.
        """.trimIndent().replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
    }

    suspend fun generateImage(
        characterCard: String,
        pageScene: String,
        pageNumber: Int
    ): ImageResult? = withContext(Dispatchers.IO) {
        val prompt = buildPrompt(characterCard, pageScene, pageNumber)
        val url = "https://image.pollinations.ai/".toHttpUrl().newBuilder()
            .addPathSegment("prompt")
            .addPathSegment(prompt)
            .addQueryParameter("width", "1024")
            .addQueryParameter("height", "1024")
            .addQueryParameter("model", "flux")
            .addQueryParameter("nologo", "true")
            .addQueryParameter("enhance", "true")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Accept", "image/jpeg,image/png,image/*,*/*")
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
                // Reject tiny/error HTML payloads
                if (bytes.size < 1024) {
                    val sniff = bytes.take(80).joinToString("") { b -> (b.toInt() and 0xFF).toChar().toString() }
                    if (sniff.contains("<", ignoreCase = true) || sniff.contains("{")) {
                        Log.w(TAG, "Non-image payload from Pollinations page=$pageNumber")
                        return@withContext null
                    }
                }
                val type = body.contentType()?.toString() ?: resp.header("Content-Type")
                Log.i(TAG, "Pollinations ok page=$pageNumber size=${bytes.size} type=$type")
                ImageResult(bytes, type)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pollinations failed page=$pageNumber: ${e.message}")
            null
        }
    }
}
