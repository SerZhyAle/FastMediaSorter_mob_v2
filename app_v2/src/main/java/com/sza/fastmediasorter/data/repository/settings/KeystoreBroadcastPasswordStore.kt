package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.domain.repository.BroadcastPasswordStore
import com.sza.fastmediasorter.domain.repository.BroadcastPasswordStore.StoredPair
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The broadcast pair of every stream row, encrypted with the Keystore-backed [CryptoHelper] in the preferences
 * DataStore that backup and device transfer exclude. The key never leaves the device, so a restored copy decrypts
 * to nothing and the broadcast simply asks for its password again.
 *
 * Answers, absent ones included, are kept in memory: every playback asks, and an unprotected stream must cost no
 * DataStore read.
 */
@Singleton
class KeystoreBroadcastPasswordStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val encrypt: (String) -> String?,
    private val decrypt: (String?) -> String?,
) : BroadcastPasswordStore {

    // The Keystore has no JVM implementation, so the cipher is a seam only the unit test replaces.
    @Inject
    constructor(dataStore: DataStore<Preferences>) : this(dataStore, CryptoHelper::encrypt, CryptoHelper::decrypt)

    private class Held(val pair: StoredPair?)

    private val cache = ConcurrentHashMap<String, Held>()

    override suspend fun get(streamId: String): StoredPair? {
        cache[streamId]?.let { return it.pair }
        val prefs = dataStore.data.first()
        val user = prefs[userKey(streamId)]
        val password = prefs[passwordKey(streamId)]?.let(decrypt)?.takeUnless(String::isEmpty)
        PrivateDiagnosticValues.register(password)
        val pair = user?.let { StoredPair(it, password) }
        cache[streamId] = Held(pair)
        return pair
    }

    override suspend fun put(streamId: String, user: String, password: String?): Boolean {
        val encrypted = password?.let { typed ->
            PrivateDiagnosticValues.register(typed)
            encrypt(typed)?.takeUnless(String::isEmpty) ?: return false
        }
        dataStore.edit { prefs ->
            prefs[userKey(streamId)] = user
            if (encrypted == null) prefs.remove(passwordKey(streamId)) else prefs[passwordKey(streamId)] = encrypted
        }
        cache[streamId] = Held(StoredPair(user, password))
        return true
    }

    override suspend fun remove(streamId: String) {
        dataStore.edit { prefs ->
            prefs.remove(userKey(streamId))
            prefs.remove(passwordKey(streamId))
        }
        cache[streamId] = Held(null)
    }

    private fun userKey(streamId: String) = stringPreferencesKey("${KEY_PREFIX}${hash(streamId)}_user")

    private fun passwordKey(streamId: String) = stringPreferencesKey("${KEY_PREFIX}${hash(streamId)}_password")

    // Hashed so the preference name does not spell out the stream row it belongs to.
    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .take(KEY_HASH_BYTES).joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_PREFIX = "broadcast_password_"
        const val KEY_HASH_BYTES = 8
    }
}
