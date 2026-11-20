package com.sza.fastmediasorter.data.remotecontrol

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentMode
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerGrant
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The person's remote-control decisions: the listening switch and the grants per controller.
 *
 * Its own DataStore file on purpose: the `settings` store is exported, imported, sent to Drive and to a
 * new device, and a restored switch would let a device accept commands nobody allowed on it
 * (REMOTE-CONTROL rule 8). Files under `datastore/` are excluded from Android backup by the rules files.
 */
private val Context.remoteControlDataStore by preferencesDataStore(
    name = RemoteControlDecisionStore.STORE_NAME,
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

@Singleton
class RemoteControlDecisionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val listening: Flow<Boolean> = context.remoteControlDataStore.data.map { it[KEY_LISTENING] ?: false }

    val grants: Flow<List<RemoteControllerGrant>> =
        context.remoteControlDataStore.data.map { RemoteControllerGrantCodec.decode(it[KEY_GRANTS]) }

    suspend fun setListening(on: Boolean) {
        context.remoteControlDataStore.edit { it[KEY_LISTENING] = on }
    }

    suspend fun updateGrants(transform: (List<RemoteControllerGrant>) -> List<RemoteControllerGrant>) {
        context.remoteControlDataStore.edit { prefs ->
            val current = RemoteControllerGrantCodec.decode(prefs[KEY_GRANTS])
            prefs[KEY_GRANTS] = RemoteControllerGrantCodec.encode(transform(current))
        }
    }

    companion object {
        const val STORE_NAME = "remote_control"
        private val KEY_LISTENING = booleanPreferencesKey("listening")
        private val KEY_GRANTS = stringPreferencesKey("grants_v1")
    }
}

/** Versioned JSON form of the grants; a member it cannot read becomes "not granted", never a wider grant. */
internal object RemoteControllerGrantCodec {
    private const val VERSION = 1
    private const val KEY_VERSION = "v"
    private const val KEY_GRANTS = "grants"
    private const val KEY_DEVICE_ID = "deviceId"
    private const val KEY_DEVICE_NAME = "deviceName"
    private const val KEY_CLASSES = "classes"

    fun encode(grants: List<RemoteControllerGrant>): String {
        val list = JsonArray()
        grants.forEach { grant ->
            val classes = JsonObject()
            grant.classes.forEach { (cls, mode) -> classes.addProperty(cls.wire, mode.wire) }
            list.add(
                JsonObject().apply {
                    addProperty(KEY_DEVICE_ID, grant.deviceId)
                    addProperty(KEY_DEVICE_NAME, grant.deviceName)
                    add(KEY_CLASSES, classes)
                },
            )
        }
        return JsonObject().apply {
            addProperty(KEY_VERSION, VERSION)
            add(KEY_GRANTS, list)
        }.toString()
    }

    fun decode(raw: String?): List<RemoteControllerGrant> {
        val root = parse(raw)
        val version = root?.get(KEY_VERSION)?.stringOrNull()?.toIntOrNull()
        val list = (root?.get(KEY_GRANTS) as? JsonArray)?.takeIf { version == VERSION }
        return list?.mapNotNull { (it as? JsonObject)?.let(::decodeGrant) }.orEmpty()
    }

    private fun parse(raw: String?): JsonObject? {
        if (raw.isNullOrBlank()) return null
        return try {
            JsonParser.parseString(raw) as? JsonObject
        } catch (e: JsonParseException) {
            Timber.w(e, "Remote-control grants unreadable; treating as none")
            null
        }
    }

    private fun decodeGrant(item: JsonObject): RemoteControllerGrant? {
        val deviceId = item.get(KEY_DEVICE_ID)?.stringOrNull() ?: return null
        val name = item.get(KEY_DEVICE_NAME)?.stringOrNull() ?: deviceId
        val classes = (item.get(KEY_CLASSES) as? JsonObject)?.entrySet().orEmpty().mapNotNull { (key, value) ->
            RemoteRiskClass.fromWire(key)?.let { it to RemoteConsentMode.fromWire(value.stringOrNull()) }
        }.toMap()
        return RemoteControllerGrant(deviceId, name, classes)
    }

    private fun JsonElement.stringOrNull(): String? =
        if (isJsonPrimitive && !asJsonPrimitive.isBoolean) asString else null
}
