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
 *
 * S0712: an entry also carries a [Entry.live] flag. Live entries are captures from this session and obey
 * the TTL as before. Restored entries ([putRestored]) are last-session thumbnails pre-warmed from disk:
 * they are always shown by [get] (the persistent thumbnail) but never count as [isFresh], so the snapshot
 * engine still captures a current frame and overwrites the stale disk thumbnail once it lands.
 */
@Singleton
class StreamFrameCache @Inject constructor() {

    private data class Entry(val bitmap: Bitmap, val capturedAtElapsed: Long, val live: Boolean)

    private val lock = Any()

    // accessOrder = true so iteration order reflects LRU; the eldest is evicted past capacity.
    private val entries = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    /**
     * S0784: always returns the last captured frame for [url] once one exists (live or restored), so a
     * grid tile keeps showing its last thumbnail and never reverts to the favicon/atlas placeholder.
     * Freshness is decoupled from what is painted: [isFresh] still decides whether the snapshot engine
     * re-captures in the background, but an expired live frame is shown until a fresh capture replaces it
     * in place. Null only when the url was never captured, so the grid does the initial capture.
     */
    fun get(url: String): Bitmap? = synchronized(lock) {
        entries[url]?.bitmap
    }

    /** True only for a non-expired live entry - the snapshot engine skips re-capture for fresh urls. */
    fun isFresh(url: String): Boolean = synchronized(lock) {
        val entry = entries[url] ?: return false
        entry.live && !isExpired(entry)
    }

    /** True when any entry (live or restored) already holds [url] - skips a redundant disk pre-warm. */
    fun hasEntry(url: String): Boolean = synchronized(lock) { entries.containsKey(url) }

    /** Stores/refreshes the live entry for [url], evicting the eldest beyond [MAX_ENTRIES]. */
    fun put(url: String, bitmap: Bitmap) = synchronized(lock) {
        entries[url] = Entry(bitmap, SystemClock.elapsedRealtime(), live = true)
    }

    /**
     * S0712: seed a restored disk thumbnail for [url], but never clobber an entry already present (a
     * live capture or earlier restore stays authoritative). Shown by [get], ignored by [isFresh].
     */
    fun putRestored(url: String, bitmap: Bitmap) = synchronized(lock) {
        if (entries.containsKey(url)) return
        entries[url] = Entry(bitmap, SystemClock.elapsedRealtime(), live = false)
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
