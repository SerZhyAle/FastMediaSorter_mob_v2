package com.sza.fastmediasorter.data.cloud

import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for resolving network credentials from paths.
 * 
 * Handles:
 * - Extracting server/port/share from SMB/SFTP/FTP URLs
 * - Looking up credentials from NetworkCredentialsRepository
 * - Converting to protocol-specific connection info
 */
@Singleton
class NetworkCredentialsResolver @Inject constructor(
    private val credentialsRepository: NetworkCredentialsRepository
) {

    /**
     * Universal network credentials container
     * @property server Server IP address
     * @property port Server port
     * @property username Username for authentication
     * @property password Password for authentication
     * @property shareName Share name (SMB only)
     * @property domain Domain name (SMB only)
     * @property privateKey SSH private key (SFTP only)
     */
    data class NetworkCredentials(
        val server: String,
        val port: Int,
        val username: String,
        val password: String,
        val shareName: String? = null,  // For SMB
        val domain: String? = null,      // For SMB
        val privateKey: String? = null   // For SFTP
    )

    /**
     * Get network credentials for resource by path
     * @param path Network resource path (smb://, sftp://, or ftp://)
     * @return NetworkCredentials if found, null otherwise
     */
    suspend fun getCredentials(path: String): NetworkCredentials? {
        val credentials = when {
            path.startsWith("smb://") -> resolveSmb(path)
            path.startsWith("sftp://") -> resolveSftp(path)
            path.startsWith("ftp://") -> resolveFtp(path)
            else -> {
                Timber.w("getCredentials: Unsupported path format: $path")
                return null
            }
        }
        
        if (credentials == null) {
            Timber.e("getCredentials: No credentials found for path $path")
        }
        
        return credentials
    }

    /**
     * Resolve SMB credentials from path
     * @param path SMB path (smb://server:port/share/path)
     * @return NetworkCredentials if found
     */
    private suspend fun resolveSmb(path: String): NetworkCredentials? {
        val parts = path.removePrefix("smb://").split("/", limit = 3)
        if (parts.size < 2) return null
        
        val serverWithPort = parts[0]
        val server = if (serverWithPort.contains(':')) {
            serverWithPort.substringBefore(':')
        } else {
            serverWithPort
        }
        val port = if (serverWithPort.contains(':')) {
            serverWithPort.substringAfter(':').toIntOrNull() ?: 445
        } else {
            445
        }
        val shareName = parts[1]
        
        // Try multiple lookup strategies for better matching
        val creds = credentialsRepository.getByServerAndShare(server, shareName)
            ?: credentialsRepository.getCredentialsByHost(server)
            ?: credentialsRepository.getByTypeServerAndPort("SMB", server, port)
        
        return creds?.let {
            NetworkCredentials(
                server = it.server,
                port = it.port,
                username = it.username,
                password = it.password,
                shareName = it.shareName,
                domain = it.domain,
                privateKey = null
            )
        }
    }

    /**
     * Resolve SFTP credentials from path
     * @param path SFTP path (sftp://server:port/path)
     * @return NetworkCredentials if found
     */
    private suspend fun resolveSftp(path: String): NetworkCredentials? {
        val parts = path.removePrefix("sftp://").split("/", limit = 2)
        if (parts.isEmpty()) return null
        
        val hostPort = parts[0].split(":", limit = 2)
        val host = hostPort[0]
        val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 22 else 22
        
        val creds = credentialsRepository.getByTypeServerAndPort("SFTP", host, port)
            ?: credentialsRepository.getCredentialsByHost(host)
        
        return creds?.let {
            NetworkCredentials(
                server = it.server,
                port = it.port,
                username = it.username,
                password = it.password,
                shareName = null,
                domain = null,
                privateKey = it.decryptedSshPrivateKey
            )
        }
    }

    /**
     * Resolve FTP credentials from path
     * @param path FTP path (ftp://server:port/path)
     * @return NetworkCredentials if found
     */
    private suspend fun resolveFtp(path: String): NetworkCredentials? {
        val parts = path.removePrefix("ftp://").split("/", limit = 2)
        if (parts.isEmpty()) return null
        
        val hostPort = parts[0].split(":", limit = 2)
        val host = hostPort[0]
        val port = if (hostPort.size > 1) hostPort[1].toIntOrNull() ?: 21 else 21
        
        val creds = credentialsRepository.getByTypeServerAndPort("FTP", host, port)
            ?: credentialsRepository.getCredentialsByHost(host)
        
        return creds?.let {
            NetworkCredentials(
                server = it.server,
                port = it.port,
                username = it.username,
                password = it.password,
                shareName = null,
                domain = null,
                privateKey = null
            )
        }
    }

    /**
     * Extract remote file path from SMB URL.
     * Handles both with and without port: smb://server/share/path and smb://server:port/share/path
     * 
     * @param smbPath Full SMB path like smb://192.168.1.100:445/common/file.jpg
     * @return Remote path within share like "file.jpg" or "folder/file.jpg"
     */
    fun extractSmbRemotePath(path: String): String {
        if (!path.startsWith("smb://")) return path
        
        val pathWithoutProtocol = path.substringAfter("smb://")
        val parts = pathWithoutProtocol.split("/", limit = 3)
        
        // parts[0] = server or server:port
        // parts[1] = share name
        // parts[2] = remote path (may be empty)
        return if (parts.size > 2) parts[2] else ""
    }

    /**
     * Convert NetworkCredentials to SmbConnectionInfo
     */
    fun NetworkCredentials.toSmbConnectionInfo(): SmbConnectionInfo {
        return SmbConnectionInfo(
            server = server,
            shareName = shareName ?: "",
            username = username,
            password = password,
            domain = domain ?: "",
            port = port
        )
    }

    /**
     * Convert NetworkCredentials to SftpConnectionInfo
     */
    fun NetworkCredentials.toSftpConnectionInfo(): SftpClient.SftpConnectionInfo {
        return SftpClient.SftpConnectionInfo(
            host = server,
            port = port,
            username = username,
            password = password,
            privateKey = privateKey
        )
    }
}
