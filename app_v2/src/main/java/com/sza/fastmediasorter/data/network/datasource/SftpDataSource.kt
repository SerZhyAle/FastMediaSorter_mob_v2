package com.sza.fastmediasorter.data.network.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import timber.log.Timber
import java.io.IOException
import java.io.InputStream

/**
 * Custom DataSource for streaming video from SFTP server via ExoPlayer.
 * Allows video playback without downloading entire file.
 * 
 * Uses JSch library's ChannelSftp API for reading file chunks with seek support.
 */
class SftpDataSource(
    private val sftpClient: SftpClient,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String
) : BaseDataSource(true) {

    private var jsch: JSch? = null
    private var session: Session? = null
    private var channel: ChannelSftp? = null
    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false
    private var totalBytesRead = 0L

    override fun open(dataSpec: DataSpec): Long {
        try {
            uri = dataSpec.uri
            val remotePath = uri?.path ?: throw IOException("Invalid URI path")

            Timber.d("SftpDataSource: Opening SFTP file: $remotePath")

            // Establish JSch connection
            jsch = JSch()
            session = jsch?.getSession(username, host, port)
            session?.setPassword(password)
            
            // Disable strict host key checking
            val config = java.util.Properties()
            config["StrictHostKeyChecking"] = "no"
            // Explicitly enable password authentication
            config["PreferredAuthentications"] = "password,publickey,keyboard-interactive"
            session?.setConfig(config)
            
            session?.connect(30000) // 30 second timeout
            
            channel = session?.openChannel("sftp") as? ChannelSftp
            channel?.connect(10000) // 10 second timeout

            if (channel == null) {
                throw IOException("Failed to open SFTP channel")
            }

            // Get file size
            val fileAttributes = channel?.stat(remotePath)
            val fileLength = fileAttributes?.size ?: 0L

            // Handle range request (for seeking)
            val position = dataSpec.position
            inputStream = channel?.get(remotePath, null, position)

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                fileLength - position
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

        try {
            inputStream?.close()
        } catch (e: Exception) {
            Timber.e(e, "SftpDataSource: Error closing InputStream")
        } finally {
            inputStream = null
        }

        try {
            channel?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "SftpDataSource: Error closing ChannelSftp")
        } finally {
            channel = null
        }

        try {
            session?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "SftpDataSource: Error disconnecting Session")
        } finally {
            session = null
        }

        if (opened) {
            opened = false
            transferEnded()
        }

        Timber.d("SftpDataSource: Closed SFTP data source")
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
