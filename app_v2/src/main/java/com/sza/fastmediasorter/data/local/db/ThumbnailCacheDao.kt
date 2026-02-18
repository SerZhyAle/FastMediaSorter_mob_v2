package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for thumbnail cache operations.
 * Provides methods for storing, retrieving, and cleaning up cached thumbnails.
 */
@Dao
interface ThumbnailCacheDao {
    
    /**
     * Get cached thumbnail path for a video file.
     * Updates lastAccessedAt timestamp automatically.
     */
    @Query("""
        UPDATE thumbnail_cache 
        SET lastAccessedAt = :currentTime 
        WHERE filePath = :filePath
    """)
    suspend fun updateAccessTime(filePath: String, currentTime: Long)
    
    @Query("SELECT * FROM thumbnail_cache WHERE filePath = :filePath")
    suspend fun getThumbnail(filePath: String): ThumbnailCacheEntity?
    
    /**
     * Save thumbnail cache entry.
     * Replaces existing entry if file path already exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThumbnail(thumbnail: ThumbnailCacheEntity)
    
    /**
     * Delete thumbnail cache entry.
     */
    @Query("DELETE FROM thumbnail_cache WHERE filePath = :filePath")
    suspend fun deleteThumbnail(filePath: String)
    
    /**
     * Get all thumbnail paths older than specified timestamp (for cleanup).
     */
    @Query("SELECT * FROM thumbnail_cache WHERE lastAccessedAt < :timestamp")
    suspend fun getThumbnailsOlderThan(timestamp: Long): List<ThumbnailCacheEntity>
    
    /**
     * Delete thumbnails not accessed for specified number of days.
     * Returns number of deleted entries.
     */
    @Query("DELETE FROM thumbnail_cache WHERE lastAccessedAt < :timestamp")
    suspend fun deleteOldThumbnails(timestamp: Long): Int
    
    /**
     * Get total cache size in bytes.
     */
    @Query("SELECT COALESCE(SUM(fileSize), 0) FROM thumbnail_cache")
    suspend fun getTotalCacheSize(): Long
    
    /**
     * Get cache statistics.
     */
    @Query("SELECT COUNT(*) FROM thumbnail_cache")
    suspend fun getCacheCount(): Int
    
    /**
     * Get all thumbnails (for migration).
     */
    @Query("SELECT * FROM thumbnail_cache")
    suspend fun getAllThumbnails(): List<ThumbnailCacheEntity>
    
    /**
     * Update thumbnail path (for migration from cacheDir to filesDir).
     */
    @Query("UPDATE thumbnail_cache SET thumbnailPath = :newPath WHERE filePath = :filePath")
    suspend fun updateThumbnailPath(filePath: String, newPath: String)

    /**
     * Returns all entries ordered by lastAccessedAt ASC (LRU first) for size-based eviction.
     */
    @Query("SELECT * FROM thumbnail_cache ORDER BY lastAccessedAt ASC")
    suspend fun getAllByLruOrder(): List<ThumbnailCacheEntity>

    /** Bulk delete by file path list. */
    @Query("DELETE FROM thumbnail_cache WHERE filePath IN (:filePaths)")
    suspend fun deleteByPaths(filePaths: List<String>): Int
}