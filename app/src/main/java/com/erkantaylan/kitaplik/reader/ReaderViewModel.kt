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
    /** Measured words per minute, 0 while there is not enough evidence. */
    val wpm: Int = 0,
    /** True while that figure is an early estimate rather than settled. */
    val wpmProvisional: Boolean = true,
    /** Minutes of reading left in this book at that speed, null if unknown. */
    val minutesLeft: Long? = null,
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

    private var tracker: SpeedTracker? = null
    private var lastOffset = 0

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
                    // Characters per word differs per book — Turkish runs
                    // longer than English — so derive it rather than assume.
                    val perWord = if (book.wordCount > 0)
                        book.charCount.toDouble() / book.wordCount else 5.5
                    tracker = SpeedTracker(perWord)
                    lastOffset = saved
                    refreshSpeed()
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
        lastOffset = offset

        tracker?.sample(offset)
        refreshSpeed()

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

    /**
     * Long-press marks the word under the finger. [indexInParagraph] is the
     * character the touch landed on, which is expanded to the whole word so
     * the mark lands on something meaningful rather than mid-syllable.
     */
    fun toggleBookmarkAt(paragraphIndex: Int, indexInParagraph: Int): Boolean {
        val para = _state.value.book.paragraphs.getOrNull(paragraphIndex) ?: return false
        val (wordStart, wordEnd) = wordBoundsAt(para.text, indexInParagraph)
        val word = para.text.substring(wordStart, wordEnd)

        val added = bookmarks.toggle(
            Bookmark(
                id = newBookmarkId(),
                itemId = item.id,
                bookTitle = item.title,
                author = item.author,
                charOffset = para.start + wordStart,
                paragraphIndex = paragraphIndex,
                wordLength = wordEnd - wordStart,
                word = word,
                // The word alone is a poor label in a list, so keep the run of
                // text around it as context.
                preview = para.text
                    .substring(maxOf(0, wordStart - 40), minOf(para.text.length, wordStart + 120))
                    .replace(Regex("\\s+"), " ")
                    .trim(),
                createdAt = System.currentTimeMillis(),
            )
        )
        refreshBookmarks()
        return added
    }

    /** Expands a character index out to the word containing it. */
    private fun wordBoundsAt(text: String, index: Int): Pair<Int, Int> {
        if (text.isEmpty()) return 0 to 0
        val i = index.coerceIn(0, text.length - 1)
        if (!text[i].isLetterOrDigit()) {
            // Landed on a space or punctuation: mark just that character.
            return i to (i + 1)
        }
        var start = i
        while (start > 0 && text[start - 1].isLetterOrDigit()) start--
        var end = i + 1
        while (end < text.length && text[end].isLetterOrDigit()) end++
        return start to end
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

    /** Closes the current reading segment: backgrounded, or reader closed. */
    fun onPaused() {
        tracker?.stop()
        refreshSpeed()
    }

    override fun onCleared() {
        onPaused()
        super.onCleared()
    }

    /** Recomputes the speed as time passes, not only as the position moves. */
    fun onTick() = refreshSpeed()

    /** For when a scroll through the pages has polluted the figure. */
    fun resetSpeed() {
        tracker?.reset()
        refreshSpeed()
    }

    private fun refreshSpeed() {
        val wpm = tracker?.wpm() ?: 0
        val provisional = tracker?.isProvisional() ?: true
        val book = _state.value.book
        val remainingChars = (book.charCount - lastOffset).coerceAtLeast(0)
        val perWord = if (book.wordCount > 0)
            book.charCount.toDouble() / book.wordCount else 5.5
        val minutes = if (wpm > 0 && book.charCount > 0)
            ((remainingChars / perWord) / wpm).toLong() else null
        _state.update {
            it.copy(wpm = wpm, wpmProvisional = provisional, minutesLeft = minutes)
        }
    }

    fun adjustFont(delta: Float) {
        _state.update { it.copy(fontScale = (it.fontScale + delta).coerceIn(0.8f, 2.0f)) }
    }
}
