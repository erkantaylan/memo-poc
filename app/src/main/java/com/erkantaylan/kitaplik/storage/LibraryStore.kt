package com.erkantaylan.kitaplik.storage

import com.erkantaylan.kitaplik.catalog.LibraryItem
import java.io.File
import java.security.MessageDigest

/**
 * Where downloaded items live on the device.
 *
 * Files are kept in app-internal storage under a flat directory keyed by item
 * id, so nothing depends on the library's folder layout and uninstalling the
 * app cleans everything up. Bodies never go into a database — the 6 MB-class
 * limits that applies to are exactly what this avoids.
 */
class LibraryStore(private val root: File) {

    init {
        root.mkdirs()
    }

    fun fileFor(item: LibraryItem): File = File(root, "${item.id}.${item.format}")

    /** Partial download target; promoted to [fileFor] once complete. */
    fun partFileFor(item: LibraryItem): File = File(root, "${item.id}.${item.format}.part")

    /** A download counts as present only if the size matches the catalog. */
    fun isDownloaded(item: LibraryItem): Boolean {
        val f = fileFor(item)
        return f.isFile && f.length() == item.bytes
    }

    fun delete(item: LibraryItem) {
        fileFor(item).delete()
        partFileFor(item).delete()
    }

    fun downloadedBytes(): Long =
        root.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }

    /** Ids of everything currently downloaded, for restoring state on launch. */
    fun downloadedIds(): Set<String> =
        root.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile && !it.name.endsWith(".part") }
            .map { it.name.substringBeforeLast('.') }
            .toSet()

    companion object {
        /** Streaming MD5, so a 70 MB PDF never lands in memory at once. */
        fun md5(file: File): String {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
