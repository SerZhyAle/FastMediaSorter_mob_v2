package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sza.fastmediasorter.domain.model.WearSourceTombstonePayload
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2507: the resources this phone deleted, kept after the resource itself is gone.
 *
 * Stored beside the `resources` table rather than inside it, for the reason [WearResourceStampStore]
 * records: a column would demand a database version bump and a migration for a value nothing outside
 * the watch exchange reads. Here the placement is also forced - the record must outlive the row it
 * describes, which a column cannot do.
 *
 * The key is the resource id in its string form, exactly as it travels on the wire.
 */
interface WearResourceTombstoneStore {

    fun read(): List<WearSourceTombstonePayload>

    fun record(tombstone: WearSourceTombstonePayload)

    fun forget(resourceId: String)
}

class SharedPreferencesWearResourceTombstoneStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearResourceTombstoneStore {

    private val preferences
        get() = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(): List<WearSourceTombstonePayload> = readTombstones()

    override fun record(tombstone: WearSourceTombstonePayload) {
        saveTombstones(readTombstones().filterNot { it.id == tombstone.id } + tombstone)
    }

    override fun forget(resourceId: String) {
        val current = readTombstones()
        val updated = current.filterNot { it.id == resourceId }
        if (updated.size != current.size) {
            saveTombstones(updated)
        }
    }

    // The declared parameter type is what keeps the serialized shape pinned to a class R8 must keep.
    private fun saveTombstones(tombstones: List<WearSourceTombstonePayload>) {
        preferences.edit().putString(KEY_TOMBSTONES, gson.toJson(tombstones)).apply()
    }

    // An unreadable list degrades to "this phone deleted nothing", which is the behaviour that
    // predates this ticket. Letting the parse throw would instead abort the whole exchange, and the
    // watch half already degrades the same way.
    private fun readTombstones(): List<WearSourceTombstonePayload> {
        val stored = preferences.getString(KEY_TOMBSTONES, null) ?: return emptyList()
        return runCatching { gson.fromJson(stored, TOMBSTONE_LIST_TYPE) ?: emptyList<WearSourceTombstonePayload>() }
            .onFailure { Timber.w(it, "Stored resource tombstones unreadable, ignoring them") }
            .getOrDefault(emptyList())
    }

    private companion object {
        const val PREFERENCES_NAME = "wear_resource_tombstones"
        const val KEY_TOMBSTONES = "tombstones"
        val TOMBSTONE_LIST_TYPE = object : TypeToken<List<WearSourceTombstonePayload>>() {}.type
    }
}
