package com.sza.fastmediasorter.data.network

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.sza.fastmediasorter.core.util.MediaFileIntegrity
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.common.MediaTypeUtils
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.transfer.trash.TrashFolderContract
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.usecase.MediaFilePage
import com.sza.fastmediasorter.domain.usecase.MediaScanner
import com.sza.fastmediasorter.domain.usecase.SizeFilter
import com.sza.fastmediasorter.utils.SmbPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** MediaScanner implementation for SMB network resources via SmbClient. */
@Singleton
class SmbMediaScanner @Inject constructor(
    private val smbClient: SmbClient,
    private val credentialsRepository: NetworkCredentialsRepository,
    @ApplicationContext private val context: Context
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

    /**
     * Per-file enrichment outcome (S0248 Phase 2/3). Wraps the optional metadata
     * payload with the lifecycle state that should be persisted in
     * `file_metadata_cache.metadataState`.
     */
    private sealed interface EnrichmentResult<out T> {
        /** Extraction succeeded within the per-file budget. */
        data class Complete<T>(val payload: T?) : EnrichmentResult<T>
        /**
         * Per-file timeout elapsed. Payload is whatever the extractor managed to
         * read before the budget cut it off (often null = listing-only fallback).
         */
        data class Partial<T>(val payload: T?) : EnrichmentResult<T>
        /** Extractor raised a hard error (not timeout). */
        data class Broken<T>(val payload: T?) : EnrichmentResult<T>
    }

    private val exifCache = LruCache<String, ExifMetadata>(100)
    private val videoMetadataCache = LruCache<String, VideoMetadata>(100)
    private val _metadataErrorCount = AtomicInteger(0)
    /** S0248 Phase 2: count of files that fell back due to per-file timeout in current scan. */
    private val _metadataTimeoutCount = AtomicInteger(0)

    companion object {
        // Extensions moved to MediaTypeUtils
        /** Skip per-file EXIF/video metadata extraction when folder exceeds this count */
        private const val METADATA_SKIP_THRESHOLD = 500

        /** S0248 Phase 2: per-file EXIF/ID3 budget. */
        private const val EXIF_TIMEOUT_MS = 1500L
        /** S0248 Phase 2: first-tier quick-probe budget for video (head 1 MiB → MediaMetadataRetriever). */
        private const val VIDEO_QUICK_TIMEOUT_MS = 500L
        /** S0248 Phase 2: second-tier escalation budget for slow-path video (moov-at-tail signature). */
        private const val VIDEO_SLOW_TIMEOUT_MS = 2000L
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
    
    /** Scan folder with progress callback support */
    suspend fun scanFolderWithProgress(
        path: String,
        supportedTypes: Set<MediaType>,
        sizeFilter: SizeFilter?,
        credentialsId: String?,
        scanSubdirectories: Boolean,
        showHiddenFiles: Boolean,
        progressCallback: com.sza.fastmediasorter.domain.usecase.ScanProgressCallback?
    ): List<MediaFile> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            throw LocalNetworkPermissionDeniedException()
        }
        try {
            // Parse path format: smb://server:port/share/path
            val connectionInfo = parseSmbPath(path, credentialsId) ?: run {
                val msg = "Invalid SMB path format: $path"
                Timber.w(msg)
                throw IllegalArgumentException(msg)
            }

            val isAllFilesMode = supportedTypes.size >= 7
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
                    // When all 7 media types are supported (allFiles mode), treat unknown files as TEXT
                    // Skip per-file metadata extraction for large folders to avoid
                    // tens of minutes of individual SMB reads (EXIF / video probes).
                    val skipMetadataExtraction = result.data.size > METADATA_SKIP_THRESHOLD
                    if (skipMetadataExtraction) {
                        Timber.d("Large folder (${result.data.size} files) - skipping per-file EXIF/video metadata extraction")
                    }
                    _metadataErrorCount.set(0)
                    _metadataTimeoutCount.set(0)
                    val files = result.data.mapNotNull { fileInfo ->
                        if (!showHiddenFiles && fileInfo.name.startsWith(".")) return@mapNotNull null
                        // Skip directories in flat scan (recursive or not) - we only want files
                        if (fileInfo.isDirectory) return@mapNotNull null
                        val mediaType = MediaTypeUtils.getMediaType(fileInfo.name) ?: if (isAllFilesMode) MediaType.TEXT else null
                        if (mediaType != null && supportedTypes.contains(mediaType)) {
                            if (sizeFilter != null && !MediaTypeUtils.isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) return@mapNotNull null

                            val exifResult = if (!skipMetadataExtraction && (mediaType == MediaType.IMAGE || mediaType == MediaType.GIF)) {
                                extractExifMetadata(connectionInfo.connectionInfo, fileInfo.path)
                            } else {
                                null
                            }

                            val videoResult = if (!skipMetadataExtraction && mediaType == MediaType.VIDEO) {
                                extractVideoMetadata(connectionInfo.connectionInfo, fileInfo.path)
                            } else {
                                null
                            }

                            // Keep the concrete payload type when folding success/timeout/error
                            // variants; star-projected casts widen the payload to Any? and break
                            // downstream field access under newer Kotlin compiler checks.
                            val exifMetadata = payloadOf(exifResult)
                            val videoMetadata = payloadOf(videoResult)

                            // S0248 Phase 3: derive per-file enrichment state from the strongest
                            // negative result (Broken trumps Partial, which trumps Complete).
                            val state = combineState(exifResult, videoResult)
                            val fullPath = buildFullSmbPath(connectionInfo, fileInfo.path)
                            val safeFields = MediaFileIntegrity.sanitize(
                                name = fileInfo.name,
                                path = fullPath,
                                type = mediaType,
                                sourcePath = fullPath,
                                metadataState = state
                            )

                            MediaFile(
                                name = safeFields.name,
                                path = safeFields.path,
                                size = fileInfo.size,
                                createdDate = fileInfo.lastModified,
                                type = safeFields.type,
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
                                videoRotation = videoMetadata?.rotation,
                                metadataState = safeFields.metadataState
                            )
                        } else null
                    }
                    val errCount = _metadataErrorCount.get()
                    if (errCount > 0) progressCallback?.onMetadataErrors(errCount)
                    // S0248 Phase 2: single aggregated metric line per scan. Skipped when zero
                    // to keep release-build noise low; never escalated to Timber.w (avoids S0169 regression).
                    val timeoutCount = _metadataTimeoutCount.get()
                    if (timeoutCount > 0) {
                        Timber.d(
                            "SmbMediaScanner: timeout-fallback count=%d totalFiles=%d path=%s",
                            timeoutCount,
                            files.size,
                            path
                        )
                    }
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

    /** Fetch single file metadata directly from SMB share without full folder rescan. */
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
                        val fullPath = buildFullSmbPath(connectionInfo, smbFile.path)
                        val safeFields = MediaFileIntegrity.sanitize(
                            name = smbFile.name,
                            path = fullPath,
                            type = mediaType,
                            sourcePath = fullPath
                        )
                        MediaFile(
                            name = safeFields.name,
                            path = safeFields.path,
                            size = smbFile.size,
                            createdDate = smbFile.lastModified,
                            type = safeFields.type,
                            metadataState = safeFields.metadataState
                        )
                    }
                }
                is SmbResult.Error -> {
                    Timber.w("getFileByPath: ${fileResult.message}")
                    null
                }
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "getFileByPath: Error resolving SMB file: $path")
            null
        }
    }

    /** Scan folder with limit (lazy load initial batch); returns first maxFiles files. */
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

            when (val result = smbClient.scanMediaFilesChunked(
                connectionInfo = connectionInfo.connectionInfo,
                remotePath = connectionInfo.remotePath,
                extensions = extensions,
                maxFiles = maxFiles,
                scanSubdirectories = scanSubdirectories
            )) {
                is SmbResult.Success -> {
                    Timber.d("scanFolderChunked: Got ${result.data.size} files from smbClient")
                    val mediaFiles = result.data.mapNotNull { fileInfo ->
                        if (!showHiddenFiles && fileInfo.name.startsWith(".")) return@mapNotNull null
                        if (fileInfo.isDirectory) return@mapNotNull null
                        val mediaType = MediaTypeUtils.getMediaType(fileInfo.name) ?: if (isAllFilesMode) MediaType.TEXT else null
                        if (mediaType == null || !supportedTypes.contains(mediaType)) return@mapNotNull null
                        if (sizeFilter != null && !MediaTypeUtils.isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) return@mapNotNull null
                        val fullPath = buildFullSmbPath(connectionInfo, fileInfo.path)
                        val safeFields = MediaFileIntegrity.sanitize(
                            name = fileInfo.name,
                            path = fullPath,
                            type = mediaType,
                            sourcePath = fullPath
                        )
                        MediaFile(
                            name = safeFields.name,
                            path = safeFields.path,
                            size = fileInfo.size,
                            createdDate = fileInfo.lastModified,
                            type = safeFields.type,
                            metadataState = safeFields.metadataState
                        )
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
                    val mediaFiles = result.data.mapNotNull { fileInfo ->
                        if (!showHiddenFiles && fileInfo.name.startsWith(".")) return@mapNotNull null
                        if (fileInfo.isDirectory) return@mapNotNull null
                        val mediaType = MediaTypeUtils.getMediaType(fileInfo.name) ?: if (isAllFilesMode) MediaType.TEXT else null
                        if (mediaType == null || !supportedTypes.contains(mediaType)) return@mapNotNull null
                        if (sizeFilter != null && !MediaTypeUtils.isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) return@mapNotNull null
                        val fullPath = buildFullSmbPath(connectionInfo, fileInfo.path)
                        val safeFields = MediaFileIntegrity.sanitize(
                            name = fileInfo.name,
                            path = fullPath,
                            type = mediaType,
                            sourcePath = fullPath
                        )
                        MediaFile(
                            name = safeFields.name,
                            path = safeFields.path,
                            size = fileInfo.size,
                            createdDate = fileInfo.lastModified,
                            type = safeFields.type,
                            metadataState = safeFields.metadataState
                        )
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
            e.rethrowIfCancellation()
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
                        if (!showHiddenFiles && fileInfo.name.startsWith(".")) return@mapNotNull null
                        if (fileInfo.isDirectory && TrashFolderContract.matchesTrashSegment(fileInfo.name)) return@mapNotNull null

                        if (fileInfo.isDirectory) {
                            val fullPath = buildFullSmbPath(connectionInfo, fileInfo.path)
                            val safeFields = MediaFileIntegrity.sanitize(
                                name = fileInfo.name,
                                path = fullPath,
                                type = MediaType.IMAGE,
                                sourcePath = fullPath
                            )
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
                                name = safeFields.name,
                                path = safeFields.path,
                                size = 0L,
                                createdDate = fileInfo.lastModified,
                                type = safeFields.type, // Placeholder for folders
                                isDirectory = true,
                                childCount = childCount,
                                metadataState = safeFields.metadataState
                            )
                        } else {
                            val mediaType = MediaTypeUtils.getMediaType(fileInfo.name) ?: if (isAllFilesMode) MediaType.TEXT else null
                            if (mediaType != null && supportedTypes.contains(mediaType)) {
                                if (sizeFilter == null || MediaTypeUtils.isFileSizeInRange(fileInfo.size, mediaType, sizeFilter)) {
                                    val fullPath = buildFullSmbPath(connectionInfo, fileInfo.path)
                                    val safeFields = MediaFileIntegrity.sanitize(
                                        name = fileInfo.name,
                                        path = fullPath,
                                        type = mediaType,
                                        sourcePath = fullPath
                                    )
                                    MediaFile(
                                        name = safeFields.name,
                                        path = safeFields.path,
                                        size = fileInfo.size,
                                        createdDate = fileInfo.lastModified,
                                        type = safeFields.type,
                                        isDirectory = false,
                                        metadataState = safeFields.metadataState
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
            e.rethrowIfCancellation()
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
            e.rethrowIfCancellation()
            Timber.e(e, "Error checking SMB write access for: $path")
            false
        }
    }

    /** Parse SMB path format: smb://server:port/share/path */
    private suspend fun parseSmbPath(path: String, credentialsId: String?): SmbConnectionInfoWithPath? {
        return try {
            val pathInfo = SmbPathUtils.parseSmbPath(path) ?: return null
            val server = pathInfo.connectionInfo.server
            val share = pathInfo.connectionInfo.shareName
            val port = pathInfo.connectionInfo.port
            val remotePath = pathInfo.remotePath
            
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
            e.rethrowIfCancellation()
            Timber.e(e, "Error parsing SMB path: $path")
            null
        }
    }

    private fun buildFullSmbPath(connectionInfo: SmbConnectionInfoWithPath, filePath: String): String {
        return "smb://${connectionInfo.connectionInfo.server}:${connectionInfo.connectionInfo.port}/${connectionInfo.connectionInfo.shareName}/$filePath"
    }

    /**
     * S0248 Phase 2: per-file EXIF extraction with hard [EXIF_TIMEOUT_MS] budget.
     * - timeout → [EnrichmentResult.Partial] (silent - only the per-scan aggregated metric logs)
     * - non-timeout exception → [EnrichmentResult.Broken]
     * - empty / no EXIF tags → [EnrichmentResult.Complete] with null payload
     *
     * S0248 Phase 5: throttled through [ConnectionThrottleManager.ProtocolLimits.SMB_HEADER]
     * (header-only tier, base concurrency 8) - independent of bulk-transfer SMB limit.
     */
    private suspend fun extractExifMetadata(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): EnrichmentResult<ExifMetadata> {
        exifCache.get(remotePath)?.let { return EnrichmentResult.Complete(it) }

        val resourceKey = "smb://${connectionInfo.server}:${connectionInfo.port}"
        val parsed: ExifMetadata? = try {
            ConnectionThrottleManager.withThrottle(
                protocol = ConnectionThrottleManager.ProtocolLimits.SMB_HEADER,
                resourceKey = resourceKey,
                highPriority = false
            ) {
                withTimeoutOrNull(EXIF_TIMEOUT_MS) {
                    when (val result = smbClient.readPartialFile(connectionInfo, remotePath, 0L, 64 * 1024)) {
                    is SmbResult.Success -> {
                        val bytes = result.data
                        if (bytes.isEmpty()) return@withTimeoutOrNull null

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
                }
            } ?: run {
                // withTimeoutOrNull returned null → budget elapsed before completion.
                _metadataTimeoutCount.incrementAndGet()
                return EnrichmentResult.Partial(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.v(e, "SMB EXIF extraction failed for $remotePath")
            return EnrichmentResult.Broken(null)
        }

        return EnrichmentResult.Complete(parsed)
    }

    /**
     * S0248 Phase 2: video metadata with two-tier timeout.
     *  - Tier 1 (`VIDEO_QUICK_TIMEOUT_MS`): probe the head 1 MiB. If the resulting
     *    metadata exposes both `duration` and a frame dimension, the moov atom is
     *    at the head - fast path complete.
     *  - Tier 2 (`VIDEO_SLOW_TIMEOUT_MS`): tier-1 returned nothing useful (slow-path
     *    signature - moov-at-tail or unreadable header). Retry the same probe with
     *    a longer budget; on a second timeout, fall back to PARTIAL.
     *
     * Hard exceptions (corrupt header, MediaMetadataRetriever crash) → BROKEN.
     */
    private suspend fun extractVideoMetadata(
        connectionInfo: SmbConnectionInfo,
        remotePath: String
    ): EnrichmentResult<VideoMetadata> {
        videoMetadataCache.get(remotePath)?.let { return EnrichmentResult.Complete(it) }

        // ── Tier 1: quick probe ──────────────────────────────────────────────
        val quickAttempt: EnrichmentResult<VideoMetadata> = runVideoProbe(connectionInfo, remotePath, VIDEO_QUICK_TIMEOUT_MS)
        if (quickAttempt is EnrichmentResult.Complete) {
            val payload = quickAttempt.payload
            // Fast path: moov-at-head - we have both duration and dimensions.
            val hasFastPathSignature = payload != null &&
                payload.duration != null &&
                (payload.width != null || payload.height != null)
            if (hasFastPathSignature) {
                return quickAttempt
            }
            // Slow-path signature detected - fall through to tier 2.
        } else if (quickAttempt is EnrichmentResult.Broken) {
            return quickAttempt
        }

        // ── Tier 2: escalate on slow-path signature ──────────────────────────
        return runVideoProbe(connectionInfo, remotePath, VIDEO_SLOW_TIMEOUT_MS)
    }

    /**
     * S0248 Phase 5: throttled through [ConnectionThrottleManager.ProtocolLimits.SMB_FULL_METADATA]
     * (RAM-bound tier, base concurrency 3) - protects against `MediaMetadataRetriever`
     * OOMs on devices with limited heap when multiple video probes overlap.
     */
    private suspend fun runVideoProbe(
        connectionInfo: SmbConnectionInfo,
        remotePath: String,
        timeoutMs: Long
    ): EnrichmentResult<VideoMetadata> {
        var tempFile: File? = null
        val resourceKey = "smb://${connectionInfo.server}:${connectionInfo.port}"
        val parsed: VideoMetadata? = try {
            ConnectionThrottleManager.withThrottle(
                protocol = ConnectionThrottleManager.ProtocolLimits.SMB_FULL_METADATA,
                resourceKey = resourceKey,
                highPriority = false
            ) {
                withTimeoutOrNull(timeoutMs) {
                    when (val result = smbClient.readPartialFile(connectionInfo, remotePath, 0L, 1024 * 1024)) {
                    is SmbResult.Success -> {
                        val bytes = result.data
                        if (bytes.isEmpty()) return@withTimeoutOrNull null

                        tempFile = File.createTempFile("smb_video_header_", ".tmp")
                        tempFile?.writeBytes(bytes)

                        val retriever = MediaMetadataRetriever()
                        val metadata = try {
                            retriever.setDataSource(tempFile?.absolutePath)
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
                }
            } ?: run {
                _metadataTimeoutCount.incrementAndGet()
                return EnrichmentResult.Partial(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            _metadataTimeoutCount.incrementAndGet()
            return EnrichmentResult.Partial(null)
        } catch (e: Exception) {
            _metadataErrorCount.incrementAndGet()
            Timber.v(e, "SMB video metadata extraction failed for $remotePath")
            return EnrichmentResult.Broken(null)
        } finally {
            runCatching { tempFile?.delete() }
        }

        return EnrichmentResult.Complete(parsed)
    }

    /**
     * S0248 Phase 3: derive overall enrichment state for a file from the per-extractor
     * results. Severity ladder: Broken > Partial > Complete. If both extractors are
     * skipped (large folder mode), the file is treated as COMPLETE because no
     * extraction was attempted - the row is listing-only by design.
     */
    private fun combineState(
        exifResult: EnrichmentResult<*>?,
        videoResult: EnrichmentResult<*>?
    ): com.sza.fastmediasorter.domain.model.MetadataState {
        val outcomes = listOfNotNull(exifResult, videoResult)
        if (outcomes.isEmpty()) return com.sza.fastmediasorter.domain.model.MetadataState.COMPLETE
        if (outcomes.any { it is EnrichmentResult.Broken<*> }) return com.sza.fastmediasorter.domain.model.MetadataState.BROKEN
        if (outcomes.any { it is EnrichmentResult.Partial<*> }) return com.sza.fastmediasorter.domain.model.MetadataState.PARTIAL
        return com.sza.fastmediasorter.domain.model.MetadataState.COMPLETE
    }

    private fun <T> payloadOf(result: EnrichmentResult<T>?): T? = when (result) {
        is EnrichmentResult.Complete -> result.payload
        is EnrichmentResult.Partial -> result.payload
        is EnrichmentResult.Broken -> result.payload
        null -> null
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
