package com.sza.fastmediasorter.ui.streams

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * S1154: turns `(atlasFile, tileIndex)` into a 240x135 preview [Bitmap] using the fixed-grid contract
 * shared with the offline packer (PHASE_06). Unlike [FaviconAtlasSlicer]'s decode-once path, the
 * channel-preview sheet is large (single 8192x8192 image), so this slicer NEVER decodes the whole
 * sheet: it opens one cached [BitmapRegionDecoder] and crops each tile with `decodeRegion` (ADR-2).
 *
 * The [atlasFileProvider] is re-read on each (re)open, so [invalidate] after a re-download picks up
 * the new sheet.
 */
class ChannelPreviewAtlasSlicer(
    private val atlasFileProvider: () -> File?
) {
    private val mutex = Mutex()
    private var decoder: BitmapRegionDecoder? = null
    private var opened = false

    /**
     * The pixel rect of tile [index] in the fixed grid. Pure - the single source of truth for the
     * index->rect math, mirrored by the offline packer. `col = index % COLS`, `row = index / COLS`.
     */
    fun rectFor(index: Int): Rect {
        val col = index % COLS
        val row = index / COLS
        val left = col * TILE_W
        val top = row * TILE_H
        return Rect(left, top, left + TILE_W, top + TILE_H)
    }

    /**
     * True only when [index] is non-negative and its tile rect fits inside an `atlasWidth x
     * atlasHeight` sheet. A stale/oversized index (e.g. an old sidecar pointing past a shrunk sheet)
     * is out of bounds -> no tile, never a crash.
     */
    fun isInBounds(index: Int, atlasWidth: Int, atlasHeight: Int): Boolean {
        if (index < 0) return false
        val rect = rectFor(index)
        return rect.right <= atlasWidth && rect.bottom <= atlasHeight
    }

    /**
     * The 240x135 tile bitmap for [index], or null when the atlas is absent or the index is out of
     * bounds. Runs off the main thread and only region-decodes the requested tile - the full sheet is
     * never materialised.
     */
    suspend fun tileFor(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (index < 0) return@withContext null
        val activeDecoder = decoder() ?: return@withContext null
        // S1220: decoder() releases the mutex before returning, so invalidate() can recycle this
        // reference at any moment and EVERY member access then throws - width/height as much as
        // decodeRegion. Keep every one of them inside this try; the bounds check used to sit above
        // it and killed the process on navigate-away.
        try {
            if (!isInBounds(index, activeDecoder.width, activeDecoder.height)) {
                null
            } else {
                activeDecoder.decodeRegion(rectFor(index), BitmapFactory.Options())
            }
        } catch (e: IllegalArgumentException) {
            // A region outside the sheet yields no tile rather than a crash.
            Timber.i(e, "Channel-preview atlas region decode rejected for index=$index")
            null
        } catch (e: IllegalStateException) {
            // A decoder recycled mid-read (post-invalidate race) yields no tile rather than a crash.
            Timber.i(e, "Channel-preview atlas decoder unusable for index=$index")
            null
        }
    }

    /** Recycles the cached decoder so the next [tileFor] re-reads [atlasFileProvider] (post-download). */
    suspend fun invalidate() = mutex.withLock {
        decoder?.recycle()
        decoder = null
        opened = false
    }

    private suspend fun decoder(): BitmapRegionDecoder? = mutex.withLock {
        if (!opened) {
            opened = true
            val file = atlasFileProvider()
            decoder = if (file != null && file.isFile) openDecoder(file) else null
        }
        decoder
    }

    private fun openDecoder(file: File): BitmapRegionDecoder? = try {
        file.inputStream().use { stream ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(stream)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(stream, false)
            }
        }
    } catch (e: IOException) {
        Timber.i(e, "Channel-preview atlas decoder could not open ${file.name}")
        null
    }

    companion object {
        // S1154: app-side half of the PHASE_06 sprite-atlas contract. The offline packer must emit
        // indices for the SAME 240x135 tile / 34-column grid, or the rects drift.
        const val TILE_W = 240
        const val TILE_H = 135
        const val COLS = 34
    }
}
