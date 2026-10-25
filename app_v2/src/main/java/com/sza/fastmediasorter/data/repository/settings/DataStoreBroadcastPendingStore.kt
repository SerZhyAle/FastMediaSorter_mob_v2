package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sza.fastmediasorter.domain.repository.BroadcastPendingStore
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** [BroadcastPendingStore] as one map of stream id to expiry; an expiry holds no secret, so it is kept in clear. */
@Singleton
class DataStoreBroadcastPendingStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : BroadcastPendingStore {

    override suspend fun untilMs(streamId: String): Long? = read(dataStore.data.first()[KEY])[streamId]

    override suspend fun put(streamId: String, untilMs: Long) {
        val now = System.currentTimeMillis()
        // A row nobody opened again before its expiry would otherwise stay in the map for good.
        edit { held -> held.filterValues { it > now } + (streamId to untilMs) }
    }

    override suspend fun clear(streamId: String) {
        edit { held -> held - streamId }
    }

    private suspend fun edit(change: (Map<String, Long>) -> Map<String, Long>) {
        dataStore.edit { prefs ->
            val next = change(read(prefs[KEY]))
            if (next.isEmpty()) {
                prefs.remove(KEY)
            } else {
                val json = JsonObject()
                next.forEach { (id, until) -> json.addProperty(id, until) }
                prefs[KEY] = json.toString()
            }
        }
    }

    private fun read(text: String?): Map<String, Long> = text?.let { json ->
        runCatching {
            JsonParser.parseString(json).asJsonObject.entrySet().associate { (id, until) -> id to until.asLong }
        }.onFailure { Timber.w(it, "Pending broadcast rows unreadable; no row waits") }
            .getOrNull()
    }.orEmpty()

    private companion object {
        val KEY = stringPreferencesKey("broadcast_pending_rows")
    }
}
