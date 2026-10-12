package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonSyntaxException
import com.sza.fastmediasorter.core.security.PrivateDiagnosticValues
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every exchange account entry this device holds (contract DEVICE-EXCHANGE item AA), one per identity.
 *
 * Kept in the preferences DataStore rather than Room on purpose: `datastore/` is excluded from cloud backup
 * and device transfer, the database is not. An entry restored elsewhere would carry this device's `deviceId`
 * and collide with it on the server, while its Keystore-encrypted token would not decrypt anyway.
 */
@Singleton
class DeviceExchangeAccountStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val entries: Flow<List<DeviceExchangeAccountEntry>> = dataStore.data
        .map { it[keyEntries] }
        .distinctUntilChanged()
        .map(::decode)

    suspend fun snapshot(): List<DeviceExchangeAccountEntry> = entries.first()

    suspend fun entry(identity: String): DeviceExchangeAccountEntry? =
        snapshot().firstOrNull { it.identity == identity }

    /** Replaces the entry of the same identity in place, so its position in the list survives a re-enroll. */
    suspend fun put(entry: DeviceExchangeAccountEntry) = replace(entry.identity, entry)

    /**
     * Writes [entry] where the entry of [oldIdentity] stood, or appends it. An entry already holding the new
     * identity is dropped, since one device holds one entry per identity. An outgoing [entry] takes the flag
     * from every other entry.
     */
    suspend fun replace(oldIdentity: String, entry: DeviceExchangeAccountEntry) = mutate { current ->
        val index = current.indexOfFirst { it.identity == oldIdentity }
        val kept = current.mapIndexedNotNull { i, held ->
            when {
                i == index -> entry
                held.identity == entry.identity -> null
                entry.outgoing -> held.copy(outgoing = false)
                else -> held
            }
        }
        if (index < 0) kept + entry else kept
    }

    suspend fun remove(identity: String) = mutate { current -> current.filterNot { it.identity == identity } }

    /** The entry the device publishes and broadcasts through (item AC), or null while none is chosen. */
    suspend fun outgoing(): DeviceExchangeAccountEntry? = snapshot().firstOrNull { it.outgoing }

    /** Moves the outgoing flag to the entry of [identity] in one edit; null clears it from every entry. */
    suspend fun setOutgoing(identity: String?) = mutate { current ->
        current.map { held -> held.copy(outgoing = held.identity == identity) }
    }

    private suspend fun mutate(change: (List<DeviceExchangeAccountEntry>) -> List<DeviceExchangeAccountEntry>) {
        dataStore.edit { preferences ->
            val updated = change(decode(preferences[keyEntries]))
            if (updated.isEmpty()) preferences.remove(keyEntries) else preferences[keyEntries] = encode(updated)
        }
    }

    // A list written with the flag on two entries keeps it on the first only: one device, one outgoing entry.
    private fun decode(stored: String?): List<DeviceExchangeAccountEntry> {
        val array = stored?.takeIf(String::isNotEmpty)?.let(::parseArray) ?: return emptyList()
        val decoded = array.mapNotNull { element ->
            element.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let(::entryOf)
        }
        val first = decoded.indexOfFirst { it.outgoing }
        return decoded.mapIndexed { i, entry -> entry.copy(outgoing = i == first) }
    }

    private fun parseArray(stored: String): JsonArray? = try {
        JsonParser.parseString(stored).takeIf(JsonElement::isJsonArray)?.asJsonArray
    } catch (e: JsonSyntaxException) {
        Timber.w(e, "Exchange account entries are unreadable; treating them as none")
        null
    }

    // One unreadable element is dropped rather than failing the whole list: the others stay usable.
    private fun entryOf(json: JsonObject): DeviceExchangeAccountEntry? {
        val host = json.text(KEY_HOST)?.takeIf(String::isNotBlank)
        val port = json.get(KEY_PORT)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
        val login = json.text(KEY_LOGIN)?.takeIf(DeviceExchangeAccountEntry::isValidLogin)
        val account = login?.let { name -> json.text(KEY_DEVICE_ID)?.takeIf(String::isNotBlank)?.let { name to it } }
        if (host == null || port == null || account == null) return null
        val pin = json.text(KEY_PIN)
        val publicEndpoint = json.text(KEY_PUBLIC_ENDPOINT)
        PrivateDiagnosticValues.register(host, account.first, pin, publicEndpoint)
        return DeviceExchangeAccountEntry(
            host = host,
            port = port,
            login = account.first,
            deviceId = account.second,
            pinnedCertificate = pin,
            autoAttach = json.flag(KEY_AUTO_ATTACH),
            publicEndpoint = publicEndpoint,
            outgoing = json.flag(KEY_OUTGOING),
        )
    }

    private fun encode(entries: List<DeviceExchangeAccountEntry>): String = JsonArray().apply {
        entries.forEach { entry ->
            add(
                JsonObject().apply {
                    addProperty(KEY_HOST, entry.host)
                    addProperty(KEY_PORT, entry.port)
                    addProperty(KEY_LOGIN, entry.login)
                    addProperty(KEY_DEVICE_ID, entry.deviceId)
                    entry.pinnedCertificate?.let { addProperty(KEY_PIN, it) }
                    addProperty(KEY_AUTO_ATTACH, entry.autoAttach)
                    entry.publicEndpoint?.let { addProperty(KEY_PUBLIC_ENDPOINT, it) }
                    if (entry.outgoing) addProperty(KEY_OUTGOING, true)
                }
            )
        }
    }.toString()

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    private fun JsonObject.flag(key: String): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean ?: false

    private companion object {
        val keyEntries = stringPreferencesKey("device_exchange_account_entries")
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_LOGIN = "login"
        const val KEY_DEVICE_ID = "deviceId"
        const val KEY_PIN = "pin"
        const val KEY_AUTO_ATTACH = "autoAttach"
        const val KEY_PUBLIC_ENDPOINT = "publicEndpoint"
        const val KEY_OUTGOING = "outgoing"
    }
}
