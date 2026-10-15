package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.data.security.e2e.E2eBytes
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The device keys this device trusts, per own `deviceId` (contract item DG): the first key seen for each other
 * device of the entry, or the one the watch returned in its hand-over answer. The keys are public, so they are
 * kept in clear; what matters is that nothing but [learn], [pin] and [drop] ever changes one.
 */
@Singleton
class ExchangeE2eTrustStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    suspend fun trusted(ownDeviceId: String): Map<String, String> = read(dataStore.data.first()[key(ownDeviceId)])

    /** Adds the keys of [firstSeen] that are not trusted yet; a held key is never replaced here. */
    suspend fun learn(ownDeviceId: String, firstSeen: Map<String, String>) {
        if (firstSeen.isEmpty()) return
        edit(ownDeviceId) { held -> firstSeen.filterKeys { it !in held } + held }
    }

    /** Trusts [kem] for [deviceId] outright - the watch's hand-over answer outranks the directory (item DG). */
    suspend fun pin(ownDeviceId: String, deviceId: String, kem: String) {
        edit(ownDeviceId) { held -> held + (deviceId to kem) }
    }

    /** Keeps only the keys of [listed] devices: one missing from the whole list was removed (`revoke`). */
    suspend fun retain(ownDeviceId: String, listed: Set<String>) {
        if (trusted(ownDeviceId).keys.all { it in listed }) return
        edit(ownDeviceId) { held -> held.filterKeys { it in listed } }
    }

    /**
     * The sender key a guest first saw for each of its grants (27 item DJ): a guest knows none of the owner's
     * devices, so the first box of a grant decides, and a later box of it from another key is not opened.
     */
    suspend fun grantSenders(ownDeviceId: String): Map<String, String> =
        read(dataStore.data.first()[grantKey(ownDeviceId)])

    suspend fun learnGrantSenders(ownDeviceId: String, firstSeen: Map<String, String>) {
        if (firstSeen.isEmpty()) return
        dataStore.edit { prefs ->
            val next = firstSeen.filterKeys { it !in read(prefs[grantKey(ownDeviceId)]) } +
                read(prefs[grantKey(ownDeviceId)])
            val json = JsonObject()
            next.forEach { (id, kem) -> json.addProperty(id, kem) }
            prefs[grantKey(ownDeviceId)] = json.toString()
        }
    }

    suspend fun forgetAll(ownDeviceId: String) {
        dataStore.edit { it.remove(key(ownDeviceId)) }
    }

    private suspend fun edit(ownDeviceId: String, change: (Map<String, String>) -> Map<String, String>) {
        dataStore.edit { prefs ->
            val next = change(read(prefs[key(ownDeviceId)]))
            val json = JsonObject()
            next.forEach { (id, kem) -> json.addProperty(id, kem) }
            prefs[key(ownDeviceId)] = json.toString()
        }
    }

    private fun read(text: String?): Map<String, String> = text?.let { json ->
        runCatching {
            JsonParser.parseString(json).asJsonObject.entrySet().associate { (id, kem) -> id to kem.asString }
        }.onFailure { Timber.w(it, "Exchange trusted keys unreadable; every key is first seen again") }
            .getOrNull()
    }.orEmpty()

    // Hashed so the preference name does not spell out the device id.
    private fun key(ownDeviceId: String): Preferences.Key<String> {
        val name = E2eBytes.hex(E2eBytes.sha256(E2eBytes.utf8(ownDeviceId)).copyOf(KEY_HASH_BYTES))
        return stringPreferencesKey(KEY_PREFIX + name)
    }

    private fun grantKey(ownDeviceId: String): Preferences.Key<String> {
        val name = E2eBytes.hex(E2eBytes.sha256(E2eBytes.utf8(ownDeviceId)).copyOf(KEY_HASH_BYTES))
        return stringPreferencesKey(GRANT_PREFIX + name)
    }

    private companion object {
        const val KEY_PREFIX = "device_exchange_e2e_trusted_"
        const val GRANT_PREFIX = "device_exchange_e2e_grant_senders_"
        const val KEY_HASH_BYTES = 8
    }
}
