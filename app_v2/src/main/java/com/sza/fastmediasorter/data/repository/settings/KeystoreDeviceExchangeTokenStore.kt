package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.data.remote.exchange.DeviceExchangeTokenStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device token of every exchange account entry, encrypted with the Keystore-backed [CryptoHelper]. The
 * key never leaves the device, so a restored copy of these preferences decrypts to nothing and the entry
 * simply enrolls again - the token is never carried to another device.
 */
@Singleton
class KeystoreDeviceExchangeTokenStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DeviceExchangeTokenStore {

    override suspend fun token(entry: DeviceExchangeAccountEntry): String? =
        dataStore.data.first()[key(entry)]?.let(CryptoHelper::decrypt)?.takeUnless(String::isEmpty)
            .also { token -> PrivateDiagnosticValues.register(token) }

    override suspend fun save(entry: DeviceExchangeAccountEntry, token: String): Boolean {
        PrivateDiagnosticValues.register(token)
        val encrypted = CryptoHelper.encrypt(token)?.takeUnless(String::isEmpty)
        if (encrypted != null) dataStore.edit { it[key(entry)] = encrypted }
        return encrypted != null
    }

    override suspend fun forget(entry: DeviceExchangeAccountEntry) {
        dataStore.edit { it.remove(key(entry)) }
    }

    // Hashed so the preference name does not spell out the server address and the login.
    private fun key(entry: DeviceExchangeAccountEntry): Preferences.Key<String> {
        val digest = MessageDigest.getInstance("SHA-256").digest(entry.identity.toByteArray(Charsets.UTF_8))
        val name = digest.take(KEY_HASH_BYTES).joinToString("") { "%02x".format(it) }
        return stringPreferencesKey(KEY_PREFIX + name)
    }

    private companion object {
        const val KEY_PREFIX = "device_exchange_token_"
        const val KEY_HASH_BYTES = 8
    }
}
