package com.sza.fastmediasorter.data.network.glide

import com.bumptech.glide.Priority
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.model.ModelLoader
import com.bumptech.glide.load.model.ModelLoaderFactory
import com.bumptech.glide.load.model.MultiModelLoaderFactory
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32

/** Glide ModelLoader for loading images from network paths (SMB/SFTP/FTP). */
class NetworkFileModelLoader(
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : ModelLoader<NetworkFileData, InputStream> {
    
    override fun buildLoadData(model: NetworkFileData, width: Int, height: Int, options: Options): ModelLoader.LoadData<InputStream>? =
        ModelLoader.LoadData(model, NetworkFileDataFetcher(model, smbClient, sftpClient, ftpClient, credentialsRepository))
    
    override fun handles(model: NetworkFileData): Boolean {
        // Skip PDF/EPUB (dedicated loaders) and video (dedicated decoder - avoids HEIF fallback errors)
        val extension = model.path.substringAfterLast('.', "").lowercase()
        val isVideo = extension in VIDEO_EXTENSIONS
        if (isVideo) Timber.d("NetworkFileModelLoader.handles: REJECTED video file ${model.path.substringAfterLast('/')}")
        return (model.path.startsWith("smb://", ignoreCase = true) ||
            model.path.startsWith("sftp://", ignoreCase = true) ||
            model.path.startsWith("ftp://", ignoreCase = true)) &&
            !model.path.endsWith(".pdf", ignoreCase = true) &&
            !model.path.endsWith(".epub", ignoreCase = true) && !isVideo
    }
    
    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mov", "avi", "mkv", "webm", "3gp", "flv", "wmv", "m4v", "mpg", "mpeg",
            // DVD / transport-stream formats - MPEG2 PS/TS headers are not valid image data
            "vob", "ts", "m2ts", "mts", "m2t", "ifo", "bup"
        )
    }
}

