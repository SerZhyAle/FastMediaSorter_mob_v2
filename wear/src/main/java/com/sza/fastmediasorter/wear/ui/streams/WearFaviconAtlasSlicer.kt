package com.sza.fastmediasorter.wear.ui.streams

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * S1708: Slices 32 px tiles from the cached favicon atlas PNG for Wear OS UI.
 */
class WearFaviconAtlasSlicer(
    private val atlasFileProvider: () -> File?
) {
    private val mutex = Mutex()
    private var cachedAtlas: Bitmap? = null
    private var decoded = false

    data class TileRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun rectFor(index: Int): TileRect {
        val col = index % COLS
        val row = index / COLS
        val left = col * TILE
        val top = row * TILE
        return TileRect(left, top, left + TILE, top + TILE)
    }

    // STREAM-BANK 2.4 SB-A12: compared in tile units because `row * TILE` overflows Int for an index
    // near Int.MAX_VALUE and wraps back inside the sheet.
    fun isInBounds(index: Int, atlasWidth: Int, atlasHeight: Int): Boolean =
        index >= 0 && index % COLS < atlasWidth / TILE && index / COLS < atlasHeight / TILE

    suspend fun tileFor(index: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (index < 0) return@withContext null
        val atlas = atlas() ?: return@withContext null
        try {
            if (!isInBounds(index, atlas.width, atlas.height)) {
                null
            } else {
                val rect = rectFor(index)
                Bitmap.createBitmap(atlas, rect.left, rect.top, TILE, TILE)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            Timber.i(e, "Favicon atlas bitmap unusable for index=$index")
            null
        } catch (e: IllegalArgumentException) {
            Timber.i(e, "Favicon atlas region rejected for index=$index")
            null
        }
    }

    suspend fun invalidate() = mutex.withLock {
        cachedAtlas?.recycle()
        cachedAtlas = null
        decoded = false
    }

    /**
     * Does not wait for an active slice: recycling its source bitmap would make that slice fail.
     */
    fun releaseNow() {
        if (!mutex.tryLock()) return
        try {
            cachedAtlas?.recycle()
            cachedAtlas = null
            decoded = false
        } finally {
            mutex.unlock()
        }
    }

    /**
     * S2149: the atlas is a single bitmap sized to the whole catalogue, so the first request for any
     * tile pays the entire decode.
     *
     * The dispatcher is named here rather than inherited from the caller, so that cost is a property
     * of the decode instead of an accident of whoever asks first. Today the only caller is [tileFor],
     * which already runs on IO; stating it here is what keeps a future second caller from deciding it
     * by mistake on the thread drawing the first visible row. The mutex still bounds it to one decode.
     */
    private suspend fun atlas(): Bitmap? = mutex.withLock {
        if (!decoded) {
            decoded = true
            val file = atlasFileProvider()
            cachedAtlas = withContext(Dispatchers.IO) {
                if (file != null && file.isFile) {
                    decodeSheet(file)
                } else {
                    null
                }
            }
        }
        cachedAtlas
    }

    /**
     * The grid is addressed in whole [TILE] pixels, so the sheet is decoded at the scale that makes it
     * [SHEET_WIDTH_PX] wide rather than at the scale of a display cell. A sheet already that wide is
     * read at full size; only a denser re-render of the same grid is sampled, and its tiles still land
     * on the same rects.
     */
    private fun decodeSheet(file: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth) }
        return BitmapFactory.decodeFile(file.path, options)
    }

    /** The largest power of two that leaves the sheet at least [SHEET_WIDTH_PX] wide. */
    private fun sampleSizeFor(sheetWidth: Int): Int {
        var sample = 1
        while (sheetWidth / (sample * SAMPLE_STEP) >= SHEET_WIDTH_PX) {
            sample *= SAMPLE_STEP
        }
        return sample
    }

    companion object {
        const val TILE = 32
        const val COLS = 16
        private const val SHEET_WIDTH_PX = COLS * TILE
        private const val SAMPLE_STEP = 2
    }
}
