package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import com.sza.fastmediasorter.core.util.errorUnlessCancellation
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Thread-safe wrapper around PdfRenderer using Kotlin Mutex.
 * Ensures only one page is open/rendered at a time, preventing concurrent access crashes.
 *
 * PdfRenderer is NOT thread-safe - only one Page can be open at any moment.
 */
class PdfRendererWrapper(
    private val renderer: PdfRenderer
) {
    private val mutex = Mutex()
    @Volatile
    var isClosed = false
        private set
    val pageCount: Int = renderer.pageCount // Cached at creation to avoid access after close

    /**
     * Render a specific page to a Bitmap.
     * Thread-safe: blocks concurrent access via Mutex.
     *
     * @param pageIndex Page number (0-based)
     * @param width Target bitmap width
     * @param maxDimension Maximum dimension cap
     * @return Rendered Bitmap, or null on error
     */
    suspend fun renderPage(pageIndex: Int, width: Int, maxDimension: Int = MAX_DIMENSION): Bitmap? {
        if (pageIndex < 0 || pageIndex >= pageCount || isClosed) return null

        return mutex.withLock {
            if (isClosed) return@withLock null
            try {
                val page = renderer.openPage(pageIndex)
                try {
                    val aspectRatio = page.height.toFloat() / page.width.toFloat()
                    var bmpWidth = width.coerceAtMost(maxDimension)
                    var bmpHeight = (bmpWidth * aspectRatio).toInt().coerceAtMost(maxDimension)

                    // If height exceeds max, scale down width proportionally
                    if (bmpHeight >= maxDimension) {
                        bmpHeight = maxDimension
                        bmpWidth = (bmpHeight / aspectRatio).toInt()
                    }

                    val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                } finally {
                    page.close()
                }
            } catch (e: Exception) {
                e.errorUnlessCancellation("PdfRendererWrapper: Error rendering page $pageIndex")
                null
            }
        }
    }

    /**
     * Get page dimensions (width x height in points) without rendering.
     */
    suspend fun getPageDimensions(pageIndex: Int): Pair<Int, Int>? {
        if (pageIndex < 0 || pageIndex >= pageCount || isClosed) return null
        return mutex.withLock {
            if (isClosed) return@withLock null
            try {
                val page = renderer.openPage(pageIndex)
                try {
                    page.width to page.height
                } finally {
                    page.close()
                }
            } catch (e: Exception) {
                e.errorUnlessCancellation("PdfRendererWrapper: Error getting dimensions for page $pageIndex")
                null
            }
        }
    }

    /**
     * Close the PdfRenderer. Must be called from main thread context.
     */
    fun close() {
        isClosed = true
        try {
            renderer.close()
            Timber.d("PdfRendererWrapper: Closed")
        } catch (e: Exception) {
            Timber.e(e, "PdfRendererWrapper: Error closing renderer")
        }
    }

    companion object {
        const val MAX_DIMENSION = 2560
    }
}
