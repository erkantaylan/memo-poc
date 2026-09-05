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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** How much of a long passage a bookmark shows as its label. */
private const val LABEL_CHARS = 90

/** How far a long press may reach for a word before giving up. */
private const val NEAREST_WORD_REACH = 48

/**
 * A trip away from where you were reading.
 *
 * Jumping to a search hit or a bookmark is not reading, but the reader records
 * position continuously and cannot tell the difference — so a glance at
 * chapter twelve used to cost you your place in chapter three. While an
 * excursion is open no progress is written, and one tap puts you back.
 *
 * It closes itself once you have stayed long enough to be reading rather than
 * looking, at which point where you are becomes where you are.
 */
data class Excursion(
    val offset: Int,
    val paragraph: Int,
    val label: String,
    val dwellMs: Long = 0,
)

/** Long enough to be reading rather than looking. */
private const val EXCURSION_SETTLES_MS = 90_000L

/** How often ReaderScreen ticks; the excursion counts in the same beats. */
private const val TICK_MS = 5_000L

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
    /** Set while you are away from where you were actually reading. */
    val excursion: Excursion? = null,
    val searchOpen: Boolean = false,
    val query: String = "",
    val matches: List<Match> = emptyList(),
    /** Which hit you are standing on, -1 before you step to one. */
    val matchIndex: Int = -1,
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
    private var searchJob: kotlinx.coroutines.Job? = null
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
                    val reading = progress.of(item.id)?.charOffset ?: 0
                    val saved = openAtOffset ?: reading
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
                            // Opened at a bookmark rather than at your place:
                            // that is a look, so keep the way back rather than
                            // letting the bookmark quietly become your place.
                            excursion = if (openAtOffset != null && openAtOffset != reading)
                                Excursion(
                                    offset = reading,
                                    paragraph = book.paragraphAt(reading),
                                    label = percentOf(reading, book.charCount),
                                )
                            else null,
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

        // An excursion is a look, not a read. Speed still counts — your eyes
        // move either way — but where you were stays where it was.
        if (_state.value.excursion != null) return

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
     * Bookmark a run of text the finger dragged over, snapped out to whole
     * words at both ends — half of "Komatsu" is not a thing you meant to keep.
     */
    fun bookmarkRange(paragraphIndex: Int, from: Int, to: Int): BookmarkToggle {
        val para = _state.value.book.paragraphs.getOrNull(paragraphIndex)
            ?: return BookmarkToggle.NO_WORD
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        val start = wordBoundsAt(para.text, lo)?.first ?: return BookmarkToggle.NO_WORD
        val end = wordBoundsAt(para.text, (hi - 1).coerceAtLeast(lo))?.second
            ?: return BookmarkToggle.NO_WORD
        if (end <= start) return BookmarkToggle.NO_WORD

        val text = para.text.substring(start, end)
        val added = bookmarks.toggle(
            Bookmark(
                id = newBookmarkId(),
                itemId = item.id,
                bookTitle = item.title,
                author = item.author,
                charOffset = para.start + start,
                paragraphIndex = paragraphIndex,
                wordLength = end - start,
                // The label is what you see in a list, so a long passage is cut
                // short there while the mark itself keeps its full length.
                word = if (text.length <= LABEL_CHARS) text
                       else text.take(LABEL_CHARS).trimEnd() + "\u2026",
                preview = para.text
                    .substring(maxOf(0, start - 40), minOf(para.text.length, end + 80))
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
    fun onTick() {
        val away = _state.value.excursion
        if (away != null) {
            val dwelt = away.dwellMs + TICK_MS
            if (dwelt >= EXCURSION_SETTLES_MS) endExcursion()
            else _state.update { it.copy(excursion = away.copy(dwellMs = dwelt)) }
        }
        refreshSpeed()
    }

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

    /**
     * Leave your place to go and look at something, keeping a way back.
     *
     * The anchor is taken once: stepping through twelve search hits is one
     * excursion from where you started, not twelve nested ones.
     */
    fun beginExcursion() = _state.update {
        if (it.excursion != null) it
        else it.copy(
            excursion = Excursion(
                offset = lastOffset,
                paragraph = it.book.paragraphAt(lastOffset),
                label = percentOf(lastOffset, it.book.charCount),
            )
        )
    }

    /** Stay: where you have ended up becomes where you were. */
    fun endExcursion() {
        if (_state.value.excursion == null) return
        _state.update { it.copy(excursion = null) }
        onScrolled(_state.value.book.paragraphAt(lastOffset), 0f)
    }

    /** Go back, and forget the trip. */
    fun returnFromExcursion(): Int? {
        val at = _state.value.excursion ?: return null
        _state.update { it.copy(excursion = null) }
        return at.paragraph
    }

    private fun percentOf(offset: Int, total: Int): String =
        if (total <= 0) "" else "${(offset * 100 / total).coerceIn(0, 100)}%"

    fun setSearchOpen(open: Boolean) = _state.update {
        // Closing clears the query, so reopening does not resume someone
        // else's search from an hour ago.
        if (open) it.copy(searchOpen = true, showPanel = false)
        else it.copy(searchOpen = false, query = "", matches = emptyList(), matchIndex = -1)
    }

    fun onQueryChange(query: String) {
        searchJob?.cancel()
        if (query.trim().length < MIN_QUERY) {
            _state.update { it.copy(query = query, matches = emptyList(), matchIndex = -1) }
            return
        }
        _state.update { it.copy(query = query) }
        searchJob = viewModelScope.launch {
            // Typing is faster than scanning a 700,000 character book, and
            // every keystroke would otherwise start a scan the next one
            // throws away.
            delay(180)
            val book = _state.value.book
            val hits = withContext(Dispatchers.Default) { search(book, query) }
            _state.update {
                if (it.query != query) it
                // Land on the first hit at or after where you are reading,
                // because you are looking for the next one, not the first in
                // the book.
                else it.copy(
                    excursion = it.excursion ?: if (hits.isEmpty()) null else Excursion(
                        offset = lastOffset,
                        paragraph = it.book.paragraphAt(lastOffset),
                        label = percentOf(lastOffset, it.book.charCount),
                    ),
                    matches = hits, matchIndex = if (hits.isEmpty()) -1 else
                    hits.indexOfFirst { m -> m.paragraph >= it.startParagraph }
                        .takeIf { at -> at >= 0 } ?: 0,
                )
            }
        }
    }

    fun stepMatch(delta: Int) {
        beginExcursion()
        stepMatchOnly(delta)
    }

    private fun stepMatchOnly(delta: Int) = _state.update {
        if (it.matches.isEmpty()) it
        else it.copy(
            matchIndex = ((it.matchIndex + delta) % it.matches.size + it.matches.size)
                % it.matches.size
        )
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
