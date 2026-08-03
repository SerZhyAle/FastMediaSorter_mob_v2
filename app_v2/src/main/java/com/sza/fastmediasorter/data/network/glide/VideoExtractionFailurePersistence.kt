package com.sza.fastmediasorter.data.network.glide

import com.sza.fastmediasorter.FastMediaSorterApp
import timber.log.Timber

/**
 * Persists video extraction failure paths across app restarts using SharedPreferences.
 * Entries expire after TTL_MS (7 days). Max MAX_ENTRIES stored (FIFO eviction on overflow).
 *
 * Storage format: Set<String> where each element is "path|timestampMs".
 */
object VideoExtractionFailurePersistence {

    private const val PREFS_NAME = "video_extraction_failures"
    private const val KEY_FAILURES = "failures"
    private const val KEY_SCHEMA_VERSION = "schema_version"
    private const val SCHEMA_VERSION = 2
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_ENTRIES = 500

    /** Returns a map of path → timestamp for all non-expired entries. */
    fun loadAll(): Map<String, Long> {
        val prefs = prefs()
        val storedVersion = prefs.getInt(KEY_SCHEMA_VERSION, 0)
        if (storedVersion != SCHEMA_VERSION) {
            val staleEntries = prefs.getStringSet(KEY_FAILURES, emptySet()) ?: emptySet()
            prefs.edit().remove(KEY_FAILURES).putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION).apply()
            Timber.i("VideoExtractionFailurePersistence: purged ${staleEntries.size} stale entries")
            return emptyMap()
        }
        val raw = prefs.getStringSet(KEY_FAILURES, emptySet()) ?: emptySet()
        val now = System.currentTimeMillis()
        val result = mutableMapOf<String, Long>()
        for (entry in raw) {
            val sep = entry.lastIndexOf('|')
            if (sep < 0) continue
            val ts = entry.substring(sep + 1).toLongOrNull() ?: continue
            if (now - ts > TTL_MS) continue
            result[entry.substring(0, sep)] = ts
        }
        Timber.d("VideoExtractionFailurePersistence: loaded ${result.size}/${raw.size} valid entries")
        return result
    }

    /** Persists a failure entry. Enforces MAX_ENTRIES by dropping the oldest on overflow. */
    fun persistFailure(path: String) {
        val prefs = prefs()
        val raw = prefs.getStringSet(KEY_FAILURES, emptySet())?.toMutableSet() ?: mutableSetOf()

        // Remove any existing entry for this path before re-inserting with fresh timestamp
        raw.removeAll { it.startsWith("$path|") }
        raw.add("$path|${System.currentTimeMillis()}")

        if (raw.size > MAX_ENTRIES) {
            // Drop the oldest entries by timestamp
            val sorted = raw.mapNotNull { entry ->
                val sep = entry.lastIndexOf('|')
                if (sep < 0) null else entry.substring(sep + 1).toLongOrNull()?.let { ts -> entry to ts }
            }.sortedBy { it.second }
            val toRemove = sorted.take(raw.size - MAX_ENTRIES).map { it.first }.toSet()
            raw.removeAll(toRemove)
        }

        prefs.edit().putStringSet(KEY_FAILURES, raw).apply()
    }

    /** Clears all persisted failure entries. */
    fun clearAll() {
        prefs().edit().remove(KEY_FAILURES).apply()
        Timber.i("VideoExtractionFailurePersistence: cleared all entries")
    }

    private fun prefs() = FastMediaSorterApp.appContext
        .getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
}
