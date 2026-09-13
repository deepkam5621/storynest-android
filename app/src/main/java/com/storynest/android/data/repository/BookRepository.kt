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
import com.storynest.android.data.remote.PollinationsApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class BookRepository(
    private val dao: BookDao,
    private val files: BookFileStore,
    private val settings: SettingsRepository,
    private val gemini: GeminiApiClient = GeminiApiClient(),
    private val pollinations: PollinationsApiClient = PollinationsApiClient()
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

    private data class PageImageOutcome(
        val path: String,
        val isPlaceholder: Boolean,
        val usedPollinations: Boolean,
        val warning: String? = null
    )

    /**
     * Image pipeline per page:
     * 1) Gemini image models
     * 2) Pollinations.ai backup (free, rate-limited)
     * 3) Local placeholder
     */
    private suspend fun resolvePageImage(
        apiKey: String,
        bookId: String,
        title: String,
        characterCard: String,
        scene: String,
        pageText: String,
        pageNum: Int,
        totalPages: Int,
        needPollinationsGap: Boolean,
        onProgress: (GenerationProgress) -> Unit,
        currentWarning: String?
    ): PageImageOutcome {
        // a) Gemini
        onProgress(
            GenerationProgress(
                stage = "Drawing page $pageNum…",
                currentPage = pageNum,
                totalPages = totalPages,
                warning = currentWarning
            )
        )
        try {
            val img = gemini.generatePageImage(apiKey, characterCard, scene, pageNum)
            if (img != null && img.bytes.isNotEmpty()) {
                val path = files.saveImage(bookId, pageNum, img.bytes)
                return PageImageOutcome(path, isPlaceholder = false, usedPollinations = false)
            }
        } catch (e: GeminiApiClient.GeminiException.QuotaExceeded) {
            // fall through to Pollinations
        } catch (_: Exception) {
            // fall through to Pollinations
        }

        // b) Pollinations backup
        if (needPollinationsGap) {
            onProgress(
                GenerationProgress(
                    stage = "Finding a backup artist…",
                    currentPage = pageNum,
                    totalPages = totalPages,
                    warning = currentWarning
                )
            )
            delay(PollinationsApiClient.CALL_GAP_MS)
        }
        onProgress(
            GenerationProgress(
                stage = "Drawing page $pageNum… (backup artist)",
                currentPage = pageNum,
                totalPages = totalPages,
                warning = currentWarning
                    ?: "Using free Pollinations backup for pictures (Gemini image unavailable)."
            )
        )
        try {
            val backup = pollinations.generateImage(characterCard, scene, pageNum, pageText)
            if (backup != null && backup.bytes.isNotEmpty()) {
                val path = files.saveImage(bookId, pageNum, backup.bytes)
                return PageImageOutcome(
                    path = path,
                    isPlaceholder = false,
                    usedPollinations = true,
                    warning = "Some or all pictures used the free Pollinations backup " +
                        "(Gemini image quota unavailable). Style may vary; a watermark is possible."
                )
            }
        } catch (_: Exception) {
            // fall through to placeholder
        }

        // c) Placeholder
        val path = files.savePlaceholder(bookId, pageNum, title, pageText)
        return PageImageOutcome(
            path = path,
            isPlaceholder = true,
            usedPollinations = false,
            warning = "Pictures unavailable right now. Story text is saved with cozy placeholders. " +
                "Gemini image quota may be exhausted; Pollinations backup also failed."
        )
    }

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
            onProgress(GenerationProgress("Writing your story… ✍️"))
            val story = gemini.generateStory(apiKey, request)
            val bookId = UUID.randomUUID().toString()
            val styleLock = GeminiApiClient.STYLE_LOCK
            var imageWarning: String? = null
            var anyRealImage = false
            var usedPollinationsBefore = false
            val pageEntities = mutableListOf<PageEntity>()

            story.pages.forEachIndexed { index, page ->
                val pageNum = index + 1
                if (index > 0 && !usedPollinationsBefore) {
                    // Only apply Gemini gap when still on Gemini path between pages
                    delay(GeminiApiClient.IMAGE_CALL_GAP_MS)
                }
                val scene = page.imagePrompt.ifBlank { page.text }
                val outcome = resolvePageImage(
                    apiKey = apiKey,
                    bookId = bookId,
                    title = story.title,
                    characterCard = story.characterCard,
                    scene = scene,
                    pageText = page.text,
                    pageNum = pageNum,
                    totalPages = story.pages.size,
                    needPollinationsGap = usedPollinationsBefore,
                    onProgress = onProgress,
                    currentWarning = imageWarning
                )
                if (outcome.usedPollinations) usedPollinationsBefore = true
                if (!outcome.isPlaceholder) anyRealImage = true
                if (outcome.warning != null) imageWarning = outcome.warning

                pageEntities.add(
                    PageEntity(
                        bookId = bookId,
                        pageNumber = pageNum,
                        text = page.text,
                        imagePath = outcome.path,
                        imagePrompt = scene,
                        isPlaceholder = outcome.isPlaceholder
                    )
                )
            }

            if (!anyRealImage && imageWarning == null) {
                imageWarning =
                    "Could not generate pictures. Story text is saved with placeholders."
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
                    stage = "Saved to your nest!",
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
                onProgress("Rewriting page text… ✍️")
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
            onProgress("Drawing this page again… 🎨")
            val scene = imagePrompt.ifBlank { text }
            var path: String
            var placeholder: Boolean
            try {
                val img = gemini.generatePageImage(apiKey, book.characterCard, scene, pageNumber)
                if (img != null && img.bytes.isNotEmpty()) {
                    path = files.saveImage(bookId, pageNumber, img.bytes)
                    placeholder = false
                } else {
                    throw GeminiApiClient.GeminiException.ApiError("empty")
                }
            } catch (_: Exception) {
                onProgress("Drawing with backup artist… 🎨")
                val backup = pollinations.generateImage(book.characterCard, scene, pageNumber, text)
                if (backup != null && backup.bytes.isNotEmpty()) {
                    path = files.saveImage(bookId, pageNumber, backup.bytes)
                    placeholder = false
                } else {
                    path = files.savePlaceholder(bookId, pageNumber, book.title, text)
                    placeholder = true
                }
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
