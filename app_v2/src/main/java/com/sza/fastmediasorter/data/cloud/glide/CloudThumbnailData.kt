package com.sza.fastmediasorter.data.cloud.glide

import androidx.annotation.Keep
import com.bumptech.glide.load.Key
import com.sza.fastmediasorter.data.cloud.CloudProvider
import java.security.MessageDigest

/**
 * Data class for loading cloud images with authentication.
 * Supports Google Drive, OneDrive, and Dropbox.
 * Implements Key for Glide caching.
 * 
 * @param thumbnailUrl The cloud thumbnail URL (used if loadFullImage is false)
 * @param fileId The cloud file ID (used for cache key stability and full image loading)
 * @param loadFullImage If true, loads full resolution image; if false, loads thumbnail
 * @param cloudProvider The cloud storage provider (GOOGLE_DRIVE, ONEDRIVE, DROPBOX)
 */
@Keep
data class CloudThumbnailData(
    val thumbnailUrl: String,
    val fileId: String,
    val loadFullImage: Boolean = false,
    val cloudProvider: CloudProvider = CloudProvider.GOOGLE_DRIVE
) : Key {
    
    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        // Use provider + fileId + mode for stable cache key
        val mode = if (loadFullImage) "full" else "thumb"
        val providerPrefix = cloudProvider.name.lowercase()
        messageDigest.update("${providerPrefix}_${mode}_$fileId".toByteArray(Key.CHARSET))
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CloudThumbnailData) return false
        return fileId == other.fileId && 
               loadFullImage == other.loadFullImage && 
               cloudProvider == other.cloudProvider
    }
    
    override fun hashCode(): Int {
        var result = fileId.hashCode()
        result = 31 * result + loadFullImage.hashCode()
        result = 31 * result + cloudProvider.hashCode()
        return result
    }
}
