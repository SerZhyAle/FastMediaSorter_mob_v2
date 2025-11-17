package com.sza.fastmediasorter_v2.data.network.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.sza.fastmediasorter_v2.data.remote.ftp.FtpClient
import org.apache.commons.net.ftp.FTPClient
import timber.log.Timber
import java.io.IOException
import java.io.InputStream

/**
 * Custom DataSource for streaming video from FTP server via ExoPlayer.
 * Allows video playback without downloading entire file.
 * 
 * Uses Apache Commons Net FTPClient's retrieveFileStream for reading with seek support.
 */
class FtpDataSource(
    private val ftpClient: FtpClient,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String
) : BaseDataSource(true) {

    private var client: FTPClient? = null
    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false
    private var totalBytesRead = 0L

    override fun open(dataSpec: DataSpec): Long {
        try {
            uri = dataSpec.uri
            val remotePath = uri?.path ?: throw IOException("Invalid URI path")

            Timber.d("FtpDataSource: Opening FTP file: $remotePath")

            // Create new FTPClient instance for this stream
            client = FTPClient()
            client?.connect(host, port)
            
            val loginSuccess = client?.login(username, password) ?: false
            if (!loginSuccess) {
                throw IOException("FTP login failed: ${client?.replyString}")
            }

            // Set passive mode and binary transfer
            client?.enterLocalPassiveMode()
            client?.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)
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
            inputStream = client?.retrieveFileStream(remotePath)
            if (inputStream == null) {
                throw IOException("Failed to open FTP file stream: ${client?.replyString}")
            }

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
            // Log first 10KB (debug start), then every 500KB (reduce spam ~20x)
            if (totalBytesRead <= 10000 || (totalBytesRead / 500000) > ((totalBytesRead - bytesRead) / 500000)) {
                Timber.d(
                    "FtpDataSource: READ - requested=$bytesToRead actual=$bytesRead total=$totalBytesRead remaining=$bytesRemaining file=${uri?.lastPathSegment}"
                )
            }

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
        uri = null

        try {
            inputStream?.close()
        } catch (e: Exception) {
            Timber.e(e, "FtpDataSource: Error closing InputStream")
        } finally {
            inputStream = null
        }

        try {
            // Abort transfer if needed before disconnect
            if (client?.isConnected == true) {
                client?.abort()
            }
        } catch (e: Exception) {
            Timber.d(e, "FtpDataSource: abort error (non-critical)")
        }

        try {
            if (client?.isConnected == true) {
                client?.logout()
            }
        } catch (e: Exception) {
            Timber.d(e, "FtpDataSource: logout error (non-critical)")
        }

        try {
            client?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "FtpDataSource: Error disconnecting FTP")
        } finally {
            client = null
        }

        if (opened) {
            opened = false
            transferEnded()
        }

        Timber.d("FtpDataSource: Closed FTP data source")
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
    private val password: String
) : DataSource.Factory {
    override fun createDataSource(): DataSource = FtpDataSource(
        ftpClient, host, port, username, password
    )
}
