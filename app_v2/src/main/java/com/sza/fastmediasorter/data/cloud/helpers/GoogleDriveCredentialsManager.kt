@file:Suppress("DEPRECATION")
package com.sza.fastmediasorter.data.cloud.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Drive credentials storage using EncryptedSharedPreferences.
 * Handles serialization/deserialization of account info and provides fallback mechanisms.
 */
@Singleton
class GoogleDriveCredentialsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "google_drive_credentials"
        private const val KEY_CREDENTIALS = "credentials"               // Legacy single-account key
        private const val KEY_CREDENTIALS_PREFIX = "credentials_"      // Per-account key prefix
    }

    /**
     * Lazy-initialized EncryptedSharedPreferences with fallback to standard SharedPreferences.
     * Fallback occurs if EncryptedSharedPreferences creation fails (e.g., due to keystore issues).
     */
    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to create EncryptedSharedPreferences, using standard SharedPreferences")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Serialize GoogleSignInAccount to JSON string for storage.
     *
     * @param account Google Sign-In account to serialize
     * @return JSON string with account email, id, and displayName
     */
    fun serializeAccount(account: GoogleSignInAccount): String {
        return JSONObject().apply {
            put("email", account.email)
            put("id", account.id)
            put("displayName", account.displayName)
        }.toString()
    }

    /**
     * Deserialize account JSON to extract email.
     *
     * @param json JSON string from serializeAccount()
     * @return Email address or empty string on failure
     */
    fun deserializeAccount(json: String): String {
        return try {
            val obj = JSONObject(json)
            obj.getString("email")
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize account")
            ""
        }
    }

    /**
     * Save credentials to encrypted storage.
     * Saves both to the legacy single-account key and to the per-account key (keyed by accountEmail).
     *
     * @param credentialsJson Serialized credentials (from serializeAccount)
     * @param accountEmail Optional account email for per-account key storage
     */
    fun saveCredentials(credentialsJson: String, accountEmail: String? = null) {
        try {
            val edit = prefs.edit().putString(KEY_CREDENTIALS, credentialsJson) // Always update legacy key
            if (!accountEmail.isNullOrEmpty()) {
                edit.putString("$KEY_CREDENTIALS_PREFIX$accountEmail", credentialsJson) // Save per-account key
            }
            edit.apply()
            Timber.d("Credentials saved successfully (account: ${accountEmail ?: "unknown"})")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save credentials")
        }
    }

    /**
     * Load stored credentials from encrypted storage.
     * If [accountEmail] is provided, tries per-account key first, then falls back to legacy.
     *
     * @param accountEmail Optional account email for per-account key lookup
     * @return Stored credentials JSON or null if not found
     */
    fun loadStoredCredentials(accountEmail: String? = null): String? {
        return try {
            if (!accountEmail.isNullOrEmpty()) {
                val perAccount = prefs.getString("$KEY_CREDENTIALS_PREFIX$accountEmail", null)
                if (perAccount != null) return perAccount
            }
            prefs.getString(KEY_CREDENTIALS, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load credentials")
            null
        }
    }

    /**
     * Clear stored credentials from encrypted storage.
     */
    fun clearStoredCredentials() {
        try {
            prefs.edit().remove(KEY_CREDENTIALS).apply()
            Timber.d("Credentials cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear credentials")
        }
    }

    /**
     * Check if credentials are stored.
     *
     * @return true if credentials exist in storage
     */
    fun hasStoredCredentials(): Boolean {
        return loadStoredCredentials() != null
    }
}
