package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for caching network video thumbnails locally.
 * Stores path to local cached thumbnail file and metadata.
 * 
 * Cache strategy:
 * - Thumbnails stored in app cache directory
 * - Database stores file paths and access timestamps
 * - Cleanup: Remove entries not accessed for 30 days
 */
@Entity(
    tableName = "thumbnail_cache",
    indices = [Index(value = ["lastAccessedAt"])]
)
data class ThumbnailCacheEntity(
    /**
     * Full path to the original video file (network path).
     * Example: "smb://192.168.1.1/share/video.mp4"
     */
    @PrimaryKey
    val filePath: String,
    
    /**
     * Local file path to cached thumbnail image.
     * Example: "/data/user/0/com.sza.fastmediasorter/cache/thumbnails/abc123.jpg"
     */
    val thumbnailPath: String,
    
    /**
     * Size of thumbnail file in bytes (for cache management).
     */
    val fileSize: Long,
    
    /**
     * When thumbnail was first created (milliseconds since epoch).
     */
    val createdAt: Long,
    
    /**
     * Last time this thumbnail was accessed (for LRU cleanup).
     */
    val lastAccessedAt: Long
)
