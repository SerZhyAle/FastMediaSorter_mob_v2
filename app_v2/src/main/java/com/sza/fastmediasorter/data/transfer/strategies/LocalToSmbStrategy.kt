package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.utils.SmbPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class LocalToSmbStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smbClient: SmbClient
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        val isSourceLocal = sourceScheme == "file" || sourceScheme == "content" || sourceScheme == null
        val isDestSmb = destScheme == "smb"
        return isSourceLocal && isDestSmb
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
        Timber.d("LocalToSmbStrategy: Copying $sourcePath to $destPath")

        val smbConnectionInfo = SmbPathUtils.parseSmbPath(destPath)
            ?: return false

        // Check existence if not overwriting
        if (!overwrite) {
            val existsResult = smbClient.exists(smbConnectionInfo.connectionInfo, smbConnectionInfo.remotePath)
            if (existsResult is SmbResult.Success && existsResult.data) {
                Timber.w("Destination file already exists: $destPath")
                return false
            }
        }

        // Prepare source stream
        val inputStreamInfo = try {
            if (source.scheme == "content") {
                val stream = context.contentResolver.openInputStream(source) 
                    ?: throw Exception("Failed to open content URI")
                val size = context.contentResolver.openAssetFileDescriptor(source, "r")?.use { it.length } 
                    ?: stream.available().toLong()
                Pair(stream, size)
            } else {
                val file = File(source.path ?: sourcePath)
                if (!file.exists()) throw Exception("Local file not found")
                Pair(file.inputStream(), file.length())
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open source")
            return false
        }

        // Upload to SMB
        return inputStreamInfo.first.use { stream ->
            val size = inputStreamInfo.second
            when (val result = smbClient.uploadFile(
                smbConnectionInfo.connectionInfo,
                smbConnectionInfo.remotePath,
                stream,
                size,
                progressCallback
            )) {
                is SmbResult.Success -> true
                is SmbResult.Error -> {
                    Timber.e("SMB upload failed: ${result.message}")
                    false
                }
            }
        }
    }
}
