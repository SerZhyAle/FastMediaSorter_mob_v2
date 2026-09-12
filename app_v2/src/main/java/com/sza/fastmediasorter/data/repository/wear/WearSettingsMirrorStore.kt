package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 *
 * S2515 (ADR-3): the contract is deliberately mixed, and the split is not an oversight. Every WRITE
 * suspends and moves itself to IO. The three reads below stay synchronous because they feed field
 * initialisers of the companion sheet's `ViewModel` - two of them derived from a third at
 * construction - and an asynchronous read would render the sheet's defaults before the mirror
 * arrived, which is precisely the defect this store was created to prevent: a picked grid reading
 * back as the list default. [readFieldTimestamps] suspends with the writes, having no such caller.
 */
interface WearSettingsMirrorStore {

    /** Synchronous by ADR-3 - read from a field initialiser. */
    fun readSettings(): WearSettingsPayload?

    suspend fun writeSettings(settings: WearSettingsPayload)

    /** Synchronous by ADR-3 - read from a field initialiser. */
    fun readLastSyncTimestamp(): Long

    /**
     * S2461: the version name the watch reported with the last completed exchange, or null when the
     * watch that answered did not report one. Synchronous by ADR-3 - read from a field initialiser.
     */
    fun readWatchAppVersion(): String?

    /**
     * S2461: the time and the version are written together because they state one fact about one
     * exchange - which build accepted the sync recorded at this moment. Two independent writes would
     * let a version from an earlier pairing stand beside a fresh time, which reads as a working sync
     * against the wrong build.
     *
     * @param watchAppVersionName null when the report carried none, which CLEARS the stored value.
     */
    suspend fun markSynced(atEpochMillis: Long, watchAppVersionName: String?)

    /**
     * S2093: contract field name to epoch-millis of that field's last edit, in this phone's time base.
     *
     * Kept beside the payload rather than inside it, so a stamp map written by this build is readable
     * by a build that predates it and vice versa - the payload's own parse already falls back to "no
     * settings known" on any change of shape, and losing the whole mirror to gain a stamp would be a
     * bad trade.
     */
    suspend fun readFieldTimestamps(): Map<String, Long>

    suspend fun writeFieldTimestamps(stamps: Map<String, Long>)
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

    override suspend fun writeSettings(settings: WearSettingsPayload) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_WATCH_SETTINGS, gson.toJson(settings)).apply()
        }
    }

    override fun readLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC, 0L)

    override fun readWatchAppVersion(): String? = prefs.getString(KEY_WATCH_APP_VERSION, null)

    override suspend fun markSynced(atEpochMillis: Long, watchAppVersionName: String?) {
        withContext(Dispatchers.IO) {
            prefs.edit().apply {
                putLong(KEY_LAST_SYNC, atEpochMillis)
                // Removed rather than left alone when the report carried no version: an older watch answering
                // must not inherit the version string of whatever build answered last.
                if (watchAppVersionName == null) {
                    remove(KEY_WATCH_APP_VERSION)
                } else {
                    putString(KEY_WATCH_APP_VERSION, watchAppVersionName)
                }
            }.apply()
        }
    }

    override suspend fun readFieldTimestamps(): Map<String, Long> = withContext(Dispatchers.IO) {
        readFieldTimestampsFromPrefs()
    }

    override suspend fun writeFieldTimestamps(stamps: Map<String, Long>) {
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_FIELD_TIMESTAMPS, gson.toJson(stamps)).apply()
        }
    }

    // An unreadable stamp map degrades to "nothing was ever edited here", which the merge reads as
    // "take the watch's value" - the behaviour that predates two-way sync, and never a reset.
    private fun readFieldTimestampsFromPrefs(): Map<String, Long> {
        val stored = prefs.getString(KEY_FIELD_TIMESTAMPS, null) ?: return emptyMap()
        return runCatching { gson.fromJson(stored, STAMP_MAP_TYPE) ?: emptyMap<String, Long>() }
            .onFailure { Timber.w(it, "Stored watch settings timestamps unreadable, ignoring them") }
            .getOrDefault(emptyMap())
    }

    companion object {
        private const val PREFS_NAME = "wear_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_WATCH_SETTINGS = "watch_settings_payload"
        private const val KEY_FIELD_TIMESTAMPS = "watch_settings_field_timestamps"
        private const val KEY_WATCH_APP_VERSION = "watch_app_version_name"

        // Gson erases the generic on a plain Map::class.java and hands back Double values; the token
        // is what keeps the epoch-millis a Long.
        private val STAMP_MAP_TYPE = object : TypeToken<Map<String, Long>>() {}.type
    }
}
