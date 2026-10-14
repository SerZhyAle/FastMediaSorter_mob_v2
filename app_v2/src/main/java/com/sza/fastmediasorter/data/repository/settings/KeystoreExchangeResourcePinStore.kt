package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.data.local.db.CryptoHelper
import com.sza.fastmediasorter.data.remote.exchange.ExchangeResourcePinStore
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import kotlinx.coroutines.flow.first
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The PIN of every attached protected resource, encrypted with the Keystore-backed [CryptoHelper] in the
 * preferences DataStore that backup and device transfer exclude. The key never leaves the device, so a restored
 * copy decrypts to nothing and the resource simply asks for its PIN again.
 *
 * Answers, absent ones included, are kept in memory: every SFTP operation on an attached resource asks, and an
 * unprotected resource must cost no DataStore read.
 */
@Singleton
class KeystoreExchangeResourcePinStore internal constructor(
    private val dataStore: DataStore<Preferences>,
    private val encrypt: (String) -> String?,
    private val decrypt: (String?) -> String?,
) : ExchangeResourcePinStore {

    // The Keystore has no JVM implementation, so the cipher is a seam only the unit test replaces.
    @Inject
    constructor(dataStore: DataStore<Preferences>) : this(dataStore, CryptoHelper::encrypt, CryptoHelper::decrypt)

    private class Held(val pin: String?)

    private val cache = ConcurrentHashMap<ExchangeBinding, Held>()

    override suspend fun pin(binding: ExchangeBinding): String? {
        cache[binding]?.let { return it.pin }
        val stored = dataStore.data.first()[key(binding)]?.let(decrypt)?.takeUnless(String::isEmpty)
        PrivateDiagnosticValues.register(stored)
        cache[binding] = Held(stored)
        return stored
    }

    override suspend fun save(binding: ExchangeBinding, pin: String): Boolean {
        PrivateDiagnosticValues.register(pin)
        val encrypted = encrypt(pin)?.takeUnless(String::isEmpty) ?: return false
        dataStore.edit { it[key(binding)] = encrypted }
        cache[binding] = Held(pin)
        return true
    }

    override suspend fun forget(binding: ExchangeBinding) {
        dataStore.edit { it.remove(key(binding)) }
        cache[binding] = Held(null)
    }

    override suspend fun move(from: String, to: String, resourceIds: Collection<String>) {
        resourceIds.forEach { resourceId ->
            val old = ExchangeBinding(from, resourceId)
            val held = pin(old) ?: return@forEach
            if (save(ExchangeBinding(to, resourceId), held)) forget(old)
        }
    }

    // Hashed so the preference name spells out neither the server and the login nor the resource.
    private fun key(binding: ExchangeBinding): Preferences.Key<String> =
        stringPreferencesKey("${KEY_PREFIX}${hash(binding.sourceKey)}_${hash(binding.resourceId)}")

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .take(KEY_HASH_BYTES).joinToString("") { "%02x".format(it) }

    private companion object {
        const val KEY_PREFIX = "exchange_resource_pin_"
        const val KEY_HASH_BYTES = 8
    }
}
