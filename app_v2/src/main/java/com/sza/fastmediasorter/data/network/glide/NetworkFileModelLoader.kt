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

/**
 * Glide ModelLoader for loading images from network paths (SMB/SFTP/FTP).
 * 
 * Uses direct byte reading with maxBytes limit for efficient thumbnail loading.
 * Full images download completely for Glide caching.
 */
class NetworkFileModelLoader(
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : ModelLoader<NetworkFileData, InputStream> {
    
    override fun buildLoadData(
        model: NetworkFileData,
        width: Int,
        height: Int,
        options: Options
    ): ModelLoader.LoadData<InputStream>? {
        // Use NetworkFileData itself as cache key (it implements Key interface)
        // This ensures consistent caching between different loads
        // Timber.d("NetworkFileModelLoader.buildLoadData: path=${model.path}, size=${width}x${height}") // Disabled for DEBUG build
        return ModelLoader.LoadData(
            model, // Use NetworkFileData as Key directly for consistent cache hits
            NetworkFileDataFetcher(model, smbClient, sftpClient, ftpClient, credentialsRepository)
        )
    }
    
    override fun handles(model: NetworkFileData): Boolean {
        // Skip PDF and EPUB files - they have dedicated loaders (NetworkPdfThumbnailLoader, NetworkEpubCoverLoader)
        val isPdf = model.path.endsWith(".pdf", ignoreCase = true)
        val isEpub = model.path.endsWith(".epub", ignoreCase = true)
        
        // Skip video files - they have dedicated decoder (NetworkVideoFrameDecoder)
        // Without this, when video decoder fails, Glide falls back to InputStream + image decoders
        // which causes HEIF decoder errors trying to decode video data as images
        val extension = model.path.substringAfterLast('.', "").lowercase()
        val isVideo = extension in VIDEO_EXTENSIONS
        
        val result = (model.path.startsWith("smb://", ignoreCase = true) || 
            model.path.startsWith("sftp://", ignoreCase = true) || 
            model.path.startsWith("ftp://", ignoreCase = true)) && !isPdf && !isEpub && !isVideo
        
        // Debug logging to track video filtering
        if (isVideo) {
            Timber.d("NetworkFileModelLoader.handles: REJECTED video file ${model.path.substringAfterLast('/')}")
        }
        
        return result
    }
    
    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mov", "avi", "mkv", "webm", "3gp", "flv", "wmv", "m4v", "mpg", "mpeg"
        )
    }
}

/**
 * DataFetcher that loads network file data with interrupt protection.
 * Includes fast-fail logic for corrupt video files to avoid excessive retry cycles.
 */
