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

/** How far a long press may reach for a word before giving up. */
private const val NEAREST_WORD_REACH = 48

/** What a long press did, so the reader can say so. */
enum class BookmarkToggle { ADDED, REMOVED, NO_WORD }

data class ReaderUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val book: BookText = BookText.EMPTY,
    val startParagraph: Int = 0,
    /** How far into that paragraph the saved position sat, 0..1. */
    val startFraction: Float = 0f,
    /** Measured words per minute, 0 while there is not enough evidence. */
    val wpm: Int = 0,
    /** True while that figure is an early estimate rather than settled. */
    val wpmProvisional: Boolean = true,
    /** Minutes of reading left in this book at that speed, null if unknown. */
    val minutesLeft: Long? = null,
    val bionic: Boolean = false,
    val bionicStrength: BionicStrength = BionicStrength.MEDIUM,
    val style: ReaderStyle = ReaderStyle.DEFAULT,
    /** The reading panel: bionic, strength and text size, over the text. */
    val showPanel: Boolean = false,
    val markedParagraphs: Set<Int> = emptySet(),
    val bookmarks: List<Bookmark> = emptyList(),
    val showBookmarks: Boolean = false,
)

class ReaderViewModel(
    private val item: LibraryItem,
    private val store: LibraryStore,
    private val progress: ReadingProgressStore,
    private val bookmarks: BookmarkStore,
    private val preferences: ReaderPreferences,
    private val cacheDir: File,
    /** Set when opening from a bookmark, overriding the saved position. */
    private val openAtOffset: Int? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ReaderUiState(
            bionic = preferences.bionic,
            bionicStrength = preferences.strength,
            style = preferences.style,
        )
    )
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
    fun toggleBookmarkAt(paragraphIndex: Int, indexInParagraph: Int): BookmarkToggle {
        val para = _state.value.book.paragraphs.getOrNull(paragraphIndex)
            ?: return BookmarkToggle.NO_WORD
        val (wordStart, wordEnd) = wordBoundsAt(para.text, indexInParagraph)
            ?: return BookmarkToggle.NO_WORD
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
        return if (added) BookmarkToggle.ADDED else BookmarkToggle.REMOVED
    }

    /**
     * Expands a character index out to the word containing it.
     *
     * A finger is wider than a letter, so a long press regularly lands in the
     * gap between words, on a full stop, or past the end of a short line. Those
     * used to be bookmarked as themselves, which produced marks on a single
     * space or a lone dot. Now the tap snaps to the nearest word instead, and
     * returns null only when there is genuinely no word within reach — a rule
     * of asterisks has nothing to mark.
     */
    private fun wordBoundsAt(text: String, index: Int): Pair<Int, Int>? {
        if (text.isEmpty()) return null
        val i = index.coerceIn(0, text.length - 1)
        val at = if (isWordChar(text, i)) i else nearestWordChar(text, i) ?: return null

        var start = at
        while (start > 0 && isWordChar(text, start - 1)) start--
        var end = at + 1
        while (end < text.length && isWordChar(text, end)) end++
        return start to end
    }

    /**
     * Apostrophes bind when they sit between letters, so "don't" is one word
     * and so is "İstanbul'da" — Turkish hangs suffixes off proper nouns that
     * way constantly, and marking "İstanbul" while dropping "da" reads wrong.
     */
    private fun isWordChar(text: String, i: Int): Boolean {
        val c = text[i]
        if (c.isLetterOrDigit()) return true
        if (c != '\'' && c != '\u2019') return false
        return i > 0 && text[i - 1].isLetterOrDigit() &&
            i + 1 < text.length && text[i + 1].isLetterOrDigit()
    }

    /**
     * Nearest word character either side, ties going left: a press past the end
     * of a line almost always means the last word on it, not the first word of
     * the next one.
     */
    private fun nearestWordChar(text: String, from: Int): Int? {
        for (d in 1..NEAREST_WORD_REACH) {
            val left = from - d
            if (left >= 0 && isWordChar(text, left)) return left
            val right = from + d
            if (right < text.length && isWordChar(text, right)) return right
        }
        return null
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

    fun setPanelVisible(visible: Boolean) =
        _state.update { it.copy(showPanel = visible) }

    fun setBionic(on: Boolean) {
        preferences.bionic = on
        _state.update { it.copy(bionic = on) }
    }

    fun setBionicStrength(strength: BionicStrength) {
        preferences.strength = strength
        _state.update { it.copy(bionicStrength = strength) }
    }

    /** Every control writes the whole blob, the way the memoriser did. */
    fun setStyle(style: ReaderStyle) {
        preferences.style = style
        _state.update { it.copy(style = style) }
    }

    fun resetStyle() = setStyle(ReaderStyle.DEFAULT)
}
