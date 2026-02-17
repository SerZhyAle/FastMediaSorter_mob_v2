package com.sza.fastmediasorter.data.transfer.access

import android.net.Uri
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.transfer.FileAccess
import com.sza.fastmediasorter.data.cloud.NetworkCredentialsResolver
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SftpFileAccess @Inject constructor(
    private val sftpClient: SftpClient,
    private val credentialsResolver: NetworkCredentialsResolver
) : FileAccess {

    override fun supports(scheme: String?): Boolean {
        return scheme == "sftp"
    }


    override suspend fun exists(uri: Uri): Boolean {
        val credentials = credentialsResolver.getCredentials(uri.toString())
        if (credentials == null) {
            Timber.w("SftpFileAccess.exists: No credentials found for $uri")
            return false
        }
        
        val remotePath = extractRemotePath(uri, credentials.server, credentials.port)
        
        val connectionInfo = SftpClient.SftpConnectionInfo(
            host = credentials.server,
            port = credentials.port,
            username = credentials.username,
            password = credentials.password,
            privateKey = credentials.privateKey
        )
        
        return sftpClient.exists(connectionInfo, remotePath).getOrDefault(false)
    }

    override suspend fun delete(uri: Uri): Boolean {
        val credentials = credentialsResolver.getCredentials(uri.toString())
        if (credentials == null) {
            Timber.w("SftpFileAccess.delete: No credentials found for $uri")
            return false
        }
        
        val remotePath = extractRemotePath(uri, credentials.server, credentials.port)
        
        val connectionInfo = SftpClient.SftpConnectionInfo(
            host = credentials.server,
            port = credentials.port,
            username = credentials.username,
            password = credentials.password,
            privateKey = credentials.privateKey
        )
        
        return sftpClient.deleteFile(connectionInfo, remotePath).isSuccess
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
