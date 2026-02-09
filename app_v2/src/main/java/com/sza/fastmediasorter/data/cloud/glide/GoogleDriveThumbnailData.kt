package com.sza.fastmediasorter.data.cloud.glide

import androidx.annotation.Keep
import com.bumptech.glide.load.Key
import java.security.MessageDigest

/**
 * Data class for loading Google Drive images with authentication.
 * Implements Key for Glide caching.
 * 
 * @param thumbnailUrl The Google Drive thumbnail URL (used if loadFullImage is false)
 * @param fileId The Google Drive file ID (used for cache key stability and full image loading)
 * @param loadFullImage If true, loads full resolution image; if false, loads thumbnail
 */
@Keep
data class GoogleDriveThumbnailData(
    val thumbnailUrl: String,
    val fileId: String,
    val loadFullImage: Boolean = false
) : Key {
    
    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        // Use fileId + mode for stable cache key
        val mode = if (loadFullImage) "full" else "thumb"
        messageDigest.update("gdrive_${mode}_$fileId".toByteArray(Key.CHARSET))
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GoogleDriveThumbnailData) return false
        return fileId == other.fileId && loadFullImage == other.loadFullImage
    }
    
    override fun hashCode(): Int {
        var result = fileId.hashCode()
        result = 31 * result + loadFullImage.hashCode()
        return result
    }
}
