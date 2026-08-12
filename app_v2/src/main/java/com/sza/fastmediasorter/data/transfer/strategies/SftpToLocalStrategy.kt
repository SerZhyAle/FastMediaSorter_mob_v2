package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.data.cloud.NetworkCredentialsResolver
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpToLocalStrategy @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val sftpClient: SftpClient,
    private val credentialsResolver: NetworkCredentialsResolver
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        val isSourceSftp = sourceScheme == "sftp"
        val isDestLocal = destScheme == "file" || destScheme == "content" || destScheme == null
        return isSourceSftp && isDestLocal
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // Resolve source credentials
        val sourceCredentials = credentialsResolver.getCredentials(source.toString())
        if (sourceCredentials == null) {
            Timber.e("SftpToLocalStrategy.copy: No credentials for source $source")
            return false
        }
        
        val sourceRemotePath = extractRemotePath(source, sourceCredentials.server, sourceCredentials.port)
        
        val outputStream = try {
            when (destination.scheme) {
                "file", null -> {
                    val destFile = java.io.File(destination.path ?: destination.toString())
                    destFile.parentFile?.mkdirs()
                    destFile.outputStream()
                }
                "content" -> {
                    context.contentResolver.openOutputStream(destination)
                        ?: throw IllegalStateException("Cannot open content:// for writing")
                }
                else -> {
                    Timber.e("SftpToLocalStrategy.copy: Unsupported destination scheme ${destination.scheme}")
                    return false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "SftpToLocalStrategy.copy: Failed to open destination")
            return false
        }
        
        // Download from SFTP
        val connectionInfo = SftpClient.SftpConnectionInfo(
            host = sourceCredentials.server,
            port = sourceCredentials.port,
            username = sourceCredentials.username,
            password = sourceCredentials.password,
            privateKey = sourceCredentials.privateKey
        )
        
        return try {
            outputStream.use { output ->
                val downloadResult = sftpClient.downloadFile(
                    connectionInfo = connectionInfo,
                    remotePath = sourceRemotePath,
                    outputStream = output,
                    fileSize = 0L, // Unknown size
                    progressCallback = progressCallback
                )
                downloadResult.isSuccess
            }
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            Timber.e(e, "SftpToLocalStrategy.copy: Download failed")
            false
        }
    }
    
    /**
     * Extract remote path from SFTP URI
     * Example: sftp://server:22/path/to/file.txt -> /path/to/file.txt
     */
    private fun extractRemotePath(uri: Uri, host: String, port: Int): String {
        val full = uri.toString()
        val prefix = "sftp://$host:$port"
        return full.removePrefix(prefix).ifBlank { "/" }
    }
}
