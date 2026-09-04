package com.sza.fastmediasorter.data.repository.wear

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S2507 phase 04: which phone resource a watch-created source became.
 *
 * A source entered on the watch carries a `UUID` the phone never issued, so the phone stores it under
 * a database id of its own. Ordinary records survive that renaming because S2502 ADR-3 falls back to
 * the credential tuple, but a tombstone carries only an id and strategic 7 forbids giving it an
 * address key - so the mapping has to be remembered at the moment the renaming happens, or the watch's
 * deletion names an id this phone cannot resolve and deletes nothing.
 *
 * Phone-local by construction: nothing here travels, and the watch adopts the phone's id on the next
 * exchange (`NetworkSourceRepositoryImpl.upsertSource`, S1734), after which the alias is dead weight
 * that [forget] clears.
 */
interface WearResourceIdAliasStore {

    /** The phone resource id recorded for [foreignId], or null when that id was never aliased. */
    fun resolve(foreignId: String): Long?

    fun record(foreignId: String, resourceId: Long)

    fun forget(foreignId: String)
}

class SharedPreferencesWearResourceIdAliasStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearResourceIdAliasStore {

    private val preferences
        get() = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun resolve(foreignId: String): Long? = readAliases()[foreignId]

    override fun record(foreignId: String, resourceId: Long) {
        saveAliases(readAliases() + (foreignId to resourceId))
    }

    override fun forget(foreignId: String) {
        val current = readAliases()
        if (current.containsKey(foreignId)) {
            saveAliases(current - foreignId)
        }
    }

    // The declared parameter type is what keeps the serialized shape pinned to a class R8 must keep.
    private fun saveAliases(aliases: Map<String, Long>) {
        preferences.edit().putString(KEY_ALIASES, gson.toJson(aliases)).apply()
    }

    // An unreadable map degrades to "no alias is known", which is the behaviour that predates this
    // phase: a foreign id resolves to nothing and the deletion is skipped rather than misapplied.
    private fun readAliases(): Map<String, Long> {
        val stored = preferences.getString(KEY_ALIASES, null) ?: return emptyMap()
        return runCatching { gson.fromJson(stored, ALIAS_MAP_TYPE) ?: emptyMap<String, Long>() }
            .onFailure { Timber.w(it, "Stored wear resource id aliases unreadable, ignoring them") }
            .getOrDefault(emptyMap())
    }

    private companion object {
        const val PREFERENCES_NAME = "wear_resource_id_aliases"
        const val KEY_ALIASES = "aliases"
        val ALIAS_MAP_TYPE = object : TypeToken<Map<String, Long>>() {}.type
    }
}
