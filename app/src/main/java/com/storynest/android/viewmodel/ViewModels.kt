package com.storynest.android.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.storynest.android.data.model.AgeBand
import com.storynest.android.data.model.BookDetail
import com.storynest.android.data.model.BookSummary
import com.storynest.android.data.model.CreateBookRequest
import com.storynest.android.data.model.PageDetail
import com.storynest.android.data.model.StoryLength
import com.storynest.android.data.model.StoryMood
import com.storynest.android.data.prefs.SettingsRepository
import com.storynest.android.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StoryNestViewModelFactory(
    private val books: BookRepository,
    private val settings: SettingsRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(LibraryViewModel::class.java) ->
                LibraryViewModel(books) as T
            modelClass.isAssignableFrom(CreateBookViewModel::class.java) ->
                CreateBookViewModel(books, settings) as T
            modelClass.isAssignableFrom(ReaderViewModel::class.java) ->
                ReaderViewModel(books, settings) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(settings) as T
            else -> throw IllegalArgumentException("Unknown VM ${modelClass.name}")
        }
    }
}

class LibraryViewModel(private val books: BookRepository) : ViewModel() {
    val library: StateFlow<List<BookSummary>> = books.observeLibrary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(bookId: String) {
        viewModelScope.launch { books.deleteBook(bookId) }
    }
}

sealed class CreateUiState {
    data object Idle : CreateUiState()
    data object Running : CreateUiState()
    data class Progress(
        val stage: String,
        val currentPage: Int,
        val totalPages: Int,
        val warning: String?
    ) : CreateUiState()
    data class Success(val bookId: String, val warning: String?) : CreateUiState()
    data class Error(val message: String, val needsApiKey: Boolean = false) : CreateUiState()
}

class CreateBookViewModel(
    private val books: BookRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    var idea: String = ""
    var ageBand: AgeBand = AgeBand.AGES_3_5
    var length: StoryLength = StoryLength.SHORT
    var mood: StoryMood = StoryMood.COZY

    private val _state = MutableStateFlow<CreateUiState>(CreateUiState.Idle)
    val state: StateFlow<CreateUiState> = _state.asStateFlow()

    fun hasApiKey(): Boolean = settings.getApiKey().isNotBlank()

    fun startGeneration() {
        if (_state.value is CreateUiState.Running || _state.value is CreateUiState.Progress) return
        if (!hasApiKey()) {
            _state.value = CreateUiState.Error(
                "Add a Gemini API key in Settings before creating a book.",
                needsApiKey = true
            )
            return
        }
        if (idea.trim().length < 3) {
            _state.value = CreateUiState.Error("Please enter a short story idea first.")
            return
        }
        val request = CreateBookRequest(idea.trim(), ageBand, length, mood)
        viewModelScope.launch {
            _state.value = CreateUiState.Running
            var lastWarning: String? = null
            val result = books.createBook(request) { p ->
                lastWarning = p.warning
                _state.value = CreateUiState.Progress(
                    stage = p.stage,
                    currentPage = p.currentPage,
                    totalPages = p.totalPages,
                    warning = p.warning
                )
            }
            _state.value = result.fold(
                onSuccess = { id -> CreateUiState.Success(id, lastWarning) },
                onFailure = { e ->
                    val needsKey = e.message?.contains("API key", ignoreCase = true) == true
                    CreateUiState.Error(e.message ?: "Something went wrong", needsApiKey = needsKey)
                }
            )
        }
    }

    fun reset() {
        _state.value = CreateUiState.Idle
    }
}

data class ReaderUiState(
    val book: BookDetail? = null,
    val pages: List<PageDetail> = emptyList(),
    val regenerating: Boolean = false,
    val status: String? = null,
    val error: String? = null,
    val nightMode: Boolean = true
)

class ReaderViewModel(
    private val books: BookRepository,
    private val settings: SettingsRepository
) : ViewModel() {
    private val _ui = MutableStateFlow(ReaderUiState())
    val ui: StateFlow<ReaderUiState> = _ui.asStateFlow()

    private var bookId: String? = null

    fun load(id: String) {
        if (bookId == id && _ui.value.book != null) return
        bookId = id
        viewModelScope.launch {
            val detail = books.getBookDetail(id)
            _ui.value = _ui.value.copy(book = detail, pages = detail?.pages.orEmpty())
            books.observePages(id).collect { pages ->
                _ui.value = _ui.value.copy(pages = pages)
            }
        }
    }

    fun toggleNight() {
        _ui.value = _ui.value.copy(nightMode = !_ui.value.nightMode)
    }

    fun regenerate(pageNumber: Int, alsoText: Boolean) {
        val id = bookId ?: return
        if (!settings.getApiKey().isNotBlank()) {
            _ui.value = _ui.value.copy(error = "API_KEY_REQUIRED")
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(regenerating = true, error = null, status = "Working…")
            val result = books.regeneratePage(id, pageNumber, alsoText) { msg ->
                _ui.value = _ui.value.copy(status = msg)
            }
            result.fold(
                onSuccess = {
                    val detail = books.getBookDetail(id)
                    _ui.value = _ui.value.copy(
                        regenerating = false,
                        status = "Page updated",
                        book = detail,
                        pages = detail?.pages.orEmpty()
                    )
                },
                onFailure = { e ->
                    _ui.value = _ui.value.copy(
                        regenerating = false,
                        status = null,
                        error = e.message ?: "Could not regenerate page"
                    )
                }
            )
        }
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }
}

class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    private val _hasKey = MutableStateFlow(settings.getApiKey().isNotBlank())
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    private val _masked = MutableStateFlow(settings.maskedPreview())
    val masked: StateFlow<String> = _masked.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun save(key: String) {
        viewModelScope.launch {
            settings.setApiKey(key)
            _hasKey.value = key.trim().isNotBlank()
            _masked.value = settings.maskedPreview()
            _saved.value = true
        }
    }

    fun clear() {
        viewModelScope.launch {
            settings.clearApiKey()
            _hasKey.value = false
            _masked.value = ""
            _saved.value = true
        }
    }

    fun consumeSaved() {
        _saved.value = false
    }
}
