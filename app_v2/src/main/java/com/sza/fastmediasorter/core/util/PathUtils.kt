package com.sza.fastmediasorter.core.util

import android.net.Uri

/**
 * Utility functions for safe path handling.
 * 
 * Addresses issues with special characters in file paths:
 * - # (hash) - treated as fragment identifier by Uri.parse
 * - ? (question mark) - treated as query string start
 * - % (percent) - URL encoding character
 */
object PathUtils {
    
    /**
     * Safely parse a file path as Uri, encoding special characters that would
     * otherwise be misinterpreted.
     * 
     * The main issue is that Uri.parse treats # as fragment identifier,
     * causing paths like "file#1.pdf" to be truncated to "file".
     * 
     * @param path The file path (can be SMB, SFTP, FTP, Cloud, or local path)
     * @return Parsed Uri with special characters properly encoded
     */
    fun safeParseUri(path: String): Uri {
        // Encode # before parsing to prevent it being treated as fragment identifier
        // Also encode ? to prevent it being treated as query string start
        val encodedPath = path
            .replace("#", "%23")
            .replace("?", "%3F")
        return Uri.parse(encodedPath)
    }
    
    /**
     * Check if a path contains characters that would cause Uri.parse issues.
     */
    fun hasProblematicCharacters(path: String): Boolean {
        return path.contains("#") || path.contains("?")
    }
    
    /**
     * Extract scheme from path without full Uri parsing.
     * Safe for paths with special characters.
     */
    fun getScheme(path: String): String? {
        val colonIndex = path.indexOf("://")
        return if (colonIndex > 0) path.substring(0, colonIndex) else null
    }
    
    /**
     * Check if path is a network path (SMB, SFTP, FTP, Cloud).
     */
    fun isNetworkPath(path: String): Boolean {
        val scheme = getScheme(path)?.lowercase()
        return scheme in listOf("smb", "sftp", "ftp", "cloud")
    }
    
    /**
     * Check if path is a content URI (SAF).
     */
    fun isContentUri(path: String): Boolean {
        return path.startsWith("content://")
    }
    
    /**
     * Check if path is a local file path.
     */
    fun isLocalPath(path: String): Boolean {
        return path.startsWith("/") || path.startsWith("file://")
    }
}
