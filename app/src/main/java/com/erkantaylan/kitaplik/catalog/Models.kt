package com.erkantaylan.kitaplik.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Mirrors catalog.json as produced by books/scripts/build-catalog.mjs.
 *
 * The catalog is the only contract between the app and wherever the files
 * actually live, so nothing here mentions HTTP, Drive, or the filesystem.
 */
@Serializable
data class BookFormat(
    val path: String,
    val bytes: Long,
    val modified: Long,
    val md5: String? = null,
    /** Populated once the library has been synced to Drive. */
    @SerialName("drive_id") val driveId: String? = null,
)

@Serializable
data class Book(
    val id: String,
    val title: String,
    val filename: String,
    val category: String,
    val formats: Map<String, BookFormat>,
) {
    /** The rendition we would open for reading: EPUB if present, else markdown. */
    val readableFormat: String?
        get() = when {
            formats.containsKey(FORMAT_EPUB) -> FORMAT_EPUB
            formats.containsKey(FORMAT_MD) -> FORMAT_MD
            else -> null
        }

    /** Formats in display order, skipping ones this book doesn't have. */
    val availableFormats: List<String>
        get() = FORMAT_ORDER.filter { formats.containsKey(it) }

    val totalBytes: Long
        get() = formats.values.sumOf { it.bytes }
}

@Serializable
data class Catalog(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("generated_at") val generatedAt: String,
    val count: Int,
    val books: List<Book>,
)

const val FORMAT_EPUB = "epub"
const val FORMAT_MD = "md"
const val FORMAT_PDF = "pdf"

val FORMAT_ORDER = listOf(FORMAT_EPUB, FORMAT_MD, FORMAT_PDF)

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}
