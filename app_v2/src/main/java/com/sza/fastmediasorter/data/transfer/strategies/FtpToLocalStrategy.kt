package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.cloud.NetworkCredentialsResolver
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpToLocalStrategy @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val ftpClient: FtpClient,
    private val credentialsResolver: NetworkCredentialsResolver
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        val isSourceFtp = sourceScheme == "ftp"
        val isDestLocal = destScheme == "file" || destScheme == "content" || destScheme == null
        return isSourceFtp && isDestLocal
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
            Timber.e("FtpToLocalStrategy.copy: No credentials for source $source")
            return false
        }
        
        val sourceRemotePath = extractRemotePath(source, sourceCredentials.server, sourceCredentials.port)
        
        // Get destination output stream
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
                    Timber.e("FtpToLocalStrategy.copy: Unsupported destination scheme ${destination.scheme}")
                    return false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "FtpToLocalStrategy.copy: Failed to open destination")
            return false
        }
        
        // Connect to FTP
        val connectResult = ftpClient.connect(
            host = sourceCredentials.server,
            port = sourceCredentials.port,
            username = sourceCredentials.username,
            password = sourceCredentials.password
        )
        
        if (connectResult.isFailure) {
            Timber.e("FtpToLocalStrategy.copy: Connection failed")
            return false
        }
        
        return try {
            outputStream.use { output ->
                val downloadResult = ftpClient.downloadFile(
                    remotePath = sourceRemotePath,
                    outputStream = output,
                    fileSize = 0L,
                    progressCallback = progressCallback
                )
                downloadResult.isSuccess
            }
        } finally {
            ftpClient.disconnect()
        }
    }
    
    /**
     * Extract remote path from FTP URI
     * Example: ftp://server:21/path/to/file.txt -> /path/to/file.txt
     */
    private fun extractRemotePath(uri: Uri, host: String, port: Int): String {
        val full = uri.toString()
        val prefix = "ftp://$host:$port"
        return full.removePrefix(prefix).ifBlank { "/" }
    }
}
