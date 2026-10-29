package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.domain.repository.BroadcastLinkPasswordStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The custom link password, encrypted with the Keystore-backed [CryptoHelper] in the same backup-excluded
 * preferences DataStore as [KeystoreBroadcastPasswordStore]. A restored copy decrypts to nothing, so the mode
 * travels in the backup while the password has to be typed again.
 */
@Singleton
class KeystoreBroadcastLinkPasswordStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val encrypt: (String) -> String?,
    private val decrypt: (String?) -> String?,
    private val ioDispatcher: CoroutineDispatcher,
) : BroadcastLinkPasswordStore {

    // The Keystore has no JVM implementation, so the cipher is a seam only a unit test replaces.
    @Inject
    constructor(
        dataStore: DataStore<Preferences>,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(dataStore, CryptoHelper::encrypt, CryptoHelper::decrypt, ioDispatcher)

    private class Held(val value: String?)

    @Volatile
    private var cache: Held? = null

    override suspend fun get(): String? {
        cache?.let { return it.value }
        val password = withContext(ioDispatcher) {
            dataStore.data.first()[KEY]?.let(decrypt)?.takeUnless(String::isEmpty)
        }
        PrivateDiagnosticValues.register(password)
        cache = Held(password)
        return password
    }

    override suspend fun put(password: String): Boolean {
        PrivateDiagnosticValues.register(password)
        val encrypted = encrypt(password)?.takeUnless(String::isEmpty) ?: return false
        dataStore.edit { prefs -> prefs[KEY] = encrypted }
        cache = Held(password)
        return true
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY) }
        cache = Held(null)
    }

    private companion object {
        val KEY = stringPreferencesKey("broadcast_link_password")
    }
}
