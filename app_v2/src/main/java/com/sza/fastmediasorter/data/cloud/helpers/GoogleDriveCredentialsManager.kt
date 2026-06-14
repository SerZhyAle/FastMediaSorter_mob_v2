package com.sza.fastmediasorter.data.cloud.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Google Drive credentials storage using EncryptedSharedPreferences.
 *
 * S0200 Phase 04c: plaintext fallback removed per strategic §5.1 - Keystore failure now
 * propagates instead of silently downgrading to unencrypted storage. The legacy
 * `serializeAccount(GoogleSignInAccount)` / `deserializeAccount` helpers are deleted; the
 * identity domain owns primary-account persistence in [PrimaryGoogleAccountStore].
 *
 * Holds the encrypted Google Drive credential blobs used by both the legacy single-account
 * storage path and the new AppAuth browser OAuth fallback introduced for Quest / XR.
 */
@Singleton
class GoogleDriveCredentialsManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "google_drive_credentials"
        private const val KEY_CREDENTIALS = "credentials"               // Legacy single-account key
        private const val KEY_CREDENTIALS_PREFIX = "credentials_"      // Per-account key prefix
    }

    /**
     * EncryptedSharedPreferences only. If `EncryptedSharedPreferences.create(..)` throws,
     * the exception propagates - no plaintext fallback (strategic §5.1).
     */
    private val prefs: SharedPreferences by lazy {
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
    }

    /**
     * Save credentials to encrypted storage.
     * Saves both to the legacy single-account key and to the per-account key (keyed by accountEmail).
     */
    fun saveCredentials(credentialsJson: String, accountEmail: String? = null) {
        try {
            val edit = prefs.edit().putString(KEY_CREDENTIALS, credentialsJson)
            if (!accountEmail.isNullOrEmpty()) {
                edit.putString("$KEY_CREDENTIALS_PREFIX$accountEmail", credentialsJson)
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

    /** Clear stored credentials from encrypted storage. */
    fun clearStoredCredentials() {
        try {
            prefs.edit().remove(KEY_CREDENTIALS).apply()
            Timber.d("Credentials cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear credentials")
        }
    }

    /**
     * S0200 Phase 05: full wipe - clear the legacy single-account key + every per-account key.
     * Used by [S0200AuthStateWipe] on first launch after the S0200 release.
     */
    fun clearAllCredentials() {
        try {
            val editor = prefs.edit()
            // Drop the legacy single-account blob.
            editor.remove(KEY_CREDENTIALS)
            // Drop every per-account key.
            prefs.all.keys
                .filter { it.startsWith(KEY_CREDENTIALS_PREFIX) }
                .forEach { editor.remove(it) }
            editor.apply()
            Timber.i("all Google Drive credentials cleared from EncryptedSharedPreferences")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear ALL credentials")
        }
    }

    /**
     * S0200 Phase 05: snapshot every stored credentials blob for offline revocation.
     * Returns the JSON values (callers will store these in `pending_revocations` table for retry).
     */
    fun snapshotAllCredentialBlobs(): List<String> {
        return try {
            prefs.all
                .filter { (k, _) -> k == KEY_CREDENTIALS || k.startsWith(KEY_CREDENTIALS_PREFIX) }
                .mapNotNull { (_, v) -> v as? String }
        } catch (e: Exception) {
            Timber.e(e, "Failed to snapshot credentials")
            emptyList()
        }
    }

    /** Check if credentials are stored. */
    fun hasStoredCredentials(): Boolean = loadStoredCredentials() != null
}
