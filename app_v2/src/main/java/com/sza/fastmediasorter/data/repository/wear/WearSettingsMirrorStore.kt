package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Mirror of the watch settings as the phone last knew them, plus when the two sides last agreed.
 *
 * S2050: a field belongs here only if nothing outside the companion sheet's own restore path ever
 * reads it and it exists solely to remember the watch's settings (the sheet's `ViewModel` dies with
 * its `BottomSheetDialogFragment`, so this is the only place that survives a reopen). A field that any
 * other part of the app reads reactively belongs in `AppSettings`/DataStore instead - see
 * `ProgramsSettingsStore.KEY_ENABLE_WEAR_COMPANION` for that case.
 *
 * S2093 supersedes one half of the S2050 ruling: this store no longer holds only "what was last told
 * to the watch", because the watch now reports its own set back over `SETTINGS_REPORT` and the merged
 * result is written here. The rest of that ruling stands unchanged - this is still not a reactive
 * store, so the boundary against `AppSettings` is where it was.
 */
interface WearSettingsMirrorStore {

    fun readSettings(): WearSettingsPayload?

    fun writeSettings(settings: WearSettingsPayload)

    fun readLastSyncTimestamp(): Long

    fun markSynced(atEpochMillis: Long)

    /**
     * S2093: contract field name to epoch-millis of that field's last edit, in this phone's time base.
     *
     * Kept beside the payload rather than inside it, so a stamp map written by this build is readable
     * by a build that predates it and vice versa - the payload's own parse already falls back to "no
     * settings known" on any change of shape, and losing the whole mirror to gain a stamp would be a
     * bad trade.
     */
    fun readFieldTimestamps(): Map<String, Long>

    fun writeFieldTimestamps(stamps: Map<String, Long>)
}

/**
 * Default [WearSettingsMirrorStore], backed by the same `SharedPreferences` file, keys and Gson
 * format the two former direct callers (`WearSyncViewModel`, `PhoneWearListenerService`) already used -
 * unchanged on purpose, so nothing already written to this file on a device needs a migration.
 */
class SharedPreferencesWearSettingsMirrorStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearSettingsMirrorStore {

    private val prefs
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // A payload written by an older build can no longer parse against the current model; falling
    // back to "nothing known yet" shows the defaults, which is what the sheet did before anyway,
    // whereas letting Gson throw here would take the whole settings screen down with it.
    override fun readSettings(): WearSettingsPayload? {
        val stored = prefs.getString(KEY_WATCH_SETTINGS, null) ?: return null
        return runCatching { gson.fromJson(stored, WearSettingsPayload::class.java) }
            .onFailure { Timber.w(it, "Stored watch settings unreadable, falling back to defaults") }
            .getOrNull()
    }

    override fun writeSettings(settings: WearSettingsPayload) {
        prefs.edit().putString(KEY_WATCH_SETTINGS, gson.toJson(settings)).apply()
    }

    override fun readLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    override fun markSynced(atEpochMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC, atEpochMillis).apply()
    }

    // An unreadable stamp map degrades to "nothing was ever edited here", which the merge reads as
    // "take the watch's value" - the behaviour that predates two-way sync, and never a reset.
    override fun readFieldTimestamps(): Map<String, Long> {
        val stored = prefs.getString(KEY_FIELD_TIMESTAMPS, null) ?: return emptyMap()
        return runCatching { gson.fromJson(stored, STAMP_MAP_TYPE) ?: emptyMap<String, Long>() }
            .onFailure { Timber.w(it, "Stored watch settings timestamps unreadable, ignoring them") }
            .getOrDefault(emptyMap())
    }

    override fun writeFieldTimestamps(stamps: Map<String, Long>) {
        prefs.edit().putString(KEY_FIELD_TIMESTAMPS, gson.toJson(stamps)).apply()
    }

    companion object {
        private const val PREFS_NAME = "wear_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_WATCH_SETTINGS = "watch_settings_payload"
        private const val KEY_FIELD_TIMESTAMPS = "watch_settings_field_timestamps"

        // Gson erases the generic on a plain Map::class.java and hands back Double values; the token
        // is what keeps the epoch-millis a Long.
        private val STAMP_MAP_TYPE = object : TypeToken<Map<String, Long>>() {}.type
    }
}
