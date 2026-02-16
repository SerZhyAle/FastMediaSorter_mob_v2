package com.sza.fastmediasorter.data.network.glide

import android.media.MediaDataSource
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
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
    
    // Buffering for reducing network round trips
    private var cachedChunkStart = -1L
    private var cachedChunkEnd = -1L
    private var cachedChunkData: ByteArray? = null
    
    // FTP connection pooling for thumbnail extraction (prevents reconnect on every read)
    private var pooledFtpConnection: FtpClient.ExoPlayerFtpConnection? = null

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (isClosed) {
            throw IOException("DataSource is closed")
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
            } else {
                // Unexpected errors get full stack trace
                Timber.e(e, "Error reading from network at position $position, size $size")
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

        when (val result = smbClient.readFileBytesRange(connectionInfo, remotePath, offset, length)) {
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

        val result = sftpClient.readFileBytesRange(connectionInfo, remotePath, offset, length)
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

        val connectionInfo = FtpClient.FtpConnectionInfo(
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
                    client.setRestartOffset(offset)

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
                    val shouldRetryInActiveMode = e is SocketTimeoutException ||
                        (e.cause is SocketTimeoutException) ||
                        (e.message?.contains("completePendingCommand", ignoreCase = true) == true)

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
