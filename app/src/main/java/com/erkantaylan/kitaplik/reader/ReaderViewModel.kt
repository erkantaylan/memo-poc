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
    /** How far into that paragraph the saved position sat, 0..1. */
    val startFraction: Float = 0f,
    val fontScale: Float = 1f,
    val markedParagraphs: Set<Int> = emptySet(),
    val bookmarks: List<Bookmark> = emptyList(),
    val showBookmarks: Boolean = false,
)

class ReaderViewModel(
    private val item: LibraryItem,
    private val store: LibraryStore,
    private val progress: ReadingProgressStore,
    private val bookmarks: BookmarkStore,
    private val cacheDir: File,
    /** Set when opening from a bookmark, overriding the saved position. */
    private val openAtOffset: Int? = null,
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
                    val saved = openAtOffset ?: progress.of(item.id)?.charOffset ?: 0
                    val index = book.paragraphAt(saved)
                    val para = book.paragraphs.getOrNull(index)
                    // Where inside that paragraph the offset fell. Restoring
                    // this is what keeps the position honest when the font
                    // size changes and a paragraph spans a different number
                    // of screens than it did before.
                    val fraction = if (para != null && para.text.isNotEmpty())
                        ((saved - para.start).toFloat() / para.text.length).coerceIn(0f, 1f)
                    else 0f

                    _state.update {
                        it.copy(
                            loading = false,
                            book = book,
                            startParagraph = index,
                            startFraction = fraction,
                            error = null,
                            markedParagraphs = bookmarks.forBook(item.id)
                                .map { b -> b.paragraphIndex }.toSet(),
                            bookmarks = bookmarks.forBook(item.id),
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

    /**
     * Called as the reader scrolls. [fraction] is how far the top visible
     * paragraph has been scrolled past, so the saved position is finer than
     * "which paragraph" — a paragraph can be several screens tall at a large
     * font size.
     */
    fun onScrolled(index: Int, fraction: Float) {
        val book = _state.value.book
        val para = book.paragraphs.getOrNull(index) ?: return
        val offset = para.start + (fraction.coerceIn(0f, 1f) * para.text.length).toInt()
        progress.save(
            BookProgress(
                itemId = item.id,
                title = item.title,
                author = item.author,
                format = item.format,
                charOffset = offset,
                charCount = book.charCount,
            )
        )
    }

    /** Long-press on a paragraph marks or unmarks it. Returns true if added. */
    fun toggleBookmark(paragraphIndex: Int): Boolean {
        val para = _state.value.book.paragraphs.getOrNull(paragraphIndex) ?: return false
        val added = bookmarks.toggle(
            Bookmark(
                id = newBookmarkId(),
                itemId = item.id,
                bookTitle = item.title,
                author = item.author,
                charOffset = para.start,
                paragraphIndex = paragraphIndex,
                preview = para.text.take(140).replace(Regex("\\s+"), " ").trim(),
                createdAt = System.currentTimeMillis(),
            )
        )
        refreshBookmarks()
        return added
    }

    fun removeBookmark(id: String) {
        bookmarks.remove(id)
        refreshBookmarks()
    }

    fun setBookmarksVisible(visible: Boolean) =
        _state.update { it.copy(showBookmarks = visible) }

    private fun refreshBookmarks() {
        val list = bookmarks.forBook(item.id)
        _state.update {
            it.copy(bookmarks = list, markedParagraphs = list.map { b -> b.paragraphIndex }.toSet())
        }
    }

    fun adjustFont(delta: Float) {
        _state.update { it.copy(fontScale = (it.fontScale + delta).coerceIn(0.8f, 2.0f)) }
    }
}
