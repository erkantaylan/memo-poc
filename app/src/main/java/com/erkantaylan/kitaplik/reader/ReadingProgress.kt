package com.erkantaylan.kitaplik.reader

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where you are in each book, and which you touched last.
 *
 * Position is a character offset into the extracted text — the one value every
 * reading mode agrees on, so switching between paged, bionic and speed reading
 * later is a re-render rather than a conversion between three index schemes.
 */
@Serializable
data class BookProgress(
    val itemId: String,
    val title: String,
    val author: String = "",
    val format: String = "",
    val charOffset: Int = 0,
    val charCount: Int = 0,
    val updatedAt: Long = 0,
) {
    val fraction: Float
        get() = if (charCount > 0) (charOffset.toFloat() / charCount).coerceIn(0f, 1f) else 0f

    val started: Boolean get() = charOffset > 0
}

class ReadingProgressStore(context: Context) {

    private val prefs = context.getSharedPreferences("reading_progress", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): List<BookProgress> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<BookProgress>>(raw) }.getOrDefault(emptyList())
    }

    fun of(itemId: String): BookProgress? = all().firstOrNull { it.itemId == itemId }

    /** Most recently opened first. */
    fun recent(limit: Int = 12): List<BookProgress> =
        all().sortedByDescending { it.updatedAt }.take(limit)

    /** Opened, moved past the first screen, and not finished. */
    fun inProgress(limit: Int = 6): List<BookProgress> =
        all().filter { it.started && it.fraction < 0.98f }
            .sortedByDescending { it.updatedAt }
            .take(limit)

    fun save(progress: BookProgress) {
        val updated = all().filterNot { it.itemId == progress.itemId } +
            progress.copy(updatedAt = System.currentTimeMillis())
        write(updated)
    }

    fun forget(itemId: String) = write(all().filterNot { it.itemId == itemId })

    private fun write(list: List<BookProgress>) {
        // Keep the file small; nobody needs the 200th least-recent book.
        val trimmed = list.sortedByDescending { it.updatedAt }.take(200)
        prefs.edit { putString(KEY, json.encodeToString(trimmed)) }
    }

    private companion object {
        const val KEY = "progress_v1"
    }
}
