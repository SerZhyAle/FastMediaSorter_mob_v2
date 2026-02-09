package com.sza.fastmediasorter.utils

import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.model.SmbConnectionInfo
import timber.log.Timber

/**
 * Utility class for parsing SMB paths.
 * Provides consistent path handling across the application.
 */
object SmbPathUtils {

    /**
     * Result of parsing an SMB path containing connection info and remote path.
     */
    data class SmbPathInfo(
        val connectionInfo: SmbConnectionInfo,
        val remotePath: String
    )

    /**
     * Parse SMB path format: smb://server:port/share/path/to/file
     * 
     * @param path The SMB path to parse
     * @param username Optional username for authentication
     * @param password Optional password for authentication
     * @param domain Optional domain for authentication
     * @return SmbPathInfo containing connection info and remote path, or null if invalid
     */
    fun parseSmbPath(
        path: String,
        username: String = "",
        password: String = "",
        domain: String = ""
    ): SmbPathInfo? {
        return try {
            if (!path.startsWith("smb:", ignoreCase = true)) {
                Timber.w("Invalid SMB path format (missing smb:): $path")
                return null
            }

            // Normalize path: replace backslashes with forward slashes
            val normalizedPath = path.replace('\\', '/')
            // Remove protocol and leading slashes to get "server/share/..."
            // substringAfter("smb:") handles "smb://", "smb:/", "smb:"
            val withoutProtocol = normalizedPath.substringAfter("smb:", "").trimStart('/')
            val parts = withoutProtocol.split("/", limit = 2)

            if (parts.isEmpty()) {
                Timber.w("Invalid SMB path format (empty path): $path")
                return null
            }

            val serverPart = parts[0]
            val pathPart = if (parts.size > 1) parts[1] else ""

            // Parse server:port
            val serverPort = serverPart.split(":", limit = 2)
            val server = serverPort[0]
            val port = if (serverPort.size > 1) serverPort[1].toIntOrNull() ?: 445 else 445

            // Parse share/path
            val pathParts = pathPart.split("/", limit = 2)
            val share = if (pathParts.isNotEmpty()) pathParts[0] else ""
            val remotePath = if (pathParts.size > 1) pathParts[1] else ""

            if (server.isEmpty() || share.isEmpty()) {
                Timber.w("Invalid SMB path format (missing server or share): $path")
                return null
            }

            SmbPathInfo(
                connectionInfo = SmbConnectionInfo(
                    server = server,
                    shareName = share,
                    username = username,
                    password = password,
                    domain = domain,
                    port = port
                ),
                remotePath = remotePath
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing SMB path: $path")
            null
        }
    }

    /**
     * Build a full SMB path from components.
     * 
     * @param server Server IP
     * @param share Share name
     * @param path Path within the share
     * @param port SMB port (default 445)
     * @return Full SMB URL (smb://server:port/share/path)
     */
    fun buildSmbPath(
        server: String,
        share: String,
        path: String,
        port: Int = 445
    ): String {
        val cleanPath = path.trimStart('/')
        return if (port == 445) {
            "smb://$server/$share/$cleanPath"
        } else {
            "smb://$server:$port/$share/$cleanPath"
        }
    }

    /**
     * Extract server from SMB path.
     * 
     * @param path SMB path (smb://server:port/share/path)
     * @return Server IP or null if invalid
     */
    fun extractServer(path: String): String? {
        val info = parseSmbPath(path) ?: return null
        return info.connectionInfo.server
    }

    /**
     * Extract share name from SMB path.
     * 
     * @param path SMB path (smb://server:port/share/path)
     * @return Share name or null if invalid
     */
    fun extractShare(path: String): String? {
        val info = parseSmbPath(path) ?: return null
        return info.connectionInfo.shareName
    }

    /**
     * Extract remote path (path within share) from SMB path.
     * 
     * @param path SMB path (smb://server:port/share/path)
     * @return Remote path or null if invalid
     */
    fun extractRemotePath(path: String): String? {
        val info = parseSmbPath(path) ?: return null
        return info.remotePath
    }

    /**
     * Check if two SMB paths are on the same server and share.
     * This is useful for determining if a rename operation is possible.
     * 
     * @param path1 First SMB path
     * @param path2 Second SMB path
     * @return True if both paths are on the same server and share
     */
    fun isSameShare(path1: String, path2: String): Boolean {
        val info1 = parseSmbPath(path1) ?: return false
        val info2 = parseSmbPath(path2) ?: return false
        return info1.connectionInfo.server == info2.connectionInfo.server &&
               info1.connectionInfo.shareName == info2.connectionInfo.shareName
    }

    /**
     * Get the parent directory path of an SMB path.
     * 
     * @param path SMB path
     * @return Parent directory path or null if at root
     */
    fun getParentPath(path: String): String? {
        val info = parseSmbPath(path) ?: return null
        val remotePath = info.remotePath
        if (remotePath.isEmpty()) return null
        
        val lastSlash = remotePath.lastIndexOf('/')
        val parentRemotePath = if (lastSlash > 0) remotePath.substring(0, lastSlash) else ""
        
        return buildSmbPath(
            info.connectionInfo.server,
            info.connectionInfo.shareName,
            parentRemotePath,
            info.connectionInfo.port
        )
    }

    /**
     * Get the filename from an SMB path.
     * 
     * @param path SMB path
     * @return Filename or empty string if at root
     */
    fun getFileName(path: String): String {
        val info = parseSmbPath(path) ?: return ""
        val remotePath = info.remotePath
        return remotePath.substringAfterLast('/').ifEmpty { remotePath }
    }
}