/** DataFetcher with interrupt protection and fast-fail for corrupt video files. */
class NetworkFileDataFetcher(
    private val data: NetworkFileData,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : DataFetcher<InputStream> {
    
    companion object {
        private const val LOCAL_THUMBNAIL_TIMEOUT_MS = 20_000L      // Local storage
        private const val SMB_THUMBNAIL_TIMEOUT_MS = 30_000L        // SMB shares
        private const val REMOTE_THUMBNAIL_TIMEOUT_MS = 30_000L     // FTP/SFTP (reduced from 40s)
        
        private const val LOCAL_FULL_IMAGE_TIMEOUT_MS = 60_000L     // Local storage full image
        private const val SMB_FULL_IMAGE_TIMEOUT_MS = 60_000L       // SMB full image (reduced from 90s)
        private const val REMOTE_FULL_IMAGE_TIMEOUT_MS = 90_000L    // FTP/SFTP full image (reduced from 120s)
        
        private const val THUMBNAIL_MAX_BYTES = 5120 * 1024L // 5MB limit for thumbnails
        
        private const val LOSSLESS_THUMBNAIL_MAX_BYTES = 25 * 1024 * 1024L // 25MB (lossless formats sensitive to truncation)
        private val LOSSLESS_EXTENSIONS = setOf("png", "webp", "gif", "bmp")
        
        // LinkedHashMap FIFO eviction; PUBLIC: shared with NetworkVideoFrameDecoder
        private val failedVideos = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, Boolean>(5000, 0.75f, false) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > MAX_FAILED_CACHE
                }
            }
        )
        private const val MAX_FAILED_CACHE = 5000

        // S0060: Transient failures (not persisted) - SMB race/timeout-during-playback; expire via TTL or playback-stop.
        private val transientFailedVideos = java.util.concurrent.ConcurrentHashMap<String, Long>() // path → timestampMs
        private const val TRANSIENT_TTL_MS = 120_000L // safety net: 2 minutes max

        @Volatile private var persistenceInitialized = false

        private fun ensurePersistenceLoaded() {
            if (persistenceInitialized) return
            synchronized(failedVideos) {
                if (persistenceInitialized) return
                try {
                    val persisted = VideoExtractionFailurePersistence.loadAll()
                    for ((path, _) in persisted) {
                        if (!failedVideos.containsKey(path)) {
                            failedVideos[path] = true
                        }
                    }
                    Timber.d("Loaded ${persisted.size} persisted failure entries into in-memory cache")
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load persisted failure cache - continuing with empty in-memory cache")
                }
                persistenceInitialized = true
            }
        }

        /** Check if video file is in failed cache (permanent OR non-expired transient). PUBLIC API. */
        fun isVideoFailed(path: String): Boolean {
            ensurePersistenceLoaded()
            if (failedVideos.containsKey(path)) return true
            // Also check transient failures (S0060)
            val ts = transientFailedVideos[path] ?: return false
            if (System.currentTimeMillis() - ts > TRANSIENT_TTL_MS) {
                transientFailedVideos.remove(path)
                return false
            }
            return true
        }

        /** True only if path is in the permanent failed cache. S0060. */
        fun isVideoPermanentlyFailed(path: String): Boolean {
            ensurePersistenceLoaded()
            return failedVideos.containsKey(path)
        }

        /** Mark video as transiently failed (SMB race/timeout). Not persisted; clears on stop or TTL. S0060. */
        fun markVideoAsTransientlyFailed(path: String) {
            transientFailedVideos[path] = System.currentTimeMillis()
            Timber.d("Added to TRANSIENT failed cache: ${path.substringAfterLast('/')}")
        }

        /** Remove path from transient failed cache (called when playback stops). S0060. */
        fun clearTransientFailure(path: String) {
            transientFailedVideos.remove(path)
        }

        /** Remove all transient failures for a network resource (called on playback stop). S0066. */
        fun clearTransientFailuresForResource(resourceKey: String) {
            val cleared = transientFailedVideos.keys.filter { pathBelongsToResource(it, resourceKey) }
            cleared.forEach { transientFailedVideos.remove(it) }
            if (cleared.isNotEmpty()) Timber.i("[scope=thumbnail resource=$resourceKey] Cleared ${cleared.size} transient failures")
        }

        @Deprecated(
            "S0066 - use clearTransientFailuresForResource(resourceKey)",
            ReplaceWith("clearTransientFailuresForResource(resourceKey)")
        )
        fun clearTransientFailuresForHost(smbHost: String) {
            // Caller passed only host - try the two well-known SMB ports.
            clearTransientFailuresForResource("smb://$smbHost:445")
            clearTransientFailuresForResource("smb://$smbHost:139")
        }

        /** Mark video file as permanently failed. PUBLIC API for NetworkVideoFrameDecoder. */
        fun markVideoAsFailed(path: String) {
            failedVideos[path] = true
            Timber.d("Added to failed video cache (${failedVideos.size}/$MAX_FAILED_CACHE): ${path.substringAfterLast('/')}")
            try {
                VideoExtractionFailurePersistence.persistFailure(path)
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist video failure entry for: ${path.substringAfterLast('/')}")
            }
        }

        /** Clear all failed cache entries (in-memory + persistent). PUBLIC API for Settings. */
        fun clearFailedVideoCache() {
            synchronized(failedVideos) {
                val count = failedVideos.size
                failedVideos.clear()
                persistenceInitialized = false
                Timber.i("Cleared failed video cache: $count entries removed")
            }
            try {
                VideoExtractionFailurePersistence.clearAll()
            } catch (e: Exception) {
                Timber.w(e, "Failed to clear persisted failure cache")
            }
        }

        /** Check if thumbnail is marked as failed. PUBLIC API for MediaFileAdapter. */
        fun isThumbnailFailed(path: String): Boolean {
            ensurePersistenceLoaded()
            return failedVideos.containsKey(path)
        }

        /** Mark thumbnail as failed. PUBLIC API for MediaFileAdapter and decoders. */
        fun markThumbnailAsFailed(path: String) {
            failedVideos[path] = true
            Timber.d("Added to failed thumbnail cache (${failedVideos.size}/$MAX_FAILED_CACHE): ${path.substringAfterLast('/')}")
            try {
                VideoExtractionFailurePersistence.persistFailure(path)
            } catch (e: Exception) {
                Timber.w(e, "Failed to persist thumbnail failure entry for: ${path.substringAfterLast('/')}")
            }
        }
    }
    
    @Volatile
    private var isCancelled = false
    private var loadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        val fileName = data.path.substringAfterLast('/')
        Timber.v("NetworkFileDataFetcher.loadData (CACHE MISS): $fileName, priority=$priority, loadFullImage=${data.loadFullImage}")
        
        if (isCancelled) {
            Timber.v("NetworkFileDataFetcher.loadData CANCELLED before start: $fileName")
            callback.onLoadFailed(Exception("Request cancelled"))
            return
        }

        loadJob = scope.launch {
            try {
                Timber.v("NetworkFileDataFetcher: Starting direct byte fetch for $fileName")
                val maxBytes = determineMaxBytes(fileName)
                
                val bytes = when {
                    data.path.startsWith("smb://", ignoreCase = true) -> fetchBytesFromSmb(maxBytes)
                    data.path.startsWith("sftp://", ignoreCase = true) -> fetchBytesFromSftp(maxBytes)
                    data.path.startsWith("ftp://", ignoreCase = true) -> fetchBytesFromFtp(maxBytes)
                    else -> null
                }

                if (isCancelled) {
                    Timber.v("NetworkFileDataFetcher: Cancelled after fetch for $fileName")
                    callback.onLoadFailed(Exception("Request cancelled"))
                    return@launch
                }

                if (bytes == null) {
                    Timber.e("NetworkFileDataFetcher: Failed to fetch $fileName - bytes is null")
                    callback.onLoadFailed(Exception("Failed to load network file: ${data.path}"))
                    return@launch
                }

                Timber.v("NetworkFileDataFetcher: Fetch complete for $fileName, read ${bytes.size / 1024}KB")

                var finalBytes = bytes
                if (!isValidImageData(finalBytes)) {
                    if (data.path.startsWith("ftp://", ignoreCase = true)) {
                        val retryMaxBytes = if (!data.loadFullImage && isJpegFile(fileName)) {
                            Timber.w("NetworkFileDataFetcher: JPEG thumbnail failed validation, retrying with full-size read for $fileName")
                            Long.MAX_VALUE
                        } else {
                            Timber.w("NetworkFileDataFetcher: Image failed validation, retrying FTP read once for $fileName")
                            maxBytes
                        }

                        val retryBytes = fetchBytesFromFtp(retryMaxBytes)
                        if (retryBytes != null) {
                            Timber.v("NetworkFileDataFetcher: FTP retry fetch complete for $fileName, read ${retryBytes.size / 1024}KB")
                            finalBytes = retryBytes
                        } else {
                            Timber.w("NetworkFileDataFetcher: FTP retry returned null for $fileName")
                        }
                    } else if (data.path.startsWith("smb://", ignoreCase = true) &&
                        !data.loadFullImage && isJpegFile(fileName)) {
                        Timber.w("NetworkFileDataFetcher: JPEG thumbnail failed validation on SMB, retrying with full-size read for $fileName")
                        val retryBytes = fetchBytesFromSmb(Long.MAX_VALUE)
                        if (retryBytes != null) {
                            Timber.v("NetworkFileDataFetcher: SMB retry fetch complete for $fileName, read ${retryBytes.size / 1024}KB")
                            finalBytes = retryBytes
                        } else {
                            Timber.w("NetworkFileDataFetcher: SMB retry returned null for $fileName")
                        }
                    }
                }

                if (!isValidImageData(finalBytes)) {
                    Timber.w("NetworkFileDataFetcher: Invalid/corrupted image data for $fileName (size=${finalBytes.size})")
                    callback.onLoadFailed(Exception("Corrupted image data: ${data.path}"))
                    return@launch
                }
                
                callback.onDataReady(ByteArrayInputStream(finalBytes))
            } catch (e: Exception) {
                when {
                    e is CancellationException || e.cause is CancellationException -> {
                        Timber.d("NetworkFileDataFetcher: Loading cancelled for $fileName (video priority throttle)")
                        callback.onLoadFailed(e)
                    }
                    !isCancelled -> {
                        Timber.e(e, "NetworkFileDataFetcher: Exception while loading $fileName")
                        callback.onLoadFailed(e)
                    }
                    else -> {
                        Timber.d("NetworkFileDataFetcher: Exception after cancel for $fileName")
                        callback.onLoadFailed(Exception("Request cancelled"))
                    }
                }
            }
        }
    }
    
    private suspend fun fetchBytesFromSmb(maxBytes: Long): ByteArray? {
        val fileName = data.path.substringAfterLast('/')
        Timber.v("fetchBytesFromSmb START: $fileName, maxBytes=${maxBytes / 1024}KB")
        val uri = data.path.replaceFirst(Regex("^smb://", RegexOption.IGNORE_CASE), "")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return null

        val serverPort = parts[0]
        val pathParts = if (parts.size > 1) parts[1] else ""
        val server = serverPort.substringBefore(':')
        val port = serverPort.substringAfter(':', "").toIntOrNull() ?: 445
        val resourceKey = "smb://${server}:${port}"
        
        return ConnectionThrottleManager.withThrottle(
            protocol = ConnectionThrottleManager.ProtocolLimits.SMB,
            resourceKey = resourceKey,
            highPriority = data.highPriority
        ) {
            val credentials = if (data.credentialsId != null) {
                credentialsRepository.getByCredentialId(data.credentialsId)
            } else {
                credentialsRepository.getByTypeServerAndPort("SMB", server, port)
            }
            
            if (credentials == null) {
                Timber.e("fetchBytesFromSmb: No credentials found for server=$server, port=$port, credentialsId=${data.credentialsId}")
                return@withThrottle null
            }

            val shareAndPath = pathParts.split("/", limit = 2)
            val shareName = if (shareAndPath.isNotEmpty()) shareAndPath[0] else (credentials.shareName ?: "")
            val remotePath = if (shareAndPath.size > 1) shareAndPath[1] else ""

            if (shareName.isEmpty()) return@withThrottle null

            val connectionInfo = SmbConnectionInfo(
                server = server,
                port = port,
                shareName = shareName,
                username = credentials.username,
                password = credentials.password,
                domain = credentials.domain
            )

            val timeoutMs = if (data.loadFullImage) SMB_FULL_IMAGE_TIMEOUT_MS else SMB_THUMBNAIL_TIMEOUT_MS
            
            try {
                val result = kotlinx.coroutines.withTimeout(timeoutMs) {
                    smbClient.readFileBytes(connectionInfo, remotePath, maxBytes)
                }
                
                when (result) {
                    is SmbResult.Success -> {
                        Timber.v("fetchBytesFromSmb SUCCESS: $fileName, ${result.data.size / 1024}KB")
                        result.data
                    }
                    is SmbResult.Error -> {
                        Timber.w("fetchBytesFromSmb ERROR: $fileName - ${result.message}")
                        null
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // S0055-B: normal scroll/recycle cancellation - re-throw to propagate coroutine cancellation
                Timber.v("fetchBytesFromSmb CANCELLED: $fileName")
                throw e
            } catch (e: Exception) {
                Timber.w("fetchBytesFromSmb TIMEOUT: $fileName - ${e.message}")
                null
            }
        }
    }
    
    private suspend fun fetchBytesFromSftp(maxBytes: Long): ByteArray? {
        val fileName = data.path.substringAfterLast('/')
        Timber.v("fetchBytesFromSftp START: $fileName, maxBytes=${maxBytes / 1024}KB")
        val uri = data.path.replaceFirst(Regex("^sftp://", RegexOption.IGNORE_CASE), "")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return null

        val serverPort = parts[0]
        val remotePath = if (parts.size > 1) "/${parts[1]}" else "/"
        val server = serverPort.substringBefore(':')
        val port = serverPort.substringAfter(':', "").toIntOrNull() ?: 22
        val resourceKey = "sftp://${server}:${port}"
        
        return ConnectionThrottleManager.withThrottle(
            protocol = ConnectionThrottleManager.ProtocolLimits.SFTP,
            resourceKey = resourceKey,
            highPriority = data.highPriority
        ) {
            val credentials = if (data.credentialsId != null) {
                credentialsRepository.getByCredentialId(data.credentialsId)
            } else {
                credentialsRepository.getByTypeServerAndPort("SFTP", server, port)
            }
            
            if (credentials == null) {
                Timber.e("fetchBytesFromSftp: No credentials found for server=$server, port=$port, credentialsId=${data.credentialsId}")
                return@withThrottle null
            }

            val connectionInfo = SftpClient.SftpConnectionInfo(
                host = server,
                port = port,
                username = credentials.username,
                password = credentials.password,
                privateKey = credentials.sshPrivateKey
            )

            val timeoutMs = if (data.loadFullImage) REMOTE_FULL_IMAGE_TIMEOUT_MS else REMOTE_THUMBNAIL_TIMEOUT_MS
            try {
                val result = kotlinx.coroutines.withTimeout(timeoutMs) {
                    sftpClient.readFileBytes(connectionInfo, remotePath, maxBytes)
                }
                result.getOrNull()?.also {
                    Timber.v("fetchBytesFromSftp SUCCESS: $fileName, ${it.size / 1024}KB")
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w("fetchBytesFromSftp TIMEOUT: $fileName - ${e.message}")
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "fetchBytesFromSftp FAILED: $fileName")
                null
            }
        }
    }
    
    private suspend fun fetchBytesFromFtp(maxBytes: Long): ByteArray? {
        val fileName = data.path.substringAfterLast('/')
        Timber.v("fetchBytesFromFtp START: $fileName, maxBytes=${maxBytes / 1024}KB")
        val uri = data.path.replaceFirst(Regex("^ftp://", RegexOption.IGNORE_CASE), "")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return null

        val serverPort = parts[0]
        val remotePath = if (parts.size > 1) "/${parts[1]}" else "/"
        val server = serverPort.substringBefore(':')
        val port = serverPort.substringAfter(':', "").toIntOrNull() ?: 21
        val resourceKey = "ftp://${server}:${port}"
        
        return ConnectionThrottleManager.withThrottle(
            protocol = ConnectionThrottleManager.ProtocolLimits.FTP,
            resourceKey = resourceKey,
            highPriority = data.highPriority
        ) {
            val credentials = if (data.credentialsId != null) {
                credentialsRepository.getByCredentialId(data.credentialsId)
            } else {
                credentialsRepository.getByTypeServerAndPort("FTP", server, port)
            }
            
            if (credentials == null) {
                Timber.e("fetchBytesFromFtp: No credentials found for server=$server, port=$port, credentialsId=${data.credentialsId}")
                return@withThrottle null
            }

            val timeoutMs = if (data.loadFullImage) REMOTE_FULL_IMAGE_TIMEOUT_MS else REMOTE_THUMBNAIL_TIMEOUT_MS
            try {
                val result = kotlinx.coroutines.withTimeout(timeoutMs) {
                    ftpClient.readFileBytesWithNewConnection(
                        host = server,
                        port = port,
                        username = credentials.username,
                        password = credentials.password,
                        remotePath = remotePath,
                        maxBytes = maxBytes
                    )
                }
                
                result.getOrNull()?.also {
                    Timber.v("fetchBytesFromFtp SUCCESS: $fileName, ${it.size / 1024}KB")
                }
            } catch (e: TimeoutCancellationException) {
                Timber.w("fetchBytesFromFtp TIMEOUT: $fileName - ${e.message}")
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "fetchBytesFromFtp FAILED: $fileName")
                null
            }
        }
    }

    /** Determine safe max bytes for current request (lossless formats use higher cap). */
    private fun determineMaxBytes(fileName: String): Long {
        if (data.loadFullImage) return Long.MAX_VALUE

        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension in LOSSLESS_EXTENSIONS) {
            if (data.size > 0L && data.size <= LOSSLESS_THUMBNAIL_MAX_BYTES) {
                return data.size
            }
            return LOSSLESS_THUMBNAIL_MAX_BYTES
        }

        return THUMBNAIL_MAX_BYTES
    }

    private fun isJpegFile(fileName: String) =
        fileName.substringAfterLast('.', "").lowercase().let { it == "jpg" || it == "jpeg" }
    
    /** Validate image data by magic bytes to prevent Glide crashes on corrupted data. */
    private fun isValidImageData(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && // PNG: 89 50 4E 47
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return isValidPngData(bytes)
        }
        
        // Check JPEG signature: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            // Motion photos (Google/Samsung) append MP4 after JPEG EOI - FF D9 may be several MB from EOF.
            // Use 4 MB tail window to avoid false-positive EXIF thumbnail markers.
            if (bytes.size < 4) return false

            val searchWindow = 4 * 1024 * 1024 // 4 MB
            val startIndex = maxOf(2, bytes.size - searchWindow)
            for (index in (bytes.size - 1) downTo startIndex) {
                if (bytes[index - 1] == 0xFF.toByte() && bytes[index] == 0xD9.toByte()) {
                    val distanceFromEnd = bytes.size - index
                    if (distanceFromEnd > 64 * 1024) {
                        Timber.d("isValidImageData: JPEG EOI found ${distanceFromEnd / 1024}KB from end - likely motion photo (size=${bytes.size})")
                    }
                    return true
                }
            }

            Timber.w("isValidImageData: JPEG without EOI marker in 4MB tail window (size=${bytes.size})")
            return false
        }
        
        // Check WebP signature: RIFF ... WEBP
        if (bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && // "RI"
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() && // "FF"
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&  // "WE"
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()) { // "BP"
            val riffPayloadSize = readUInt32LittleEndian(bytes, 4)
            if (riffPayloadSize < 4) return false
            return riffPayloadSize + 8 <= bytes.size.toLong()
        }
        
        // Check GIF signature: GIF87a or GIF89a
        if (bytes.size >= 6 &&
            bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte()) {
            // GIF trailer must end with ';' (0x3B)
            return bytes[bytes.size - 1] == 0x3B.toByte()
        }
        
        // Check BMP signature: BM
        if (bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte()) {
            if (bytes.size >= 6 && readUInt32LittleEndian(bytes, 2) > bytes.size.toLong()) return false
            return true
        }
        
        Timber.w("isValidImageData: Unknown format - first 8 bytes: ${bytes.take(8).joinToString(" ") { "%02X".format(it) }}")
        return false
    }

    private fun isValidPngData(bytes: ByteArray): Boolean {
        if (bytes.size < 33) return false
        if (bytes[0] != 0x89.toByte() || bytes[1] != 0x50.toByte() || bytes[2] != 0x4E.toByte() || bytes[3] != 0x47.toByte() ||
            bytes[4] != 0x0D.toByte() || bytes[5] != 0x0A.toByte() || bytes[6] != 0x1A.toByte() || bytes[7] != 0x0A.toByte()) {
            return false
        }

        var offset = 8
        var seenIhdr = false
        val crc32 = CRC32()

        while (offset + 12 <= bytes.size) {
            val length = readIntBigEndian(bytes, offset)
            if (length < 0) return false

            val typeOffset = offset + 4
            val dataOffset = offset + 8
            val crcOffset = dataOffset + length
            if (crcOffset + 4 > bytes.size) return false

            if (!seenIhdr) {
                if (!chunkTypeEquals(bytes, typeOffset, 'I', 'H', 'D', 'R')) return false
                seenIhdr = true
            }

            crc32.reset()
            crc32.update(bytes, typeOffset, 4 + length)
            val actualCrc = crc32.value and 0xFFFFFFFFL
            val expectedCrc = readIntBigEndian(bytes, crcOffset).toLong() and 0xFFFFFFFFL
            if (actualCrc != expectedCrc) return false

            val isIend = chunkTypeEquals(bytes, typeOffset, 'I', 'E', 'N', 'D')
            if (isIend) {
                if (length != 0) return false
                return crcOffset + 4 == bytes.size
            }

            offset = crcOffset + 4
        }

        return false
    }

    private fun chunkTypeEquals(bytes: ByteArray, offset: Int, c1: Char, c2: Char, c3: Char, c4: Char): Boolean {
        return offset + 3 < bytes.size &&
            bytes[offset] == c1.code.toByte() &&
            bytes[offset + 1] == c2.code.toByte() &&
            bytes[offset + 2] == c3.code.toByte() &&
            bytes[offset + 3] == c4.code.toByte()
    }

    private fun readIntBigEndian(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readUInt32LittleEndian(bytes: ByteArray, offset: Int): Long {
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
    
    override fun cleanup() {
        // ByteArrayInputStream doesn't require cleanup
    }
    
    override fun cancel() {
        // S0055-A: debug stack trace removed - cancel() is called on every RecyclerView scroll recycle
        isCancelled = true
        loadJob?.cancel()
    }
    
    override fun getDataClass(): Class<InputStream> = InputStream::class.java
    
    override fun getDataSource(): DataSource = DataSource.REMOTE
}

/** Factory for NetworkFileModelLoader - lazily resolves Hilt dependencies on first build. */
class NetworkFileModelLoaderFactory : ModelLoaderFactory<NetworkFileData, InputStream> {

    private var smbClient: SmbClient? = null
    private var sftpClient: SftpClient? = null
    private var ftpClient: FtpClient? = null
    private var credentialsRepository: NetworkCredentialsRepository? = null
    
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<NetworkFileData, InputStream> {
        val context = com.sza.fastmediasorter.FastMediaSorterApp.appContext
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context,
            NetworkFileModelLoaderEntryPoint::class.java
        )
        
        smbClient = entryPoint.smbClient()
        sftpClient = entryPoint.sftpClient()
        ftpClient = entryPoint.ftpClient()
        credentialsRepository = entryPoint.credentialsRepository()
        
        return NetworkFileModelLoader(
            smbClient!!,
            sftpClient!!,
            ftpClient!!,
            credentialsRepository!!
        )
    }
    
    override fun teardown() {} // No resources to release
}

/** Hilt EntryPoint for accessing dependencies in Glide module. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface NetworkFileModelLoaderEntryPoint {
    fun smbClient(): SmbClient
    fun sftpClient(): SftpClient
    fun ftpClient(): FtpClient
    fun credentialsRepository(): NetworkCredentialsRepository
    fun thumbnailCacheRepository(): com.sza.fastmediasorter.domain.repository.ThumbnailCacheRepository
    fun unifiedCache(): com.sza.fastmediasorter.core.cache.UnifiedFileCache
}
