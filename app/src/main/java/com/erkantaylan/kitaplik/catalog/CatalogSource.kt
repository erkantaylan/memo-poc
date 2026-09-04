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
 * Where library items come from: a static file server during development,
 * Google Drive in normal use. The UI only ever sees this interface.
 *
 * Sources hand back a fully-formed [Request] rather than a bare URL, because
 * authentication is the source's business — the downloader should not know
 * whether a backend needs a bearer token.
 */
interface CatalogSource {
    val name: String

    suspend fun fetchCatalog(): Catalog

    /** A ready-to-execute request that streams the item's bytes. */
    suspend fun fileRequest(item: LibraryItem): Request
}

internal val catalogJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

const val SUPPORTED_SCHEMA_VERSION = 2

internal fun parseCatalog(body: String): Catalog {
    val catalog = catalogJson.decodeFromString<Catalog>(body)
    if (catalog.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
        throw IOException(
            "Unsupported catalog schema_version ${catalog.schemaVersion} " +
                "(this build understands $SUPPORTED_SCHEMA_VERSION)"
        )
    }
    return catalog
}

/** Plain HTTP: a directory served as static files with catalog.json at its root. */
class HttpCatalogSource(
    baseUrl: String,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) : CatalogSource {

    override val name = "http"

    private val base: HttpUrl = baseUrl.trimEnd('/').toHttpUrl()

    override suspend fun fetchCatalog(): Catalog = withContext(Dispatchers.IO) {
        val url = base.newBuilder().addPathSegment("catalog.json").build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} from $url")
            parseCatalog(response.body.string())
        }
    }

    override suspend fun fileRequest(item: LibraryItem): Request {
        // Paths come from a filesystem walk and are full of spaces, commas and
        // parentheses; addPathSegment percent-encodes each one correctly.
        val url = base.newBuilder()
            .apply { item.path.split('/').forEach { addPathSegment(it) } }
            .build()
        return Request.Builder().url(url).build()
    }
}
