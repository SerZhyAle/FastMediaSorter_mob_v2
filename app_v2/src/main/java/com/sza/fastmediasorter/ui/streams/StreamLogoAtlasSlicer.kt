package com.sza.fastmediasorter.ui.streams

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException

/**
 * S1201: turns `(atlasFile, tileIndex)` into a 136x136 logo [Bitmap] using the fixed-grid contract
 * shared with the offline packer's `Build-StreamLogoAtlas`. Like [ChannelPreviewAtlasSlicer] the sheet
 * is large, so this slicer NEVER decodes the whole image: it opens one cached [BitmapRegionDecoder] and
 * crops each tile with `decodeRegion`.
 *
 * Two things differ from the preview sheet, both deliberate. The tile is SQUARE, because a logo is
 * fitted whole and is almost always square - on a 16:9 tile it reached the same height while nearly
 * half the width stayed empty padding. And the sheet carries real alpha, so the area around a logo
 * takes the cell's own colour and one sheet serves both themes; tiles are therefore decoded as
 * `ARGB_8888`, since flattening them would paint that area black. The square tile is rendered
 * letterboxed into the 16:9 cell by the caller.
 *
 * The [atlasFileProvider] is re-read on each (re)open, so [invalidate] after a download picks up the
 * new sheet.
 *
 * S1445: this sheet path is now the FALLBACK, for the same reason as in [ChannelPreviewAtlasSlicer] -
 * a region decode out of a sprite sheet costs a share of a full-sheet decode. When a tile pack is
 * installed, [tilePackReader] serves the tile; the sheet path stays for installs that have not taken
 * the payload update.
 */
class StreamLogoAtlasSlicer(
    private val atlasFileProvider: () -> File?,
    private val tilePackReader: StreamTilePackReader? = null,
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
     * The 136x136 tile bitmap for [index], or null when the atlas is absent or the index is out of
     * bounds. Runs off the main thread and only region-decodes the requested tile.
     */
    suspend fun tileFor(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (index < 0) return@withContext null
        val pack = tilePackReader
        if (pack != null && pack.hasPack()) return@withContext pack.tile(index)
        val activeDecoder = decoder() ?: return@withContext null
        val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        // S1220: decoder() releases the mutex before returning, so invalidate() can recycle this
        // reference at any moment and EVERY member access then throws - width/height as much as
        // decodeRegion. Keep every one of them inside this try.
        try {
            if (!isInBounds(index, activeDecoder.width, activeDecoder.height)) {
                null
            } else {
                activeDecoder.decodeRegion(rectFor(index), options)
            }
        } catch (e: IllegalArgumentException) {
            // A region outside the sheet yields no tile rather than a crash.
            Timber.i(e, "Stream logo atlas region decode rejected for index=$index")
            null
        } catch (e: CancellationException) {
            // A cancelled read is not a recycled decoder: answering null here caches "no tile" for
            // an index that was never actually read (S1889).
            throw e
        } catch (e: IllegalStateException) {
            // A decoder recycled mid-read (post-invalidate race) yields no tile rather than a crash.
            Timber.i(e, "Stream logo atlas decoder unusable for index=$index")
            null
        }
    }

    /** Recycles the cached decoder so the next [tileFor] re-reads [atlasFileProvider] (post-download). */
    suspend fun invalidate() {
        // The pack reader holds its own handle and tile cache, so a payload update has to reset both
        // halves of this slicer or the stale one keeps answering.
        tilePackReader?.invalidate()
        mutex.withLock {
            decoder?.recycle()
            decoder = null
            opened = false
        }
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
        Timber.i(e, "Stream logo atlas decoder could not open ${file.name}")
        null
    }

    companion object {
        // S1201: app-side half of the offline packer's grid contract. `Build-StreamLogoAtlas` must emit
        // indices for the SAME 136x136 tile / 59-column grid, or the rects drift. The size is even
        // because the sheet is lossy WebP (always 4:2:0) - an odd tile would put every second boundary
        // mid-chroma-block and bleed one tile's edge colour into the next.
        const val TILE_W = 136
        const val TILE_H = 136
        const val COLS = 59
    }
}
