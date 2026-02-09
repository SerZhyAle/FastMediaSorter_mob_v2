package com.sza.fastmediasorter.utils

import java.io.File

/**
 * Get the network-safe path from a File object.
 * 
 * The standard File.path property normalizes paths, which breaks network URLs:
 * - File("smb:///server/path").path returns "smb:/server/path" (single slash!)
 * - File("sftp:///server:22/path").path returns "sftp:/server:22/path"
 * 
 * This extension preserves the original path for network protocols by using
 * the File's toString() which doesn't normalize.
 * 
 * @return Original path string that preserves network URL format
 */
val File.networkPath: String
    get() {
        val original = this.toString()
        
        // If it's a network protocol, use toString() which preserves the original
        return when {
            original.startsWith("smb://") || original.startsWith("smb:/") -> original
            original.startsWith("sftp://") || original.startsWith("sftp:/") -> original
            original.startsWith("ftp://") || original.startsWith("ftp:/") -> original
            original.startsWith("cloud://") || original.startsWith("cloud:/") -> original
            else -> this.path // For local files, use standard path
        }
    }
