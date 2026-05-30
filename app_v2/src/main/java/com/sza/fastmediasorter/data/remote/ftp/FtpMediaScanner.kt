package com.sza.fastmediasorter.data.remote.ftp

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
import com.sza.fastmediasorter.utils.FtpPathUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaScanner implementation for FTP network resources.
 * Scans remote FTP servers for media files using FtpClient.
 */
@Singleton
class FtpMediaScanner @Inject constructor(
    private val ftpClient: FtpClient,
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
            Timber.d("FTP scanFolder start: hasCredentials=${!credentialsId.isNullOrBlank()}")
            
            // Parse path format: ftp://server:port/remotePath
            val connectionInfo = parseFtpPath(path, credentialsId) ?: run {
                Timber.w("Invalid FTP path format: $path")
                return@withContext emptyList()
            }

            Timber.d("FTP connection info: host=${connectionInfo.host}, port=${connectionInfo.port}, hasUser=${connectionInfo.username.isNotBlank()}, remotePath=${connectionInfo.remotePath}")

            // Connect and list files with metadata
            val connectResult = ftpClient.connect(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password
            )

            if (connectResult.isFailure) {
                Timber.e("Failed to connect to FTP: ${connectResult.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            // List files with metadata (size, timestamp) in remote path (throttled to avoid network overload)
            val resourceKey = "ftp://${connectionInfo.host}:${connectionInfo.port}"
            val filesResult = ConnectionThrottleManager.withThrottle(
                protocol = ConnectionThrottleManager.ProtocolLimits.FTP,
                resourceKey = resourceKey,
                highPriority = false
            ) {
                ftpClient.listFilesWithMetadata(connectionInfo.remotePath, recursive = scanSubdirectories)
            }
            ftpClient.disconnect()

            if (filesResult.isFailure) {
                val exception = filesResult.exceptionOrNull() ?: IOException("Unknown FTP error")
                Timber.e("Failed to list FTP files: ${exception.message}")
                throw IOException("FTP connection error: ${exception.message}", exception)
            }

            // Filter and convert to MediaFile with real size/date from FTPFile
            // When all 7 media types are supported (allFiles mode), treat unknown files as TEXT
            val isAllFilesMode = supportedTypes.size >= 7
            filesResult.getOrNull()?.mapNotNull { ftpFile ->
                if (TrashFolderContract.matchesTrashSegment(ftpFile.name)) {
                    return@mapNotNull null
                }
                
                // Skip hidden files if not requested
                if (!showHiddenFiles && ftpFile.name.startsWith(".")) {
                    return@mapNotNull null
                }
                
                val mediaType = getMediaType(ftpFile.name) ?: if (isAllFilesMode) MediaType.TEXT else null
                if (mediaType != null && supportedTypes.contains(mediaType)) {
                    val fileSize = ftpFile.size
                    val timestamp = ftpFile.timestamp?.timeInMillis ?: 0L
                    
                    // Apply size filter if specified based on media type
                    if (sizeFilter != null) {
                        val minSize = when (mediaType) {
                            MediaType.IMAGE, MediaType.GIF -> sizeFilter.imageSizeMin
                            MediaType.VIDEO -> sizeFilter.videoSizeMin
                            MediaType.AUDIO -> sizeFilter.audioSizeMin
                            MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT -> 0L
                            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> 0L
                        }
                        val maxSize = when (mediaType) {
                            MediaType.IMAGE, MediaType.GIF -> sizeFilter.imageSizeMax
                            MediaType.VIDEO -> sizeFilter.videoSizeMax
                            MediaType.AUDIO -> sizeFilter.audioSizeMax
                            MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT -> Long.MAX_VALUE
                            MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> Long.MAX_VALUE
                        }
                        
                        if (fileSize < minSize || fileSize > maxSize) {
                            return@mapNotNull null
                        }
                    }
                    
                    MediaFile(
                        name = ftpFile.name,
                        path = buildFullFtpPath(connectionInfo, ftpFile.name),
                        size = fileSize,
                        createdDate = timestamp,
                        type = mediaType
                    )
                } else null
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Error scanning FTP folder: $path")
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
        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            throw LocalNetworkPermissionDeniedException()
        }
        try {
            val connectionInfo = parseFtpPath(path, credentialsId) ?: run {
                Timber.w("Invalid FTP path format: $path")
                return@withContext MediaFilePage(emptyList(), false)
            }

            val connectResult = ftpClient.connect(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password
            )

            if (connectResult.isFailure) {
                Timber.e("Failed to connect to FTP for paged scan: ${connectResult.exceptionOrNull()?.message}")
                return@withContext MediaFilePage(emptyList(), false)
            }

            val resourceKey = "ftp://${connectionInfo.host}:${connectionInfo.port}"
            val safeLimit = limit.coerceAtLeast(1)
            val batchSize = (safeLimit * 3).coerceIn(100, 1000)

            val pageFiles = mutableListOf<MediaFile>()
            var filteredSkipped = 0
            var rawOffset = 0
            var rawHasMore = true

            while (rawHasMore && pageFiles.size <= safeLimit && kotlinx.coroutines.currentCoroutineContext().isActive) {
                val batchResult = ConnectionThrottleManager.withThrottle(
                    protocol = ConnectionThrottleManager.ProtocolLimits.FTP,
                    resourceKey = resourceKey,
                    highPriority = false
                ) {
                    ftpClient.listFilesWithMetadataPaged(
                        remotePath = connectionInfo.remotePath,
                        offset = rawOffset,
                        limit = batchSize + 1,
                        recursive = scanSubdirectories
                    )
                }

                if (batchResult.isFailure) {
                    Timber.e("Failed to list FTP files (paged batch): ${batchResult.exceptionOrNull()?.message}")
                    break
                }

                val rawBatch = batchResult.getOrNull().orEmpty()
                rawHasMore = rawBatch.size > batchSize
                val candidateFiles = rawBatch.take(batchSize)

                if (candidateFiles.isEmpty()) {
                    break
                }

                candidateFiles.forEach { ftpFile ->
                    val mediaFile = toMediaFileOrNull(
                        ftpFile = ftpFile,
                        connectionInfo = connectionInfo,
                        supportedTypes = supportedTypes,
                        sizeFilter = sizeFilter,
                        showHiddenFiles = showHiddenFiles
                    ) ?: return@forEach

                    if (filteredSkipped < offset) {
                        filteredSkipped++
                    } else if (pageFiles.size < safeLimit + 1) {
                        pageFiles.add(mediaFile)
                    }
                }

                rawOffset += candidateFiles.size
                if (candidateFiles.size < batchSize) {
                    rawHasMore = false
                }
            }

            ftpClient.disconnect()

            val hasMore = pageFiles.size > safeLimit || rawHasMore
            val resultFiles = pageFiles.take(safeLimit)

            Timber.d(
                "FtpMediaScanner paged(native): offset=$offset, limit=$limit, returned=${resultFiles.size}, hasMore=$hasMore, rawOffset=$rawOffset"
            )
            MediaFilePage(resultFiles, hasMore)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning FTP folder (paged): $path")
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
            Timber.d("FTP getFileCount: path=$path")
            
            val connectionInfo = parseFtpPath(path, credentialsId) ?: run {
                Timber.w("Invalid FTP path format: $path")
                return@withContext 0
            }

            val connectResult = ftpClient.connect(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password
            )

            if (connectResult.isFailure) {
                Timber.e("Failed to connect to FTP for file count: ${connectResult.exceptionOrNull()?.message}")
                return@withContext 0
            }

            val resourceKey = "ftp://${connectionInfo.host}:${connectionInfo.port}"
            val filesResult = ConnectionThrottleManager.withThrottle(
                protocol = ConnectionThrottleManager.ProtocolLimits.FTP,
                resourceKey = resourceKey,
                highPriority = false
            ) {
                ftpClient.listFilesWithMetadata(connectionInfo.remotePath, recursive = scanSubdirectories)
            }
            ftpClient.disconnect()

            if (filesResult.isFailure) {
                Timber.e("Failed to list FTP files for count: ${filesResult.exceptionOrNull()?.message}")
                return@withContext 0
            }

            // Count only matching files without creating MediaFile objects
            val isAllFilesMode = supportedTypes.size >= 7
            val count = filesResult.getOrNull()?.count { ftpFile ->
                val mediaType = getMediaType(ftpFile.name) ?: if (isAllFilesMode) MediaType.TEXT else null
                if (mediaType == null || !supportedTypes.contains(mediaType)) {
                    false
                } else if (sizeFilter == null) {
                    true
                } else {
                    val fileSize = ftpFile.size
                    val minSize = when (mediaType) {
                        MediaType.IMAGE, MediaType.GIF -> sizeFilter.imageSizeMin
                        MediaType.VIDEO -> sizeFilter.videoSizeMin
                        MediaType.AUDIO -> sizeFilter.audioSizeMin
                        MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT -> 0L
                        MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> 0L
                    }
                    val maxSize = when (mediaType) {
                        MediaType.IMAGE, MediaType.GIF -> sizeFilter.imageSizeMax
                        MediaType.VIDEO -> sizeFilter.videoSizeMax
                        MediaType.AUDIO -> sizeFilter.audioSizeMax
                        MediaType.TEXT, MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT -> Long.MAX_VALUE
                        MediaType.BINARY_ARCHIVE, MediaType.BINARY_DISK, MediaType.BINARY_EXECUTABLE, MediaType.BINARY_OTHER -> Long.MAX_VALUE
                    }
                    fileSize in minSize..maxSize
                }
            } ?: 0
            
            Timber.d("FTP getFileCount result: $count files")
            count
        } catch (e: Exception) {
            Timber.e(e, "Error counting FTP files in: $path")
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
            Timber.d("FTP listDirectoryContents: path=$path, credentialsId=$credentialsId")

            val connectionInfo = parseFtpPath(path, credentialsId) ?: run {
                Timber.w("Invalid FTP path format: $path")
                return@withContext emptyList()
            }

            val connectResult = ftpClient.connect(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password
            )

            if (connectResult.isFailure) {
                Timber.e("Failed to connect to FTP")
                return@withContext emptyList()
            }

            // List files with metadata (non-recursive)
            val resourceKey = "ftp://${connectionInfo.host}:${connectionInfo.port}"
            val filesResult = ConnectionThrottleManager.withThrottle(
                protocol = ConnectionThrottleManager.ProtocolLimits.FTP,
                resourceKey = resourceKey,
                highPriority = false
            ) {
                ftpClient.listFilesWithMetadata(connectionInfo.remotePath, recursive = false)
            }
            ftpClient.disconnect()

            if (filesResult.isFailure) {
                Timber.e("Failed to list FTP directory contents: ${filesResult.exceptionOrNull()?.message}")
                return@withContext emptyList()
            }

            val isAllFilesMode = supportedTypes.size >= 7
            filesResult.getOrNull()?.mapNotNull { ftpFile ->
                val fileName = ftpFile.name

                // Skip hidden files if not requested
                if (!showHiddenFiles && fileName.startsWith(".")) {
                    return@mapNotNull null
                }

                if (ftpFile.isDirectory) {
                    if (TrashFolderContract.matchesTrashSegment(fileName)) {
                        return@mapNotNull null
                    }

                    // Count children in this directory
                    val childCountResult = try {
                        val connectForCount = ftpClient.connect(
                            host = connectionInfo.host,
                            port = connectionInfo.port,
                            username = connectionInfo.username,
                            password = connectionInfo.password
                        )
                        if (connectForCount.isFailure) {
                            Result.failure(Exception("Failed to connect for count"))
                        } else {
                            val childPath = if (connectionInfo.remotePath.isEmpty()) fileName else "${connectionInfo.remotePath}/$fileName"
                            val result = ConnectionThrottleManager.withThrottle(
                                protocol = ConnectionThrottleManager.ProtocolLimits.FTP,
                                resourceKey = resourceKey,
                                highPriority = false
                            ) {
                                ftpClient.listFilesWithMetadata(childPath, recursive = false)
                            }
                            ftpClient.disconnect()
                            result
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }

                    val childCount = when {
                        childCountResult.isSuccess -> childCountResult.getOrNull()?.size ?: 0
                        else -> 0
                    }

                    MediaFile(
                        name = fileName,
                        path = "ftp://${connectionInfo.host}:${connectionInfo.port}${connectionInfo.remotePath}/$fileName",
                        size = 0L,
                        createdDate = ftpFile.timestamp?.timeInMillis ?: 0L,
                        type = MediaType.IMAGE, // Placeholder for directories
                        isDirectory = true,
                        childCount = childCount
                    )
                } else {
                    // Regular file
                    val mediaType = getMediaType(fileName) ?: if (isAllFilesMode) MediaType.TEXT else null
                    if (mediaType != null && supportedTypes.contains(mediaType)) {
                        val fileSize = ftpFile.size
                        if (sizeFilter == null || MediaTypeUtils.isFileSizeInRange(fileSize, mediaType, sizeFilter)) {
                            MediaFile(
                                name = fileName,
                                path = "ftp://${connectionInfo.host}:${connectionInfo.port}${connectionInfo.remotePath}/$fileName",
                                size = fileSize,
                                createdDate = ftpFile.timestamp?.timeInMillis ?: 0L,
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

        } catch (e: Exception) {
            Timber.e(e, "Error listing FTP directory contents: $path")
            emptyList()
        }
    }

    override suspend fun isWritable(path: String, credentialsId: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            Timber.d("FTP isWritable: path=$path, credentialsId=$credentialsId")
            
            val connectionInfo = parseFtpPath(path, credentialsId) ?: run {
                Timber.w("Invalid FTP path format for isWritable: $path")
                return@withContext false
            }

            val connectResult = ftpClient.connect(
                host = connectionInfo.host,
                port = connectionInfo.port,
                username = connectionInfo.username,
                password = connectionInfo.password
            )

            if (connectResult.isFailure) {
                Timber.e("Failed to connect to FTP for writable check")
                return@withContext false
            }

            // For FTP we can't easily check permissions without attempting write
            // Assume writable if connection succeeds
            ftpClient.disconnect()
            Timber.d("FTP isWritable result: true")
            true
        } catch (e: Exception) {
            Timber.e(e, "Error checking FTP writable: $path")
            false
        }
    }

    private suspend fun parseFtpPath(path: String, credentialsId: String?): ConnectionInfo? {
        Timber.d("Parsing FTP path: $path, credentialsId=$credentialsId")
        
        // Parse path using utility
        val pathInfo = FtpPathUtils.parseFtpPath(path) ?: run {
            Timber.w("Failed to parse FTP path")
            return null
        }
        val (host, port, remotePath) = pathInfo
        
        Timber.d("Parsed FTP URL: host=$host, port=$port, remotePath=$remotePath")

        // Get credentials from database
        if (credentialsId == null) {
            Timber.w("No credentials ID provided for FTP connection")
            return null
        }

        val credentials = credentialsRepository.getByCredentialId(credentialsId)
        if (credentials == null) {
            Timber.w("Credentials not found for ID: $credentialsId")
            return null
        }
        
        Timber.d("Found credentials: type=${credentials.type}, username=${credentials.username}")

        return ConnectionInfo(
            host = host,
            port = port,
            username = credentials.username,
            password = credentials.password,
            remotePath = remotePath
        )
    }

    private fun buildFullFtpPath(connectionInfo: ConnectionInfo, fileName: String): String {
        val cleanRemotePath = connectionInfo.remotePath.trimEnd('/')
        val cleanFileName = fileName.trimStart('/')
        
        // If remotePath is empty (was "/"), don't add extra slash
        val fullPath = if (cleanRemotePath.isEmpty()) {
            "ftp://${connectionInfo.host}:${connectionInfo.port}/$cleanFileName"
        } else {
            "ftp://${connectionInfo.host}:${connectionInfo.port}$cleanRemotePath/$cleanFileName"
        }
        
        // Verbose logging - too noisy for large file lists
        // Timber.d("buildFullFtpPath: remotePath='${connectionInfo.remotePath}', fileName='$fileName' -> '$fullPath'")
        return fullPath
    }

    private fun getMediaType(fileName: String): MediaType? {
        return MediaTypeUtils.getMediaType(fileName)
    }

    private fun toMediaFileOrNull(
        ftpFile: org.apache.commons.net.ftp.FTPFile,
        connectionInfo: ConnectionInfo,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        showHiddenFiles: Boolean
    ): MediaFile? {
        if (TrashFolderContract.matchesTrashSegment(ftpFile.name)) return null
        if (!showHiddenFiles && ftpFile.name.startsWith(".")) return null

        val isAllFilesMode = supportedTypes.size >= 7
        val mediaType = getMediaType(ftpFile.name) ?: if (isAllFilesMode) MediaType.TEXT else null
        if (mediaType == null || !supportedTypes.contains(mediaType)) return null

        val fileSize = ftpFile.size
        if (sizeFilter != null && !MediaTypeUtils.isFileSizeInRange(fileSize, mediaType, sizeFilter)) {
            return null
        }

        return MediaFile(
            name = ftpFile.name,
            path = buildFullFtpPath(connectionInfo, ftpFile.name),
            size = fileSize,
            createdDate = ftpFile.timestamp?.timeInMillis ?: 0L,
            type = mediaType
        )
    }

    private data class ConnectionInfo(
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
        val remotePath: String
    )
}
