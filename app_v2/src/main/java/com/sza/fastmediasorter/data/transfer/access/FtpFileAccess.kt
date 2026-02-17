package com.sza.fastmediasorter.data.transfer.access

import android.net.Uri
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.transfer.FileAccess
import com.sza.fastmediasorter.data.cloud.NetworkCredentialsResolver
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FtpFileAccess @Inject constructor(
    private val ftpClient: FtpClient,
    private val credentialsResolver: NetworkCredentialsResolver
) : FileAccess {

    override fun supports(scheme: String?): Boolean {
        return scheme == "ftp"
    }


    override suspend fun exists(uri: Uri): Boolean {
        val credentials = credentialsResolver.getCredentials(uri.toString())
        if (credentials == null) {
            Timber.w("FtpFileAccess.exists: No credentials found for $uri")
            return false
        }
        
        val remotePath = extractRemotePath(uri, credentials.server, credentials.port)
        
        // FTP requires explicit connect/disconnect
        val connectResult = ftpClient.connect(
            host = credentials.server,
            port = credentials.port,
            username = credentials.username,
            password = credentials.password
        )
        
        if (connectResult.isFailure) {
            Timber.e("FtpFileAccess.exists: Connection failed for ${credentials.server}")
            return false
        }
        
        return try {
            val parentPath = remotePath.substringBeforeLast('/', "").ifBlank { "/" }
            val fileName = remotePath.substringAfterLast('/', remotePath)
            val result = ftpClient.listFilesWithMetadata(parentPath, recursive = false)
            result.getOrNull()?.any { it.name == fileName } == true
        } catch (e: Exception) {
            Timber.e(e, "FtpFileAccess.exists: Check failed for $remotePath")
            false
        } finally {
            ftpClient.disconnect()
        }
    }

    override suspend fun delete(uri: Uri): Boolean {
        val credentials = credentialsResolver.getCredentials(uri.toString())
        if (credentials == null) {
            Timber.w("FtpFileAccess.delete: No credentials found for $uri")
            return false
        }
        
        val remotePath = extractRemotePath(uri, credentials.server, credentials.port)
        
        // FTP requires explicit connect/disconnect
        val connectResult = ftpClient.connect(
            host = credentials.server,
            port = credentials.port,
            username = credentials.username,
            password = credentials.password
        )
        
        if (connectResult.isFailure) {
            Timber.e("FtpFileAccess.delete: Connection failed for ${credentials.server}")
            return false
        }
        
        return try {
            ftpClient.deleteFile(remotePath).isSuccess
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
