package com.sza.fastmediasorter.utils

import com.bumptech.glide.load.DataSource
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Diagnostic utility for tracking Glide disk cache hits/misses.
 * Helps debug cache persistence issues between app launches.
 * 
 * Usage: Call recordLoad() in Glide RequestListener.onResourceReady()
 * Call logStats() periodically or when leaving browse screen.
 */
object GlideCacheStats {

    private val diskCacheHits = AtomicInteger(0)
    private val memoryCacheHits = AtomicInteger(0)
    private val networkLoads = AtomicInteger(0)
    private val localLoads = AtomicInteger(0)
    private val thumbnailRepoCacheHits = AtomicInteger(0)

    /**
     * Record a successful Glide load and its source.
     * @param dataSource The source from Glide's onResourceReady callback
     */
    fun recordLoad(dataSource: DataSource) {
        when (dataSource) {
            DataSource.RESOURCE_DISK_CACHE,
            DataSource.DATA_DISK_CACHE -> diskCacheHits.incrementAndGet()
            DataSource.MEMORY_CACHE -> memoryCacheHits.incrementAndGet()
            DataSource.REMOTE -> networkLoads.incrementAndGet()
            DataSource.LOCAL -> localLoads.incrementAndGet()
        }
    }

    /**
     * Record a hit served from ThumbnailCacheRepository (filesDir-backed persistent cache).
     * These loads appear as LOCAL in Glide's dataSource and are tracked separately
     * to avoid false "zero disk cache hits" warnings.
     */
    fun recordThumbnailRepoHit() {
        thumbnailRepoCacheHits.incrementAndGet()
    }

    /**
     * Reset all counters (call when entering browse screen).
     */
    fun reset() {
        diskCacheHits.set(0)
        memoryCacheHits.set(0)
        networkLoads.set(0)
        localLoads.set(0)
        thumbnailRepoCacheHits.set(0)
    }
    
    /**
     * Log current statistics. Call when leaving browse screen or periodically.
     */
    fun logStats() {
        val disk = diskCacheHits.get()
        val memory = memoryCacheHits.get()
        val network = networkLoads.get()
        val local = localLoads.get()
        val repo = thumbnailRepoCacheHits.get()
        val total = disk + memory + network + local + repo

        Timber.i("========================================")
        Timber.i("=== GLIDE CACHE STATISTICS ===")
        Timber.i("========================================")

        if (total == 0) {
            Timber.d("⚠️ No loads recorded (no thumbnails loaded or recordLoad() not called)")
            Timber.i("========================================")
            return
        }

        Timber.d("S0136: GlideCacheStats summary total=$total disk=$disk memory=$memory repo=$repo network=$network local=$local")

        val diskPercent = (disk * 100.0 / total)
        val memoryPercent = (memory * 100.0 / total)
        val networkPercent = (network * 100.0 / total)
        val localPercent = (local * 100.0 / total)
        val repoPercent = (repo * 100.0 / total)

        Timber.i("Total loads: $total")
        Timber.i("")
        Timber.i("💾 Disk cache hits:      $disk (%.1f%%)".format(diskPercent))
        Timber.i("🧠 Memory cache hits:    $memory (%.1f%%)".format(memoryPercent))
        Timber.i("📂 Thumbnail repo hits:  $repo (%.1f%%)".format(repoPercent))
        Timber.i("🌐 Network loads:        $network (%.1f%%)".format(networkPercent))
        Timber.i("📁 Local file loads:     $local (%.1f%%)".format(localPercent))
        Timber.i("")

        val cacheHitRate = (disk + memory + repo) * 100.0 / total
        Timber.i("✅ Overall cache hit rate: %.1f%%".format(cacheHitRate))

        if (disk == 0 && repo == 0 && total > 10) {
            Timber.w("")
            Timber.w("⚠️ WARNING: Zero disk cache hits with $total total loads!")
            Timber.w("This suggests:")
            Timber.w("  1. First time browsing (cache empty)")
            Timber.w("  2. Cache keys changed (refreshVersion increment?)")
            Timber.w("  3. Disk cache was cleared")
            Timber.w("  4. DiskCacheStrategy is NONE (check config)")
        }

        Timber.i("========================================")
    }

    /**
     * Get summary string for quick display (e.g., in debug overlay).
     */
    fun getSummary(): String {
        val disk = diskCacheHits.get()
        val memory = memoryCacheHits.get()
        val repo = thumbnailRepoCacheHits.get()
        val other = networkLoads.get() + localLoads.get()
        return "Cache: D=$disk M=$memory R=$repo O=$other"
    }
}
