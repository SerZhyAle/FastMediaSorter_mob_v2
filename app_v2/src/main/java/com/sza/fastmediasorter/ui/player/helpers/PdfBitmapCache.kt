package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.util.LruCache
import timber.log.Timber

/**
 * LruCache-based bitmap cache for rendered PDF pages.
 * Limits memory usage to [maxBitmaps] most recently used pages.
 *
 * Bitmaps are recycled when evicted from cache.
 */
class PdfBitmapCache(private val maxBitmaps: Int = MAX_CACHED_PAGES) {

    companion object {
        const val MAX_CACHED_PAGES = 5
    }

    private val cache = object : LruCache<Int, Bitmap>(maxBitmaps) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: Bitmap, newValue: Bitmap?) {
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
                Timber.d("PdfBitmapCache: Evicted and recycled page $key")
            }
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
        val bitmap = cache.remove(pageIndex)
        if (bitmap != null && !bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    /**
     * Clear all cached bitmaps and recycle them.
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
