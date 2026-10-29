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
import com.sza.fastmediasorter.data.remote.exchange.ExchangePinRecorder
import com.sza.fastmediasorter.domain.model.DeviceExchangeAccountEntry
import com.sza.fastmediasorter.domain.model.DeviceExchangeOutgoingRule
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
) : ExchangePinRecorder {

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

    /**
     * Moves the entry of [oldIdentity] to [host] and [port] inside the same edit, so concurrent changes of other
     * fields (pins, features, accountKind) survive; an absent identity changes nothing.
     */
    suspend fun changeAddress(oldIdentity: String, host: String, port: Int): DeviceExchangeAccountEntry? {
        var moved: DeviceExchangeAccountEntry? = null
        mutate { current ->
            val index = current.indexOfFirst { it.identity == oldIdentity }
            if (index < 0) return@mutate current
            val target = current[index].copy(host = host, port = port)
            moved = target
            current.mapIndexedNotNull { i, held ->
                when {
                    i == index -> target
                    held.identity == target.identity -> null
                    target.outgoing -> held.copy(outgoing = false)
                    else -> held
                }
            }
        }
        return moved
    }

    /**
     * Rewrites the entry of [identity] from the value held inside the same edit, so a concurrent change of another
     * field - a pin renewal through [recordPin] - survives; an absent identity changes nothing.
     */
    suspend fun update(identity: String, change: (DeviceExchangeAccountEntry) -> DeviceExchangeAccountEntry) =
        mutate { current -> current.map { held -> if (held.identity == identity) change(held) else held } }

    suspend fun remove(identity: String) = mutate { current -> current.filterNot { it.identity == identity } }

    /** The entry the device publishes and broadcasts through (item AC); null only while no entry may produce (DS). */
    suspend fun outgoing(): DeviceExchangeAccountEntry? = snapshot().firstOrNull { it.outgoing }

    /**
     * Moves the outgoing flag to the entry of [identity] in one edit. Null clears it only while no entry may produce:
     * otherwise item DS gives it straight to the first producing entry.
     */
    suspend fun setOutgoing(identity: String?) = mutate { current ->
        current.map { held -> held.copy(outgoing = held.identity == identity) }
    }

    /**
     * Writes [leaf] and [key] as the pin of the entry of [identity] (19 item AY), read inside the same edit so a
     * concurrent change of another field survives. An entry that pins nothing stays platform-trusted: a pin is
     * only ever the user's acceptance, never something a connect creates.
     */
    suspend fun recordPin(identity: String, leaf: String, key: String) = mutate { current ->
        current.map { held ->
            if (held.identity == identity && held.pinnedCertificate != null) {
                held.copy(pinnedCertificate = leaf, pinnedKey = key)
            } else {
                held
            }
        }
    }

    override suspend fun record(identity: String, leaf: String, key: String) = recordPin(identity, leaf, key)

    private suspend fun mutate(change: (List<DeviceExchangeAccountEntry>) -> List<DeviceExchangeAccountEntry>) {
        dataStore.edit { preferences ->
            // Item DS: enrollment, removal, a guest demotion through update() and a restore all keep an outgoing entry.
            val updated = DeviceExchangeOutgoingRule.normalize(change(decode(preferences[keyEntries])))
            if (updated.isEmpty()) preferences.remove(keyEntries) else preferences[keyEntries] = encode(updated)
        }
    }

    // A list written with the flag on two entries keeps it on the first only: one device, one outgoing entry. A list
    // an older build wrote with no outgoing entry reads with one at once (item DS); the next write persists it.
    private fun decode(stored: String?): List<DeviceExchangeAccountEntry> {
        val array = stored?.takeIf(String::isNotEmpty)?.let(::parseArray) ?: return emptyList()
        val decoded = array.mapNotNull { element ->
            element.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let(::entryOf)
        }
        val first = decoded.indexOfFirst { it.outgoing }
        val oneFlag = decoded.mapIndexed { i, entry -> entry.copy(outgoing = i == first) }
        return DeviceExchangeOutgoingRule.normalize(oneFlag)
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
        // A key is half of a pin; without the leaf it would vouch for nothing.
        val pinnedKey = pin?.let { json.text(KEY_PINNED_KEY) }
        val publicEndpoint = json.text(KEY_PUBLIC_ENDPOINT)
        PrivateDiagnosticValues.register(host, account.first, pin, pinnedKey, publicEndpoint)
        return DeviceExchangeAccountEntry(
            host = host,
            port = port,
            login = account.first,
            deviceId = account.second,
            pinnedCertificate = pin,
            autoAttach = json.flag(KEY_AUTO_ATTACH),
            publicEndpoint = publicEndpoint,
            outgoing = json.flag(KEY_OUTGOING),
            pinCapable = json.flag(KEY_PIN_CAPABLE),
            features = json.textList(KEY_FEATURES),
            accountKind = json.text(KEY_ACCOUNT_KIND),
            pinnedKey = pinnedKey,
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
                    if (entry.pinCapable) addProperty(KEY_PIN_CAPABLE, true)
                    entry.features?.let { features -> add(KEY_FEATURES, JsonArray().apply { features.forEach(::add) }) }
                    entry.accountKind?.let { addProperty(KEY_ACCOUNT_KIND, it) }
                    entry.pinnedKey?.let { addProperty(KEY_PINNED_KEY, it) }
                }
            )
        }
    }.toString()

    private fun JsonObject.text(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    // Absent or malformed reads as "not seen yet": an older build drops the key on its next write (S4226).
    private fun JsonObject.textList(key: String): List<String>? =
        get(key)?.takeIf(JsonElement::isJsonArray)?.asJsonArray?.mapNotNull { element ->
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        }

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
        const val KEY_PIN_CAPABLE = "pinCapable"
        const val KEY_FEATURES = "features"
        const val KEY_ACCOUNT_KIND = "accountKind"
        const val KEY_PINNED_KEY = "pinnedKey"
    }
}
