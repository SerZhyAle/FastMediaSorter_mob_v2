package com.sza.fastmediasorter.utils

import timber.log.Timber

/**
 * Utility class for parsing FTP paths.
 * Provides consistent path handling across the application.
 */
object FtpPathUtils {

    /**
     * Result of parsing an FTP path containing connection details and remote path.
     */
    data class FtpPathInfo(
        val host: String,
        val port: Int,
        val remotePath: String
    )

    /**
     * Parse FTP path format: ftp://host:port/path/to/file
     * 
     * @param path The FTP path to parse
     * @return FtpPathInfo containing host, port, and remote path, or null if invalid
     */
    fun parseFtpPath(path: String): FtpPathInfo? {
        return try {
            val normalizedPath = normalizeFtpPath(path)
            if (!normalizedPath.startsWith("ftp://")) {
                Timber.w("Invalid FTP path format (missing ftp://): $path")
                return null
            }

            val withoutProtocol = normalizedPath.removePrefix("ftp://")
            
            // Split by first '/' to separate host[:port] from path
            val firstSlash = withoutProtocol.indexOf('/')
            if (firstSlash == -1) {
                Timber.w("Invalid FTP path format (no path separator): $path")
                return null
            }
            
            val hostPart = withoutProtocol.substring(0, firstSlash)
            val pathPart = withoutProtocol.substring(firstSlash + 1) // Remove leading /
            
            // Parse host:port
            // Handle optional username in host part (user@host[:port])
            val cleanHostPart = if (hostPart.contains('@')) {
                hostPart.substringAfterLast('@')
            } else {
                hostPart
            }

            val (host, port) = if (cleanHostPart.contains(':')) {
                val parts = cleanHostPart.split(':', limit = 2)
                parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 21)
            } else {
                cleanHostPart to 21
            }

            if (host.isEmpty()) {
                Timber.w("Invalid FTP path format (missing host): $path")
                return null
            }

            FtpPathInfo(
                host = host,
                port = port,
                remotePath = pathPart
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing FTP path: $path")
            null
        }
    }

    /**
     * Build a full FTP path from components.
     * 
     * @param host Server IP
     * @param path Remote path
     * @param port FTP port (default 21)
     * @return Full FTP URL (ftp://host:port/path)
     */
    fun buildFtpPath(
        host: String,
        path: String,
        port: Int = 21
    ): String {
        val normalizedPath = normalizeFtpPath(path)
        val cleanPath = if (normalizedPath.startsWith("/")) normalizedPath.substring(1) else normalizedPath
        return if (port != 21) {
            "ftp://$host:$port/$cleanPath"
        } else {
            "ftp://$host/$cleanPath"
        }
    }

    /**
     * Normalize FTP path by ensuring proper format.
     * Replaces backslashes with forward slashes and removes duplicate slashes.
     * 
     * @param path Path to normalize
     * @return Normalized path
     */
    fun normalizeFtpPath(path: String): String {
        val normalized = path.replace('\\', '/')

        // Preserve scheme separator for FTP URLs.
        // java.io.File and various concatenations in the app may produce malformed variants like:
        // - ftp:/host/path
        // - ftp:////host//path
        // We normalize them to: ftp://host/path
        return when {
            normalized.startsWith("ftp://", ignoreCase = true) -> {
                val rest = normalized.substringAfter("ftp://")
                "ftp://" + rest.replace(Regex("/+"), "/")
            }
            normalized.startsWith("ftp:/", ignoreCase = true) -> {
                val rest = normalized.substringAfter("ftp:/").trimStart('/')
                "ftp://" + rest.replace(Regex("/+"), "/")
            }
            else -> normalized
        }
    }
}
