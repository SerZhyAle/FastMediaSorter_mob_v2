package com.sza.fastmediasorter.data.scanner

import com.sza.fastmediasorter.data.cloud.CloudFile
import com.sza.fastmediasorter.data.cloud.CloudProvider
import com.sza.fastmediasorter.data.cloud.CloudResult
import com.sza.fastmediasorter.data.cloud.CloudStorageClient
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scanner for cloud storage providers
 * 
 * Supports Google Drive, OneDrive, Dropbox through CloudStorageClient interface
 * Converts CloudFile to MediaFile for app usage
 */
@Singleton
class CloudMediaScanner @Inject constructor(
    private val googleDriveClient: GoogleDriveRestClient
) {
    
    companion object {
        // MIME type patterns for media detection
        private val IMAGE_PATTERNS = listOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp")
        private val VIDEO_PATTERNS = listOf("video/mp4", "video/mpeg", "video/quicktime", "video/webm", "video/x-matroska", "video/avi")
        private val AUDIO_PATTERNS = listOf("audio/mpeg", "audio/mp3", "audio/wav", "audio/ogg", "audio/flac", "audio/aac")
        private val GIF_PATTERN = "image/gif"
    }
    
    /**
     * Get appropriate client for cloud provider
     */
    private fun getClient(provider: CloudProvider): CloudStorageClient {
        return when (provider) {
            CloudProvider.GOOGLE_DRIVE -> googleDriveClient
            CloudProvider.ONEDRIVE -> throw NotImplementedError("OneDrive not yet implemented")
            CloudProvider.DROPBOX -> throw NotImplementedError("Dropbox not yet implemented")
        }
    }
    
    /**
     * Scan cloud folder for media files
     * 
     * @param provider Cloud provider type
     * @param folderId Folder ID in cloud storage (null for root)
     * @param progressCallback Progress callback (current count)
     * @return List of MediaFile objects
     */
    suspend fun scanFolder(
        provider: CloudProvider,
        folderId: String? = null,
        progressCallback: ((Int) -> Unit)? = null
    ): Result<List<MediaFile>> {
        return try {
            val client = getClient(provider)
            val mediaFiles = mutableListOf<MediaFile>()
            var pageToken: String? = null
            var processedCount = 0
            
            do {
                when (val result = client.listFiles(folderId, pageToken)) {
                    is CloudResult.Success -> {
                        val (files, nextToken) = result.data
                        
                        // Filter and convert media files
                        val converted = files
                            .filter { !it.isFolder && isMediaFile(it) }
                            .mapNotNull { cloudFileToMediaFile(it) }
                        
                        mediaFiles.addAll(converted)
                        processedCount += files.size
                        progressCallback?.invoke(processedCount)
                        
                        pageToken = nextToken
                    }
                    is CloudResult.Error -> {
                        Timber.e("Failed to list cloud files: ${result.message}")
                        return Result.failure(Exception(result.message, result.cause))
                    }
                }
            } while (pageToken != null)
            
            Timber.d("Scanned ${mediaFiles.size} media files from cloud folder")
            Result.success(mediaFiles)
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan cloud folder")
            Result.failure(e)
        }
    }
    
    /**
     * Scan folder with pagination support
     * 
     * @param provider Cloud provider type
     * @param folderId Folder ID
     * @param offset Skip first N files
     * @param limit Return max N files
     * @return List of MediaFile objects
     */
    suspend fun scanFolderPaged(
        provider: CloudProvider,
        folderId: String? = null,
        offset: Int,
        limit: Int
    ): Result<List<MediaFile>> {
        return try {
            val client = getClient(provider)
            val allFiles = mutableListOf<MediaFile>()
            var pageToken: String? = null
            
            // Fetch all files (cloud APIs handle pagination internally)
            do {
                when (val result = client.listFiles(folderId, pageToken)) {
                    is CloudResult.Success -> {
                        val (files, nextToken) = result.data
                        
                        val converted = files
                            .filter { !it.isFolder && isMediaFile(it) }
                            .mapNotNull { cloudFileToMediaFile(it) }
                        
                        allFiles.addAll(converted)
                        pageToken = nextToken
                    }
                    is CloudResult.Error -> {
                        return Result.failure(Exception(result.message, result.cause))
                    }
                }
            } while (pageToken != null)
            
            // Apply offset and limit
            val paged = allFiles.drop(offset).take(limit)
            Result.success(paged)
        } catch (e: Exception) {
            Timber.e(e, "Failed to scan cloud folder (paged)")
            Result.failure(e)
        }
    }
    
    /**
     * Check if CloudFile is a media file
     */
    private fun isMediaFile(file: CloudFile): Boolean {
        val mimeType = file.mimeType ?: return false
        return IMAGE_PATTERNS.any { mimeType.startsWith(it) } ||
               VIDEO_PATTERNS.any { mimeType.startsWith(it) } ||
               AUDIO_PATTERNS.any { mimeType.startsWith(it) }
    }
    
    /**
     * Convert CloudFile to MediaFile
     */
    private fun cloudFileToMediaFile(cloudFile: CloudFile): MediaFile? {
        val mediaType = detectMediaType(cloudFile.mimeType) ?: return null
        
        return MediaFile(
            name = cloudFile.name,
            path = cloudFile.id, // Use cloud file ID as path for cloud files
            type = mediaType,
            size = cloudFile.size,
            createdDate = cloudFile.modifiedDate,
            duration = null, // Cloud API doesn't provide duration directly
            width = null,
            height = null
        )
    }
    
    /**
     * Detect MediaType from MIME type
     */
    private fun detectMediaType(mimeType: String?): MediaType? {
        if (mimeType == null) return null
        
        return when {
            mimeType == GIF_PATTERN -> MediaType.GIF
            IMAGE_PATTERNS.any { mimeType.startsWith(it) } -> MediaType.IMAGE
            VIDEO_PATTERNS.any { mimeType.startsWith(it) } -> MediaType.VIDEO
            AUDIO_PATTERNS.any { mimeType.startsWith(it) } -> MediaType.AUDIO
            else -> null
        }
    }
    
    /**
     * Test cloud connection
     */
    suspend fun testConnection(provider: CloudProvider): Result<Boolean> {
        return try {
            val client = getClient(provider)
            when (val result = client.testConnection()) {
                is CloudResult.Success -> Result.success(result.data)
                is CloudResult.Error -> Result.failure(Exception(result.message, result.cause))
            }
        } catch (e: Exception) {
            Timber.e(e, "Cloud connection test failed")
            Result.failure(e)
        }
    }
}
