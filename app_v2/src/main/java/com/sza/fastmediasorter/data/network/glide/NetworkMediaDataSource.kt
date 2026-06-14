package com.sza.fastmediasorter.data.network.glide

import android.media.MediaDataSource
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.ftp.FtpExoPlayerPool
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * MediaDataSource implementation for network files (SMB/SFTP/FTP).
 * Provides direct byte access to MediaMetadataRetriever without temporary files.
 *
 * OPTIMIZATION: Uses chunked buffering to reduce network round trips.
 * MediaMetadataRetriever makes many small sequential reads, so we cache
 * larger chunks (256KB) to minimize SMB/SFTP/FTP latency overhead.
 */
class NetworkMediaDataSource(
    val path: String,
    private val fileSize: Long,
    private val credentialsId: String?,
    private val smbClient: SmbClient,
    private val sftpClient: SftpClient,
    private val ftpClient: FtpClient,
    private val credentialsRepository: NetworkCredentialsRepository
) : MediaDataSource() {

    companion object {
        // Buffer size for reducing network round trips (256KB)
        private const val BUFFER_CHUNK_SIZE = 256 * 1024L
    }

    private var isClosed = false

    /**
     * Set to true if a DiskShare-already-closed or transport error is caught during readAt().
     * Read by NetworkVideoFrameDecoder after extraction to classify the failure as transient
     * (SMB share race between thumbnail IO and active playback). S0060.
     */
    @Volatile var encounteredStaleShare: Boolean = false
        private set

    /**
     * Protocol-agnostic transient-failure reason populated by readAt() detectors. S0066.
     * Read by NetworkVideoFrameDecoder after extraction to classify the failure as transient
     * for SMB / SFTP / FTP uniformly. Null = no transient signal observed.
     */
    @Volatile var transientFailureReason: TransientReason? = null
        private set

    // Buffering for reducing network round trips
    private var cachedChunkStart = -1L
    private var cachedChunkEnd = -1L
    private var cachedChunkData: ByteArray? = null
    
    // FTP connection pooling for thumbnail extraction (prevents reconnect on every read)
    private var pooledFtpConnection: FtpExoPlayerPool.ExoPlayerFtpConnection? = null

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (isClosed) {
            return -1 // closed MediaDataSource returns EOF per contract - no logging (tight-loop caller)
        }

        if (position >= fileSize) {
            return -1 // EOF
        }

        val bytesToRead = minOf(size.toLong(), fileSize - position).toInt()

        return try {
            // Check if we can serve from cached chunk
            if (cachedChunkData != null && 
                position >= cachedChunkStart && 
                position < cachedChunkEnd) {
                
                val chunkOffset = (position - cachedChunkStart).toInt()
                val availableInChunk = (cachedChunkEnd - position).toInt()
                val bytesFromCache = minOf(bytesToRead, availableInChunk)
                
                cachedChunkData!!.copyInto(buffer, offset, chunkOffset, chunkOffset + bytesFromCache)
                
                // If we satisfied the entire request from cache, return immediately
                if (bytesFromCache >= bytesToRead) {
                    return bytesFromCache
                }
                
                // Partial cache hit - fall through to fetch remaining bytes
                // (This is rare, usually MediaMetadataRetriever reads sequentially)
            }
            
            // Cache miss or partial miss - fetch a larger chunk
            val chunkSize = maxOf(bytesToRead.toLong(), BUFFER_CHUNK_SIZE)
            val chunkEnd = minOf(position + chunkSize, fileSize)
            
            val bytes = readBytesFromNetwork(position, chunkEnd - position)
            if (bytes.isEmpty()) {
                return -1 // EOF or error
            }
            
            // Update cache with the fetched chunk
            cachedChunkStart = position
            cachedChunkEnd = position + bytes.size
            cachedChunkData = bytes
            
            // Copy requested amount to output buffer
            val bytesToCopy = minOf(bytesToRead, bytes.size)
            bytes.copyInto(buffer, offset, 0, bytesToCopy)
            
            bytesToCopy
        } catch (e: Exception) {
            // Expected during video thumbnail timeout cancellation - log without stack trace
            if (e is InterruptedException || e.cause is InterruptedException ||
                e is java.util.concurrent.CancellationException || e.cause is java.util.concurrent.CancellationException) {
                Timber.d("Network read interrupted at position $position (expected during cancellation)")
            } else if (e is SocketTimeoutException || e.cause is SocketTimeoutException ||
                e.message?.contains("timed out", ignoreCase = true) == true) {
                Timber.w("Network read timeout at position $position, size $size")
            } else if (isSmbStaleShareError(e)) {
                // SMB DiskShare lifecycle race: the playback path invalidated the share while
                // thumbnail was still reading. Mark for transient classification in decoder. S0060.
                encounteredStaleShare = true
                Timber.w("[scope=thumbnail failureClass=stale-share] DiskShare race at position=$position: ${e.message}")
            } else {
                // Unexpected errors get full stack trace
                Timber.e(e, "Error reading from network at position $position, size $size")
            }
            // S0066: protocol-aware transient classification (uniform for SMB / SFTP / FTP).
            when {
                path.startsWith("smb://") -> if (isSmbStaleShareError(e)) {
                    transientFailureReason = TransientReason.STALE_SHARE
                }
                path.startsWith("sftp://") -> transientFailureReason = classifySftpTransient(e)
                path.startsWith("ftp://") -> transientFailureReason = classifyFtpTransient(e)
            }
            if (transientFailureReason == null && classifyTimeoutTransient(e)) {
                transientFailureReason = TransientReason.TIMEOUT
            }
            transientFailureReason?.let { reason ->
                if (!path.startsWith("smb://")) {
                    Timber.w("[scope=thumbnail protocol=${path.substringBefore("://")} failureClass=$reason] Transient at position=$position: ${e.message}")
                }
            }
            -1
        }
    }

    override fun getSize(): Long = fileSize

    override fun close() {
        isClosed = true
        // Clear cache to free memory
        cachedChunkData = null
        cachedChunkStart = -1L
        cachedChunkEnd = -1L
        
        // Release pooled FTP connection
        pooledFtpConnection?.let { conn ->
            try {
                ftpClient.releaseExoPlayerConnection(conn.client)
                Timber.d("Network read interrupted at position 0 (expected during cancellation)")
            } catch (e: Exception) {
                Timber.w(e, "NetworkMediaDataSource: Error releasing FTP connection")
            }
            pooledFtpConnection = null
        }
    }

    /**
     * Detects SMB DiskShare-lifecycle errors that occur when the playback path
     * invalidates a share while thumbnail extraction is still reading from it.
     * These are transient race conditions (S0060), NOT permanent file failures.
     */
    private fun classifySftpTransient(e: Throwable): TransientReason? {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 5) {
            val msg = current.message?.lowercase().orEmpty()
            val cls = current.javaClass.simpleName
            if (cls == "TransportException" || msg.contains("transport")) return TransientReason.TRANSPORT
            if (msg.contains("channel") && msg.contains("closed")) return TransientReason.BROKEN_CHANNEL
            if (msg.contains("session is down") || msg.contains("session closed")) return TransientReason.BROKEN_CHANNEL
            if (current is java.net.SocketException) return TransientReason.TRANSPORT
            current = current.cause
            depth++
        }
        return null
    }

    private fun classifyFtpTransient(e: Throwable): TransientReason? {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 5) {
            val msg = current.message?.lowercase().orEmpty()
            if (msg.contains("broken pipe") || msg.contains("connection reset")) return TransientReason.BROKEN_PIPE
            if (current is java.net.SocketException) return TransientReason.TRANSPORT
            if (msg.contains("replycode=421") || msg.contains("reply='421") ||
                msg.contains("replycode=426") || msg.contains("reply='426")) return TransientReason.BROKEN_PIPE
            current = current.cause
            depth++
        }
        return null
    }

    private fun classifyTimeoutTransient(e: Throwable): Boolean {
        return e is java.net.SocketTimeoutException ||
            e.cause is java.net.SocketTimeoutException ||
            e.message?.contains("timed out", ignoreCase = true) == true
    }

    private fun isSmbStaleShareError(e: Exception): Boolean {
        var current: Throwable? = e
        var depth = 0
        while (current != null && depth < 5) {
            val msg = current.message?.lowercase() ?: ""
            if (msg.contains("diskshare has already been closed") ||
                (msg.contains("share") && msg.contains("closed"))) {
                return true
            }
            current = current.cause
            depth++
        }
        return false
    }

    private fun readBytesFromNetwork(offset: Long, length: Long): ByteArray {
        return when {
            path.startsWith("smb://") -> readFromSmb(offset, length)
            path.startsWith("sftp://") -> readFromSftp(offset, length)
            path.startsWith("ftp://") -> readFromFtp(offset, length)
            else -> throw IOException("Unsupported protocol: $path")
        }
    }

    private fun readFromSmb(offset: Long, length: Long): ByteArray = runBlocking {
        val uri = path.removePrefix("smb://")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) throw IOException("Invalid SMB path")

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

        val credentials = if (credentialsId != null) {
            credentialsRepository.getByCredentialId(credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("SMB", server, port)
        } ?: throw IOException("No credentials found for SMB")

        val shareAndPath = pathParts.split("/", limit = 2)
        val shareName = if (shareAndPath.isNotEmpty()) shareAndPath[0] else (credentials.shareName ?: "")
        val remotePath = if (shareAndPath.size > 1) shareAndPath[1] else ""

        if (shareName.isEmpty()) throw IOException("No share name")

        val connectionInfo = SmbConnectionInfo(
            server = server,
            port = port,
            shareName = shareName,
            username = credentials.username,
            password = credentials.password,
            domain = credentials.domain
        )

        // allowRetry=true: enables one retry after transient TCP failures (brief WiFi hiccup, ARP cache miss,
        // NIC power-save wake). Without retry, a dead pooled connection + brief TCP pre-check failure = permanent
        // "Server unreachable" even though the server is actually fine. The outer
        // VIDEO_THUMBNAIL_EXTRACTION_TIMEOUT_MS (10s) is the safety net against real hangs.
        when (val result = smbClient.readFileBytesRange(connectionInfo, remotePath, offset, length, allowRetry = true)) {
            is SmbResult.Success -> result.data
            else -> throw IOException("SMB read failed: ${result}")
        }
    }

    private fun readFromSftp(offset: Long, length: Long): ByteArray = runBlocking {
        val uri = path.removePrefix("sftp://")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) throw IOException("Invalid SFTP path")

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

        val credentials = if (credentialsId != null) {
            credentialsRepository.getByCredentialId(credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("SFTP", server, port)
        } ?: throw IOException("No credentials found for SFTP")

        val connectionInfo = SftpClient.SftpConnectionInfo(
            host = server,
            port = port,
            username = credentials.username,
            password = credentials.password,
            privateKey = credentials.sshPrivateKey
        )

        // allowRetry=true: same reasoning as SMB - handles transient TCP failures without false "Server unreachable".
        val result = sftpClient.readFileBytesRange(connectionInfo, remotePath, offset, length, allowRetry = true)
        result.getOrNull() ?: run {
            val cause = result.exceptionOrNull()
            Timber.e(
                cause,
                "SFTP range read failed [path=%s, offset=%d, length=%d]",
                remotePath,
                offset,
                length
            )
            throw IOException(
                "SFTP read failed [path=$remotePath, offset=$offset, length=$length, cause=${cause?.message}]",
                cause
            )
        }
    }

    private fun readFromFtp(offset: Long, length: Long): ByteArray = runBlocking {
        val uri = path.removePrefix("ftp://")
        val parts = uri.split("/", limit = 2)
        if (parts.isEmpty()) throw IOException("Invalid FTP path")

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

        val credentials = if (credentialsId != null) {
            credentialsRepository.getByCredentialId(credentialsId)
        } else {
            credentialsRepository.getByTypeServerAndPort("FTP", server, port)
        } ?: throw IOException("No credentials found for FTP")

        val connectionInfo = FtpExoPlayerPool.FtpConnectionInfo(
            host = server,
            port = port,
            username = credentials.username,
            password = credentials.password
        )
        
        try {
            // Get or reuse pooled FTP connection (prevents reconnect on every read)
            // Validate connection is still alive before reuse
            val connection = pooledFtpConnection?.let { existing ->
                if (existing.client.isConnected) {
                    Timber.d("NetworkMediaDataSource: Reusing pooled FTP connection")
                    existing
                } else {
                    // Connection is dead, release it and get a new one
                    Timber.w("NetworkMediaDataSource: Pooled connection is dead, getting new one")
                    try {
                        ftpClient.releaseExoPlayerConnection(existing.client)
                    } catch (ignored: Exception) {}
                    pooledFtpConnection = null
                    null
                }
            } ?: run {
                val newConn = ftpClient.getConnectionForExoPlayer(connectionInfo)
                pooledFtpConnection = newConn
                Timber.d("NetworkMediaDataSource: Created new pooled FTP connection")
                newConn
            }
            
            // Read bytes using pooled connection directly
            val client = connection.client
            val bytes = synchronized(client) {
                val readInCurrentMode: (modeLabel: String) -> ByteArray = { modeLabel ->
                    // Some FTP servers reject REST 0 with 501.
                    // Initial reads (offset=0) must start without restart command.
                    if (offset > 0L) {
                        client.setRestartOffset(offset)
                    }

                    client.retrieveFileStream(remotePath)?.use { inputStream ->
                        val buffer = ByteArray(length.toInt())
                        var totalRead = 0

                        while (totalRead < length) {
                            val read = inputStream.read(buffer, totalRead, (length - totalRead).toInt())
                            if (read == -1) break
                            totalRead += read
                        }

                        if (!client.completePendingCommand()) {
                            val replyCode = runCatching { client.replyCode }.getOrDefault(-1)
                            val replyString = runCatching { client.replyString }.getOrNull().orEmpty().trim()
                            Timber.e(
                                "FTP range read completePendingCommand failed " +
                                    "[mode=$modeLabel, path=$remotePath, offset=$offset, length=$length, " +
                                    "replyCode=$replyCode, reply='$replyString']"
                            )
                            throw IOException(
                                "FTP completePendingCommand failed " +
                                    "(mode=$modeLabel, replyCode=$replyCode, reply=$replyString)"
                            )
                        }

                        if (totalRead < length) {
                            buffer.copyOf(totalRead)
                        } else {
                            buffer
                        }
                    } ?: run {
                        val replyCode = runCatching { client.replyCode }.getOrDefault(-1)
                        val replyString = runCatching { client.replyString }.getOrNull().orEmpty().trim()
                        Timber.e(
                            "FTP retrieveFileStream returned null " +
                                "[mode=$modeLabel, path=$remotePath, offset=$offset, length=$length, " +
                                "replyCode=$replyCode, reply='$replyString']"
                        )
                        throw IOException(
                            "Failed to open FTP stream: $remotePath " +
                                "(mode=$modeLabel, replyCode=$replyCode, reply=$replyString)"
                        )
                    }
                }

                try {
                    readInCurrentMode("passive")
                } catch (e: Exception) {
                    val timeoutInCompletePendingCommand = e is SocketTimeoutException &&
                        e.stackTrace.any { element ->
                            element.className.contains("FTPClient") &&
                                element.methodName == "completePendingCommand"
                        }

                    val shouldRetryInActiveMode = e is SocketTimeoutException ||
                        (e.cause is SocketTimeoutException)

                    if (timeoutInCompletePendingCommand) {
                        Timber.w(e, "NetworkMediaDataSource: FTP timeout in completePendingCommand, skipping active-mode retry")
                        throw e
                    }

                    if (!shouldRetryInActiveMode) {
                        throw e
                    }

                    Timber.w(e, "NetworkMediaDataSource: Passive FTP read failed, retrying in active mode")
                    client.enterLocalActiveMode()

                    try {
                        readInCurrentMode("active")
                    } finally {
                        try {
                            client.enterLocalPassiveMode()
                            Timber.d("NetworkMediaDataSource: Switched back to passive mode")
                        } catch (passiveError: Exception) {
                            Timber.w(passiveError, "NetworkMediaDataSource: Failed to switch back to passive mode")
                        }
                    }
                }
            }
            
            bytes
        } catch (e: Exception) {
            // On error, release and clear pooled connection so next attempt reconnects
            pooledFtpConnection?.let { conn ->
                try {
                    ftpClient.releaseExoPlayerConnection(conn.client)
                } catch (ignored: Exception) {
                    Timber.w(ignored, "Error releasing pooled connection")
                }
                pooledFtpConnection = null
                Timber.d("NetworkMediaDataSource: Cleared pooled connection after error")
            }
            throw IOException("FTP read failed at offset $offset", e)
        }
    }
}
