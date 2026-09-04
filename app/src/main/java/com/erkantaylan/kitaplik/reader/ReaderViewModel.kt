package com.erkantaylan.kitaplik.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.erkantaylan.kitaplik.catalog.LibraryItem
import com.erkantaylan.kitaplik.storage.LibraryStore
import com.erkantaylan.kitaplik.text.BookText
import com.erkantaylan.kitaplik.text.TextExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val book: BookText = BookText.EMPTY,
    val startParagraph: Int = 0,
    val fontScale: Float = 1f,
)

class ReaderViewModel(
    private val item: LibraryItem,
    private val store: LibraryStore,
    private val progress: ReadingProgressStore,
    private val cacheDir: File,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    val title: String = item.title
    val author: String = item.author

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { TextExtractor.extract(item, store.fileFor(item), cacheDir) }
                .onSuccess { book ->
                    val saved = progress.of(item.id)?.charOffset ?: 0
                    _state.update {
                        it.copy(
                            loading = false,
                            book = book,
                            startParagraph = book.paragraphAt(saved),
                            error = null,
                        )
                    }
                }
                .onFailure { t ->
                    _state.update {
                        it.copy(loading = false,
                                error = t.message ?: "Could not read this book")
                    }
                }
        }
    }

    /** Called as the reader scrolls; cheap enough to run on every settle. */
    fun onParagraphVisible(index: Int) {
        val book = _state.value.book
        if (book.paragraphs.isEmpty()) return
        progress.save(
            BookProgress(
                itemId = item.id,
                title = item.title,
                author = item.author,
                format = item.format,
                charOffset = book.offsetOf(index),
                charCount = book.charCount,
            )
        )
    }

    fun adjustFont(delta: Float) {
        _state.update { it.copy(fontScale = (it.fontScale + delta).coerceIn(0.8f, 2.0f)) }
    }
}
