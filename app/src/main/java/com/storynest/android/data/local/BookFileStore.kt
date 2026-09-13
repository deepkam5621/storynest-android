package com.storynest.android.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import java.io.File
import java.io.FileOutputStream

/**
 * Persists story page images under app-private filesDir so covers never vanish.
 */
class BookFileStore(private val context: Context) {

    fun bookDir(bookId: String): File {
        val dir = File(context.filesDir, "books/$bookId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun pageImageFile(bookId: String, pageNumber: Int): File =
        File(bookDir(bookId), "page_${pageNumber.toString().padStart(2, '0')}.png")

    fun pageImageFileJpg(bookId: String, pageNumber: Int): File =
        File(bookDir(bookId), "page_${pageNumber.toString().padStart(2, '0')}.jpg")

    /** Saves raw PNG bytes (Gemini path). */
    fun savePng(bookId: String, pageNumber: Int, bytes: ByteArray): String {
        clearPageImageVariants(bookId, pageNumber)
        val file = pageImageFile(bookId, pageNumber)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file.absolutePath
    }

    /**
     * Saves JPEG or PNG bytes from any image provider.
     * Detects format by magic bytes; writes .jpg or .png accordingly.
     */
    fun saveImage(bookId: String, pageNumber: Int, bytes: ByteArray): String {
        clearPageImageVariants(bookId, pageNumber)
        val isJpeg = bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        val file = if (isJpeg) {
            pageImageFileJpg(bookId, pageNumber)
        } else {
            pageImageFile(bookId, pageNumber)
        }
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun clearPageImageVariants(bookId: String, pageNumber: Int) {
        pageImageFile(bookId, pageNumber).delete()
        pageImageFileJpg(bookId, pageNumber).delete()
    }

    fun savePlaceholder(
        bookId: String,
        pageNumber: Int,
        title: String,
        pageText: String
    ): String {
        val w = 768
        val h = 768
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val colors = arrayOf(
            intArrayOf(0xFF2D2454.toInt(), 0xFF5B4A8A.toInt(), 0xFFE8B86D.toInt()),
            intArrayOf(0xFF1A3A4A.toInt(), 0xFF3D7A6A.toInt(), 0xFFF5E6C8.toInt()),
            intArrayOf(0xFF4A2040.toInt(), 0xFF8A4A6A.toInt(), 0xFFF0C8A0.toInt()),
            intArrayOf(0xFF203050.toInt(), 0xFF4A6A9A.toInt(), 0xFFC8D8F0.toInt())
        )
        val palette = colors[(pageNumber - 1) % colors.size]
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            palette, null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF5E6C8.toInt()
            textSize = 42f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6F5E6C8.toInt()
            textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xCCF5E6C8.toInt()
            textSize = 22f
            textAlign = Paint.Align.CENTER
        }

        canvas.drawText("StoryNest · Page $pageNumber", w / 2f, 80f, labelPaint)
        drawWrapped(canvas, title.take(48), w / 2f, 160f, w - 100f, titlePaint)
        drawWrapped(canvas, pageText.take(120) + if (pageText.length > 120) "…" else "", w / 2f, 320f, w - 120f, bodyPaint)
        canvas.drawText("Illustration pending", w / 2f, h - 60f, labelPaint)

        clearPageImageVariants(bookId, pageNumber)
        val file = pageImageFile(bookId, pageNumber)
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 92, out)
        }
        bitmap.recycle()
        return file.absolutePath
    }

    private fun drawWrapped(
        canvas: Canvas,
        text: String,
        cx: Float,
        startY: Float,
        maxWidth: Float,
        paint: Paint
    ) {
        val words = text.split(' ')
        var line = ""
        var y = startY
        for (word in words) {
            val trial = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(trial) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line, cx, y, paint)
                line = word
                y += paint.textSize * 1.35f
            } else {
                line = trial
            }
        }
        if (line.isNotEmpty()) canvas.drawText(line, cx, y, paint)
    }

    fun deleteBook(bookId: String) {
        bookDir(bookId).deleteRecursively()
    }
}
