package com.sza.fastmediasorter.data.network.datasource

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.data.network.SmbClient
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.util.EnumSet
import java.util.Locale

/**
 * Custom DataSource for streaming video from SMB server via ExoPlayer.
 * Allows video playback without downloading entire file.
 * 
 * Uses SMBJ library's InputStream API for reading file chunks.
 * Supports seeking for video scrubbing.
 */
class SmbDataSource(
    private val smbClient: SmbClient,
    private val connectionInfo: SmbClient.SmbConnectionInfo
) : BaseDataSource(true) {
    companion object {
        private const val CHUNK_LOG_BYTES = 1_000_000L // ~1 MB summaries
        private const val BYTES_IN_MEBIBYTE = 1_048_576.0
    }

    private fun isInterruption(error: Throwable?): Boolean {
        var current: Throwable? = error
        var depth = 0
        while (current != null && depth < 5) {
            val currentNonNull = current // immutable local for smart cast
            if (currentNonNull is InterruptedException) {
                return true
            }
            current = currentNonNull.cause
            depth++
        }
        return false
    }
    
    private var connection: com.hierynomus.smbj.connection.Connection? = null
    private var session: com.hierynomus.smbj.session.Session? = null
    private var share: DiskShare? = null
    private var file: File? = null
    private var inputStream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false
    private var totalBytesRead = 0L
    private var nextLogThresholdBytes = CHUNK_LOG_BYTES

    override fun open(dataSpec: DataSpec): Long {
        try {
            uri = dataSpec.uri
            val remotePath = uri?.path ?: throw IOException("Invalid URI path")
            
            // Remove leading slash and share name for SMB path
            // URI path format: /shareName/relativePath
            // We need: relativePath (without share name)
            val pathWithoutLeadingSlash = remotePath.removePrefix("/")
            val sharePrefix = "${connectionInfo.shareName}/"
            val smbPath = if (pathWithoutLeadingSlash.startsWith(sharePrefix)) {
                pathWithoutLeadingSlash.substring(sharePrefix.length)
            } else {
                pathWithoutLeadingSlash
            }
            
            Timber.d("SmbDataSource: Opening SMB file: $smbPath")
            Timber.d("SmbDataSource: Details - originalPath=$remotePath, share=${connectionInfo.shareName}, extractedPath=$smbPath")

            // Establish connection
            val config = com.hierynomus.smbj.SmbConfig.builder()
                .withTimeout(30000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .withSoTimeout(30000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .withMultiProtocolNegotiate(true)
                .build()
            
            val client = com.hierynomus.smbj.SMBClient(config)
            connection = client.connect(connectionInfo.server, connectionInfo.port)
            
            val authContext = if (connectionInfo.username.isNotEmpty()) {
                com.hierynomus.smbj.auth.AuthenticationContext(
                    connectionInfo.username,
                    connectionInfo.password.toCharArray(),
                    connectionInfo.domain.ifEmpty { null }
                )
            } else {
                com.hierynomus.smbj.auth.AuthenticationContext.anonymous()
            }
            
            session = connection?.authenticate(authContext)
            share = session?.connectShare(connectionInfo.shareName) as? DiskShare
                ?: throw IOException("Failed to connect to share: ${connectionInfo.shareName}")

            // Open file for reading
            file = share?.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            ) ?: throw IOException("Failed to open file: $smbPath")

            inputStream = file?.inputStream ?: throw IOException("Failed to get input stream")

            // Get file info
            val fileInfo = share?.getFileInformation(smbPath)
            val fileLength = fileInfo?.standardInformation?.endOfFile ?: 0L

            // Handle range request (for seeking)
            val position = dataSpec.position
            if (position > 0) {
                inputStream?.skip(position)
            }

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                fileLength - position
            }

            opened = true
            totalBytesRead = 0L
            nextLogThresholdBytes = CHUNK_LOG_BYTES
            transferStarted(dataSpec)

            Timber.d(
                "SmbDataSource: Opened - position=$position, bytesRemaining=$bytesRemaining, fileLength=$fileLength"
            )

            return if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                fileLength
            } else {
                bytesRemaining
            }
        } catch (e: Exception) {
            Timber.e(e, "SmbDataSource: Error opening SMB file")
            close()
            throw IOException("Failed to open SMB file: ${e.message}", e)
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
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= bytesRead.toLong()
            }

            if (BuildConfig.LOG_SMB_IO) {
                Timber.d(
                    "SmbDataSource: READ - requested=$bytesToRead actual=$bytesRead total=$totalBytesRead remaining=${formatRemaining()} file=${uri?.lastPathSegment}"
                )
            } else if (totalBytesRead >= nextLogThresholdBytes) {
                val transferredMb = totalBytesRead / BYTES_IN_MEBIBYTE
                Timber.d(
                    String.format(
                        Locale.US,
                        "SmbDataSource: streamed %.2f MB so far (remaining=%s, file=%s)",
                        transferredMb,
                        formatRemaining(),
                        uri?.lastPathSegment
                    )
                )
                nextLogThresholdBytes += CHUNK_LOG_BYTES
            }
            bytesTransferred(bytesRead)

            return bytesRead
        } catch (e: Exception) {
            // Check if this is a normal interruption (user closed player) or a real error
            val isInterruption = e is InterruptedException || 
                                 (e.cause is InterruptedException) ||
                                 e.message?.contains("InterruptedException", ignoreCase = true) == true
            
            if (isInterruption) {
                Timber.d("SmbDataSource: Read operation interrupted (player closed)")
            } else {
                Timber.e(e, "SmbDataSource: Error reading from SMB file")
            }
            throw IOException("Failed to read from SMB file: ${e.message}", e)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null

        try {
            inputStream?.close()
        } catch (e: Exception) {
            Timber.e(e, "SmbDataSource: Error closing InputStream")
        } finally {
            inputStream = null
        }

        try {
            file?.close()
        } catch (e: Exception) {
            if (isInterruption(e)) {
                Timber.d("SmbDataSource: File close interrupted (normal during playback cancel)")
                Thread.currentThread().interrupt()
            } else {
                Timber.e(e, "SmbDataSource: Error closing File")
            }
        } finally {
            file = null
        }

        try {
            share?.close()
        } catch (e: Exception) {
            if (isInterruption(e)) {
                Timber.d("SmbDataSource: DiskShare close interrupted (normal during playback cancel)")
                Thread.currentThread().interrupt()
            } else {
                Timber.e(e, "SmbDataSource: Error closing DiskShare")
            }
        } finally {
            share = null
        }

        try {
            session?.close()
        } catch (e: Exception) {
            if (isInterruption(e)) {
                Timber.d("SmbDataSource: Session close interrupted (normal during playback cancel)")
                Thread.currentThread().interrupt()
            } else {
                Timber.e(e, "SmbDataSource: Error closing Session")
            }
        } finally {
            session = null
        }

        try {
            connection?.close()
        } catch (e: Exception) {
            if (isInterruption(e)) {
                Timber.d("SmbDataSource: Connection close interrupted (normal during playback cancel)")
                Thread.currentThread().interrupt()
            } else {
                Timber.e(e, "SmbDataSource: Error closing Connection")
            }
        } finally {
            connection = null
        }

        if (opened) {
            opened = false
            transferEnded()
        }

        Timber.d("SmbDataSource: Closed SMB data source (totalRead=$totalBytesRead bytes)")
    }

    private fun formatRemaining(): String = when (bytesRemaining) {
        C.LENGTH_UNSET.toLong() -> "unknown"
        else -> bytesRemaining.toString()
    }
}

/**
 * Factory for creating SmbDataSource instances
 */
class SmbDataSourceFactory(
    private val smbClient: SmbClient,
    private val connectionInfo: SmbClient.SmbConnectionInfo
) : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbDataSource(smbClient, connectionInfo)
}
