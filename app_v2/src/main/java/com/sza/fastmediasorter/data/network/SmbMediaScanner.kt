package com.sza.fastmediasorter.data.network

import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.utils.SmbPathUtils
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * MediaScanner implementation for SMB network resources.
 * Scans remote SMB shares for media files using SmbClient.
 */
@Singleton
class SmbMediaScanner @Inject constructor(
    private val smbClient: SmbClient,
    private val credentialsRepository: NetworkCredentialsRepository
    // Metadata extraction removed - loaded on-demand
) : MediaScanner {

    private data class ExifMetadata(
        val width: Int?,
        val height: Int?,
        val orientation: Int?,
        val dateTimeMillis: Long?,
        val latitude: Double?,
        val longitude: Double?
    )

    private data class VideoMetadata(
        val duration: Long?,
        val width: Int?,
        val height: Int?,
        val codec: String?,
        val bitrate: Int?,
        val frameRate: Float?,
        val rotation: Int?
    )

    private val exifCache = LruCache<String, ExifMetadata>(100)
    private val videoMetadataCache = LruCache<String, VideoMetadata>(100)
    private val _metadataErrorCount = AtomicInteger(0)

    companion object {
        // Extensions moved to MediaTypeUtils
        /** Skip per-file EXIF/video metadata extraction when folder exceeds this count */
        private const val METADATA_SKIP_THRESHOLD = 500
    }

    override suspend fun scanFolder(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        onProgress: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        scanFolderWithProgress(path, supportedTypes, sizeFilter, credentialsId, scanSubdirectories, showHiddenFiles, onProgress)
    }
    
    /**
     * Scan folder with progress callback support
     */
    suspend fun scanFolderWithProgress(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        progressCallback: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            // Parse path format: smb://server:port/share/path
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                val msg = "Invalid SMB path format: $path"
                Timber.w(msg)
                throw IllegalArgumentException(msg)
            }

            // Determine if we are in "All Files" mode (all 7 types supported)
            val isAllFilesMode = supportedTypes.size >= 7

            // Get all supported extensions or null if in All Files mode (to disable filtering)
            val extensions = if (isAllFilesMode) null else MediaTypeUtils.buildExtensionsSet(supportedTypes)

            // Scan SMB folder with progress callback (throttled to avoid network overload)
            val resourceKey = "smb://${connectionInfo.connectionInfo.server}:${connectionInfo.connectionInfo.port}"
            
            when (val result = ConnectionThrottleManager.withThrottle(
                protocol = ConnectionThrottleManager.ProtocolLimits.SMB,
                resourceKey = resourceKey,
                highPriority = false
            ) {
                smbClient.scanMediaFiles(
                    connectionInfo = connectionInfo.connectionInfo,
                    remotePath = connectionInfo.remotePath,
                    extensions = extensions,
                    scanSubdirectories = scanSubdirectories,
                    progressCallback = progressCallback
                )
            }) {
                is SmbResult.Success -> {
                    // Convert SmbFileInfo to MediaFile
                    // When all 7 media types are supported (allFiles mode), treat unknown files as TEXT
                    // val isAllFilesMode = supportedTypes.size >= 7 // Already calculated above
                    // Skip per-file metadata extraction for large folders to avoid
                    // tens of minutes of individual SMB reads (EXIF / video probes).
                    val skipMetadataExtraction = result.data.size > METADATA_SKIP_THRESHOLD
                    if (skipMetadataExtraction) {
                        Timber.d("Large folder (${result.data.size} files) — skipping per-file EXIF/video metadata extraction")
                    }
                    _metadataErrorCount.set(0)
                    val files = result.data.mapNotNull { fileInfo ->
                        // Skip hidden files if not requested (files starting with ".")
                        if (!showHiddenFiles && fileInfo.name.startsWith(".")) {
                            return@mapNotNull null
                        }
                        
                        // Skip directories in flat scan (recursive or not) - we only want files
                        if (fileInfo.isDirectory) {
                            return@mapNotNull null
                        }
                        
                        val mediaType = MediaTypeUtils.getMediaType(fileInfo.name) ?: if (isAllFilesMode) MediaType.TEXT else null
                        if (mediaType != null && supportedTypes.contains(mediaType)) {
                            // Apply size filter if provided
                            if (sizeFilter != null && !MediaTypeUtils.isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) {
                                return@mapNotNull null
                            }

                            val exifMetadata = if (!skipMetadataExtraction && (mediaType == MediaType.IMAGE || mediaType == MediaType.GIF)) {
                                extractExifMetadata(connectionInfo.connectionInfo, fileInfo.path)
                            } else {
                                null
                            }

                            val videoMetadata = if (!skipMetadataExtraction && mediaType == MediaType.VIDEO) {
                                extractVideoMetadata(connectionInfo.connectionInfo, fileInfo.path)
                            } else {
                                null
                            }
                            
                            MediaFile(
                                name = fileInfo.name,
                                path = buildFullSmbPath(connectionInfo, fileInfo.path),
                                size = fileInfo.size,
                                createdDate = fileInfo.lastModified,
                                type = mediaType,
                                duration = videoMetadata?.duration,
                                width = exifMetadata?.width ?: videoMetadata?.width,
                                height = exifMetadata?.height ?: videoMetadata?.height,
                                exifOrientation = exifMetadata?.orientation,
                                exifDateTime = exifMetadata?.dateTimeMillis,
                                exifLatitude = exifMetadata?.latitude,
                                exifLongitude = exifMetadata?.longitude,
                                videoCodec = videoMetadata?.codec,
                                videoBitrate = videoMetadata?.bitrate,
                                videoFrameRate = videoMetadata?.frameRate,
                                videoRotation = videoMetadata?.rotation
                            )
                        } else null
                    }
                    val errCount = _metadataErrorCount.get()
                    if (errCount > 0) progressCallback?.onMetadataErrors(errCount)
                    files
                }
                is SmbResult.Error -> {
                    val ex = result.exception ?: Exception(result.message)
                    if (ex !is CancellationException) Timber.e("Error scanning SMB folder: ${result.message}")
                    throw ex
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SMB folder: $path")
            throw e
        }
    }

    /**
     * Fetch single file metadata directly from SMB share without full folder rescan.
     */
    suspend fun getFileByPath(
        path: String,
        supportedTypes: Set<MediaType>,
        credentialsId: String?
    ): MediaFile? = withContext(Dispatchers.IO) {
        try {
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                Timber.w("getFileByPath: Invalid SMB path: $path")
                return@withContext null // getFileByPath is allowed to return null
            }

            if (connectionInfo.remotePath.isEmpty()) {
                Timber.w("getFileByPath: Remote path is empty for $path")
                return@withContext null
            }

            val fileResult = smbClient.getFileInfo(
                connectionInfo = connectionInfo.connectionInfo,
                remotePath = connectionInfo.remotePath
            )

            when (fileResult) {
                is SmbResult.Success -> {
                    val smbFile = fileResult.data
                    val mediaType = MediaTypeUtils.getMediaType(smbFile.name)
                    if (mediaType == null || !supportedTypes.contains(mediaType)) {
                        Timber.d("getFileByPath: Unsupported media type for ${smbFile.name}")
                        null
                    } else {
                        MediaFile(
                            name = smbFile.name,
                            path = buildFullSmbPath(connectionInfo, smbFile.path),
                            size = smbFile.size,
                            createdDate = smbFile.lastModified,
                            type = mediaType
                        )
                    }
                }
                is SmbResult.Error -> {
                    Timber.w("getFileByPath: ${fileResult.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "getFileByPath: Error resolving SMB file: $path")
            null
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
        credentialsId: String? = null,
        scanSubdirectories: Boolean = true,
        showHiddenFiles: Boolean = false
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        try {
            Timber.d("scanFolderChunked: START - path=$path, maxFiles=$maxFiles, scanSubdirectories=$scanSubdirectories, credentialsId=$credentialsId")
            
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                val msg = "Invalid SMB path format: $path"
                Timber.w(msg)
                throw IllegalArgumentException(msg)
            }

            Timber.d("scanFolderChunked: Parsed path - share=${connectionInfo.connectionInfo.shareName}, remotePath=${connectionInfo.remotePath}")

            val isAllFilesMode = supportedTypes.size >= 7
            val extensions = if (isAllFilesMode) null else MediaTypeUtils.buildExtensionsSet(supportedTypes)
            
            Timber.d("scanFolderChunked: Extensions=${if (extensions == null) "ALL (null)" else extensions.size}")

            // Use chunked scan method
            when (val result = smbClient.scanMediaFilesChunked(
                connectionInfo = connectionInfo.connectionInfo,
                remotePath = connectionInfo.remotePath,
                extensions = extensions,
                maxFiles = maxFiles,
                scanSubdirectories = scanSubdirectories
            )) {
                is SmbResult.Success -> {
                    Timber.d("scanFolderChunked: Got ${result.data.size} files from smbClient")
                    
                    // val isAllFilesMode = supportedTypes.size >= 7 // Already calculated above
                    val mediaFiles = result.data.mapNotNull { fileInfo ->
                        // Skip hidden files if not requested (files starting with ".")
                        if (!showHiddenFiles && fileInfo.name.startsWith(".")) {
                            return@mapNotNull null
                        }
                        
                        // Skip directories
                        if (fileInfo.isDirectory) {
                            return@mapNotNull null
                        }
                        
                        val mediaType = MediaTypeUtils.getMediaType(fileInfo.name) ?: if (isAllFilesMode) MediaType.TEXT else null
                        if (mediaType != null && supportedTypes.contains(mediaType)) {
                            if (sizeFilter != null && !MediaTypeUtils.isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) {
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
                is SmbResult.Error -> {
                    val ex = result.exception ?: Exception(result.message)
                    if (ex !is CancellationException) Timber.e("Error scanning SMB folder (chunked): ${result.message}")
                    throw ex
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SMB folder (chunked): $path")
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
        try {
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                val msg = "Invalid SMB path format: $path"
                Timber.w(msg)
                throw IllegalArgumentException(msg)
            }



            val isAllFilesMode = supportedTypes.size >= 7
            val extensions = if (isAllFilesMode) null else MediaTypeUtils.buildExtensionsSet(supportedTypes)

            // Use optimized paged scan with native offset/limit support
            when (val result = smbClient.scanMediaFilesPaged(
                connectionInfo = connectionInfo.connectionInfo,
                remotePath = connectionInfo.remotePath,
                extensions = extensions,
                offset = offset,
                limit = limit,
                scanSubdirectories = scanSubdirectories
            )) {
                is SmbResult.Success -> {
                    // Convert to MediaFile list with optional size filtering
                    // val isAllFilesMode = supportedTypes.size >= 7 // Already calculated above
                    val mediaFiles = result.data.mapNotNull { fileInfo ->
                        // Skip hidden files if not requested (files starting with ".")
                        if (!showHiddenFiles && fileInfo.name.startsWith(".")) {
                            return@mapNotNull null
                        }
                        
                        // Skip directories
                        if (fileInfo.isDirectory) {
                            return@mapNotNull null
                        }
                        
                        val mediaType = MediaTypeUtils.getMediaType(fileInfo.name) ?: if (isAllFilesMode) MediaType.TEXT else null
                        if (mediaType != null && supportedTypes.contains(mediaType)) {
                            if (sizeFilter != null && !MediaTypeUtils.isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) {
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
                is SmbResult.Error -> {
                    val ex = result.exception ?: Exception(result.message)
                    if (ex !is CancellationException) Timber.e("Error scanning SMB folder (paged): ${result.message}")
                    throw ex
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error scanning SMB folder (paged): $path")
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
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                val msg = "Invalid SMB path format for count: $path"
                Timber.w(msg)
                throw IllegalArgumentException(msg)
            }

            // Get all supported extensions
            val isAllFilesMode = supportedTypes.size >= 7
            val extensions = if (isAllFilesMode) null else MediaTypeUtils.buildExtensionsSet(supportedTypes)

            // Use optimized count method (no SmbFileInfo objects created)
            when (val result = smbClient.countMediaFiles(
                connectionInfo = connectionInfo.connectionInfo,
                remotePath = connectionInfo.remotePath,
                extensions = extensions
            )) {
                is SmbResult.Success -> {
                    // Note: sizeFilter is ignored for counting (would require fetching size for each file)
                    result.data
                }
                is SmbResult.Error -> {
                    Timber.e("Error counting SMB files: ${result.message}")
                    throw result.exception ?: Exception(result.message)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error counting SMB files in: $path")
            throw e
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
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                Timber.w("Invalid SMB path format: $path")
                return@withContext emptyList()
            }

            val isAllFilesMode = supportedTypes.size >= 7
            val extensions = if (isAllFilesMode) null else MediaTypeUtils.buildExtensionsSet(supportedTypes)

            // List immediate children (non-recursive)
            when (val result = smbClient.scanMediaFiles(
                connectionInfo = connectionInfo.connectionInfo,
                remotePath = connectionInfo.remotePath,
                extensions = extensions,
                scanSubdirectories = false, // Only immediate children
                progressCallback = null,
                includeDirectories = true
            )) {
                is SmbResult.Success -> {
                    result.data.mapNotNull { fileInfo ->
                        // Skip hidden files if not requested
                        if (!showHiddenFiles && fileInfo.name.startsWith(".")) {
                            return@mapNotNull null
                        }

                        // Skip .trash directories
                        if (fileInfo.isDirectory && fileInfo.name.startsWith(".trash")) {
                            return@mapNotNull null
                        }

                        if (fileInfo.isDirectory) {
                            // For directories, count immediate children
                            val childCount = smbClient.scanMediaFiles(
                                connectionInfo = connectionInfo.connectionInfo,
                                remotePath = if (connectionInfo.remotePath.isEmpty()) fileInfo.name else "${connectionInfo.remotePath}/${fileInfo.name}",
                                extensions = null,
                                scanSubdirectories = false,
                                progressCallback = null
                            ).let { scanResult ->
                                when (scanResult) {
                                    is SmbResult.Success -> scanResult.data.size
                                    is SmbResult.Error -> 0
                                }
                            }

                            MediaFile(
                                name = fileInfo.name,
                                path = buildFullSmbPath(connectionInfo, fileInfo.path),
                                size = 0L,
                                createdDate = fileInfo.lastModified,
                                type = MediaType.IMAGE, // Placeholder for folders
                                isDirectory = true,
                                childCount = childCount
                            )
                        } else {
                            // Regular file
                            val mediaType = MediaTypeUtils.getMediaType(fileInfo.name) ?: if (isAllFilesMode) MediaType.TEXT else null
                            if (mediaType != null && supportedTypes.contains(mediaType)) {
                                if (sizeFilter == null || MediaTypeUtils.isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) {
                                    MediaFile(
                                        name = fileInfo.name,
                                        path = buildFullSmbPath(connectionInfo, fileInfo.path),
                                        size = fileInfo.size,
                                        createdDate = fileInfo.lastModified,
                                        type = mediaType,
                                        isDirectory = false
                                    )
                                } else null
                            } else null
                        }
                    }.sortedWith(
                        // Sort: folders first, then by name
                        compareBy<MediaFile> { !it.isDirectory }
                            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
                    )
                }
                is SmbResult.Error -> {
                    Timber.e("Error listing SMB directory contents: ${result.message}")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error listing SMB directory contents: $path")
            emptyList()
        }
    }
    override suspend fun isWritable(path: String, credentialsId: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val pathInfo = parseSmbPath(path, credentialsId) ?: return@withContext false

            // Test actual write permission by creating a test file
            when (val result = smbClient.checkWritePermission(pathInfo.connectionInfo, pathInfo.remotePath)) {
                is SmbResult.Success -> result.data
                is SmbResult.Error -> {
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
            // Parse path using utility
            val pathInfo = SmbPathUtils.parseSmbPath(path) ?: return null
            val server = pathInfo.connectionInfo.server
            val share = pathInfo.connectionInfo.shareName
            val port = pathInfo.connectionInfo.port
            val remotePath = pathInfo.remotePath
            
            // Try to get credentials from database using credentialsId first
            val credentials = if (credentialsId != null) {
                credentialsRepository.getByCredentialId(credentialsId)
            } else {
                // Fallback to old behavior for backward compatibility
                credentialsRepository.getByServerAndShare(server, share)
            }
            
            SmbConnectionInfoWithPath(
                connectionInfo = SmbConnectionInfo(
                    server = server,
                    shareName = share,
                    username = credentials?.username ?: "",
                    password = credentials?.password ?: "",
                    domain = credentials?.domain ?: "",
                    port = port
                ),
                remotePath = remotePath
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing SMB path: $path")
            null
        }
    }

    private fun buildFullSmbPath(connectionInfo: SmbConnectionInfoWithPath, filePath: String): String {
        return "smb://${connectionInfo.connectionInfo.server}:${connectionInfo.connectionInfo.port}/${connectionInfo.connectionInfo.shareName}/$filePath"
    }

    private suspend fun extractExifMetadata(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): ExifMetadata? {
        exifCache.get(remotePath)?.let { return it }

        return try {
            when (val result = smbClient.readPartialFile(connectionInfo, remotePath, 0L, 64 * 1024)) {
                is SmbResult.Success -> {
                    val bytes = result.data
                    if (bytes.isEmpty()) return null

                    val exif = ExifInterface(ByteArrayInputStream(bytes))
                    val width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                        ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0).takeIf { it > 0 }
                    val height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                        ?: exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0).takeIf { it > 0 }
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
                        .takeIf { it != ExifInterface.ORIENTATION_UNDEFINED }

                    val dateTimeMillis = parseExifDateTimeMillis(
                        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                    )

                    val latLong = exif.latLong
                    val metadata = ExifMetadata(
                        width = width,
                        height = height,
                        orientation = orientation,
                        dateTimeMillis = dateTimeMillis,
                        latitude = latLong?.getOrNull(0),
                        longitude = latLong?.getOrNull(1)
                    )

                    if (
                        metadata.width != null || metadata.height != null || metadata.orientation != null ||
                        metadata.dateTimeMillis != null || metadata.latitude != null || metadata.longitude != null
                    ) {
                        exifCache.put(remotePath, metadata)
                        metadata
                    } else {
                        null
                    }
                }
                is SmbResult.Error -> {
                    Timber.v("SMB EXIF extraction skipped for $remotePath: ${result.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.v(e, "SMB EXIF extraction failed for $remotePath")
            null
        }
    }

    private suspend fun extractVideoMetadata(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): VideoMetadata? {
        videoMetadataCache.get(remotePath)?.let { return it }

        var tempFile: File? = null
        return try {
            when (val result = smbClient.readPartialFile(connectionInfo, remotePath, 0L, 1024 * 1024)) {
                is SmbResult.Success -> {
                    val bytes = result.data
                    if (bytes.isEmpty()) return null

                    tempFile = File.createTempFile("smb_video_header_", ".tmp")
                    tempFile.writeBytes(bytes)

                    val retriever = MediaMetadataRetriever()
                    val metadata = try {
                        retriever.setDataSource(tempFile.absolutePath)
                        VideoMetadata(
                            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                            codec = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull(),
                            frameRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull(),
                            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
                        )
                    } finally {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            retriever.close()
                        } else {
                            retriever.release()
                        }
                    }

                    if (
                        metadata.duration != null || metadata.width != null || metadata.height != null ||
                        metadata.codec != null || metadata.bitrate != null || metadata.frameRate != null || metadata.rotation != null
                    ) {
                        videoMetadataCache.put(remotePath, metadata)
                        metadata
                    } else {
                        null
                    }
                }
                is SmbResult.Error -> {
                    Timber.v("SMB video metadata extraction skipped for $remotePath: ${result.message}")
                    null
                }
            }
        } catch (e: Exception) {
            _metadataErrorCount.incrementAndGet()
            Timber.w(e, "SMB video metadata extraction failed for $remotePath")
            null
        } finally {
            runCatching { tempFile?.delete() }
        }
    }

    private fun parseExifDateTimeMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null

        val patterns = listOf(
            "yyyy:MM:dd HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        )

        patterns.forEach { pattern ->
            runCatching {
                val formatter = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
                formatter.parse(value)?.time
            }.getOrNull()?.let { return it }
        }

        return null
    }

    private data class SmbConnectionInfoWithPath(
        val connectionInfo: SmbConnectionInfo,
        val remotePath: String
    )
}
