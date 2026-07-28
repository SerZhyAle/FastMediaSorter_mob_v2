package com.sza.fastmediasorter.ui.streams

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * S0668 / PHASE_04: turns `(atlasFile, faviconIndex)` into a 32 px tile [Bitmap] using the fixed-grid
 * contract shared with the offline packer (see PHASE_01). The atlas PNG is decoded ONCE and cached
 * whole; per-row calls crop a sub-rect off the cached bitmap on a background dispatcher, so list
 * scrolling never decodes per row.
 *
 * The [atlasFileProvider] is re-read on each (re)decode, so [invalidate] after a catalog import that
 * replaced the file is enough to pick up the new atlas.
 */
class FaviconAtlasSlicer(
    private val atlasFileProvider: () -> File?
) {
    private val mutex = Mutex()
    private var cachedAtlas: Bitmap? = null
    private var decoded = false

    /**
     * The pixel rect of tile [index] in the fixed grid. Pure (the single source of truth for the
     * index->rect math, mirrored by the offline packer in PHASE_01/PHASE_02). `col = index % COLS`,
     * `row = index / COLS`.
     */
    fun rectFor(index: Int): Rect {
        val col = index % COLS
        val row = index / COLS
        val left = col * TILE
        val top = row * TILE
        return Rect(left, top, left + TILE, top + TILE)
    }

    /**
     * True only when [index] is non-negative and its tile rect fits inside an `atlasWidth x
     * atlasHeight` atlas. A stale/oversized index (e.g. an old sidecar pointing past a shrunk atlas)
     * is out of bounds -> no thumbnail, never a crash.
     */
    fun isInBounds(index: Int, atlasWidth: Int, atlasHeight: Int): Boolean {
        if (index < 0) return false
        val rect = rectFor(index)
        return rect.right <= atlasWidth && rect.bottom <= atlasHeight
    }

    /**
     * The 32 px tile bitmap for [index], or null when the atlas is absent or the index is out of
     * bounds. Decode-once: the whole atlas is decoded on first use and cached; subsequent calls reuse
     * it. Runs off the main thread.
     *
     * WHY decode-once over BitmapRegionDecoder: a 32 px / 16-col atlas is small enough to hold whole
     * (one decode covers the whole list); escalate to BitmapRegionDecoder only if profiling shows the
     * full-atlas bitmap is a memory problem (INDEX "Decisions fixed by this plan").
     */
    suspend fun tileFor(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (index < 0) return@withContext null
        val atlas = atlas() ?: return@withContext null
        // S1220: atlas() releases the mutex before returning, so invalidate() can recycle this bitmap
        // at any moment - the same race that crashed the two decoder slicers, and this path had no
        // guard at all. A recycled source is rejected by createBitmap, but which exception carries
        // that rejection has varied across platform versions, so both are caught rather than betting
        // on one. Every access to the bitmap belongs inside this try.
        try {
            if (!isInBounds(index, atlas.width, atlas.height)) {
                null
            } else {
                val rect = rectFor(index)
                // createBitmap copies the sub-region, so cropping does not mutate the cached atlas.
                Bitmap.createBitmap(atlas, rect.left, rect.top, TILE, TILE)
            }
        } catch (e: IllegalStateException) {
            // The cached atlas was recycled mid-read - no tile rather than a crash.
            Timber.i(e, "Favicon atlas bitmap unusable for index=$index")
            null
        } catch (e: IllegalArgumentException) {
            // A rect outside a re-downloaded, smaller sheet yields no tile rather than a crash.
            Timber.i(e, "Favicon atlas region rejected for index=$index")
            null
        }
    }

    /** Drops the cached atlas so the next [tileFor] re-reads [atlasFileProvider] (post-import refresh). */
    suspend fun invalidate() = mutex.withLock {
        cachedAtlas?.recycle()
        cachedAtlas = null
        decoded = false
    }

    private suspend fun atlas(): Bitmap? = mutex.withLock {
        if (!decoded) {
            decoded = true
            val file = atlasFileProvider()
            cachedAtlas = if (file != null && file.isFile) {
                BitmapFactory.decodeFile(file.path)
            } else {
                null
            }
        }
        cachedAtlas
    }

    companion object {
        // S0668: app-side half of the PHASE_01/PHASE_02 sprite-atlas contract. The offline packer must
        // emit indices for the SAME 32 px tile / 16-column grid, or the rects drift.
        const val TILE = 32
        const val COLS = 16
    }
}
