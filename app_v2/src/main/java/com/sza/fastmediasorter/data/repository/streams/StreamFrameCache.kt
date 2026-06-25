package com.sza.fastmediasorter.data.repository.streams

import android.graphics.Bitmap
import android.os.SystemClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0675: in-memory, TTL-bounded cache of the last captured live-stream frame, keyed by stream URL.
 * The snapshot engine writes off the main thread; the grid adapter reads on it - so all access is
 * guarded by [lock]. Entries older than [FRAME_TTL_MS] are treated as missing (the engine re-captures);
 * an LRU eviction keeps the map under [MAX_ENTRIES]. Bitmaps are never recycled here - one may still be
 * set on a live ImageView; reclamation is left to GC.
 */
@Singleton
class StreamFrameCache @Inject constructor() {

    private data class Entry(val bitmap: Bitmap, val capturedAtElapsed: Long)

    private val lock = Any()

    // accessOrder = true so iteration order reflects LRU; the eldest is evicted past capacity.
    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    /** Returns the cached bitmap only if younger than the TTL; null otherwise (miss or expired). */
    fun get(url: String): Bitmap? = synchronized(lock) {
        val entry = entries[url] ?: return null
        if (isExpired(entry)) null else entry.bitmap
    }

    /** True when a non-expired entry exists - the snapshot engine skips re-capture for fresh urls. */
    fun isFresh(url: String): Boolean = synchronized(lock) {
        val entry = entries[url] ?: return false
        !isExpired(entry)
    }

    /** Stores/refreshes the entry for [url], evicting the eldest beyond [MAX_ENTRIES]. */
    fun put(url: String, bitmap: Bitmap) = synchronized(lock) {
        entries[url] = Entry(bitmap, SystemClock.elapsedRealtime())
    }

    fun invalidate(url: String) = synchronized(lock) {
        entries.remove(url)
        Unit
    }

    fun clear() = synchronized(lock) {
        entries.clear()
    }

    private fun isExpired(entry: Entry): Boolean =
        SystemClock.elapsedRealtime() - entry.capturedAtElapsed > FRAME_TTL_MS

    private companion object {
        const val FRAME_TTL_MS = 60_000L
        const val MAX_ENTRIES = 64
    }
}
