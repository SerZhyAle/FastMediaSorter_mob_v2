package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.util.LruCache
import timber.log.Timber

/**
 * LruCache-based bitmap cache for rendered PDF pages.
 * Limits memory usage to [maxBitmaps] most recently used pages.
 *
 * Evicted bitmaps are never recycled: a bitmap evicted here can still be bound to a visible
 * ImageView or held by an async consumer (translation/OCR/share), and drawing a recycled
 * bitmap crashes. GC reclaims the pixel data after the last reference drops.
 */
class PdfBitmapCache(private val maxBitmaps: Int = MAX_CACHED_PAGES) {

    companion object {
        const val MAX_CACHED_PAGES = 5
    }

    private val cache = object : LruCache<Int, Bitmap>(maxBitmaps) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            // No recycle: the bitmap may still be drawn by a bound view or read by a consumer.
            Timber.d("PdfBitmapCache: Released page $key (evicted=$evicted)")
        }

        override fun sizeOf(key: Int, value: Bitmap): Int = 1 // Count-based, not byte-based
    }

    /**
     * Get cached bitmap for page index, or null if not cached.
     */
    fun get(pageIndex: Int): Bitmap? {
        val bitmap = cache.get(pageIndex)
        if (bitmap != null && bitmap.isRecycled) {
            cache.remove(pageIndex)
            return null
        }
        return bitmap
    }

    /**
     * Put rendered bitmap into cache.
     */
    fun put(pageIndex: Int, bitmap: Bitmap) {
        cache.put(pageIndex, bitmap)
    }

    /**
     * Remove specific page from cache (e.g., when re-rendering at different zoom).
     */
    fun remove(pageIndex: Int) {
        cache.remove(pageIndex)
    }

    /**
     * Drop all cached bitmaps without recycling them.
     */
    fun clear() {
        cache.evictAll()
        Timber.d("PdfBitmapCache: Cleared all cached pages")
    }

    /**
     * Get current number of cached pages.
     */
    fun size(): Int = cache.size()
}
