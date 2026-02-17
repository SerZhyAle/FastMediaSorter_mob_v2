package com.sza.fastmediasorter.data.transfer.strategies

import android.content.Context
import android.net.Uri
import com.sza.fastmediasorter.data.cloud.NetworkCredentialsResolver
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import com.sza.fastmediasorter.data.transfer.TransferStrategy
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpToSftpStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sftpClient: SftpClient,
    private val credentialsResolver: NetworkCredentialsResolver
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        return sourceScheme == "sftp" && destScheme == "sftp"
    }

    override suspend fun move(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // Resolve credentials for source and destination
        val sourceCredentials = credentialsResolver.getCredentials(source.toString())
        if (sourceCredentials == null) {
            Timber.e("SftpToSftpStrategy.move: No credentials for source $source")
            return false
        }
        
        val destCredentials = credentialsResolver.getCredentials(destination.toString())
        if (destCredentials == null) {
            Timber.e("SftpToSftpStrategy.move: No credentials for destination $destination")
            return false
        }
        
        val sourceRemotePath = extractRemotePath(source, sourceCredentials.server, sourceCredentials.port)
        val destRemotePath = extractRemotePath(destination, destCredentials.server, destCredentials.port)
        
        // Check if same server - can use server-side rename
        val isSameServer = sourceCredentials.server == destCredentials.server &&
                          sourceCredentials.port == destCredentials.port &&
                          sourceCredentials.username == destCredentials.username
        
        if (isSameServer) {
            // Native SFTP rename (server-side move)
            val connectionInfo = sourceCredentials.toSftpConnectionInfo()
            
            val renameResult = sftpClient.rename(connectionInfo, sourceRemotePath, destRemotePath)
            if (renameResult.isSuccess) {
                Timber.i("SftpToSftpStrategy.move: Server-side rename success")
                return true
            } else {
                Timber.w("SftpToSftpStrategy.move: Server-side rename failed, fallback to copy+delete")
            }
        }
        
        // Cross-server or rename failed: copy + delete
        val copySuccess = copy(source, destination, overwrite, sourceCredentialsId, progressCallback)
        if (!copySuccess) {
            Timber.e("SftpToSftpStrategy.move: Copy failed")
            return false
        }
        
        // Delete source after successful copy
        val sourceConnectionInfo = sourceCredentials.toSftpConnectionInfo()
        
        val deleteResult = sftpClient.deleteFile(sourceConnectionInfo, sourceRemotePath)
        if (!deleteResult.isSuccess) {
            Timber.w("SftpToSftpStrategy.move: Source delete failed after copy")
            return false
        }
        
        return true
    }

    override suspend fun copy(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // Resolve credentials
        val sourceCredentials = credentialsResolver.getCredentials(source.toString())
        if (sourceCredentials == null) {
            Timber.e("SftpToSftpStrategy.copy: No credentials for source $source")
            return false
        }
        
        val destCredentials = credentialsResolver.getCredentials(destination.toString())
        if (destCredentials == null) {
            Timber.e("SftpToSftpStrategy.copy: No credentials for destination $destination")
            return false
        }
        
        val sourceRemotePath = extractRemotePath(source, sourceCredentials.server, sourceCredentials.port)
        val destRemotePath = extractRemotePath(destination, destCredentials.server, destCredentials.port)
        
        // Use temp file for transfer (download -> upload)
        val tempFile = java.io.File.createTempFile("sftp_transfer_", ".tmp", context.cacheDir)
        
        try {
            // Download from source
            val sourceConnectionInfo = sourceCredentials.toSftpConnectionInfo()
            
            val downloadResult = tempFile.outputStream().use { outputStream ->
                sftpClient.downloadFile(
                    connectionInfo = sourceConnectionInfo,
                    remotePath = sourceRemotePath,
                    outputStream = outputStream,
                    fileSize = 0L, // Unknown size
                    progressCallback = progressCallback
                )
            }
            
            if (!downloadResult.isSuccess) {
                Timber.e("SftpToSftpStrategy.copy: Download failed: ${downloadResult.exceptionOrNull()?.message}")
                return false
            }
            
            // Upload to destination
            val destConnectionInfo = destCredentials.toSftpConnectionInfo()
            
            val uploadResult = tempFile.inputStream().use { inputStream ->
                sftpClient.uploadFile(
                    connectionInfo = destConnectionInfo,
                    remotePath = destRemotePath,
                    inputStream = inputStream,
                    fileSize = tempFile.length(),
                    progressCallback = progressCallback
                )
            }
            
            if (!uploadResult.isSuccess) {
                Timber.e("SftpToSftpStrategy.copy: Upload failed: ${uploadResult.exceptionOrNull()?.message}")
                return false
            }
            
            return true
        } finally {
            // Clean up temp file
            tempFile.delete()
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

    private fun NetworkCredentialsResolver.NetworkCredentials.toSftpConnectionInfo(): SftpClient.SftpConnectionInfo {
        return SftpClient.SftpConnectionInfo(
            host = server,
            port = port,
            username = username,
            password = password,
            privateKey = privateKey
        )
    }
}
