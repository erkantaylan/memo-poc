package com.erkantaylan.kitaplik.reader

import android.content.Context
import androidx.core.content.edit
import com.erkantaylan.kitaplik.ui.theme.Highlight
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A place worth coming back to, deliberately saved.
 *
 * Distinct from [BookProgress], which answers "where was I" automatically.
 * A bookmark answers "that passage I wanted again", so it keeps a preview of
 * the text and the time it was made.
 *
 * The position is a character offset for the same reason progress is: it
 * survives a font-size change, because it points into the text rather than
 * into a layout.
 */
@Serializable
data class Bookmark(
    val id: String,
    val itemId: String,
    val bookTitle: String,
    val author: String = "",
    val charOffset: Int,
    val paragraphIndex: Int,
    /** Length of the marked word, so it can be highlighted again on reopen. */
    val wordLength: Int = 0,
    /** The marked word on its own, for a compact label. */
    val word: String = "",
    val preview: String,
    val note: String = "",
    val createdAt: Long,
    /** Marks made before colours existed are yellow, like a highlighter. */
    val color: Highlight = Highlight.YELLOW,
)

class BookmarkStore(context: Context) {

    private val prefs = context.getSharedPreferences("bookmarks", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<Bookmark> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<Bookmark>>(raw) }.getOrDefault(emptyList())
    }

    /** Newest first — the order you want when scanning a list. */
    fun recent(limit: Int = 50): List<Bookmark> =
        all().sortedByDescending { it.createdAt }.take(limit)

    /** In reading order, for jumping around inside one book. */
    fun forBook(itemId: String): List<Bookmark> =
        all().filter { it.itemId == itemId }.sortedBy { it.charOffset }

    fun add(bookmark: Bookmark) = write(all() + bookmark)

    fun remove(id: String) = write(all().filterNot { it.id == id })

    /**
     * Adds the bookmark, or removes the one already covering that word.
     * Matching is by character range rather than by paragraph, so a long
     * paragraph can hold several marks.
     */
    fun toggle(bookmark: Bookmark): Boolean {
        val existing = all().firstOrNull {
            it.itemId == bookmark.itemId &&
                bookmark.charOffset < it.charOffset + maxOf(it.wordLength, 1) &&
                it.charOffset < bookmark.charOffset + maxOf(bookmark.wordLength, 1)
        }
        return if (existing != null) {
            remove(existing.id); false
        } else {
            add(bookmark); true
        }
    }

    private fun write(list: List<Bookmark>) {
        prefs.edit { putString(KEY, json.encodeToString(list.sortedBy { it.createdAt })) }
    }

    private companion object {
        const val KEY = "bookmarks_v1"
    }
}

fun newBookmarkId(): String =
    java.lang.Long.toHexString(System.nanoTime()) + (0..9999).random().toString(36)
