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
class FtpToFtpStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ftpClient: FtpClient,
    private val credentialsResolver: NetworkCredentialsResolver
) : TransferStrategy {

    override fun supports(sourceScheme: String?, destScheme: String?): Boolean {
        return sourceScheme == "ftp" && destScheme == "ftp"
    }
    
    override suspend fun move(
        source: Uri,
        destination: Uri,
        overwrite: Boolean,
        sourceCredentialsId: String?,
        progressCallback: ByteProgressCallback?
    ): Boolean {
        // Resolve credentials
        val sourceCredentials = credentialsResolver.getCredentials(source.toString())
        if (sourceCredentials == null) {
            Timber.e("FtpToFtpStrategy.move: No credentials for source $source")
            return false
        }
        
        val destCredentials = credentialsResolver.getCredentials(destination.toString())
        if (destCredentials == null) {
            Timber.e("FtpToFtpStrategy.move: No credentials for destination $destination")
            return false
        }
        
        val sourceRemotePath = extractRemotePath(source, sourceCredentials.server, sourceCredentials.port)
        val destRemotePath = extractRemotePath(destination, destCredentials.server, destCredentials.port)
        
        // Check if same server - can use server-side rename
        val isSameServer = sourceCredentials.server == destCredentials.server &&
                          sourceCredentials.port == destCredentials.port &&
                          sourceCredentials.username == destCredentials.username
        
        if (isSameServer) {
            // Connect once for rename
            val connectResult = ftpClient.connect(
                host = sourceCredentials.server,
                port = sourceCredentials.port,
                username = sourceCredentials.username,
                password = sourceCredentials.password
            )
            
            if (connectResult.isFailure) {
                Timber.e("FtpToFtpStrategy.move: Connection failed")
                return false
            }
            
            return try {
                val moveResult = ftpClient.moveFile(sourceRemotePath, destRemotePath)
                if (moveResult.isSuccess) {
                    Timber.i("FtpToFtpStrategy.move: Server-side rename success")
                    true
                } else {
                    Timber.w("FtpToFtpStrategy.move: Server-side rename failed")
                    false
                }
            } finally {
                ftpClient.disconnect()
            }
        }
        
        // Cross-server: copy + delete
        val copySuccess = copy(source, destination, overwrite, sourceCredentialsId, progressCallback)
        if (!copySuccess) {
            Timber.e("FtpToFtpStrategy.move: Copy failed")
            return false
        }
        
        // Delete source after successful copy
        val connectResult = ftpClient.connect(
            host = sourceCredentials.server,
            port = sourceCredentials.port,
            username = sourceCredentials.username,
            password = sourceCredentials.password
        )
        
        if (connectResult.isFailure) {
            Timber.w("FtpToFtpStrategy.move: Cannot connect to delete source")
            return false
        }
        
        return try {
            ftpClient.deleteFile(sourceRemotePath).isSuccess
        } finally {
            ftpClient.disconnect()
        }
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
            Timber.e("FtpToFtpStrategy.copy: No credentials for source $source")
            return false
        }
        
        val destCredentials = credentialsResolver.getCredentials(destination.toString())
        if (destCredentials == null) {
            Timber.e("FtpToFtpStrategy.copy: No credentials for destination $destination")
            return false
        }
        
        val sourceRemotePath = extractRemotePath(source, sourceCredentials.server, sourceCredentials.port)
        val destRemotePath = extractRemotePath(destination, destCredentials.server, destCredentials.port)
        
        // Use temp file for transfer (download -> upload)
        val tempFile = java.io.File.createTempFile("ftp_transfer_", ".tmp", context.cacheDir)
        
        try {
            // Download from source
            val sourceConnectResult = ftpClient.connect(
                host = sourceCredentials.server,
                port = sourceCredentials.port,
                username = sourceCredentials.username,
                password = sourceCredentials.password
            )
            
            if (sourceConnectResult.isFailure) {
                Timber.e("FtpToFtpStrategy.copy: Source connection failed")
                return false
            }
            
            val downloadResult = try {
                tempFile.outputStream().use { outputStream ->
                    ftpClient.downloadFile(
                        remotePath = sourceRemotePath,
                        outputStream = outputStream,
                        fileSize = 0L,
                        progressCallback = progressCallback
                    )
                }
            } finally {
                ftpClient.disconnect()
            }
            
            if (!downloadResult.isSuccess) {
                Timber.e("FtpToFtpStrategy.copy: Download failed: ${downloadResult.exceptionOrNull()?.message}")
                return false
            }
            
            // Upload to destination
            val destConnectResult = ftpClient.connect(
                host = destCredentials.server,
                port = destCredentials.port,
                username = destCredentials.username,
                password = destCredentials.password
            )
            
            if (destConnectResult.isFailure) {
                Timber.e("FtpToFtpStrategy.copy: Destination connection failed")
                return false
            }
            
            val uploadResult = try {
                tempFile.inputStream().use { inputStream ->
                    ftpClient.uploadFile(
                        remotePath = destRemotePath,
                        inputStream = inputStream,
                        fileSize = tempFile.length(),
                        progressCallback = progressCallback
                    )
                }
            } finally {
                ftpClient.disconnect()
            }
            
            if (!uploadResult.isSuccess) {
                Timber.e("FtpToFtpStrategy.copy: Upload failed: ${uploadResult.exceptionOrNull()?.message}")
                return false
            }
            
            return true
        } finally {
            // Clean up temp file
            tempFile.delete()
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
