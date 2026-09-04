package com.erkantaylan.kitaplik.catalog

import com.erkantaylan.kitaplik.auth.TokenProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
private data class DriveFile(val id: String, val name: String)

@Serializable
private data class DriveFileList(val files: List<DriveFile> = emptyList())

/**
 * Reads the library out of Google Drive.
 *
 * Only two calls are ever made: find catalog.json, then download items by the
 * `drive_id` the catalog already carries. There is no folder traversal and no
 * dependence on Drive's structure — the catalog is the index, Drive is a blob
 * store. Under `drive.file` the account's other files are invisible to this
 * client, so the search below can only ever match our own catalog.
 */
class DriveCatalogSource(
    private val tokens: TokenProvider,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) : CatalogSource {

    override val name = "drive"

    override suspend fun fetchCatalog(): Catalog = withContext(Dispatchers.IO) {
        val catalogId = findCatalogId()
        val request = authorized(mediaRequest(catalogId))
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(describe(response.code, "downloading catalog.json"))
            }
            parseCatalog(response.body.string())
        }
    }

    override suspend fun fileRequest(item: LibraryItem): Request {
        val id = item.driveId
            ?: throw IOException(
                "\"${item.title}\" has no Drive id — re-run drive_sync.py to link it"
            )
        return authorized(mediaRequest(id))
    }

    private fun mediaRequest(fileId: String): Request.Builder =
        Request.Builder().url(
            "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
        )

    private suspend fun authorized(builder: Request.Builder): Request =
        builder.header("Authorization", "Bearer ${tokens.accessToken()}").build()

    private suspend fun findCatalogId(): String {
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?q=" + java.net.URLEncoder.encode("name = 'catalog.json' and trashed = false", "UTF-8") +
            "&fields=files(id,name)&pageSize=10&orderBy=modifiedTime desc"

        val request = authorized(Request.Builder().url(url))
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                throw IOException(describe(response.code, "searching for catalog.json"))
            }
            val files = catalogJson.decodeFromString<DriveFileList>(text).files
            return files.firstOrNull()?.id ?: throw IOException(
                "No catalog.json found in Drive. Run drive_sync.py to upload the library."
            )
        }
    }

    private fun describe(code: Int, what: String): String = when (code) {
        401 -> "Drive rejected the token while $what — re-connect Drive."
        403 -> "Drive refused access while $what. If this persists, the Drive API " +
            "may be disabled for the project."
        404 -> "Not found while $what — the file may have been deleted from Drive."
        else -> "Drive returned HTTP $code while $what"
    }
}
