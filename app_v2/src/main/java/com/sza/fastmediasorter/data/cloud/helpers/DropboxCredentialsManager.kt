package com.sza.fastmediasorter.data.cloud.helpers

import android.content.Context
import android.content.SharedPreferences
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.core.security.EncryptedPrefsOpener
import com.sza.fastmediasorter.data.identity.transfer.TransferableSignInWriter
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInProviderKeys
import com.sza.fastmediasorter.domain.identity.transfer.TransferableSignInRecord
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the Dropbox OAuth credential blobs - serialized `DbxCredential` JSON, which carries the
 * long-lived refresh token - in EncryptedSharedPreferences.
 *
 * S2115: the plaintext fallback is removed. `EncryptedSharedPreferences.create(..)` used to be
 * wrapped in a catch that reopened the same file in `MODE_PRIVATE`, so a Keystore failure wrote the
 * refresh token to disk unencrypted and said nothing. Storage failure now degrades to "no
 * credentials" per accessor, matching the policy already adopted for Google Drive in
 * [GoogleDriveCredentialsManager].
 *
 * The preference file name and the key names are unchanged, so credentials written by the previous
 * implementation stay readable and no re-authentication is forced.
 */
@Singleton
class DropboxCredentialsManager internal constructor(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val transferWriter: Lazy<TransferableSignInWriter>,
    private val openEncryptedPrefs: (Context, String) -> SharedPreferences
) {
    /**
     * The opener is a parameter only so a JVM test, which has no AndroidKeyStore, can stand in for the
     * encrypted store: the real opener empties the plaintext file whenever it fails, and the purge tests
     * could not observe the purge through it.
     */
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @ApplicationScope appScope: CoroutineScope,
        transferWriter: Lazy<TransferableSignInWriter>
    ) : this(context, appScope, transferWriter, EncryptedPrefsOpener::open)

    companion object {
        private const val PREFS_NAME = "dropbox_credentials"
        private const val KEY_CREDENTIALS = "credentials_json" // Legacy single-account key
        private const val KEY_CREDENTIALS_PREFIX = "credentials_json_" // Per-account key prefix
    }

    @Volatile
    private var legacyPlaintextPurged = false

    /**
     * EncryptedSharedPreferences only - never downgraded to unencrypted storage. A keyset the Keystore master
     * key no longer decrypts is reset once by [EncryptedPrefsOpener], so the store is not dead on every access.
     */
    private val prefs: SharedPreferences by lazy { openEncryptedPrefs(context, PREFS_NAME) }

    /** Save under the legacy single-account key and, when the account is known, the per-account key. */
    fun saveCredentials(credentialsJson: String, accountEmail: String? = null) {
        purgeLegacyPlaintextCredentials()
        guardStorage("save", Unit) {
            val edit = prefs.edit().putString(KEY_CREDENTIALS, credentialsJson)
            if (!accountEmail.isNullOrEmpty()) {
                edit.putString("$KEY_CREDENTIALS_PREFIX$accountEmail", credentialsJson)
            }
            edit.apply()
            Timber.d("Dropbox credentials saved (account: ${accountEmail ?: "unknown"})")
        }
        publishTransferableSecret(credentialsJson, accountEmail)
    }

    /** Load credentials, preferring the per-account key when [accountEmail] is given. */
    fun loadStoredCredentials(accountEmail: String? = null): String? {
        purgeLegacyPlaintextCredentials()
        return guardStorage("load", null) {
            val perAccount = if (accountEmail.isNullOrEmpty()) {
                null
            } else {
                prefs.getString("$KEY_CREDENTIALS_PREFIX$accountEmail", null)
            }
            perAccount ?: prefs.getString(KEY_CREDENTIALS, null)
        }
    }

    /** Clear stored credentials (legacy single-account key and per-account entry/all per-account entries). */
    fun clearStoredCredentials(accountEmail: String? = null) {
        purgeLegacyPlaintextCredentials()
        guardStorage("clear", Unit) {
            val editor = prefs.edit().remove(KEY_CREDENTIALS)
            if (!accountEmail.isNullOrEmpty()) {
                editor.remove("$KEY_CREDENTIALS_PREFIX$accountEmail")
            } else {
                prefs.all.keys
                    .filter { it.startsWith(KEY_CREDENTIALS_PREFIX) }
                    .forEach { editor.remove(it) }
            }
            editor.apply()
        }
        forgetTransferableSecret()
    }

    /**
     * Mirror the credential into the transferable record so it survives a migration (S2101).
     *
     * Fire and forget by design: the transfer write is best-effort and must never affect the
     * credential save the user just completed, so it neither blocks that save nor reports a failure
     * back to it. [TransferableSignInWriter] merges, leaving the other providers' entries intact.
     */
    private fun publishTransferableSecret(credentialsJson: String, accountEmail: String?) {
        appScope.launch {
            transferWriter.get().putEntry(
                TransferableSignInProviderKeys.DROPBOX,
                TransferableSignInRecord.Kind.SECRET,
                mapOf(
                    TransferredCredentialPayload.EMAIL to accountEmail.orEmpty(),
                    TransferredCredentialPayload.CREDENTIALS to credentialsJson
                )
            )
        }
    }

    /** Drop this provider's transferable entry on sign-out; fire and forget for the same reason. */
    private fun forgetTransferableSecret() {
        appScope.launch {
            transferWriter.get().removeEntry(TransferableSignInProviderKeys.DROPBOX)
        }
    }

    /**
     * Degrade any credential-storage failure to [fallback] and a log line. The catch is deliberately
     * broad: a Keystore or Tink failure surfaces as GeneralSecurityException, IOException,
     * ProviderException or IllegalStateException depending on how it broke, and letting any of them
     * escape into a caller is the silent-failure mode this class exists to prevent. Same policy as
     * [GoogleDriveCredentialsManager].
     */
    @Suppress("TooGenericExceptionCaught")
    private fun <T> guardStorage(operation: String, fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            Timber.e(e, "Dropbox credential storage failed: $operation")
            fallback
        }
    }

    /**
     * Erase credential entries left by the removed plaintext fallback. In a healthy encrypted store
     * every key name is Base64 AES-SIV, so a key named literally [KEY_CREDENTIALS] or carrying the
     * literal [KEY_CREDENTIALS_PREFIX] prefix can only have been written by that fallback. Runs
     * before the encrypted store is touched, because the installs that need the cleanup are exactly
     * the ones where creating that store still fails.
     */
    @Synchronized
    private fun purgeLegacyPlaintextCredentials() {
        if (legacyPlaintextPurged) return
        guardStorage("purge", Unit) {
            val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val leaked = plainPrefs.all.keys.filter {
                it == KEY_CREDENTIALS || it.startsWith(KEY_CREDENTIALS_PREFIX)
            }
            if (leaked.isNotEmpty()) {
                val editor = plainPrefs.edit()
                leaked.forEach { editor.remove(it) }
                editor.apply()
                Timber.i("Purged ${leaked.size} plaintext Dropbox credential entries")
            }
            legacyPlaintextPurged = true
        }
    }
}
