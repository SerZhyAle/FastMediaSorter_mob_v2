package com.sza.fastmediasorter_v2.data.network

import com.sza.fastmediasorter_v2.data.local.db.NetworkCredentialsDao
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.usecase.ExtractExifMetadataUseCase
import com.sza.fastmediasorter_v2.domain.usecase.MediaFilePage
import com.sza.fastmediasorter_v2.domain.usecase.MediaScanner
import com.sza.fastmediasorter_v2.domain.usecase.SizeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaScanner implementation for SMB network resources.
 * Scans remote SMB shares for media files using SmbClient.
 */
@Singleton
class SmbMediaScanner @Inject constructor(
    private val smbClient: SmbClient,
    private val credentialsDao: NetworkCredentialsDao,
    private val exifExtractor: ExtractExifMetadataUseCase
) : MediaScanner {

    companion object {
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp")
        private val GIF_EXTENSIONS = setOf("gif")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "webm", "3gp", "flv", "wmv", "m4v")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "aac", "ogg", "wma", "opus")
    }

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        scanFolderWithProgress(path, supportedTypes, sizeFilter, credentialsId, null)
    }
    
    /**
     * Scan folder with progress callback support
     */
    suspend fun scanFolderWithProgress(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        progressCallback: com.sza.fastmediasorter_v2.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            // Parse path format: smb://server:port/share/path
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                Timber.w("Invalid SMB path format: $path")
                return@withContext emptyList()
            }

            // Get all supported extensions
            val extensions = buildExtensionsSet(supportedTypes)

            // Scan SMB folder with progress callback
            when (val result = smbClient.scanMediaFiles(
                connectionInfo = connectionInfo.connectionInfo,
                remotePath = connectionInfo.remotePath,
                extensions = extensions,
                progressCallback = progressCallback
            )) {
                is SmbClient.SmbResult.Success -> {
                    // Convert SmbFileInfo to MediaFile
                    result.data.mapNotNull { fileInfo ->
                        val mediaType = getMediaType(fileInfo.name)
                        if (mediaType != null && supportedTypes.contains(mediaType)) {
                            // Apply size filter if provided
                            if (sizeFilter != null && !isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) {
                                return@mapNotNull null
                            }

                            // TODO: Extract EXIF from SMB files (requires downloading file header)
                            // For now, EXIF extraction is skipped for network files to avoid slow scanning
                            // EXIF can be extracted on-demand during image viewing
                            
                            // TODO: Extract video metadata from SMB files (requires downloading file or partial read)
                            // For now, video metadata extraction is skipped for network files to avoid slow scanning
                            // Video metadata can be extracted on-demand during video viewing
                            
                            MediaFile(
                                name = fileInfo.name,
                                path = buildFullSmbPath(connectionInfo, fileInfo.path),
                                size = fileInfo.size,
                                createdDate = fileInfo.lastModified,
                                type = mediaType
                            )
                        } else null
                    }
                }
                is SmbClient.SmbResult.Error -> {
                    Timber.e("Error scanning SMB folder: ${result.message}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SMB folder: $path")
            emptyList()
        }
    }

    /**
     * Scan folder with limit (for lazy loading initial batch)
     * Returns first maxFiles files quickly
     */
    suspend fun scanFolderChunked(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter? = null,
        maxFiles: Int = 100,
        credentialsId: String? = null
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            Timber.d("scanFolderChunked: START - path=$path, maxFiles=$maxFiles, credentialsId=$credentialsId")
            
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                Timber.w("Invalid SMB path format: $path")
                return@withContext emptyList()
            }

            Timber.d("scanFolderChunked: Parsed path - share=${connectionInfo.connectionInfo.shareName}, remotePath=${connectionInfo.remotePath}")

            val extensions = buildExtensionsSet(supportedTypes)
            
            Timber.d("scanFolderChunked: Extensions=$extensions")

            // Use chunked scan method
            when (val result = smbClient.scanMediaFilesChunked(
                connectionInfo = connectionInfo.connectionInfo,
                remotePath = connectionInfo.remotePath,
                extensions = extensions,
                maxFiles = maxFiles
            )) {
                is SmbClient.SmbResult.Success -> {
                    Timber.d("scanFolderChunked: Got ${result.data.size} files from smbClient")
                    
                    val mediaFiles = result.data.mapNotNull { fileInfo ->
                        val mediaType = getMediaType(fileInfo.name)
                        if (mediaType != null && supportedTypes.contains(mediaType)) {
                            if (sizeFilter != null && !isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) {
                                return@mapNotNull null
                            }

                            MediaFile(
                                name = fileInfo.name,
                                path = buildFullSmbPath(connectionInfo, fileInfo.path),
                                size = fileInfo.size,
                                createdDate = fileInfo.lastModified,
                                type = mediaType
                            )
                        } else null
                    }
                    
                    Timber.d("scanFolderChunked: Returning ${mediaFiles.size} MediaFile objects")
                    mediaFiles
                }
                is SmbClient.SmbResult.Error -> {
                    Timber.e("Error scanning SMB folder (chunked): ${result.message}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SMB folder (chunked): $path")
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
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                Timber.w("Invalid SMB path format: $path")
                return@withContext MediaFilePage(emptyList(), false)
            }

            val extensions = buildExtensionsSet(supportedTypes)

            // Use optimized paged scan with native offset/limit support
            when (val result = smbClient.scanMediaFilesPaged(
                connectionInfo = connectionInfo.connectionInfo,
                remotePath = connectionInfo.remotePath,
                extensions = extensions,
                offset = offset,
                limit = limit
            )) {
                is SmbClient.SmbResult.Success -> {
                    // Convert to MediaFile list with optional size filtering
                    val mediaFiles = result.data.mapNotNull { fileInfo ->
                        val mediaType = getMediaType(fileInfo.name)
                        if (mediaType != null && supportedTypes.contains(mediaType)) {
                            if (sizeFilter != null && !isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) {
                                return@mapNotNull null
                            }

                            MediaFile(
                                name = fileInfo.name,
                                path = buildFullSmbPath(connectionInfo, fileInfo.path),
                                size = fileInfo.size,
                                createdDate = fileInfo.lastModified,
                                type = mediaType
                            )
                        } else null
                    }
                    
                    // If we got fewer files than requested, no more pages
                    val hasMore = mediaFiles.size >= limit
                    
                    Timber.d("SmbMediaScanner paged: offset=$offset, limit=$limit, returned=${mediaFiles.size}, hasMore=$hasMore")
                    MediaFilePage(mediaFiles, hasMore)
                }
                is SmbClient.SmbResult.Error -> {
                    Timber.e("Error scanning SMB folder (paged): ${result.message}")
                    MediaFilePage(emptyList(), false)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SMB folder (paged): $path")
            MediaFilePage(emptyList(), false)
        }
    }

    override suspend fun getFileCount(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?
    ): Int = withContext(Dispatchers.IO) {
        try {
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                Timber.w("Invalid SMB path format for count: $path")
                return@withContext 0
            }

            // Get all supported extensions
            val extensions = buildExtensionsSet(supportedTypes)

            // Use optimized count method (no SmbFileInfo objects created)
            when (val result = smbClient.countMediaFiles(
                connectionInfo = connectionInfo.connectionInfo,
                remotePath = connectionInfo.remotePath,
                extensions = extensions
            )) {
                is SmbClient.SmbResult.Success -> {
                    // Note: sizeFilter is ignored for counting (would require fetching size for each file)
                    result.data
                }
                is SmbClient.SmbResult.Error -> {
                    Timber.e("Error counting SMB files: ${result.message}")
                    0
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error counting SMB files in: $path")
            0
        }
    }

    override suspend fun isWritable(path: String, credentialsId: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val pathInfo = parseSmbPath(path, credentialsId) ?: return@withContext false

            // Test actual write permission by creating a test file
            when (val result = smbClient.checkWritePermission(pathInfo.connectionInfo, pathInfo.remotePath)) {
                is SmbClient.SmbResult.Success -> result.data
                is SmbClient.SmbResult.Error -> {
                    Timber.w("SMB path not writable: ${result.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking SMB write access for: $path")
            false
        }
    }

    /**
     * Parse SMB path format: smb://server:port/share/path
     */
    private suspend fun parseSmbPath(path: String, credentialsId: String?): SmbConnectionInfoWithPath? {
        return try {
            if (path.startsWith("smb://")) {
                // Format: smb://server:port/share/path
                val withoutProtocol = path.removePrefix("smb://")
                val parts = withoutProtocol.split("/", limit = 2)
                
                if (parts.isEmpty()) return null
                
                val serverPart = parts[0]
                val pathPart = if (parts.size > 1) parts[1] else ""
                
                // Parse server:port/share
                val serverPort = serverPart.split(":", limit = 2)
                val server = serverPort[0]
                val port = if (serverPort.size > 1) serverPort[1].toIntOrNull() ?: 445 else 445
                
                // Parse share/path
                val pathParts = pathPart.split("/", limit = 2)
                val share = if (pathParts.isNotEmpty()) pathParts[0] else ""
                val remotePath = if (pathParts.size > 1) pathParts[1] else ""
                
                if (server.isEmpty() || share.isEmpty()) return null
                
                // Try to get credentials from database using credentialsId first
                val credentials = if (credentialsId != null) {
                    credentialsDao.getCredentialsById(credentialsId)
                } else {
                    // Fallback to old behavior for backward compatibility
                    credentialsDao.getByServerAndShare(server, share)
                }
                
                SmbConnectionInfoWithPath(
                    connectionInfo = SmbClient.SmbConnectionInfo(
                        server = server,
                        shareName = share,
                        username = credentials?.username ?: "",
                        password = credentials?.password ?: "",
                        domain = credentials?.domain ?: "",
                        port = port
                    ),
                    remotePath = remotePath
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing SMB path: $path")
            null
        }
    }

    private fun buildFullSmbPath(connectionInfo: SmbConnectionInfoWithPath, filePath: String): String {
        return "smb://${connectionInfo.connectionInfo.server}:${connectionInfo.connectionInfo.port}/${connectionInfo.connectionInfo.shareName}/$filePath"
    }

    private fun buildExtensionsSet(supportedTypes: Set<MediaType>): Set<String> {
        val extensions = mutableSetOf<String>()
        
        supportedTypes.forEach { type ->
            when (type) {
                MediaType.IMAGE -> extensions.addAll(IMAGE_EXTENSIONS)
                MediaType.GIF -> extensions.addAll(GIF_EXTENSIONS)
                MediaType.VIDEO -> extensions.addAll(VIDEO_EXTENSIONS)
                MediaType.AUDIO -> extensions.addAll(AUDIO_EXTENSIONS)
            }
        }
        
        return extensions
    }

    private fun getMediaType(fileName: String): MediaType? {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when {
            IMAGE_EXTENSIONS.contains(extension) -> MediaType.IMAGE
            GIF_EXTENSIONS.contains(extension) -> MediaType.GIF
            VIDEO_EXTENSIONS.contains(extension) -> MediaType.VIDEO
            AUDIO_EXTENSIONS.contains(extension) -> MediaType.AUDIO
            else -> null
        }
    }

    private fun isFileSizeInRange(size: Long, mediaType: MediaType, filter: SizeFilter): Boolean {
        return when (mediaType) {
            MediaType.IMAGE, MediaType.GIF -> size in filter.imageSizeMin..filter.imageSizeMax
            MediaType.VIDEO -> size in filter.videoSizeMin..filter.videoSizeMax
            MediaType.AUDIO -> size in filter.audioSizeMin..filter.audioSizeMax
        }
    }

    private data class SmbConnectionInfoWithPath(
        val connectionInfo: SmbClient.SmbConnectionInfo,
        val remotePath: String
    )
}
