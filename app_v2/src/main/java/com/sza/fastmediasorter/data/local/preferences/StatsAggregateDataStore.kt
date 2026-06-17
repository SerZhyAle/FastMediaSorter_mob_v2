package com.sza.fastmediasorter.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.sza.fastmediasorter.domain.stats.MediaActionCounts
import com.sza.fastmediasorter.domain.stats.StatsAggregateDelta
import com.sza.fastmediasorter.domain.stats.StatsAggregateValues
import com.sza.fastmediasorter.domain.stats.StatsKey
import com.sza.fastmediasorter.domain.stats.StatsMediaType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detailed (opt-in) aggregate counters for usage statistics (S0473).
 *
 * Holds the per-metric scalar counters and the action x type matrix. A whole batch of
 * increments is applied in a single [apply] / `edit {}` so one flush equals one disk write
 * (strategic §3.2 / ADR-6 - never per-event). Keys are namespaced with `stats_agg_` so
 * [wipeDetailed] can erase exactly the detailed data on toggle-off while the
 * `stats_baseline_` keys survive (strategic §3.2 "Поведение при выключении" / ADR-2).
 */
@Singleton
class StatsAggregateDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    private companion object {
        const val PREFIX = "stats_agg_"
        const val TYPE_PREFIX = "stats_agg_type_"
    }

    private fun counterKey(key: StatsKey) = longPreferencesKey(PREFIX + key.name.lowercase())

    private fun typeKey(type: StatsMediaType, field: String) =
        longPreferencesKey(TYPE_PREFIX + type.name.lowercase() + "_" + field)

    /** Atomically adds a batch of increments. A single edit = a single disk write. */
    suspend fun apply(delta: StatsAggregateDelta) {
        if (delta.isEmpty) return
        dataStore.edit { prefs ->
            delta.counters.forEach { (key, inc) ->
                if (inc != 0L) {
                    val k = counterKey(key)
                    prefs[k] = (prefs[k] ?: 0L) + inc
                }
            }
            delta.byType.forEach { (type, counts) ->
                addType(prefs, type, "copied", counts.copied)
                addType(prefs, type, "moved", counts.moved)
                addType(prefs, type, "deleted", counts.deleted)
                addType(prefs, type, "bytes", counts.totalBytes)
            }
        }
    }

    private fun addType(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        type: StatsMediaType,
        field: String,
        inc: Long
    ) {
        if (inc == 0L) return
        val k = typeKey(type, field)
        prefs[k] = (prefs[k] ?: 0L) + inc
    }

    /** Reads back all detailed counters and the action x type matrix. */
    suspend fun read(): StatsAggregateValues {
        val prefs = dataStore.data.first()
        val counters = StatsKey.entries.associateWith { prefs[counterKey(it)] ?: 0L }
        val byType = StatsMediaType.entries.mapNotNull { type ->
            val counts = MediaActionCounts(
                copied = prefs[typeKey(type, "copied")] ?: 0L,
                moved = prefs[typeKey(type, "moved")] ?: 0L,
                deleted = prefs[typeKey(type, "deleted")] ?: 0L,
                totalBytes = prefs[typeKey(type, "bytes")] ?: 0L
            )
            if (counts == MediaActionCounts()) null else type to counts
        }.toMap()
        return StatsAggregateValues(counters = counters, byType = byType)
    }

    /** Removes every detailed `stats_agg_` key; leaves the always-on baseline untouched. */
    suspend fun wipeDetailed() {
        dataStore.edit { prefs ->
            val toRemove = prefs.asMap().keys.filter { it.name.startsWith(PREFIX) }
            toRemove.forEach { prefs.remove(it) }
        }
    }
}
