package com.erkantaylan.kitaplik.download

import com.erkantaylan.kitaplik.catalog.CatalogSource
import com.erkantaylan.kitaplik.catalog.LibraryItem
import com.erkantaylan.kitaplik.storage.LibraryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import kotlin.coroutines.coroutineContext

sealed interface DownloadState {
    data object Absent : DownloadState
    data class InProgress(val bytesRead: Long, val total: Long) : DownloadState {
        val fraction: Float get() = if (total > 0) bytesRead.toFloat() / total else 0f
    }
    data object Done : DownloadState
    data class Failed(val message: String) : DownloadState
}

class Downloader(
    private val source: CatalogSource,
    private val store: LibraryStore,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) {

    /**
     * Streams [item] to disk, resuming a previous partial transfer when the
     * server supports it. Returns the finished file.
     */
    suspend fun download(
        item: LibraryItem,
        onProgress: (bytesRead: Long, total: Long) -> Unit = { _, _ -> },
    ): File = withContext(Dispatchers.IO) {
        val target = store.fileFor(item)
        if (store.isDownloaded(item)) return@withContext target

        val part = store.partFileFor(item)
        val alreadyHave = if (part.isFile) part.length() else 0L

        val request = Request.Builder()
            .url(source.fileUrl(item))
            .apply { if (alreadyHave > 0) header("Range", "bytes=$alreadyHave-") }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for ${item.title}")
            }

            // A server that ignores Range answers 200 with the whole body — the
            // dev python server does exactly this, Drive does not. Only append
            // when the server actually confirmed the range with a 206.
            val resuming = response.code == 206
            if (!resuming && part.exists()) part.delete()

            val body = response.body
            val startedAt = if (resuming) alreadyHave else 0L
            val total = if (body.contentLength() >= 0) startedAt + body.contentLength() else item.bytes

            var written = startedAt
            onProgress(written, total)

            body.byteStream().use { input ->
                java.io.FileOutputStream(part, resuming).buffered().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var sinceReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        sinceReport += read
                        // Reporting every chunk would thrash recomposition.
                        if (sinceReport >= 256 * 1024) {
                            onProgress(written, total)
                            sinceReport = 0
                        }
                    }
                }
            }
            onProgress(written, total)
        }

        if (part.length() != item.bytes) {
            val actual = part.length()
            part.delete()
            throw IOException("Size mismatch: got $actual bytes, catalog says ${item.bytes}")
        }

        item.md5?.let { expected ->
            val actual = LibraryStore.md5(part)
            if (!actual.equals(expected, ignoreCase = true)) {
                part.delete()
                throw IOException("Checksum mismatch for ${item.title}")
            }
        }

        target.delete()
        if (!part.renameTo(target)) {
            part.delete()
            throw IOException("Could not move the finished file into place")
        }
        target
    }
}
