package com.sza.fastmediasorter.data.transfer.strategies

import android.net.Uri
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.utils.SmbPathUtils
import timber.log.Timber
import java.io.InputStream
import javax.inject.Inject

class SmbToSmbStrategy @Inject constructor(
    private val smbClient: SmbClient
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        return sourceScheme == "smb" && destScheme == "smb"
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        val sourcePath = source.toString()
        val destPath = destination.toString()
        Timber.d("SmbToSmbStrategy: Copying $sourcePath to $destPath")

        val sourceSmbInfo = SmbPathUtils.parseSmbPath(sourcePath) ?: return false
        val destSmbInfo = SmbPathUtils.parseSmbPath(destPath) ?: return false

        // Check existence
        if (!overwrite) {
            val existsResult = smbClient.exists(destSmbInfo.connectionInfo, destSmbInfo.remotePath)
            if (existsResult is SmbResult.Success && existsResult.data) {
                Timber.w("Destination file already exists: $destPath")
                return false
            }
        }

        // Open input stream from source
        val inputStream: InputStream = when (val result = smbClient.openInputStream(sourceSmbInfo.connectionInfo, sourceSmbInfo.remotePath)) {
            is SmbResult.Success -> result.data
            is SmbResult.Error -> {
                Timber.e("Failed to open source stream: ${result.message}")
                return false
            }
        }

        // Upload to destination
        return try {
            inputStream.use { stream ->
                when (val result = smbClient.uploadFile(
                    destSmbInfo.connectionInfo,
                    destSmbInfo.remotePath,
                    stream,
                    0L,
                    progressCallback
                )) {
                    is SmbResult.Success -> true
                    is SmbResult.Error -> {
                        Timber.e("SMB upload failed: ${result.message}")
                        false
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Transfer failed")
            false
        }
    }

    override suspend fun move(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        val sourcePath = source.toString()
        val destPath = destination.toString()
        Timber.d("SmbToSmbStrategy: Moving $sourcePath to $destPath")

        val sourceSmbInfo = SmbPathUtils.parseSmbPath(sourcePath) ?: throw UnsupportedOperationException("Invalid source path")
        val destSmbInfo = SmbPathUtils.parseSmbPath(destPath) ?: throw UnsupportedOperationException("Invalid dest path")

        // Check if same server for optimized rename
        val sameServer = sourceSmbInfo.connectionInfo.server == destSmbInfo.connectionInfo.server &&
                         sourceSmbInfo.connectionInfo.shareName == destSmbInfo.connectionInfo.shareName

        if (!sameServer) {
            throw UnsupportedOperationException("Cross-server move not optimized")
        }

        return when (val result = smbClient.moveFile(
            sourceSmbInfo.connectionInfo,
            sourceSmbInfo.remotePath,
            destSmbInfo.remotePath
        )) {
            is SmbResult.Success -> true
            is SmbResult.Error -> {
                Timber.e("SMB move failed: ${result.message}")
                false
            }
        }
    }
}
