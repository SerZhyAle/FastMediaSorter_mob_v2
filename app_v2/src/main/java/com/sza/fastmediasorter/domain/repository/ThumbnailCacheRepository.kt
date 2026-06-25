package com.sza.fastmediasorter.domain.repository

import java.io.File

/**
 * Repository interface for thumbnail cache operations.
 * Manages local storage of network file thumbnails to avoid repeated extraction.
 */
interface ThumbnailCacheRepository {
    
    /**
     * Get cached thumbnail file for a video.
     * Returns null if no cache exists or file was deleted.
     * Updates access timestamp if cache hit.
     */
    suspend fun getCachedThumbnail(filePath: String): File?
    
    /**
     * Save thumbnail to cache.
     * @param filePath Original video file path (network path)
     * @param thumbnailFile Local thumbnail file to cache
     */
    suspend fun saveThumbnail(filePath: String, thumbnailFile: File)
    
    /**
     * Delete thumbnail from cache.
     */
    suspend fun deleteThumbnail(filePath: String)

    // S0677: synchronous variants for the Glide decode thread (ResourceDecoder.decode runs on a
    // Glide pool thread, never the main thread). They let NetworkVideoFrameDecoder drop runBlocking.
    fun getCachedThumbnailBlocking(filePath: String): File?

    fun saveThumbnailBlocking(filePath: String, thumbnailFile: File)

    fun deleteThumbnailBlocking(filePath: String)

    /**
     * Clean up old thumbnails not accessed for specified days.
     * @param days Number of days of inactivity before deletion
     * @return Number of deleted entries
     */
    suspend fun cleanupOldThumbnails(days: Int = 30): Int
    
    /**
     * Get cache statistics.
     */
    suspend fun getCacheStats(): CacheStats

    /**
     * Evict least-recently-used thumbnails until total size is below [maxBytes].
     * @return Number of deleted entries.
     */
    suspend fun enforceSizeLimit(maxBytes: Long): Int
}

/**
 * Thumbnail cache statistics.
 */
data class CacheStats(
    val entryCount: Int,
    val totalSizeBytes: Long
) {
    val totalSizeMb: Int
        get() = (totalSizeBytes / 1024 / 1024).toInt()
}
