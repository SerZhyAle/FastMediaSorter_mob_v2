package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2502: when each phone resource was last edited, in this phone's own time base.
 *
 * Kept beside the `resources` table rather than inside it (ADR-1). A column would demand a database
 * version bump and a migration - the one irreversible change in this project - for a value nothing
 * outside the watch exchange ever reads. The settings mirror made the same choice for the same
 * reason: see [WearSettingsMirrorStore.readFieldTimestamps].
 *
 * The key is the resource id in its string form, which is exactly what travels on the wire, so the
 * importer needs no conversion in the one place a mismatch would be silent.
 */
interface WearResourceStampStore {

    fun readStamps(): Map<String, Long>

    /** Records an edit made on this phone, now. */
    fun stampEdit(resourceId: String)

    /**
     * Records the edit time a merge resolved, in this phone's time base. Separate from [stampEdit]
     * because an imported record must carry the time it was edited on the OTHER device, corrected for
     * the clock offset - stamping it "now" would make every imported record look freshly edited and
     * win the next exchange against a genuine edit.
     */
    fun writeStamp(resourceId: String, atEpochMillis: Long)

    /** Drops the stamp of a resource that no longer exists, so the map does not grow without bound. */
    fun forget(resourceId: String)
}

class SharedPreferencesWearResourceStampStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearResourceStampStore {

    private val prefs
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // An unreadable map degrades to "nothing was ever edited here", which the merge rule answers with
    // "take the incoming record" - the behaviour that predates this ticket, and never a data reset.
    override fun readStamps(): Map<String, Long> {
        val stored = prefs.getString(KEY_STAMPS, null) ?: return emptyMap()
        return runCatching { gson.fromJson(stored, STAMP_MAP_TYPE) ?: emptyMap<String, Long>() }
            .onFailure { Timber.w(it, "Stored resource edit stamps unreadable, ignoring them") }
            .getOrDefault(emptyMap())
    }

    override fun stampEdit(resourceId: String) {
        writeStamp(resourceId, System.currentTimeMillis())
    }

    override fun writeStamp(resourceId: String, atEpochMillis: Long) {
        val updated = readStamps() + (resourceId to atEpochMillis)
        prefs.edit().putString(KEY_STAMPS, gson.toJson(updated)).apply()
    }

    override fun forget(resourceId: String) {
        val current = readStamps()
        if (!current.containsKey(resourceId)) {
            return
        }
        prefs.edit().putString(KEY_STAMPS, gson.toJson(current - resourceId)).apply()
    }

    private companion object {
        const val PREFS_NAME = "wear_resource_stamps"
        const val KEY_STAMPS = "resource_edit_stamps"

        // Gson erases the generic on a plain Map::class.java and hands back Double values; the token
        // is what keeps the epoch-millis a Long.
        val STAMP_MAP_TYPE = object : TypeToken<Map<String, Long>>() {}.type
    }
}
