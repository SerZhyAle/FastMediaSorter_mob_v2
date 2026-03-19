package com.sza.fastmediasorter.data.cloud

import android.content.Context
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.ScanProgressCallback
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

import com.sza.fastmediasorter.data.common.MediaTypeUtils

@Singleton
class CloudMediaScanner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val googleDriveClient: GoogleDriveRestClient,
    private val dropboxClient: DropboxClient,
    private val oneDriveRestClient: OneDriveRestClient,
    private val resourceRepository: ResourceRepository
) : MediaScanner {

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> {
        return scanFolderInternal(
            path, supportedTypes, sizeFilter, credentialsId, 
            scanSubdirectories, showHiddenFiles, onProgress, 
            includeDirectories = false
        )
    }

    private suspend fun scanFolderInternal(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        @Suppress("UNUSED_PARAMETER") credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        @Suppress("UNUSED_PARAMETER") onProgress: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?,
        includeDirectories: Boolean
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            // For cloud resources, path can be either:
            // 1. Full path like "cloud://google_drive/FOLDER_ID"
            // 2. Just the FOLDER_ID (for backward compatibility)
            val isFullCloudPath = path.startsWith("cloud://")
            val folderId = if (isFullCloudPath) {
                // Extract folder ID from full path: cloud://provider/FOLDER_ID
                path.substringAfterLast("/")
            } else {
                path
            }
            
            // Find resource - use path matching for full cloud paths, or cloudFolderId for legacy
            val resources = resourceRepository.getAllResourcesSync()
            val resource = if (isFullCloudPath) {
                // Match by path first (handles Dropbox /path vs path mismatch)
                resources.find { it.path == path }
                    ?: resources.find { it.cloudFolderId == folderId }
                    ?: resources.find { it.cloudFolderId == "/$folderId" } // Dropbox uses leading slash
            } else {
                resources.find { it.cloudFolderId == folderId }
            }
            
            if (resource == null) {
                Timber.w("CloudMediaScanner: No resource found for path=$path, folderId=$folderId")
                return@withContext emptyList()
            }
            
            Timber.d("CloudMediaScanner: Found resource id=${resource.id}, name=${resource.name}, cloudFolderId=${resource.cloudFolderId}")
            
            val client = getClient(resource.cloudProvider) ?: return@withContext emptyList()
            
            // Ensure authenticated with the correct account (credentialsId = account email for cloud resources)
            ensureAuthenticated(client, resource.cloudProvider, resource.credentialsId)
            
            // Use cloudFolderId from resource for API calls (important for Dropbox which uses /path format)
            val actualFolderId = resource.cloudFolderId ?: folderId
            Timber.d("CloudMediaScanner: Scanning folder actualFolderId=$actualFolderId")
            
            // Scan folder (throttled to avoid network overload)
            val resourceKey = "cloud://${resource.cloudProvider}/${actualFolderId}"
            
            val allCloudFiles = if (scanSubdirectories) {
                // Recursive scan: collect files from all subfolders
                listFilesRecursive(client, actualFolderId, resourceKey)
            } else {
                // Single level scan
                when (val result = ConnectionThrottleManager.withThrottle(
                    protocol = ConnectionThrottleManager.ProtocolLimits.CLOUD,
                    resourceKey = resourceKey,
                    highPriority = false,
                    operation = {
                        client.listFiles(actualFolderId)
                    }
                )) {
                    is CloudResult.Success -> result.data.first
                    is CloudResult.Error -> {
                        Timber.e("CloudMediaScanner: listFiles failed - ${result.message}")
                        emptyList()
                    }
                }
            }
            
            // Filter and convert to MediaFile
            // When all 7 media types are supported (allFiles mode), treat unknown files as TEXT
            val isAllFilesMode = supportedTypes.size == 7
            val provider = resource.cloudProvider?.name?.lowercase() ?: "unknown"
            
            allCloudFiles.mapNotNull { cloudFile ->
                if (cloudFile.isFolder) {
                    if (includeDirectories) {
                         // Add subdirectory to results
                        MediaFile(
                            name = cloudFile.name,
                            path = "cloud://$provider/${cloudFile.id}",
                            type = MediaType.IMAGE, // Placeholder type for folders
                            size = 0L,
                            createdDate = cloudFile.modifiedDate,
                            isDirectory = true,
                            childCount = 0, // Expensive to calculate for Cloud, setting to 0
                            cloudDisplayPath = cloudFile.path,
                            cloudItemId = cloudFile.id
                        )
                    } else {
                        null
                    }
                } else {
                    // Skip hidden files if not requested (files starting with ".")
                    if (!showHiddenFiles && cloudFile.name.startsWith(".")) {
                        return@mapNotNull null
                    }
                    
                    // Try MIME type first, then fallback to extension, then TEXT if allFiles mode
                    val mediaType = MediaTypeUtils.getMediaTypeFromMime(cloudFile.mimeType) 
                        ?: MediaTypeUtils.getMediaType(cloudFile.name)
                        ?: if (isAllFilesMode) MediaType.TEXT else null
                    
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        // Apply size filter
                        if (sizeFilter != null && !MediaTypeUtils.isFileSizeInRange(cloudFile.size, mediaType, sizeFilter)) {
                            return@mapNotNull null
                        }
                        
                        MediaFile(
                            name = cloudFile.name,
                            path = "cloud://$provider/${cloudFile.id}",
                            type = mediaType,
                            size = cloudFile.size,
                            createdDate = cloudFile.modifiedDate,
                            thumbnailUrl = cloudFile.thumbnailUrl,
                            webViewUrl = cloudFile.webViewUrl,
                            cloudDisplayPath = cloudFile.path,
                            cloudItemId = cloudFile.id
                        )
                    } else null
                }
            }.sortedWith(
                // Sort: folders first, then by name
                compareBy<MediaFile> { !it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        } catch (e: IllegalStateException) {
            // Re-throw authentication errors to be handled by ViewModel
            if (e.message?.contains("Interactive sign-in required", ignoreCase = true) == true ||
                e.message?.contains("Not authenticated", ignoreCase = true) == true ||
                e.message?.contains("Authentication cancelled", ignoreCase = true) == true) {
                throw e
            } else {
                Timber.e(e, "Error scanning cloud folder")
                emptyList()
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
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): MediaFilePage = withContext(Dispatchers.IO) {
        try {
            val resourceId = path.toLongOrNull() ?: return@withContext MediaFilePage(emptyList(), false)
            val resource = resourceRepository.getResourceById(resourceId) ?: return@withContext MediaFilePage(emptyList(), false)
            
            val client = getClient(resource.cloudProvider) ?: return@withContext MediaFilePage(emptyList(), false)
            
            ensureAuthenticated(client, resource.cloudProvider, resource.credentialsId)
            
            // Get all files first (cloud APIs don't support offset-based pagination natively)
            // Use scanFolderInternal
            val allFiles = scanFolderInternal(
                path, supportedTypes, sizeFilter, credentialsId, 
                scanSubdirectories, showHiddenFiles, null, includeDirectories = false
            )
            
            // Apply pagination
            val start = offset.coerceAtMost(allFiles.size)
            val end = (offset + limit).coerceAtMost(allFiles.size)
            val pageFiles = if (start < end) allFiles.subList(start, end) else emptyList()
            val hasMore = end < allFiles.size
            
            MediaFilePage(pageFiles, hasMore)
        } catch (e: IllegalStateException) {
            // Re-throw authentication errors to be handled by ViewModel
            if (e.message?.contains("Interactive sign-in required", ignoreCase = true) == true ||
                e.message?.contains("Not authenticated", ignoreCase = true) == true ||
                e.message?.contains("Authentication cancelled", ignoreCase = true) == true) {
                throw e
            } else {
                Timber.e(e, "Error scanning cloud folder paged")
                MediaFilePage(emptyList(), false)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning cloud folder paged")
            MediaFilePage(emptyList(), false)
        }
    }

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): Int {
        // Fast count: use paged scan with limit 1000
        val page = scanFolderPaged(path, supportedTypes, sizeFilter, offset = 0, limit = 1000, credentialsId, scanSubdirectories, showHiddenFiles)
        // If we got exactly 1000 files, there are likely more (return 1000 to show ">1000")
        // If we got less, that's the actual count
        return page.files.size
    }

    override suspend fun listDirectoryContents(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        showHiddenFiles: Boolean
    ): List<MediaFile> {
        // Cloud storage: use scanFolderInternal with includeDirectories=true
        return scanFolderInternal(
            path = path,
            supportedTypes = supportedTypes,
            sizeFilter = sizeFilter,
            credentialsId = credentialsId,
            scanSubdirectories = false, // Only immediate children
            showHiddenFiles = showHiddenFiles,
            onProgress = null,
            includeDirectories = true
        )
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

    private suspend fun ensureAuthenticated(
        client: CloudStorageClient,
        provider: CloudProvider?,
        credentialsId: String? = null
    ) {
        when (provider) {
            CloudProvider.GOOGLE_DRIVE -> {
                // For Google Drive: restore for the specific account linked to this resource
                if (credentialsId != null) {
                    if (googleDriveClient.tryRestoreForAccount(credentialsId)) {
                        Timber.d("CloudMediaScanner: Google Drive restored for account $credentialsId")
                        return
                    }
                    Timber.w("CloudMediaScanner: Could not restore Google Drive account $credentialsId, falling back")
                }
                // Try to authenticate (will use cached account or fail)
                when (val result = client.authenticate()) {
                    is AuthResult.Success -> {
                        Timber.d("Cloud client authenticated: ${result.accountName}")
                    }
                    is AuthResult.Error -> {
                        Timber.e("Cloud authentication failed: ${result.message}")
                        // User-friendly message for re-authentication requirement
                        val message = if (result.message.contains("Interactive sign-in required")) {
                            "Authorization required. Please delete and re-add this cloud resource to update permissions."
                        } else {
                            "Not authenticated with $provider: ${result.message}"
                        }
                        throw IllegalStateException(message)
                    }
                    AuthResult.Cancelled -> {
                        Timber.w("Cloud authentication cancelled")
                        throw IllegalStateException("Authentication cancelled for $provider")
                    }
                }
            }
            CloudProvider.DROPBOX, CloudProvider.ONEDRIVE -> {
                // For Dropbox: try to restore the specific account linked to this resource
                if (credentialsId != null && provider == CloudProvider.DROPBOX) {
                    if (dropboxClient.tryRestoreForAccount(credentialsId)) {
                        Timber.d("CloudMediaScanner: Dropbox restored for account $credentialsId")
                        return
                    }
                    Timber.w("CloudMediaScanner: Could not restore Dropbox account $credentialsId, falling back")
                }
                when (val result = client.authenticate()) {
                    is AuthResult.Success -> {
                        Timber.d("Cloud client authenticated: ${result.accountName}")
                    }
                    is AuthResult.Error -> {
                        // Propagate with "Not authenticated" prefix so catch block re-throws to ViewModel
                        throw IllegalStateException("Not authenticated with $provider. ${result.message}")
                    }
                    AuthResult.Cancelled -> {
                        throw IllegalStateException("Authentication cancelled for $provider")
                    }
                }
            }
            null -> throw IllegalStateException("Cloud provider not specified")
        }
    }
    
    /**
     * Recursively list all files in folder and all subfolders
     */
    private suspend fun listFilesRecursive(
        client: CloudStorageClient,
        folderId: String,
        resourceKey: String
    ): List<CloudFile> {
        val allFiles = mutableListOf<CloudFile>()
        
        // Get items in current folder
        when (val result = ConnectionThrottleManager.withThrottle(
            protocol = ConnectionThrottleManager.ProtocolLimits.CLOUD,
            resourceKey = resourceKey,
            highPriority = false,
            operation = {
                client.listFiles(folderId)
            }
        )) {
            is CloudResult.Success -> {
                val (cloudFiles, _) = result.data
                
                cloudFiles.forEach { cloudFile ->
                    if (cloudFile.isFolder) {
                        // Recursively scan subfolder
                        val subfolderFiles = listFilesRecursive(client, cloudFile.id, resourceKey)
                        allFiles.addAll(subfolderFiles)
                    } else {
                        // Add file to results
                        allFiles.add(cloudFile)
                    }
                }
            }
            is CloudResult.Error -> {
                Timber.e("Failed to list files in folder $folderId: ${result.message}")
            }
        }
        
        return allFiles
    }
}
