package com.sza.fastmediasorter.wear.ui.streams

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

    fun isInBounds(index: Int, atlasWidth: Int, atlasHeight: Int): Boolean {
        if (index < 0) return false
        val rect = rectFor(index)
        return rect.right <= atlasWidth && rect.bottom <= atlasHeight
    }

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
        const val TILE = 32
        const val COLS = 16
    }
}
