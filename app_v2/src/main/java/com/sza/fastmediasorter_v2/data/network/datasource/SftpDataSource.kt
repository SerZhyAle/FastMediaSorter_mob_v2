package com.sza.fastmediasorter_v2.data.network.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.sza.fastmediasorter_v2.data.remote.sftp.SftpClient
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import timber.log.Timber
import java.io.IOException
import java.io.InputStream

/**
 * Custom DataSource for streaming video from SFTP server via ExoPlayer.
 * Allows video playback without downloading entire file.
 * 
 * Uses SSHJ library's RemoteFile API for reading file chunks with seek support.
 */
class SftpDataSource(
    private val sftpClient: SftpClient,
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String
) : BaseDataSource(true) {

    private var sshClient: SSHClient? = null
    private var sftp: SFTPClient? = null
    private var remoteFile: RemoteFile? = null
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

            // Establish connection
            sshClient = SSHClient()
            sshClient?.addHostKeyVerifier(PromiscuousVerifier()) // Accept all host keys
            sshClient?.connect(host, port)
            sshClient?.authPassword(username, password)

            sftp = sshClient?.newSFTPClient()
            remoteFile = sftp?.open(remotePath)

            if (remoteFile == null) {
                throw IOException("Failed to open remote file: $remotePath")
            }

            // Get file size
            val fileAttributes = sftp?.stat(remotePath)
            val fileLength = fileAttributes?.size ?: 0L

            // Handle range request (for seeking)
            val position = dataSpec.position
            inputStream = remoteFile?.RemoteFileInputStream(position)

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
            if (totalBytesRead <= 10000 || (totalBytesRead / 100000) > ((totalBytesRead - bytesRead) / 100000)) {
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
            remoteFile?.close()
        } catch (e: Exception) {
            Timber.e(e, "SftpDataSource: Error closing RemoteFile")
        } finally {
            remoteFile = null
        }

        try {
            sftp?.close()
        } catch (e: Exception) {
            Timber.e(e, "SftpDataSource: Error closing SFTPClient")
        } finally {
            sftp = null
        }

        try {
            sshClient?.disconnect()
        } catch (e: Exception) {
            Timber.e(e, "SftpDataSource: Error disconnecting SSH")
        } finally {
            sshClient = null
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
