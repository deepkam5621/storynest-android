package com.storynest.android.data.repository

import com.storynest.android.data.local.BookDao
import com.storynest.android.data.local.BookEntity
import com.storynest.android.data.local.BookFileStore
import com.storynest.android.data.local.PageEntity
import com.storynest.android.data.model.BookDetail
import com.storynest.android.data.model.BookSummary
import com.storynest.android.data.model.CreateBookRequest
import com.storynest.android.data.model.PageDetail
import com.storynest.android.data.prefs.SettingsRepository
import com.storynest.android.data.remote.GeminiApiClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class BookRepository(
    private val dao: BookDao,
    private val files: BookFileStore,
    private val settings: SettingsRepository,
    private val gemini: GeminiApiClient = GeminiApiClient()
) {
    fun observeLibrary(): Flow<List<BookSummary>> =
        dao.observeBooks().map { list ->
            list.map {
                BookSummary(
                    id = it.id,
                    title = it.title,
                    createdAt = it.createdAt,
                    pageCount = it.pageCount,
                    coverPath = it.coverPath,
                    ageBand = it.ageBand,
                    mood = it.mood
                )
            }
        }

    suspend fun getBookDetail(bookId: String): BookDetail? {
        val book = dao.getBook(bookId) ?: return null
        val pages = dao.getPages(bookId)
        return BookDetail(
            id = book.id,
            title = book.title,
            createdAt = book.createdAt,
            ageBand = book.ageBand,
            mood = book.mood,
            characterCard = book.characterCard,
            styleLock = book.styleLock,
            pages = pages.map {
                PageDetail(
                    pageNumber = it.pageNumber,
                    text = it.text,
                    imagePath = it.imagePath,
                    imagePrompt = it.imagePrompt,
                    isPlaceholder = it.isPlaceholder
                )
            }
        )
    }

    fun observePages(bookId: String): Flow<List<PageDetail>> =
        dao.observePages(bookId).map { pages ->
            pages.map {
                PageDetail(
                    pageNumber = it.pageNumber,
                    text = it.text,
                    imagePath = it.imagePath,
                    imagePrompt = it.imagePrompt,
                    isPlaceholder = it.isPlaceholder
                )
            }
        }

    data class GenerationProgress(
        val stage: String,
        val currentPage: Int = 0,
        val totalPages: Int = 0,
        val warning: String? = null
    )

    /**
     * Full pipeline: story JSON → character/style lock → per-page images → Room + files.
     */
    suspend fun createBook(
        request: CreateBookRequest,
        onProgress: (GenerationProgress) -> Unit
    ): Result<String> {
        val apiKey = settings.getApiKey()
        if (apiKey.isBlank()) {
            return Result.failure(GeminiApiClient.GeminiException.MissingApiKey())
        }
        return try {
            onProgress(GenerationProgress("Writing your story…"))
            val story = gemini.generateStory(apiKey, request)
            val bookId = UUID.randomUUID().toString()
            val styleLock = GeminiApiClient.STYLE_LOCK
            var imageWarning: String? = null
            var anyRealImage = false
            val pageEntities = mutableListOf<PageEntity>()

            story.pages.forEachIndexed { index, page ->
                val pageNum = index + 1
                onProgress(
                    GenerationProgress(
                        stage = "Illustrating page $pageNum of ${story.pages.size}…",
                        currentPage = pageNum,
                        totalPages = story.pages.size,
                        warning = imageWarning
                    )
                )
                val scene = page.imagePrompt.ifBlank { page.text }
                var imagePath: String
                var isPlaceholder = false
                try {
                    val img = gemini.generatePageImage(apiKey, story.characterCard, scene, pageNum)
                    if (img != null) {
                        imagePath = files.savePng(bookId, pageNum, img.bytes)
                        anyRealImage = true
                    } else {
                        imagePath = files.savePlaceholder(bookId, pageNum, story.title, page.text)
                        isPlaceholder = true
                        imageWarning =
                            "Image model unavailable or failed. Saved cozy placeholder cards. Check Settings / free-tier limits."
                    }
                } catch (e: GeminiApiClient.GeminiException.QuotaExceeded) {
                    imagePath = files.savePlaceholder(bookId, pageNum, story.title, page.text)
                    isPlaceholder = true
                    imageWarning =
                        "Gemini rate/quota limit while illustrating. Text is saved; placeholders used for remaining images. ${e.message}"
                    // Fill remaining with placeholders quickly
                    pageEntities.add(
                        PageEntity(
                            bookId = bookId,
                            pageNumber = pageNum,
                            text = page.text,
                            imagePath = imagePath,
                            imagePrompt = scene,
                            isPlaceholder = true
                        )
                    )
                    for (rest in (pageNum + 1)..story.pages.size) {
                        val p = story.pages[rest - 1]
                        val path = files.savePlaceholder(bookId, rest, story.title, p.text)
                        pageEntities.add(
                            PageEntity(
                                bookId = bookId,
                                pageNumber = rest,
                                text = p.text,
                                imagePath = path,
                                imagePrompt = p.imagePrompt.ifBlank { p.text },
                                isPlaceholder = true
                            )
                        )
                    }
                    // break out early
                    val cover = pageEntities.firstOrNull()?.imagePath
                    dao.insertBookWithPages(
                        BookEntity(
                            id = bookId,
                            title = story.title,
                            createdAt = System.currentTimeMillis(),
                            ageBand = request.ageBand.label,
                            mood = request.mood.label,
                            characterCard = story.characterCard,
                            styleLock = styleLock,
                            coverPath = cover,
                            pageCount = pageEntities.size
                        ),
                        pageEntities
                    )
                    onProgress(
                        GenerationProgress(
                            stage = "Done (with quota limits)",
                            currentPage = pageEntities.size,
                            totalPages = pageEntities.size,
                            warning = imageWarning
                        )
                    )
                    return Result.success(bookId)
                } catch (e: Exception) {
                    imagePath = files.savePlaceholder(bookId, pageNum, story.title, page.text)
                    isPlaceholder = true
                    imageWarning = "Illustration issue: ${e.message}. Placeholders used where needed."
                }

                pageEntities.add(
                    PageEntity(
                        bookId = bookId,
                        pageNumber = pageNum,
                        text = page.text,
                        imagePath = imagePath,
                        imagePrompt = scene,
                        isPlaceholder = isPlaceholder
                    )
                )
            }

            if (!anyRealImage && imageWarning == null) {
                imageWarning =
                    "Could not reach an image-capable Gemini model. Story text is saved with placeholder art. Try gemini-2.5-flash-image access or check your API key plan."
            }

            val cover = pageEntities.firstOrNull()?.imagePath
            dao.insertBookWithPages(
                BookEntity(
                    id = bookId,
                    title = story.title,
                    createdAt = System.currentTimeMillis(),
                    ageBand = request.ageBand.label,
                    mood = request.mood.label,
                    characterCard = story.characterCard,
                    styleLock = styleLock,
                    coverPath = cover,
                    pageCount = pageEntities.size
                ),
                pageEntities
            )
            onProgress(
                GenerationProgress(
                    stage = "Saved to your library",
                    currentPage = pageEntities.size,
                    totalPages = pageEntities.size,
                    warning = imageWarning
                )
            )
            Result.success(bookId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun regeneratePage(
        bookId: String,
        pageNumber: Int,
        alsoRewriteText: Boolean,
        onProgress: (String) -> Unit
    ): Result<Unit> {
        val apiKey = settings.getApiKey()
        if (apiKey.isBlank()) return Result.failure(GeminiApiClient.GeminiException.MissingApiKey())
        val book = dao.getBook(bookId) ?: return Result.failure(IllegalStateException("Book not found"))
        val page = dao.getPage(bookId, pageNumber)
            ?: return Result.failure(IllegalStateException("Page not found"))
        return try {
            var text = page.text
            var imagePrompt = page.imagePrompt
            if (alsoRewriteText) {
                onProgress("Rewriting page text…")
                val pair = gemini.regeneratePageText(
                    apiKey = apiKey,
                    title = book.title,
                    characterCard = book.characterCard,
                    pageNumber = pageNumber,
                    previousText = page.text,
                    ageBand = book.ageBand,
                    mood = book.mood
                )
                text = pair.first
                imagePrompt = pair.second
            }
            onProgress("Redrawing illustration…")
            val img = gemini.generatePageImage(apiKey, book.characterCard, imagePrompt.ifBlank { text }, pageNumber)
            val (path, placeholder) = if (img != null) {
                files.savePng(bookId, pageNumber, img.bytes) to false
            } else {
                files.savePlaceholder(bookId, pageNumber, book.title, text) to true
            }
            dao.updatePage(
                page.copy(
                    text = text,
                    imagePrompt = imagePrompt,
                    imagePath = path,
                    isPlaceholder = placeholder
                )
            )
            if (pageNumber == 1) {
                dao.updateBook(book.copy(coverPath = path))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteBook(bookId: String) {
        files.deleteBook(bookId)
        dao.deleteBook(bookId)
    }
}
