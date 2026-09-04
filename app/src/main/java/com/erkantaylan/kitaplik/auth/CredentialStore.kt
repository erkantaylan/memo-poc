package com.erkantaylan.kitaplik.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json

/**
 * Holds the Drive credentials, encrypted at rest under a Keystore-backed key.
 *
 * The refresh token is long-lived (the OAuth app is published "In production",
 * so it does not expire on the 7-day testing clock), which is exactly why it
 * should not sit in plain SharedPreferences.
 */
class CredentialStore(context: Context) {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "drive_credentials",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun load(): DriveCredentials? =
        prefs.getString(KEY, null)?.let {
            runCatching { json.decodeFromString<DriveCredentials>(it) }.getOrNull()
        }

    fun save(credentials: DriveCredentials) {
        prefs.edit().putString(KEY, json.encodeToString(credentials)).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    fun isConfigured(): Boolean = load()?.isUsable() == true

    private companion object {
        const val KEY = "credentials_v1"
    }
}
