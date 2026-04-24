package com.sza.fastmediasorter.vr.helpers

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import timber.log.Timber

/**
 * SharedPreferences-backed LRU list of recent copy/move destinations for the
 * VR immersive file operations panel.
 *
 * Per ADR-1 (tech spec) we avoid Room entities + migrations for this; a
 * 10-entry JSON array is sufficient until the feature proves it needs more.
 */
class VrRecentDestinationsPrefs(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Add [path] to the front of the MRU list, trimming to [MAX_ENTRIES]. */
    fun add(path: String) {
        if (path.isBlank()) return
        val current = getAll().toMutableList()
        current.remove(path)
        current.add(0, path)
        while (current.size > MAX_ENTRIES) current.removeAt(current.size - 1)
        save(current)
    }

    /** Read the MRU list (most recent first). */
    fun getAll(): List<String> {
        val raw = prefs.getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) out.add(arr.getString(i))
            out
        } catch (t: Throwable) {
            Timber.w(t, "VrRecentDestinationsPrefs: failed to parse, resetting")
            prefs.edit().remove(KEY_LIST).apply()
            emptyList()
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_LIST).apply()
    }

    private fun save(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        prefs.edit().putString(KEY_LIST, arr.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "vr_recent_destinations"
        private const val KEY_LIST = "paths"
        const val MAX_ENTRIES = 10
    }
}
