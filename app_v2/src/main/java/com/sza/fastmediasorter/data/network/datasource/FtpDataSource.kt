package com.sza.fastmediasorter.data.network.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.sza.fastmediasorter.core.util.PermissionHelper
import com.sza.fastmediasorter.data.network.exceptions.LocalNetworkPermissionDeniedException
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.ftp.FtpExoPlayerPool
import org.apache.commons.net.ftp.FTPClient
import timber.log.Timber
import java.io.IOException
import java.io.InputStream

/**
 * Custom DataSource for streaming video from FTP server via ExoPlayer.
 * Allows video playback without downloading entire file.
 * 
 * Uses connection pooling via FtpClient.getConnectionForExoPlayer() to prevent
 * connection exhaustion during rapid file switching.
 * 
 * IMPORTANT: FTPClient is managed by FtpClient pool.
 * This DataSource only manages the InputStream lifecycle.
 */
class FtpDataSource(
    private val ftpClient: FtpClient,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val context: Context
) : BaseDataSource(true) {

    // Connection from pool - DO NOT disconnect, managed by pool
    private var client: FTPClient? = null

    // Only this should be closed
    private var inputStream: InputStream? = null

    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false
    private var totalBytesRead = 0L
    private var readCallCount = 0L
    private var openTimeMs = 0L
    private var connectionAcquired = false

    override fun open(dataSpec: DataSpec): Long {
        if (!PermissionHelper.hasLocalNetworkPermission(context)) {
            throw LocalNetworkPermissionDeniedException()
        }
        try {
            uri = dataSpec.uri
            // Use encodedPath to prevent automatic decoding, then manually decode
            val encodedPath = uri?.encodedPath ?: throw IOException("Invalid URI path")
            val remotePath = Uri.decode(encodedPath)
            
            Timber.d("FtpDataSource: Opening FTP file - encoded=$encodedPath, decoded=$remotePath")
            
            // Get connection from pool (blocking call for ExoPlayer compatibility)
            val connectionInfo = FtpExoPlayerPool.FtpConnectionInfo(
                host = host,
                port = port,
                username = username,
                password = password
            )
            
            val pooledConnection = ftpClient.getConnectionForExoPlayer(connectionInfo)
            client = pooledConnection.client
            connectionAcquired = true
            
            Timber.d("FtpDataSource: Got pooled connection - connected=${client?.isConnected}")

            if (client == null || client?.isConnected != true) {
                throw IOException("Failed to get connected FTP client from pool")
            }

            client?.soTimeout = 30000

            // Get file size using SIZE command
            val fileSize = try {
                // Try MLST first (more reliable)
                client?.mlistFile(remotePath)?.size
            } catch (e: Exception) {
                null
            } ?: run {
                // Fallback: use SIZE command
                Timber.w("FtpDataSource: Could not get file size from MLST, trying SIZE command")
                try {
                    // Send SIZE command and parse response
                    val sizeCommand = client?.sendCommand("SIZE", remotePath)
                    if (sizeCommand == 213) {
                        // 213 response contains file size
                        val reply = client?.replyString?.trim()
                        val size = reply?.split(" ")?.lastOrNull()?.toLongOrNull()
                        Timber.d("FtpDataSource: SIZE command returned: $size bytes")
                        size
                    } else {
                        Timber.w("FtpDataSource: SIZE command failed: ${client?.replyString}")
                        null
                    }
                } catch (e: Exception) {
                    Timber.w(e, "FtpDataSource: Could not get file size")
                    null
                }
            } ?: 0L

            // Handle range request (for seeking)
            val position = dataSpec.position
            
            // For seek: use REST command to set file offset
            if (position > 0) {
                client?.setRestartOffset(position)
                Timber.d("FtpDataSource: Set restart offset: $position")
            }

            // Open file stream
            val rawStream = client?.retrieveFileStream(remotePath)
            if (rawStream == null) {
                throw IOException("Failed to open FTP file stream: ${client?.replyString}")
            }
            
            val resourceKey = "ftp://$host:$port"
            val bufferSize = com.sza.fastmediasorter.data.network.ConnectionThrottleManager.getRecommendedBufferSize(resourceKey)
            inputStream = java.io.BufferedInputStream(rawStream, bufferSize)
            Timber.d("FtpDataSource: Using BufferedInputStream with size ${bufferSize / 1024} KB")

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                if (fileSize > 0) {
                    fileSize - position
                } else {
                    C.LENGTH_UNSET.toLong()
                }
            }

            opened = true
            readCallCount = 0L
            openTimeMs = System.currentTimeMillis()
            transferStarted(dataSpec)

            Timber.d(
                "FtpDataSource: Opened - position=$position, bytesRemaining=$bytesRemaining, fileSize=$fileSize"
            )

            return if (fileSize > 0) {
                fileSize
            } else {
                bytesRemaining
            }
        } catch (e: Exception) {
            Timber.e(e, "FtpDataSource: Error opening FTP file")
            close()
            throw IOException("Failed to open FTP file: ${e.message}", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }

        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        try {
            val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                length
            } else {
                minOf(bytesRemaining, length.toLong()).toInt()
            }

            val bytesRead = inputStream?.read(buffer, offset, bytesToRead) ?: C.RESULT_END_OF_INPUT

            if (bytesRead < 0) {
                // End of stream reached
                return C.RESULT_END_OF_INPUT
            }

            if (bytesRead == 0) {
                // No data available but not end of stream yet
                return 0
            }

            // bytesRead > 0: successful read
            totalBytesRead += bytesRead
            readCallCount++
            // ExoPlayer may read network streams in tiny chunks, so per-read logs flood logcat during playback.

            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= bytesRead.toLong()
            }
            bytesTransferred(bytesRead)

            return bytesRead
        } catch (e: Exception) {
            Timber.e(e, "FtpDataSource: Error reading from FTP file")
            throw IOException("Failed to read from FTP file: ${e.message}", e)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        // Keep `uri` as stable source identity - clearing it on close races the media3 stats
        // wrapper's non-null getUri() check during rapid file switching (same NPE class as SMB).
        // The next open() rebinds it; resources are released below regardless.

        // Only close the InputStream - client is managed by pool
        try {
            inputStream?.close()
        } catch (e: Exception) {
            Timber.e(e, "FtpDataSource: Error closing InputStream")
        } finally {
            inputStream = null
        }

        // DO NOT disconnect client - it is pooled!
        // Release connection slot back to pool (also calls completePendingCommand)
        if (connectionAcquired) {
            ftpClient.releaseExoPlayerConnection(client)
            connectionAcquired = false
        }
        
        // Just clear reference
        client = null

        if (opened) {
            opened = false
            transferEnded()
        }

        val elapsedMs = System.currentTimeMillis() - openTimeMs
        Timber.d(
            "FtpDataSource: Closed - totalRead=${totalBytesRead}B, calls=$readCallCount, elapsed=${elapsedMs}ms, connection returned to pool"
        )
    }
}

/**
 * Factory for creating FtpDataSource instances
 */
class FtpDataSourceFactory(
    private val ftpClient: FtpClient,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private val context: Context
) : DataSource.Factory {
    override fun createDataSource(): DataSource = FtpDataSource(
        ftpClient, host, port, username, password, context
    )
}
