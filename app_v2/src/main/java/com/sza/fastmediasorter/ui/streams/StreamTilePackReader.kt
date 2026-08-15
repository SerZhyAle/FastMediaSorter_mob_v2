package com.sza.fastmediasorter.ui.streams

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * S1445: reads one artwork tile out of a tile-pack container, in time that does not depend on how
 * many tiles the payload holds.
 *
 * The sprite sheets this replaces are not randomly addressable: a WebP decoder has to walk the
 * stream from the top to reach the requested row, so a single `decodeRegion` against the 61,7 Mpx
 * channel-preview sheet costs a share of a full-sheet decode (measured at 1,48 s on the dev PC).
 * A grid of cells asking for one tile each therefore filled in one cell at a time, and most decodes
 * finished after their cell had already been recycled. Here each tile is its own ZIP entry, so a
 * tile costs one small image decode, and a re-shown tile costs nothing.
 *
 * Container contract, shared with the offline packer: entry name is the slot index as a plain
 * decimal string with no extension, entries are stored uncompressed, and the image format of an
 * entry is whatever [BitmapFactory] can sniff.
 *
 * The [packFileProvider] is re-read on each (re)open, so [invalidate] after a download picks up the
 * new container.
 */
class StreamTilePackReader(
    private val packFileProvider: () -> File?,
    cacheBudgetBytes: Int,
) {
    private val mutex = Mutex()
    private var archive: ZipFile? = null
    private var opened = false

    // Sized in bytes rather than entries: a tile is a fixed-size bitmap per payload, but the two
    // payloads have different tile sizes and would otherwise need different entry counts.
    private val cache = object : LruCache<Int, Bitmap>(cacheBudgetBytes.coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    /** True when a tile pack is installed - the caller uses this to choose between reader and sheet. */
    fun hasPack(): Boolean = packFileProvider()?.isFile == true

    /**
     * The tile bitmap for [index], or null when the pack is absent, holds no such entry, or the
     * entry does not decode. Runs off the main thread; a cache hit answers without touching disk.
     */
    suspend fun tile(index: Int): Bitmap? {
        if (index < 0) return null
        return cache.get(index) ?: withContext(Dispatchers.IO) { readTile(index) }
    }

    private suspend fun readTile(index: Int): Bitmap? {
        val zip = archive() ?: return null
        // archive() releases the mutex before returning, so invalidate() can close this handle at any
        // moment and every later access on it throws - entry lookup as much as the read. Keep both
        // inside this try (the S1220 lesson from the region-decoder path).
        return try {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            zip.getEntry(index.toString())
                ?.let { entry -> zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) } }
                ?.also {
                    cache.put(index, it)
                    logFirstDecode(index, android.os.SystemClock.elapsedRealtime() - startedAt)
                }
        } catch (e: IOException) {
            Timber.i(e, "Stream tile pack entry unreadable for index=$index")
            null
        } catch (e: IllegalStateException) {
            Timber.i(e, "Stream tile pack closed while reading index=$index")
            null
        }
    }

    // Temporary S1445 device probe: one line per (re)open, carrying the cost of the first tile read
    // from that container - the number the ticket is about. Removed when the ticket leaves
    // BlockNeedUserTest.
    @Volatile
    private var decodeProbeLogged = false

    private fun logFirstDecode(index: Int, elapsedMs: Long) {
        if (decodeProbeLogged) return
        decodeProbeLogged = true
    }

    /** Closes the container and drops every cached tile, so the next [tile] re-reads the provider. */
    suspend fun invalidate() = mutex.withLock {
        try {
            archive?.close()
        } catch (e: IOException) {
            Timber.i(e, "Stream tile pack close failed")
        }
        archive = null
        opened = false
        decodeProbeLogged = false
        cache.evictAll()
    }

    private suspend fun archive(): ZipFile? = mutex.withLock {
        if (!opened) {
            opened = true
            val file = packFileProvider()
            archive = if (file != null && file.isFile) openArchive(file) else null
        }
        archive
    }

    private fun openArchive(file: File): ZipFile? = try {
        ZipFile(file)
    } catch (e: IOException) {
        Timber.i(e, "Stream tile pack could not open ${file.name}")
        null
    }

    companion object {
        private const val BYTES_PER_MB = 1024 * 1024
        private const val MIN_CACHE_BYTES = 4 * BYTES_PER_MB
        // Two readers live side by side (previews and logos), so the ceiling is per payload and has to
        // be half of what one cache could afford on its own.
        private const val MAX_CACHE_BYTES = 12 * BYTES_PER_MB
        private const val HEAP_SHARE_DIVISOR = 8

        /**
         * Tile-cache budget for this device: an eighth of the app's heap class, clamped so a small
         * device still caches a screenful and a large one does not hoard tens of megabytes of
         * artwork the user may never scroll back to.
         */
        fun budgetBytes(context: Context): Int {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val heapBytes = (manager?.memoryClass ?: 0) * BYTES_PER_MB
            return (heapBytes / HEAP_SHARE_DIVISOR).coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
        }
    }
}
