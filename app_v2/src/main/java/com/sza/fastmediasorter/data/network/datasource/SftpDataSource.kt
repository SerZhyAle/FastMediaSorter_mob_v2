package com.sza.fastmediasorter.data.network.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import timber.log.Timber
import java.io.IOException
import java.io.InputStream

/**
 * Custom DataSource for streaming video from SFTP server via ExoPlayer.
 * Allows video playback without downloading entire file.
 * 
 * Uses connection pooling via SftpClient.getConnectionForExoPlayer() to prevent
 * connection exhaustion during rapid file switching.
 * 
 * IMPORTANT: Session and channel are managed by SftpClient pool.
 * This DataSource only manages the InputStream lifecycle.
 */
class SftpDataSource(
    private val sftpClient: SftpClient,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String
) : BaseDataSource(true) {

    // Connection from pool - DO NOT close these, managed by pool
    private var session: Session? = null
    private var channel: ChannelSftp? = null
    
    // Only this should be closed
    private var inputStream: InputStream? = null
    
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false
    private var totalBytesRead = 0L
    private var connectionAcquired = false

    override fun open(dataSpec: DataSpec): Long {
        try {
            uri = dataSpec.uri
            // Use encodedPath to prevent automatic decoding, then manually decode
            val encodedPath = uri?.encodedPath ?: throw IOException("Invalid URI path")
            val remotePath = Uri.decode(encodedPath)

            Timber.d("SftpDataSource: Opening SFTP file - encoded=$encodedPath, decoded=$remotePath")

            // Get connection from pool (blocking call for ExoPlayer compatibility)
            val connectionInfo = SftpClient.SftpConnectionInfo(
                host = host,
                port = port,
                username = username,
                password = password
            )
            
            val pooledConnection = sftpClient.getConnectionForExoPlayer(connectionInfo)
            session = pooledConnection.session
            channel = pooledConnection.channel
            connectionAcquired = true
            
            Timber.d("SftpDataSource: Got pooled connection - session=${session?.isConnected}, channel=${channel?.isConnected}")

            if (channel == null || !channel!!.isConnected) {
                throw IOException("Failed to get connected SFTP channel from pool")
            }

            // Get file size
            val fileAttributes = channel?.stat(remotePath)
            val rawLength = fileAttributes?.size ?: 0L
            val fileLength = if (rawLength > 0) rawLength else C.LENGTH_UNSET.toLong()

            // Handle range request (for seeking)
            val position = dataSpec.position
            val rawStream = channel?.get(remotePath, null, position)
            
            // Apply adaptive buffering
            val resourceKey = "sftp://$host:$port"
            val bufferSize = com.sza.fastmediasorter.data.network.ConnectionThrottleManager.getRecommendedBufferSize(resourceKey)
            inputStream = java.io.BufferedInputStream(rawStream, bufferSize)
            Timber.d("SftpDataSource: Using BufferedInputStream with size ${bufferSize / 1024} KB")

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else if (fileLength != C.LENGTH_UNSET.toLong()) {
                fileLength - position
            } else {
                C.LENGTH_UNSET.toLong()
            }

            opened = true
            transferStarted(dataSpec)

            Timber.d(
                "SftpDataSource: Opened - position=$position, bytesRemaining=$bytesRemaining, fileLength=$fileLength"
            )

            return if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                fileLength
            } else {
                bytesRemaining
            }
        } catch (e: Exception) {
            Timber.e(e, "SftpDataSource: Error opening SFTP file")
            close()
            throw IOException("Failed to open SFTP file: ${e.message}", e)
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
            // Log first 10KB (debug start), then every 500KB (reduce spam ~20x)
            if (totalBytesRead <= 10000 || (totalBytesRead / 500000) > ((totalBytesRead - bytesRead) / 500000)) {
                Timber.d(
                    "SftpDataSource: READ - requested=$bytesToRead actual=$bytesRead total=$totalBytesRead remaining=$bytesRemaining file=${uri?.lastPathSegment}"
                )
            }

            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= bytesRead.toLong()
            }
            bytesTransferred(bytesRead)

            return bytesRead
        } catch (e: Exception) {
            Timber.e(e, "SftpDataSource: Error reading from SFTP file")
            throw IOException("Failed to read from SFTP file: ${e.message}", e)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null

        // Only close the InputStream - session and channel are managed by pool
        try {
            inputStream?.close()
        } catch (e: Exception) {
            Timber.e(e, "SftpDataSource: Error closing InputStream")
        } finally {
            inputStream = null
        }

        // DO NOT close session or channel - they are pooled!
        // Just clear references
        channel = null
        session = null

        // Release connection slot back to pool
        if (connectionAcquired) {
            sftpClient.releaseExoPlayerConnection()
            connectionAcquired = false
        }

        if (opened) {
            opened = false
            transferEnded()
        }

        Timber.d("SftpDataSource: Closed (connection returned to pool)")
    }
}

/**
 * Factory for creating SftpDataSource instances
 */
class SftpDataSourceFactory(
    private val sftpClient: SftpClient,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String
) : DataSource.Factory {
    override fun createDataSource(): DataSource = SftpDataSource(
        sftpClient, host, port, username, password
    )
}
