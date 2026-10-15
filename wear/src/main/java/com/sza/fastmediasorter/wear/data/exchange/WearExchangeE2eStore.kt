package com.sza.fastmediasorter.wear.data.exchange

import android.content.SharedPreferences
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import com.sza.fastmediasorter.data.security.e2e.E2eDeviceKey
import com.sza.fastmediasorter.wear.di.EncryptedPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S4240: the watch's device keys and the peer keys it trusts (contract DEVICE-EXCHANGE items DF and DG), one set
 * per own `deviceId`, because a key lives exactly as long as its id.
 */
interface WearExchangeE2eStore {

    /** The key of [deviceId], made and kept on first use; null when the secret store refused it. */
    suspend fun keyFor(deviceId: String): E2eDeviceKey?

    suspend fun existing(deviceId: String): E2eDeviceKey?

    /** Forgets the key and every trusted peer key of [deviceId]. */
    suspend fun forget(deviceId: String)

    suspend fun trusted(deviceId: String): Map<String, String>

    suspend fun saveTrusted(deviceId: String, trusted: Map<String, String>)
}

/**
 * Keeps both in the watch's Keystore-backed encrypted preferences beside the device tokens, which no backup
 * carries (strategic 3.2 of S4158): a restored watch has no key and enrolls again from the phone.
 */
@Singleton
class EncryptedWearExchangeE2eStore @Inject constructor(
    @EncryptedPrefs private val prefs: SharedPreferences,
) : WearExchangeE2eStore {

    // Two enrollments of one id must not each make a key and race to keep it.
    private val creation = Mutex()

    override suspend fun keyFor(deviceId: String): E2eDeviceKey? = creation.withLock {
        existing(deviceId) ?: create(deviceId)
    }

    override suspend fun existing(deviceId: String): E2eDeviceKey? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_PRIVATE_PREFIX + deviceId, null)
            ?.let { hex -> runCatching { E2eDeviceKey.fromPrivate(E2eBytes.fromHex(hex)) }.getOrNull() }
    }

    override suspend fun forget(deviceId: String) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .remove(KEY_PRIVATE_PREFIX + deviceId)
                .remove(KEY_TRUSTED_PREFIX + deviceId)
                .commit()
        }
    }

    override suspend fun trusted(deviceId: String): Map<String, String> = withContext(Dispatchers.IO) {
        prefs.getString(KEY_TRUSTED_PREFIX + deviceId, null)?.let { json ->
            runCatching {
                JsonParser.parseString(json).asJsonObject.entrySet().associate { (id, kem) -> id to kem.asString }
            }.getOrNull()
        }.orEmpty()
    }

    override suspend fun saveTrusted(deviceId: String, trusted: Map<String, String>) {
        val json = JsonObject()
        trusted.forEach { (id, kem) -> json.addProperty(id, kem) }
        withContext(Dispatchers.IO) { prefs.edit().putString(KEY_TRUSTED_PREFIX + deviceId, json.toString()).commit() }
    }

    private suspend fun create(deviceId: String): E2eDeviceKey? = withContext(Dispatchers.IO) {
        val made = E2eDeviceKey.generate()
        val kept = prefs.edit()
            .putString(KEY_PRIVATE_PREFIX + deviceId, E2eBytes.hex(made.exportPrivateForSecretStore()))
            .commit()
        if (!kept) Timber.w("Exchange device key: the encrypted store refused it, the entry cannot seal")
        made.takeIf { kept }
    }

    private companion object {
        const val KEY_PRIVATE_PREFIX = "exchange_e2e_key_v1/"
        const val KEY_TRUSTED_PREFIX = "exchange_e2e_trusted_v1/"
    }
}
