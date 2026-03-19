package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbResult
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import com.sza.fastmediasorter.utils.SmbPathUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

class SmbToLocalStrategy @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val smbClient: SmbClient
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        val isSourceSmb = sourceScheme == "smb"
        val isDestLocal = destScheme == "file" || destScheme == null
        return isSourceSmb && isDestLocal
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        val sourcePath = source.toString()
        val destPath = destination.path ?: destination.toString()
        Timber.d("SmbToLocalStrategy: Copying $sourcePath to $destPath")

        val destFile = File(destPath)
        if (destFile.exists() && !overwrite) {
            Timber.w("Destination file already exists: $destPath")
            return false
        }

        val smbConnectionInfo = SmbPathUtils.parseSmbPath(sourcePath)
            ?: return false

        destFile.parentFile?.mkdirs()

        return try {
            destFile.outputStream().use { outputStream ->
                when (val result = smbClient.downloadFile(
                    smbConnectionInfo.connectionInfo,
                    smbConnectionInfo.remotePath,
                    outputStream,
                    0L,
                    progressCallback
                )) {
                    is SmbResult.Success -> {
                        MediaStoreNotifier.notifyFile(context, destFile.absolutePath, "smb-to-local")
                        true
                    }
                    is SmbResult.Error -> {
                        Timber.e("SMB download failed: ${result.message}")
                        false
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to write local file")
            false
        }
    }
}
