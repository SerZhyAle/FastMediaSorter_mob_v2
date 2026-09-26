package com.sza.fastmediasorter.identity

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.identity.PrimaryGoogleAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted-only persistent store for the primary Google account binding (strategic S0200 §5.1).
 *
 * Modelled after `EncryptedCookieStore` - NEVER falls back to plaintext SharedPreferences on
 * Keystore failure. If encrypted-prefs initialization throws (e.g. corrupted Keystore,
 * unsupported device), the failure propagates to the caller and `CredentialManagerGoogleIdentityRepository`
 * surfaces it as `PrimaryGoogleAccountState.Error(IdentityFailureReason.UnknownError)`.
 *
 * The token strings themselves are not stored here - only the [PrimaryGoogleAccount] identity envelope.
 * Tokens live in [GoogleTokenIssuer]'s in-memory cache and get re-issued via `GoogleAuthUtil`.
 */
@Singleton
class PrimaryGoogleAccountStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
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

    suspend fun load(): PrimaryGoogleAccount? = withContext(ioDispatcher) {
        val json = prefs.getString(KEY_ACCOUNT, null) ?: return@withContext null
        runCatching { gson.fromJson(json, PrimaryGoogleAccount::class.java) }
            .onFailure { Timber.e(it, "Failed to deserialize primary Google account; treating as Unbound") }
            .getOrNull()
    }

    /**
     * Returns false when the encrypted write did not reach disk, so the caller does not publish a
     * binding that is gone after the next restart.
     */
    suspend fun save(account: PrimaryGoogleAccount): Boolean = withContext(ioDispatcher) {
        val written = prefs.edit().putString(KEY_ACCOUNT, gson.toJson(account)).commit()
        if (!written) Timber.e("Primary Google account write was not committed")
        written
    }

    suspend fun clear(): Unit = withContext(ioDispatcher) {
        if (!prefs.edit().clear().commit()) {
            Timber.w("Primary Google account clear was not committed; the binding may return after restart")
        }
    }

    private companion object {
        const val PREFS_NAME = "primary_google_account_v1"
        const val KEY_ACCOUNT = "account_json"
    }
}
