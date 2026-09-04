package com.erkantaylan.kitaplik.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Locale

/**
 * Mirrors catalog.json as produced by books/scripts/build-catalog.mjs.
 *
 * One entry per file. A PDF, an EPUB and a markdown rendering are different
 * artifacts with different uses, so they are separate items rather than
 * "formats" of a shared parent — nothing here has to pick between them.
 *
 * The catalog is the only contract between the app and wherever the files
 * actually live, so nothing below mentions HTTP, Drive, or the filesystem.
 */
@Serializable
data class LibraryItem(
    val id: String,
    val title: String,
    val filename: String,
    val category: String,
    val format: String,
    val path: String,
    val bytes: Long,
    val modified: Long,
    val md5: String? = null,
    /** Populated once the library has been synced to Drive. */
    @SerialName("drive_id") val driveId: String? = null,
) {
    val kind: ItemKind get() = ItemKind.of(format)
}

@Serializable
data class Catalog(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("generated_at") val generatedAt: String,
    val count: Int,
    val items: List<LibraryItem>,
)

/**
 * What the app can actually do with an item. Each kind is handled on its own
 * terms rather than through one generic path.
 */
enum class ItemKind(val format: String, val label: String, val mimeType: String) {
    EPUB("epub", "EPUB", "application/epub+zip"),
    MARKDOWN("md", "MD", "text/markdown"),
    PDF("pdf", "PDF", "application/pdf"),
    UNKNOWN("", "FILE", "application/octet-stream");

    companion object {
        fun of(format: String): ItemKind =
            entries.firstOrNull { it.format == format.lowercase() } ?: UNKNOWN
    }
}

fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}
