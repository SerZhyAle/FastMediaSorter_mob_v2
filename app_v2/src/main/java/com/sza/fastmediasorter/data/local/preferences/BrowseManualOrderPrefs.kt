package com.sza.fastmediasorter.data.local.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists manual file ordering per directory path + resource.
 * Stored as a newline-delimited list of file paths in SharedPreferences.
 * Key format: "<resourceId>_<stablePathHash>".
 */
@Singleton
class BrowseManualOrderPrefs @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveOrder(resourceId: Long, dirPath: String, orderedPaths: List<String>) {
        val key = buildKey(resourceId, dirPath)
        prefs.edit()
            .putString(key, orderedPaths.joinToString(SEPARATOR))
            .apply()
        Timber.d("BrowseManualOrderPrefs: saved ${orderedPaths.size} paths for key=$key")
    }

    fun loadOrder(resourceId: Long, dirPath: String): List<String>? {
        val key = buildKey(resourceId, dirPath)
        val raw = prefs.getString(key, null) ?: return null
        if (raw.isBlank()) return null
        return raw.split(SEPARATOR).filter { it.isNotBlank() }
    }

    fun clearOrder(resourceId: Long, dirPath: String) {
        prefs.edit().remove(buildKey(resourceId, dirPath)).apply()
    }

    private fun buildKey(resourceId: Long, dirPath: String): String {
        // Hash path to keep key length reasonable for SharedPreferences
        val pathHash = dirPath.hashCode().toString()
        return "${resourceId}_$pathHash"
    }

    companion object {
        private const val PREFS_NAME = "browse_manual_order"
        private const val SEPARATOR = "\n"
    }
}
