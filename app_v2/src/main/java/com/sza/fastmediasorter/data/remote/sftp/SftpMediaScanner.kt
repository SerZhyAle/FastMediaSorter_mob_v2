package com.sza.fastmediasorter.data.remote.sftp

import android.content.Context
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import dagger.hilt.android.qualifiers.ApplicationContext
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.utils.SftpPathUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaScanner implementation for SFTP network resources.
 * Scans remote SFTP servers for media files using SftpClient.
 */
@Singleton
class SftpMediaScanner @Inject constructor(
    private val sftpClient: SftpClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    @ApplicationContext private val context: Context
) : MediaScanner {

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            throw LocalNetworkPermissionDeniedException()
        }
        try {
            // Parse path format: sftp://server:port/remotePath
            val connectionInfo = parseSftpPath(path, credentialsId) ?: run {
                Timber.w("Invalid SFTP path format: $path")
                return@withContext emptyList()
            }

            val clientInfo = SftpClient.SftpConnectionInfo(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password,
                privateKey = connectionInfo.privateKey,
                passphrase = connectionInfo.password.ifEmpty { null }
            )

            // List files in remote path (throttled to avoid network overload)
            val resourceKey = "sftp://${connectionInfo.host}:${connectionInfo.port}"
            val filesResult = ConnectionThrottleManager.withThrottle(
                protocol = ConnectionThrottleManager.ProtocolLimits.SFTP,
                resourceKey = resourceKey,
                highPriority = false
            ) {
                sftpClient.listFiles(clientInfo, connectionInfo.remotePath, recursive = scanSubdirectories)
            }
            
            if (filesResult.isFailure) {
                val e = filesResult.exceptionOrNull() ?: IOException("Unknown SFTP error")
                Timber.e("Failed to list SFTP files: ${e.message}")
                throw IOException("SFTP error: ${e.message}", e)
            }

            // Filter and convert to MediaFile
            // When all 7 media types are supported (allFiles mode), treat unknown files as TEXT
            val isAllFilesMode = supportedTypes.size >= 7
            var processedCount = 0
            val mediaFiles = mutableListOf<MediaFile>()
            for (listing in filesResult.getOrNull() ?: emptyList()) {
                // Extract just the filename from the full path
                val fileName = listing.path.substringAfterLast('/')
                
                if (TrashFolderContract.matchesTrashSegment(fileName)) {
                    continue
                }
                
                // Skip hidden files if not requested
                if (!showHiddenFiles && fileName.startsWith(".")) {
                    continue
                }
                
                // Skip directories (already excluded by listFiles in recursive mode, guard for safety)
                if (listing.isDirectory) {
                    continue
                }
                
                val mediaType = getMediaType(fileName) ?: if (isAllFilesMode) MediaType.TEXT else null
                if (mediaType != null && supportedTypes.contains(mediaType)) {
                    val fullPath = "sftp://${connectionInfo.host}:${connectionInfo.port}${listing.path}"

                    // Use attrs from listFiles() - no extra stat() round-trip needed.
                    // Fallback to stat() only if size is zero and a minimum size filter is active
                    // (rare: non-standard SFTP servers may omit attrs in ls response).
                    val fileSize: Long
                    val fileDate: Long
                    val needsSizeForFilter = sizeFilter != null && when (mediaType) {
                        MediaType.IMAGE -> sizeFilter.imageSizeMin > 0
                        MediaType.VIDEO -> sizeFilter.videoSizeMin > 0
                        MediaType.AUDIO -> sizeFilter.audioSizeMin > 0
                        MediaType.GIF -> sizeFilter.imageSizeMin > 0
                        else -> false
                    }
                    if (listing.size <= 0L && needsSizeForFilter) {
                        val attrsResult = sftpClient.stat(clientInfo, listing.path)
                        if (attrsResult.isFailure || attrsResult.getOrNull() == null) {
                            Timber.w("Fallback stat() failed for ${listing.path}, skipping")
                            continue
                        }
                        val attrs = attrsResult.getOrNull()!!
                        fileSize = attrs.size
                        fileDate = attrs.modifiedDate
                    } else {
                        fileSize = listing.size
                        fileDate = listing.modifiedDate
                    }
                    
                    // Apply size filter if provided
                    if (sizeFilter != null) {
                        val passesFilter = when (mediaType) {
                            MediaType.IMAGE -> fileSize >= sizeFilter.imageSizeMin && fileSize <= sizeFilter.imageSizeMax
                            MediaType.VIDEO -> fileSize >= sizeFilter.videoSizeMin && fileSize <= sizeFilter.videoSizeMax
                            MediaType.AUDIO -> fileSize >= sizeFilter.audioSizeMin && fileSize <= sizeFilter.audioSizeMax
                            MediaType.GIF -> fileSize >= sizeFilter.imageSizeMin && fileSize <= sizeFilter.imageSizeMax
                            MediaType.TEXT -> true
                            MediaType.PDF -> true
                            MediaType.EPUB -> true
                            MediaType.OFFICE_DOCUMENT -> true
                            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> true
                        }
                        if (!passesFilter) {
                            continue
                        }
                    }
                    
                    mediaFiles.add(
                        MediaFile(
                            name = fileName,
                            path = fullPath,
                            size = fileSize,
                            createdDate = fileDate, // Use mtime as creation date
                            type = mediaType
                        )
                    )
                    
                    processedCount++
                    // Report progress every 10 files to keep UI responsive without excess overhead
                    if (processedCount % 10 == 0) {
                        onProgress?.onProgress(processedCount)
                    }
                }
            }
            
            mediaFiles
        } catch (e: CancellationException) {
            Timber.d("SFTP scan cancelled for path: $path")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SFTP folder: $path")
            throw e
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
        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            throw LocalNetworkPermissionDeniedException()
        }
        try {
            // Parse path format: sftp://server:port/remotePath
            val connectionInfo = parseSftpPath(path, credentialsId) ?: run {
                Timber.w("Invalid SFTP path format: $path")
                return@withContext MediaFilePage(emptyList(), false)
            }

            val clientInfo = SftpClient.SftpConnectionInfo(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password,
                privateKey = connectionInfo.privateKey,
                passphrase = connectionInfo.password.ifEmpty { null }
            )

            // List files in remote path (throttled to avoid network overload)
            val resourceKey = "sftp://${connectionInfo.host}:${connectionInfo.port}"
            val filesResult = ConnectionThrottleManager.withThrottle(
                protocol = ConnectionThrottleManager.ProtocolLimits.SFTP,
                resourceKey = resourceKey,
                highPriority = false
            ) {
                sftpClient.listFiles(clientInfo, connectionInfo.remotePath)
            }
            
            if (filesResult.isFailure) {
                val e = filesResult.exceptionOrNull() ?: IOException("Unknown SFTP error")
                Timber.e("Failed to list SFTP files (paged): ${e.message}")
                throw IOException("SFTP error: ${e.message}", e)
            }

            // Filter and convert to MediaFile (all files first)
            val isAllFilesMode = supportedTypes.size >= 7
            val allMediaFiles = mutableListOf<MediaFile>()
            for (listing in filesResult.getOrNull() ?: emptyList()) {
                // Extract just the filename from the full path
                val fileName = listing.path.substringAfterLast('/')
                // Skip directories (listFiles in recursive mode already excludes them; guard for safety)
                if (listing.isDirectory) continue
                val mediaType = getMediaType(fileName) ?: if (isAllFilesMode) MediaType.TEXT else null
                if (mediaType != null && supportedTypes.contains(mediaType)) {
                    val fullPath = "sftp://${connectionInfo.host}:${connectionInfo.port}${listing.path}"

                    // Use attrs from listFiles() - no extra stat() round-trip needed.
                    // Fallback to stat() only if size is zero and a minimum size filter is active.
                    val fileSize: Long
                    val fileDate: Long
                    val needsSizeForFilter = sizeFilter != null && when (mediaType) {
                        MediaType.IMAGE -> sizeFilter.imageSizeMin > 0
                        MediaType.VIDEO -> sizeFilter.videoSizeMin > 0
                        MediaType.AUDIO -> sizeFilter.audioSizeMin > 0
                        MediaType.GIF -> sizeFilter.imageSizeMin > 0
                        else -> false
                    }
                    if (listing.size <= 0L && needsSizeForFilter) {
                        val attrsResult = sftpClient.stat(clientInfo, listing.path)
                        if (attrsResult.isFailure || attrsResult.getOrNull() == null) {
                            Timber.w("Fallback stat() failed for ${listing.path}, skipping")
                            continue
                        }
                        val attrs = attrsResult.getOrNull()!!
                        fileSize = attrs.size
                        fileDate = attrs.modifiedDate
                    } else {
                        fileSize = listing.size
                        fileDate = listing.modifiedDate
                    }
                    
                    // Apply size filter if provided
                    if (sizeFilter != null) {
                        val passesFilter = when (mediaType) {
                            MediaType.IMAGE -> fileSize >= sizeFilter.imageSizeMin && fileSize <= sizeFilter.imageSizeMax
                            MediaType.VIDEO -> fileSize >= sizeFilter.videoSizeMin && fileSize <= sizeFilter.videoSizeMax
                            MediaType.AUDIO -> fileSize >= sizeFilter.audioSizeMin && fileSize <= sizeFilter.audioSizeMax
                            MediaType.GIF -> fileSize >= sizeFilter.imageSizeMin && fileSize <= sizeFilter.imageSizeMax
                            MediaType.TEXT -> true
                            MediaType.PDF -> true
                            MediaType.EPUB -> true
                            MediaType.OFFICE_DOCUMENT -> true
                            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> true
                        }
                        if (!passesFilter) {
                            continue
                        }
                    }
                    
                    allMediaFiles.add(
                        MediaFile(
                            name = fileName,
                            path = fullPath,
                            size = fileSize,
                            createdDate = fileDate,
                            type = mediaType
                        )
                    )
                }
            }
            
            // Apply offset and limit
            val pageFiles = allMediaFiles.drop(offset).take(limit)
            val hasMore = offset + limit < allMediaFiles.size
            
            Timber.d("SftpMediaScanner paged: offset=$offset, limit=$limit, returned=${pageFiles.size}, hasMore=$hasMore")
            MediaFilePage(pageFiles, hasMore)
            
        } catch (e: CancellationException) {
            Timber.d("SFTP paged scan cancelled for path: $path")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SFTP folder (paged): $path")
            throw e
        }
    }

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean
    ): Int = withContext(Dispatchers.IO) {
        try {
            // Fast count: use paged scan with limit 1000
            val page = scanFolderPaged(path, supportedTypes, sizeFilter, offset = 0, limit = 1000, credentialsId)
            // If we got exactly 1000 files, there are likely more (return 1000 to show ">1000")
            // If we got less, that's the actual count
            page.files.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error counting SFTP files in: $path")
            0
        }
    }

    override suspend fun listDirectoryContents(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        showHiddenFiles: Boolean
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            val connectionInfo = parseSftpPath(path, credentialsId) ?: run {
                Timber.w("Invalid SFTP path format: $path")
                return@withContext emptyList()
            }

            val clientInfo = SftpClient.SftpConnectionInfo(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password,
                privateKey = connectionInfo.privateKey,
                passphrase = connectionInfo.password.ifEmpty { null }
            )

            // List files in remote path (throttled to avoid network overload)
            val resourceKey = "sftp://${connectionInfo.host}:${connectionInfo.port}"
            val filesResult = ConnectionThrottleManager.withThrottle(
                protocol = ConnectionThrottleManager.ProtocolLimits.SFTP,
                resourceKey = resourceKey,
                highPriority = false
            ) {
                // includeDirectories=true so directory entries appear in the listing for the browse UI
                sftpClient.listFiles(clientInfo, connectionInfo.remotePath, recursive = false, includeDirectories = true)
            }

            if (filesResult.isFailure) {
                val e = filesResult.exceptionOrNull() ?: IOException("Unknown SFTP error")
                Timber.e("Failed to list SFTP directory contents: ${e.message}")
                throw IOException("SFTP error: ${e.message}", e)
            }

            val isAllFilesMode = supportedTypes.size >= 7
            filesResult.getOrNull()?.mapNotNull { listing ->
                val fileName = listing.path.substringAfterLast('/')

                // Skip hidden files if not requested
                if (!showHiddenFiles && fileName.startsWith(".")) {
                    return@mapNotNull null
                }

                // attrs already available from listing; no stat() call needed
                if (listing.isDirectory) {
                    if (TrashFolderContract.matchesTrashSegment(fileName)) {
                        return@mapNotNull null
                    }

                    // Count children in this directory (separate listFiles call - not a stat call)
                    val childCountResult = ConnectionThrottleManager.withThrottle(
                        protocol = ConnectionThrottleManager.ProtocolLimits.SFTP,
                        resourceKey = resourceKey,
                        highPriority = false
                    ) {
                        sftpClient.listFiles(clientInfo, listing.path, recursive = false)
                    }

                    val childCount = when {
                        childCountResult.isSuccess -> childCountResult.getOrNull()?.size ?: 0
                        else -> 0
                    }

                    MediaFile(
                        name = fileName,
                        path = "sftp://${connectionInfo.host}:${connectionInfo.port}${listing.path}",
                        size = 0L,
                        createdDate = listing.modifiedDate,
                        type = MediaType.IMAGE, // Placeholder for directories
                        isDirectory = true,
                        childCount = childCount
                    )
                } else {
                    // Regular file
                    val mediaType = getMediaType(fileName) ?: if (isAllFilesMode) MediaType.TEXT else null
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        if (sizeFilter == null || MediaTypeUtils.isFileSizeInRange(listing.size, mediaType, sizeFilter)) {
                            MediaFile(
                                name = fileName,
                                path = "sftp://${connectionInfo.host}:${connectionInfo.port}${listing.path}",
                                size = listing.size,
                                createdDate = listing.modifiedDate,
                                type = mediaType,
                                isDirectory = false
                            )
                        } else null
                    } else null
                }
            }?.sortedWith(
                // Sort: folders first, then by name
                compareBy<MediaFile> { !it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            ) ?: emptyList()

        } catch (e: CancellationException) {
            Timber.d("SFTP directory listing cancelled for path: $path")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error listing SFTP directory contents: $path")
            throw e
        }
    }

    override suspend fun isWritable(path: String, credentialsId: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val connectionInfo = parseSftpPath(path, credentialsId) ?: return@withContext false

            // Test connection (with password or private key)
            val result = if (connectionInfo.privateKey != null) {
                sftpClient.testConnectionWithPrivateKey(
                    host = connectionInfo.host,
                    port = connectionInfo.port,
                    username = connectionInfo.username,
                    privateKey = connectionInfo.privateKey,
                    passphrase = connectionInfo.password.ifEmpty { null } // Passphrase stored in password field
                )
            } else {
                sftpClient.testConnection(
                    host = connectionInfo.host,
                    port = connectionInfo.port,
                    username = connectionInfo.username,
                    password = connectionInfo.password
                )
            }

            result.isSuccess
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error checking SFTP write access for: $path")
            false
        }
    }

    /**
     * Parse SFTP path format: sftp://server:port/remotePath
     * Retrieves credentials from database based on host
     */
    private suspend fun parseSftpPath(path: String, credentialsId: String?): SftpConnectionInfo? {
        return try {
            // Parse path using utility
            val pathInfo = SftpPathUtils.parseSftpPath(path) ?: return null
            val (host, port, remotePath) = pathInfo

            // Try to get credentials from database using credentialsId first
            val credentials = if (credentialsId != null) {
                credentialsRepository.getByCredentialId(credentialsId)
            } else {
                // Fallback to old behavior for backward compatibility
                credentialsRepository.getByTypeServerAndPort("SFTP", host, port)
            }

            if (credentials == null) {
                Timber.w("No SFTP credentials found for host: $host:$port")
                return null
            }

            SftpConnectionInfo(
                host = host,
                port = port,
                username = credentials.username,
                password = credentials.password,
                privateKey = credentials.decryptedSshPrivateKey,
                remotePath = remotePath
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing SFTP path: $path")
            null
        }
    }

    private fun getMediaType(fileName: String): MediaType? {
        return MediaTypeUtils.getMediaType(fileName)
    }

    private data class SftpConnectionInfo(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val privateKey: String?,
        val remotePath: String
    )
}
