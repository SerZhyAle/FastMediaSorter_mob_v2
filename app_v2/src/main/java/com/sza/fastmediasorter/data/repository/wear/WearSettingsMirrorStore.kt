package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Mirror of the last watch settings pushed from the phone, plus when the watch last acknowledged.
 *
 * S2050: a field belongs here only if nothing outside the companion sheet's own restore path ever
 * reads it and it exists solely to remember what was last told to the watch (the sheet's `ViewModel`
 * dies with its `BottomSheetDialogFragment`, and the watch has no channel to report its own settings
 * back, so this is the only place that survives a reopen). A field that any other part of the app
 * reads reactively belongs in `AppSettings`/DataStore instead - see
 * `ProgramsSettingsStore.KEY_ENABLE_WEAR_COMPANION` for that case.
 */
interface WearSettingsMirrorStore {

    fun readSettings(): WearSettingsPayload?

    fun writeSettings(settings: WearSettingsPayload)

    fun readLastSyncTimestamp(): Long

    fun markSynced(atEpochMillis: Long)
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

    companion object {
        private const val PREFS_NAME = "wear_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_WATCH_SETTINGS = "watch_settings_payload"
    }
}
