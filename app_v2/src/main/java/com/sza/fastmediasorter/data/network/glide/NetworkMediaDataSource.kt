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
        result.getOrNull() ?: throw IOException("SFTP read failed")
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

        try {
            ftpClient.connect(server, port, credentials.username, credentials.password)
            if (!ftpClient.isConnected()) throw IOException("FTP connection failed")

            val result = ftpClient.readFileBytesRange(remotePath, offset, length)
            result.getOrNull() ?: throw IOException("FTP read failed")
        } finally {
            ftpClient.disconnect()
        }
    }
}
