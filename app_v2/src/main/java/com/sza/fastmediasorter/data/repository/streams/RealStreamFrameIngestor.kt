package com.sza.fastmediasorter.data.repository.streams

import android.graphics.Bitmap
import android.graphics.Color
import com.sza.fastmediasorter.domain.streams.StreamFrameIngestor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RealStreamFrameIngestor @Inject constructor(
    private val cache: StreamFrameCache,
    private val persistentStore: StreamFramePersistentStore,
) : StreamFrameIngestor {

    override suspend fun ingest(url: String, bitmap: Bitmap): Boolean {
        if (!isAcceptable(bitmap)) return false
        cache.put(url, bitmap)
        persistentStore.save(url, bitmap)
        return true
    }

    private fun isAcceptable(bitmap: Bitmap): Boolean {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) return false
        var darkSamples = 0
        var totalSamples = 0
        for (row in 0 until SAMPLE_GRID_SIZE) {
            val y = sampleCoordinate(row, bitmap.height)
            for (column in 0 until SAMPLE_GRID_SIZE) {
                val color = bitmap.getPixel(sampleCoordinate(column, bitmap.width), y)
                val brightness = Color.red(color) + Color.green(color) + Color.blue(color)
                if (brightness <= DARK_RGB_SUM_THRESHOLD) darkSamples++
                totalSamples++
            }
        }
        return darkSamples.toFloat() / totalSamples < MAX_DARK_SAMPLE_RATIO
    }

    private fun sampleCoordinate(index: Int, size: Int): Int =
        (((index + SAMPLE_CENTER_OFFSET) * size) / SAMPLE_GRID_SIZE).toInt().coerceAtMost(size - 1)

    private companion object {
        const val SAMPLE_GRID_SIZE = 8
        const val SAMPLE_CENTER_OFFSET = 0.5f
        const val DARK_RGB_SUM_THRESHOLD = 48
        const val MAX_DARK_SAMPLE_RATIO = 0.95f
    }
}
