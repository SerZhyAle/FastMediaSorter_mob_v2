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
