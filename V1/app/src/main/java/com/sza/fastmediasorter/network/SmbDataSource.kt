package com.sza.fastmediasorter.network

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.sza.fastmediasorter.utils.Logger
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.IOException

/**
 * Custom DataSource for streaming video from SMB server via ExoPlayer
 * Allows video playback without downloading entire file
 */
class SmbDataSource(
    private val smbClient: SmbClient,
) : BaseDataSource(
    true,
) {
    private var smbFile: SmbFile? = null
    private var smbRandomAccessFile: SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        try {
            uri = dataSpec.uri
            val smbUrl = uri.toString()

            Logger.d("SmbDataSource", "Opening SMB file: $smbUrl")

            // Create SMB file instance
            smbFile = SmbFile(smbUrl, smbClient.getContext())

            if (smbFile?.exists() != true) {
                throw IOException("SMB file not found: $smbUrl")
            }

            if (!smbFile!!.canRead()) {
                throw IOException("Cannot read SMB file: $smbUrl")
            }

            // Open random access for seeking
            smbRandomAccessFile = SmbRandomAccessFile(smbFile, "r")

            val fileLength = smbFile!!.length()

            // Handle range request (for seeking)
            val position = dataSpec.position
            if (position > 0) {
                smbRandomAccessFile?.seek(position)
            }

            bytesRemaining =
                if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                    dataSpec.length
                } else {
                    fileLength - position
                }

            opened = true
            transferStarted(dataSpec)

            Logger.d(
                "SmbDataSource",
                "Opened: position=$position, bytesRemaining=$bytesRemaining, fileLength=$fileLength",
            )

            return if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                fileLength
            } else {
                bytesRemaining
            }
        } catch (e: Exception) {
            Logger.e("SmbDataSource", "Error opening SMB file", e)
            throw IOException("Failed to open SMB file: ${e.message}", e)
        }
    }

    private var totalBytesRead = 0L

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (length == 0) {
            return 0
        }

        if (bytesRemaining == 0L) {
            return C.RESULT_END_OF_INPUT
        }

        try {
            val bytesToRead =
                if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                    length
                } else {
                    minOf(bytesRemaining, length.toLong()).toInt()
                }

            val bytesRead = smbRandomAccessFile?.read(buffer, offset, bytesToRead) ?: C.RESULT_END_OF_INPUT

            if (bytesRead > 0) {
                totalBytesRead += bytesRead
                if (totalBytesRead <= 10000 || totalBytesRead % 100000 == 0L) {
                    Logger.e(
                        "SmbDataSource",
                        "READ: requested=$bytesToRead actual=$bytesRead total=$totalBytesRead remaining=$bytesRemaining file=${uri?.lastPathSegment}",
                    )
                }

                if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                    bytesRemaining -= bytesRead.toLong()
                }
                bytesTransferred(bytesRead)
            }

            return bytesRead
        } catch (e: Exception) {
            Logger.e("SmbDataSource", "Error reading from SMB file", e)
            throw IOException("Failed to read from SMB file: ${e.message}", e)
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null

        try {
            smbRandomAccessFile?.close()
        } catch (e: Exception) {
            Logger.e("SmbDataSource", "Error closing SmbRandomAccessFile", e)
        } finally {
            smbRandomAccessFile = null
        }

        smbFile = null

        if (opened) {
            opened = false
            transferEnded()
        }

        Logger.d("SmbDataSource", "Closed SMB data source")
    }
}

/**
 * Factory for creating SmbDataSource instances
 */
class SmbDataSourceFactory(
    private val smbClient: SmbClient,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = SmbDataSource(smbClient)
}
