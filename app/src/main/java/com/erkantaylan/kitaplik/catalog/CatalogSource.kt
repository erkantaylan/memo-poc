package com.erkantaylan.kitaplik.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Where library items come from. Today a static file server on the dev
 * workstation; tomorrow Google Drive. The UI only ever sees this interface, so
 * swapping the backend touches no screen code.
 */
interface CatalogSource {
    val name: String

    suspend fun fetchCatalog(): Catalog

    /** URL a downloader can stream the given item from. */
    fun fileUrl(item: LibraryItem): HttpUrl
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

const val SUPPORTED_SCHEMA_VERSION = 2

/** Plain HTTP: a directory served as static files with catalog.json at its root. */
class HttpCatalogSource(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) : CatalogSource {

    override val name = "http"

    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    override suspend fun fetchCatalog(): Catalog = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegment("catalog.json").build()
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from $url")
            }
            val catalog = json.decodeFromString<Catalog>(response.body.string())
            if (catalog.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
                throw IOException(
                    "Unsupported catalog schema_version ${catalog.schemaVersion} " +
                        "(this build understands $SUPPORTED_SCHEMA_VERSION)"
                )
            }
            catalog
        }
    }

    override fun fileUrl(item: LibraryItem): HttpUrl {
        // Paths come from a filesystem walk and are full of spaces, commas and
        // parentheses; addPathSegment percent-encodes each one correctly.
        return base.newBuilder()
            .apply { item.path.split('/').forEach { addPathSegment(it) } }
            .build()
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder().build()
    }
}
