package com.sza.fastmediasorter.utils

import timber.log.Timber

/**
 * Utility class for parsing SFTP paths.
 * Provides consistent path handling across the application.
 */
object SftpPathUtils {

    /**
     * Result of parsing an SFTP path containing connection details and remote path.
     */
    data class SftpPathInfo(
        val host: String,
        val port: Int,
        val remotePath: String
    )

    /**
     * Parse SFTP path format: sftp://host:port/path/to/file
     * 
     * @param path The SFTP path to parse
     * @return SftpPathInfo containing host, port, and remote path, or null if invalid
     */
    fun parseSftpPath(path: String): SftpPathInfo? {
        return try {
            val normalizedPath = normalizeSftpPath(path)
            if (!normalizedPath.startsWith("sftp://")) {
                Timber.w("Invalid SFTP path format (missing sftp://): $path")
                return null
            }

            val withoutProtocol = normalizedPath.removePrefix("sftp://")
            
            // Split by first '/' to separate host[:port] from path
            val firstSlash = withoutProtocol.indexOf('/')
            if (firstSlash == -1) {
                Timber.w("Invalid SFTP path format (no path separator): $path")
                return null
            }
            
            val hostPart = withoutProtocol.substring(0, firstSlash)
            val pathPart = withoutProtocol.substring(firstSlash) // Keep leading /
            
            // Parse host:port
            // Handle optional username in host part (user@host[:port])
            val cleanHostPart = if (hostPart.contains('@')) {
                hostPart.substringAfterLast('@')
            } else {
                hostPart
            }

            val (host, port) = if (cleanHostPart.contains(':')) {
                val parts = cleanHostPart.split(':', limit = 2)
                parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 22)
            } else {
                cleanHostPart to 22
            }

            if (host.isEmpty()) {
                Timber.w("Invalid SFTP path format (missing host): $path")
                return null
            }

            SftpPathInfo(
                host = host,
                port = port,
                remotePath = pathPart
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing SFTP path: $path")
            null
        }
    }

    /**
     * Build a full SFTP path from components.
     * 
     * @param host Server IP
     * @param path Remote path
     * @param port SFTP port (default 22)
     * @return Full SFTP URL (sftp://host:port/path)
     */
    fun buildSftpPath(
        host: String,
        path: String,
        port: Int = 22
    ): String {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        return if (port != 22) {
            "sftp://$host:$port$normalizedPath"
        } else {
            "sftp://$host$normalizedPath"
        }
    }

    /**
     * Normalize SFTP path by ensuring proper format.
     * 
     * @param path Path to normalize
     * @return Normalized path
     */
    fun normalizeSftpPath(path: String): String {
        val normalized = path.replace('\\', '/')
        
        return when {
            normalized.startsWith("sftp://", ignoreCase = true) -> {
                val rest = normalized.substringAfter("sftp://")
                "sftp://" + rest.replace(Regex("/+"), "/")
            }
            normalized.startsWith("sftp:/", ignoreCase = true) -> {
                val rest = normalized.substringAfter("sftp:/").trimStart('/')
                "sftp://" + rest.replace(Regex("/+"), "/")
            }
            else -> normalized
        }
    }
}
