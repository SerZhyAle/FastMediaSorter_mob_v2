package com.sza.fastmediasorter.data.remote.sftp

import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.utils.SftpPathUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaScanner implementation for SFTP network resources.
 * Scans remote SFTP servers for media files using SftpClient.
 */
@Singleton
class SftpMediaScanner @Inject constructor(
    private val sftpClient: SftpClient,
    private val credentialsRepository: NetworkCredentialsRepository
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
                Timber.e("Failed to list SFTP files for pagination: ${filesResult.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            // Filter and convert to MediaFile
            // When all 7 media types are supported (allFiles mode), treat unknown files as TEXT
            val isAllFilesMode = supportedTypes.size == 7
            val mediaFiles = filesResult.getOrNull()?.mapNotNull { filePath ->
                // Extract just the filename from the full path
                val fileName = filePath.substringAfterLast('/')
                
                // Skip trash folders created by soft-delete (optimization: avoid stat() call)
                if (fileName.startsWith(".trash")) {
                    return@mapNotNull null
                }
                
                // Skip hidden files if not requested
                if (!showHiddenFiles && fileName.startsWith(".")) {
                    return@mapNotNull null
                }
                
                val mediaType = getMediaType(fileName) ?: if (isAllFilesMode) MediaType.TEXT else null
                if (mediaType != null && supportedTypes.contains(mediaType)) {
                    // Get file attributes via stat()
                    // Build full SFTP path using the complete filePath from listFiles()
                    val fullPath = "sftp://${connectionInfo.host}:${connectionInfo.port}$filePath"
                    
                    val attrsResult = sftpClient.stat(clientInfo, filePath)
                    
                    if (attrsResult.isFailure) {
                        Timber.w("Failed to get attributes for $filePath, skipping")
                        return@mapNotNull null
                    }
                    
                    val attrs = attrsResult.getOrNull()
                    if (attrs == null) {
                        Timber.w("Attributes are null for $filePath after successful result, skipping")
                        return@mapNotNull null
                    }
                    
                    // Skip directories
                    if (attrs.isDirectory) {
                        return@mapNotNull null
                    }
                    
                    // Apply size filter if provided
                    if (sizeFilter != null) {
                        val passesFilter = when (mediaType) {
                            MediaType.IMAGE -> attrs.size >= sizeFilter.imageSizeMin && attrs.size <= sizeFilter.imageSizeMax
                            MediaType.VIDEO -> attrs.size >= sizeFilter.videoSizeMin && attrs.size <= sizeFilter.videoSizeMax
                            MediaType.AUDIO -> attrs.size >= sizeFilter.audioSizeMin && attrs.size <= sizeFilter.audioSizeMax
                            MediaType.GIF -> attrs.size >= sizeFilter.imageSizeMin && attrs.size <= sizeFilter.imageSizeMax
                            MediaType.TEXT -> true
                            MediaType.PDF -> true
                            MediaType.EPUB -> true
                            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> true
                        }
                        if (!passesFilter) {
                            return@mapNotNull null
                        }
                    }
                    
                    MediaFile(
                        name = fileName,
                        path = fullPath,
                        size = attrs.size,
                        createdDate = attrs.modifiedDate, // Use mtime as creation date
                        type = mediaType
                    )
                } else null
            } ?: emptyList()
            
            mediaFiles
        } catch (e: CancellationException) {
            Timber.d("SFTP scan cancelled for path: $path")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SFTP folder: $path")
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
                Timber.e("Failed to list SFTP files: ${filesResult.exceptionOrNull()?.message}")
                return@withContext MediaFilePage(emptyList(), false)
            }

            // Filter and convert to MediaFile (all files first)
            val isAllFilesMode = supportedTypes.size == 7
            val allMediaFiles = filesResult.getOrNull()?.mapNotNull { filePath ->
                // Extract just the filename from the full path
                val fileName = filePath.substringAfterLast('/')
                val mediaType = getMediaType(fileName) ?: if (isAllFilesMode) MediaType.TEXT else null
                if (mediaType != null && supportedTypes.contains(mediaType)) {
                    // Get file attributes via stat()
                    // Build full SFTP path using the complete filePath from listFiles()
                    val fullPath = "sftp://${connectionInfo.host}:${connectionInfo.port}$filePath"
                    
                    val attrsResult = sftpClient.stat(clientInfo, filePath)
                    
                    if (attrsResult.isFailure) {
                        Timber.w("Failed to get attributes for $filePath, skipping")
                        return@mapNotNull null
                    }
                    
                    val attrs = attrsResult.getOrNull()
                    if (attrs == null) {
                        Timber.w("Attributes are null for $filePath after successful result, skipping")
                        return@mapNotNull null
                    }
                    
                    // Skip directories
                    if (attrs.isDirectory) {
                        return@mapNotNull null
                    }
                    
                    // Apply size filter if provided
                    if (sizeFilter != null) {
                        val passesFilter = when (mediaType) {
                            MediaType.IMAGE -> attrs.size >= sizeFilter.imageSizeMin && attrs.size <= sizeFilter.imageSizeMax
                            MediaType.VIDEO -> attrs.size >= sizeFilter.videoSizeMin && attrs.size <= sizeFilter.videoSizeMax
                            MediaType.AUDIO -> attrs.size >= sizeFilter.audioSizeMin && attrs.size <= sizeFilter.audioSizeMax
                            MediaType.GIF -> attrs.size >= sizeFilter.imageSizeMin && attrs.size <= sizeFilter.imageSizeMax
                            MediaType.TEXT -> true
                            MediaType.PDF -> true
                            MediaType.EPUB -> true
                            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> true
                        }
                        if (!passesFilter) {
                            return@mapNotNull null
                        }
                    }
                    
                    MediaFile(
                        name = fileName,
                        path = fullPath,
                        size = attrs.size,
                        createdDate = attrs.modifiedDate,
                        type = mediaType
                    )
                } else null
            } ?: emptyList()
            
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
                sftpClient.listFiles(clientInfo, connectionInfo.remotePath, recursive = false)
            }

            if (filesResult.isFailure) {
                Timber.e("Failed to list SFTP directory contents: ${filesResult.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            val isAllFilesMode = supportedTypes.size == 7
            filesResult.getOrNull()?.mapNotNull { filePath ->
                val fileName = filePath.substringAfterLast('/')

                // Skip hidden files if not requested
                if (!showHiddenFiles && fileName.startsWith(".")) {
                    return@mapNotNull null
                }

                // Get file attributes to determine if it's a directory
                val attrsResult = sftpClient.stat(clientInfo, filePath)
                if (attrsResult.isFailure) {
                    Timber.w("Failed to get attributes for $filePath, skipping")
                    return@mapNotNull null
                }

                val attrs = attrsResult.getOrNull()
                if (attrs == null) {
                    Timber.w("Null attributes for $filePath")
                    return@mapNotNull null
                }

                if (attrs.isDirectory) {
                    // Skip .trash directories
                    if (fileName.startsWith(".trash")) {
                        return@mapNotNull null
                    }

                    // Count children in this directory
                    val childCountResult = ConnectionThrottleManager.withThrottle(
                        protocol = ConnectionThrottleManager.ProtocolLimits.SFTP,
                        resourceKey = resourceKey,
                        highPriority = false
                    ) {
                        sftpClient.listFiles(clientInfo, filePath, recursive = false)
                    }

                    val childCount = when {
                        childCountResult.isSuccess -> childCountResult.getOrNull()?.size ?: 0
                        else -> 0
                    }

                    MediaFile(
                        name = fileName,
                        path = "sftp://${connectionInfo.host}:${connectionInfo.port}$filePath",
                        size = 0L,
                        createdDate = attrs.modifiedDate,
                        type = MediaType.IMAGE, // Placeholder for directories
                        isDirectory = true,
                        childCount = childCount
                    )
                } else {
                    // Regular file
                    val mediaType = getMediaType(fileName) ?: if (isAllFilesMode) MediaType.TEXT else null
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        if (sizeFilter == null || MediaTypeUtils.isFileSizeInRange(attrs.size, mediaType, sizeFilter)) {
                            MediaFile(
                                name = fileName,
                                path = "sftp://${connectionInfo.host}:${connectionInfo.port}$filePath",
                                size = attrs.size,
                                createdDate = attrs.modifiedDate,
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
            emptyList()
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
