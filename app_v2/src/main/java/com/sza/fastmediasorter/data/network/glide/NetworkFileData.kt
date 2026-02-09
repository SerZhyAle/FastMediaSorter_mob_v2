package com.sza.fastmediasorter.data.network.glide

import com.bumptech.glide.load.Key
import java.security.MessageDigest

/**
 * Data class for passing network file info to Glide ModelLoader.
 * Used for SMB/SFTP/FTP image loading.
 * 
 * Implements Glide's Key interface for stable disk caching.
 * Only path and size are used for cache key - other fields are transient.
 */
data class NetworkFileData(
    val path: String,                    // smb://, sftp://, or ftp:// URL
    val credentialsId: String? = null,   // Optional credentialsId for specific credentials
    val loadFullImage: Boolean = false,  // true for fullscreen view, false for thumbnails
    val highPriority: Boolean = false,   // true for PlayerActivity, false for background thumbnails
    val size: Long = 0,                  // File size in bytes (for cache key stability)
    val createdDate: Long = 0            // File modification timestamp (for cache key stability)
) : Key {
    
    /**
     * Generate cache key for Glide disk cache.
     * Includes path + size to identify file uniquely.
     * After edit operations (rotation/flip/filter/adjustments), file size changes,
     * automatically invalidating the cache.
     */
    fun getCacheKey(): String = "${path}_${size}"
    
    /**
     * Update MessageDigest for Glide's disk cache key.
     * Only uses path and size - ignores transient fields like loadFullImage, highPriority.
     */
    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(getCacheKey().toByteArray(Key.CHARSET))
    }
    
    /**
     * Override equals to only compare cache-relevant fields.
     * This ensures disk cache hits even when loadFullImage/highPriority differ.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NetworkFileData) return false
        return path == other.path && size == other.size
    }
    
    /**
     * Override hashCode to match equals - only uses cache-relevant fields.
     */
    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + size.hashCode()
        return result
    }
}
