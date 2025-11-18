package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val googleDriveClient: GoogleDriveClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveRestClient: OneDriveRestClient,
    private val resourceRepository: ResourceRepository
) : MediaScanner {

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        onProgress: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            // For cloud resources, path is the cloud folder ID
            val folderId = path
            
            // Find resource by cloudFolderId to determine cloud provider
            val resources = resourceRepository.getAllResourcesSync()
            val resource = resources.find { it.cloudFolderId == folderId }
                ?: return@withContext emptyList()
            
            val client = getClient(resource.cloudProvider) ?: return@withContext emptyList()
            
            // Ensure authenticated
            ensureAuthenticated(client, resource.cloudProvider)
            
            // Scan folder
            when (val result = client.listFiles(folderId)) {
                is CloudResult.Success -> {
                    val (cloudFiles, _) = result.data
                    cloudFiles.mapNotNull { cloudFile ->
                        if (!cloudFile.isFolder) {
                            val mediaType = getMediaType(cloudFile.mimeType, cloudFile.name)
                            if (mediaType != null && supportedTypes.contains(mediaType)) {
                                // Apply size filter
                                if (sizeFilter != null && !isFileSizeInRange(cloudFile.size, mediaType, sizeFilter)) {
                                    return@mapNotNull null
                                }
                                
                                MediaFile(
                                    name = cloudFile.name,
                                    path = cloudFile.id,
                                    type = mediaType,
                                    size = cloudFile.size,
                                    createdDate = cloudFile.modifiedDate,
                                    thumbnailUrl = cloudFile.thumbnailUrl,
                                    webViewUrl = cloudFile.webViewUrl
                                )
                            } else null
                        } else null
                    }
                }
                is CloudResult.Error -> {
                    Timber.e("Failed to scan cloud folder: ${result.message}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning cloud folder")
            emptyList()
        }
    }

    override suspend fun scanFolderPaged(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        offset: Int,
        limit: Int,
        credentialsId: String?
    ): MediaFilePage = withContext(Dispatchers.IO) {
        try {
            val resourceId = path.toLongOrNull() ?: return@withContext MediaFilePage(emptyList(), false)
            val resource = resourceRepository.getResourceById(resourceId) ?: return@withContext MediaFilePage(emptyList(), false)
            
            val client = getClient(resource.cloudProvider) ?: return@withContext MediaFilePage(emptyList(), false)
            
            ensureAuthenticated(client, resource.cloudProvider)
            
            // Get all files first (cloud APIs don't support offset-based pagination natively)
            val allFiles = scanFolder(path, supportedTypes, sizeFilter, credentialsId)
            
            // Apply pagination
            val start = offset.coerceAtMost(allFiles.size)
            val end = (offset + limit).coerceAtMost(allFiles.size)
            val pageFiles = if (start < end) allFiles.subList(start, end) else emptyList()
            val hasMore = end < allFiles.size
            
            MediaFilePage(pageFiles, hasMore)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning cloud folder paged")
            MediaFilePage(emptyList(), false)
        }
    }

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?
    ): Int {
        // Fast count: use paged scan with limit 1000
        val page = scanFolderPaged(path, supportedTypes, sizeFilter, offset = 0, limit = 1000, credentialsId)
        // If we got exactly 1000 files, there are likely more (return 1000 to show ">1000")
        // If we got less, that's the actual count
        return page.files.size
    }

    override suspend fun isWritable(path: String, credentialsId: String?): Boolean {
        // Cloud storage is always writable if authenticated
        return true
    }

    private fun getClient(provider: CloudProvider?): CloudStorageClient? {
        return when (provider) {
            CloudProvider.GOOGLE_DRIVE -> googleDriveClient
            CloudProvider.DROPBOX -> dropboxClient
            CloudProvider.ONEDRIVE -> oneDriveRestClient
            null -> null
        }
    }

    private suspend fun ensureAuthenticated(@Suppress("UNUSED_PARAMETER") client: CloudStorageClient, provider: CloudProvider?) {
        // Check if already authenticated
        val isAuthenticated = when (provider) {
            CloudProvider.GOOGLE_DRIVE -> {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                account != null
            }
            else -> false
        }
        
        if (!isAuthenticated) {
            throw IllegalStateException("Not authenticated with $provider")
        }
    }

    private fun getMediaType(mimeType: String?, fileName: String): MediaType? {
        // Try MIME type first
        mimeType?.let {
            return when {
                it.startsWith("image/gif") -> MediaType.GIF
                it.startsWith("image/") -> MediaType.IMAGE
                it.startsWith("video/") -> MediaType.VIDEO
                it.startsWith("audio/") -> MediaType.AUDIO
                else -> null
            }
        }
        
        // Fallback to extension
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "gif" -> MediaType.GIF
            "jpg", "jpeg", "png", "webp", "heic", "heif", "bmp" -> MediaType.IMAGE
            "mp4", "mkv", "mov", "webm", "3gp", "flv", "wmv", "m4v" -> MediaType.VIDEO
            "mp3", "m4a", "wav", "flac", "aac", "ogg", "wma", "opus" -> MediaType.AUDIO
            else -> null
        }
    }

    private fun isFileSizeInRange(size: Long, mediaType: MediaType, sizeFilter: SizeFilter): Boolean {
        return when (mediaType) {
            MediaType.IMAGE, MediaType.GIF -> size in sizeFilter.imageSizeMin..sizeFilter.imageSizeMax
            MediaType.VIDEO -> size in sizeFilter.videoSizeMin..sizeFilter.videoSizeMax
            MediaType.AUDIO -> size in sizeFilter.audioSizeMin..sizeFilter.audioSizeMax
        }
    }
}
