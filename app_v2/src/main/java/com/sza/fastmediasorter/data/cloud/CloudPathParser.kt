package com.sza.fastmediasorter.data.cloud

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for parsing and normalizing cloud storage paths.
 * 
 * Cloud path format: cloud://provider/folderId/fileId or cloud://provider/folderId/path/to/file.ext
 * 
 * Handles:
 * - Path normalization (cloud:/ → cloud://)
 * - Parsing provider, folderId, and fileId from paths
 * - Path validation
 */
@Singleton
class CloudPathParser @Inject constructor() {

    /**
     * Parse cloud path to extract provider, folderId, and fileId
     * @param path Cloud path (cloud://provider/folderId/fileId or cloud://provider/folderId/path/to/file.ext)
     * @return CloudPathInfo with parsed components, or null if invalid
     */
    fun parseCloudPath(path: String): CloudPathInfo? {
        try {
            val normalizedPath = normalizePath(path)
            if (!normalizedPath.startsWith("cloud://")) return null
            
            val withoutProtocol = normalizedPath.removePrefix("cloud://")
            val parts = withoutProtocol.split("/", limit = 3)
            
            if (parts.isEmpty()) return null
            
            val providerStr = parts[0].uppercase()
            val provider = when (providerStr) {
                "GOOGLE_DRIVE", "GDRIVE", "GOOGLE" -> CloudProvider.GOOGLE_DRIVE
                "DROPBOX" -> CloudProvider.DROPBOX
                "ONEDRIVE", "MSDRIVE" -> CloudProvider.ONEDRIVE
                else -> try {
                    CloudProvider.valueOf(providerStr)
                } catch (e: IllegalArgumentException) {
                    Timber.w("Unknown cloud provider: $providerStr")
                    return null
                }
            }
            
            val folderId = if (parts.size > 1) parts[1] else null
            
            // For Dropbox, fileId should be the full path including folderId
            // For Google Drive/OneDrive, fileId format: "folderId/filename" for name resolution
            val fileId = if (parts.size > 2) {
                when (provider) {
                    CloudProvider.DROPBOX -> {
                        // Include folderId + remaining path for Dropbox
                        "/" + parts.drop(1).joinToString("/")
                    }
                    CloudProvider.GOOGLE_DRIVE,
                    CloudProvider.ONEDRIVE -> {
                        // Encode as "folderId/filename" for name resolution in client
                        val filename = parts[2].substringAfterLast('/')
                        if (folderId != null) "$folderId/$filename" else filename
                    }
                }
            } else {
                folderId ?: ""
            }
            
            return CloudPathInfo(provider, folderId, fileId)
        } catch (e: Exception) {
            Timber.e(e, "Error parsing cloud path: $path. Stack trace: ${e.stackTraceToString()}")
            return null
        }
    }

    /**
     * Normalize cloud path by ensuring proper protocol format.
     * Converts cloud:/ to cloud://
     * @param path Cloud path to normalize
     * @return Normalized path with cloud://
     */
    fun normalizePath(path: String): String {
        return if (path.startsWith("cloud:/") && !path.startsWith("cloud://")) {
            path.replaceFirst("cloud:/", "cloud://")
        } else {
            path
        }
    }

    /**
     * Check if path is a valid cloud path
     * @param path Path to check
     * @return true if path starts with cloud:/ or cloud://
     */
    fun isCloudPath(path: String): Boolean {
        return path.startsWith("cloud:/") || path.startsWith("cloud://")
    }

    /**
     * Information extracted from cloud path
     * @property provider Cloud storage provider (GOOGLE_DRIVE, DROPBOX, ONEDRIVE)
     * @property folderId Parent folder ID, may be null for root
     * @property fileId File or folder ID
     */
    data class CloudPathInfo(
        val provider: CloudProvider,
        val folderId: String?,
        val fileId: String
    )
}
