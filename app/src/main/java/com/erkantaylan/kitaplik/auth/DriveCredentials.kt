package com.erkantaylan.kitaplik.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The OAuth material the app needs to reach Drive.
 *
 * This is the same Desktop client that the desktop uploader uses, deliberately:
 * a `drive.file` grant is only guaranteed to cover files created by the same
 * app, and Google does not document whether that spans OAuth clients inside one
 * project. Sharing one client removes the question entirely.
 *
 * The scope is `drive.file`, so this credential can only ever reach files the
 * client created — the library folder, and nothing else in the user's Drive.
 */
@Serializable
data class DriveCredentials(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("refresh_token") val refreshToken: String,
) {
    fun isUsable(): Boolean =
        clientId.isNotBlank() && clientSecret.isNotBlank() && refreshToken.isNotBlank()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * Parses the blob produced by `tools/make_app_credentials.py`, accepting
         * either raw JSON or the base64 form that survives being pasted around.
         */
        fun parse(blob: String): DriveCredentials {
            val text = blob.trim()
            val decoded = if (text.startsWith("{")) text else String(
                android.util.Base64.decode(text, android.util.Base64.DEFAULT)
            )
            return json.decodeFromString<DriveCredentials>(decoded).also {
                require(it.isUsable()) { "Credentials are missing a required field" }
            }
        }
    }
}
