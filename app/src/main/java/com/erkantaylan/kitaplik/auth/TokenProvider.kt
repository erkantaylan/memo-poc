package com.erkantaylan.kitaplik.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
)

@Serializable
private data class TokenError(
    val error: String = "",
    @SerialName("error_description") val description: String = "",
)

/**
 * Turns the long-lived refresh token into short-lived access tokens.
 *
 * Access tokens last about an hour; they are cached and re-minted a minute
 * early so a download never starts against a token that expires mid-transfer.
 */
class TokenProvider(
    private val store: CredentialStore,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    private var cachedToken: String? = null
    private var expiresAtMillis: Long = 0

    /** Forget the cached access token, e.g. after a 401. */
    suspend fun invalidate() = mutex.withLock {
        cachedToken = null
        expiresAtMillis = 0
    }

    suspend fun accessToken(): String = mutex.withLock {
        val cached = cachedToken
        if (cached != null && System.currentTimeMillis() < expiresAtMillis) return@withLock cached

        val credentials = store.load()
            ?: throw IOException("Drive is not connected — paste credentials in Settings")

        val fresh = withContext(Dispatchers.IO) { refresh(credentials) }
        cachedToken = fresh.accessToken
        expiresAtMillis = System.currentTimeMillis() + (fresh.expiresIn - 60) * 1000
        fresh.accessToken
    }

    private fun refresh(credentials: DriveCredentials): TokenResponse {
        val body = FormBody.Builder()
            .add("client_id", credentials.clientId)
            .add("client_secret", credentials.clientSecret)
            .add("refresh_token", credentials.refreshToken)
            .add("grant_type", "refresh_token")
            .build()

        val request = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val err = runCatching { json.decodeFromString<TokenError>(text) }.getOrNull()
                throw IOException(
                    when (err?.error) {
                        // The two failures worth naming, because the fix differs.
                        "invalid_grant" ->
                            "Refresh token rejected — it was revoked, or the app was moved " +
                                "back to Testing. Re-connect Drive."
                        "invalid_client" ->
                            "Client secret rejected — it was rotated in Cloud Console. " +
                                "Re-connect Drive with fresh credentials."
                        else -> "Token refresh failed: ${err?.error ?: response.code} " +
                            (err?.description ?: "")
                    }
                )
            }
            return json.decodeFromString<TokenResponse>(text)
        }
    }
}