class NetworkFileDataFetcher(
    private val data: NetworkFileData,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : DataFetcher<InputStream> {
    
    companion object {
        // Protocol-specific timeouts: All networks capped at 30s for thumbnails
        private const val LOCAL_THUMBNAIL_TIMEOUT_MS = 20_000L      // Local storage
        private const val SMB_THUMBNAIL_TIMEOUT_MS = 30_000L        // SMB shares
        private const val REMOTE_THUMBNAIL_TIMEOUT_MS = 30_000L     // FTP/SFTP (reduced from 40s)
        
        private const val LOCAL_FULL_IMAGE_TIMEOUT_MS = 60_000L     // Local storage full image
        private const val SMB_FULL_IMAGE_TIMEOUT_MS = 60_000L       // SMB full image (reduced from 90s)
        private const val REMOTE_FULL_IMAGE_TIMEOUT_MS = 90_000L    // FTP/SFTP full image (reduced from 120s)
        
        // Thumbnail optimization: Limit bytes read for thumbnail generation
        // Most image headers + thumbnail data < 512KB (JPEG/PNG/WebP all decode from headers)
        private const val THUMBNAIL_MAX_BYTES = 5120 * 1024L // 5MB limit for thumbnails (increased from 2MB)
        
        // Lossless formats (PNG/WebP/GIF/BMP) are sensitive to truncation.
        // Keep a higher cap for thumbnails to avoid passing corrupted/partial payloads to Glide decoder.
        private const val LOSSLESS_THUMBNAIL_MAX_BYTES = 25 * 1024 * 1024L // 25MB
        private val LOSSLESS_EXTENSIONS = setOf("png", "webp", "gif", "bmp")
        
        // Track failed video files to avoid repeated decode attempts
        // Use LinkedHashMap for FIFO eviction (insertion order)
        // PUBLIC: Shared with NetworkVideoFrameDecoder
        private val failedVideos = java.util.Collections.synchronizedMap(
            object : LinkedHashMap<String, Boolean>(5000, 0.75f, false) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > MAX_FAILED_CACHE
                }
            }
        )
        private const val MAX_FAILED_CACHE = 5000
        
        /**
         * Check if video file is in failed cache.
         * PUBLIC API for NetworkVideoFrameDecoder.
         */
        fun isVideoFailed(path: String): Boolean {
            return failedVideos.containsKey(path)
        }
        
        /**
         * Mark video file as failed (thumbnail extraction failed).
         * PUBLIC API for NetworkVideoFrameDecoder.
         */
        fun markVideoAsFailed(path: String) {
            failedVideos[path] = true
            Timber.d("Added to failed video cache (${failedVideos.size}/$MAX_FAILED_CACHE): ${path.substringAfterLast('/')}")
        }
        
        /**
         * Clear all failed video cache entries.
         * PUBLIC API for Settings -> Clear Cache.
         */
        fun clearFailedVideoCache() {
            synchronized(failedVideos) {
                val count = failedVideos.size
                failedVideos.clear()
                Timber.i("Cleared failed video cache: $count entries removed")
            }
        }
        
        /**
         * Check if thumbnail is marked as failed (generic, works for all media types).
         * PUBLIC API for MediaFileAdapter.
         */
        fun isThumbnailFailed(path: String): Boolean {
            return failedVideos.containsKey(path)
        }
        
        /**
         * Mark thumbnail as failed (generic, works for all media types).
         * PUBLIC API for MediaFileAdapter and decoders.
         */
        fun markThumbnailAsFailed(path: String) {
            failedVideos[path] = true
            Timber.d("Added to failed thumbnail cache (${failedVideos.size}/$MAX_FAILED_CACHE): ${path.substringAfterLast('/')}")
        }
    }
    
    @Volatile
    private var isCancelled = false
    private var loadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    
    override fun loadData(priority: Priority, callback: DataFetcher.DataCallback<in InputStream>) {
        val fileName = data.path.substringAfterLast('/')
        Timber.d("NetworkFileDataFetcher.loadData (CACHE MISS): $fileName, priority=$priority, loadFullImage=${data.loadFullImage}")
        
        if (isCancelled) {
            Timber.d("NetworkFileDataFetcher.loadData CANCELLED before start: $fileName")
            callback.onLoadFailed(Exception("Request cancelled"))
            return
        }

        // Read file bytes directly using maxBytes limit for thumbnails
        loadJob = scope.launch {
            try {
                Timber.d("NetworkFileDataFetcher: Starting direct byte fetch for $fileName")
                
                // Determine max bytes: limit for thumbnails, unlimited for full images
                val maxBytes = determineMaxBytes(fileName)
                
                val bytes = when {
                    data.path.startsWith("smb://", ignoreCase = true) -> fetchBytesFromSmb(maxBytes)
                    data.path.startsWith("sftp://", ignoreCase = true) -> fetchBytesFromSftp(maxBytes)
                    data.path.startsWith("ftp://", ignoreCase = true) -> fetchBytesFromFtp(maxBytes)
                    else -> null
                }

                if (isCancelled) {
                    Timber.d("NetworkFileDataFetcher: Cancelled after fetch for $fileName")
                    callback.onLoadFailed(Exception("Request cancelled"))
                    return@launch
                }

                if (bytes == null) {
                    Timber.e("NetworkFileDataFetcher: Failed to fetch $fileName - bytes is null")
                    callback.onLoadFailed(Exception("Failed to load network file: ${data.path}"))
                    return@launch
                }

                Timber.d("NetworkFileDataFetcher: Fetch complete for $fileName, read ${bytes.size / 1024}KB")

                var finalBytes = bytes
                // Validate image data before passing to Glide to prevent SIGSEGV in disk cache.
                // For FTP, do one automatic retry before failing.
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
                            Timber.d("NetworkFileDataFetcher: FTP retry fetch complete for $fileName, read ${retryBytes.size / 1024}KB")
                            finalBytes = retryBytes
                        } else {
                            Timber.w("NetworkFileDataFetcher: FTP retry returned null for $fileName")
                        }
                    }
                }

                if (!isValidImageData(finalBytes)) {
                    Timber.w("NetworkFileDataFetcher: Invalid/corrupted image data for $fileName (size=${finalBytes.size})")
                    callback.onLoadFailed(Exception("Corrupted image data: ${data.path}"))
                    return@launch
                }
                
                // Return ByteArrayInputStream to Glide (fully buffered, no synchronization issues)
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
        Timber.d("fetchBytesFromSmb START: $fileName, maxBytes=${maxBytes / 1024}KB")
        val uri = data.path.replaceFirst(Regex("^smb://", RegexOption.IGNORE_CASE), "")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return null

        val serverPort = parts[0]
        val pathParts = if (parts.size > 1) parts[1] else ""

        val server: String
        val port: Int
        if (serverPort.contains(":")) {
            val sp = serverPort.split(":")
            server = sp[0]
            port = sp[1].toIntOrNull() ?: 445
        } else {
            server = serverPort
            port = 445
        }
        
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
                        Timber.d("fetchBytesFromSmb SUCCESS: $fileName, ${result.data.size / 1024}KB")
                        result.data
                    }
                    is SmbResult.Error -> {
                        Timber.w("fetchBytesFromSmb ERROR: $fileName - ${result.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                Timber.w("fetchBytesFromSmb TIMEOUT: $fileName - ${e.message}")
                null
            }
        }
    }
    
    private suspend fun fetchBytesFromSftp(maxBytes: Long): ByteArray? {
        val fileName = data.path.substringAfterLast('/')
        Timber.d("fetchBytesFromSftp START: $fileName, maxBytes=${maxBytes / 1024}KB")
        val uri = data.path.replaceFirst(Regex("^sftp://", RegexOption.IGNORE_CASE), "")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return null

        val serverPort = parts[0]
        val remotePath = if (parts.size > 1) "/${parts[1]}" else "/"

        val server: String
        val port: Int
        if (serverPort.contains(":")) {
            val sp = serverPort.split(":")
            server = sp[0]
            port = sp[1].toIntOrNull() ?: 22
        } else {
            server = serverPort
            port = 22
        }
        
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
                    Timber.d("fetchBytesFromSftp SUCCESS: $fileName, ${it.size / 1024}KB")
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
        Timber.d("fetchBytesFromFtp START: $fileName, maxBytes=${maxBytes / 1024}KB")
        val uri = data.path.replaceFirst(Regex("^ftp://", RegexOption.IGNORE_CASE), "")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) return null

        val serverPort = parts[0]
        val remotePath = if (parts.size > 1) "/${parts[1]}" else "/"

        val server: String
        val port: Int
        if (serverPort.contains(":")) {
            val sp = serverPort.split(":")
            server = sp[0]
            port = sp[1].toIntOrNull() ?: 21
        } else {
            server = serverPort
            port = 21
        }
        
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

            // Use independent connection for thread safety and robustness
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
                    Timber.d("fetchBytesFromFtp SUCCESS: $fileName, ${it.size / 1024}KB")
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

    /**
     * Determine safe max bytes for current request.
     * For lossless image formats, avoid aggressive truncation that can lead to native decoder crashes.
     */
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

    private fun isJpegFile(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension == "jpg" || extension == "jpeg"
    }
    
    /**
     * Validate image data by checking magic bytes (file signature).
     * Prevents Glide from crashing when decoding corrupted data.
     */
    private fun isValidImageData(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        
        // Check PNG signature: 89 50 4E 47 0D 0A 1A 0A
        if (bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && 
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte()) {
            return isValidPngData(bytes)
        }
        
        // Check JPEG signature: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            // Some FTP servers/files can contain trailing bytes after EOI.
            // Accept JPEG when EOI marker exists near tail, not strictly at the last 2 bytes.
            if (bytes.size < 4) return false

            val searchWindow = 64 * 1024
            val startIndex = maxOf(2, bytes.size - searchWindow)
            for (index in (bytes.size - 1) downTo startIndex) {
                if (bytes[index - 1] == 0xFF.toByte() && bytes[index] == 0xD9.toByte()) {
                    return true
                }
            }

            Timber.w("isValidImageData: JPEG without EOI marker in tail window (size=${bytes.size})")
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
            if (bytes.size >= 6) {
                val declaredSize = readUInt32LittleEndian(bytes, 2)
                if (declaredSize > bytes.size.toLong()) return false
            }
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
        val fileName = data.path.substringAfterLast('/')
        // Use Exception to capture stack trace of who called cancel
        Timber.d(Exception("Trace"), "NetworkFileDataFetcher.cancel() called for $fileName")
        isCancelled = true
        loadJob?.cancel()
    }
    
    override fun getDataClass(): Class<InputStream> = InputStream::class.java
    
    override fun getDataSource(): DataSource = DataSource.REMOTE
}

/**
 * Factory for creating NetworkFileModelLoader instances.
 * Lazily initializes dependencies from Hilt.
 */
class NetworkFileModelLoaderFactory : ModelLoaderFactory<NetworkFileData, InputStream> {
    
    // These will be injected lazily when first ModelLoader is created
    private var smbClient: SmbClient? = null
    private var sftpClient: SftpClient? = null
    private var ftpClient: FtpClient? = null
    private var credentialsRepository: NetworkCredentialsRepository? = null
    
    override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<NetworkFileData, InputStream> {
        // Get dependencies from Hilt EntryPoint
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
    
    override fun teardown() {
        // No resources to release
    }
}

/**
 * Hilt EntryPoint for accessing dependencies in Glide module.
 */
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
