package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.domain.model.MediaType

/**
 * Utility for detecting binary file types by extension.
 * Used to display custom thumbnails and handle binary files appropriately.
 * 
 * Task 6: Binary file support
 */
object BinaryFileTypeDetector {
    
    private val ARCHIVES = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tar.gz", "tgz",
        "tbz2", "txz", "cab", "arj", "lzh", "ace", "zipx"
    )
    
    private val DISK_IMAGES = setOf(
        "iso", "dmg", "img", "vhd", "vdi", "qcow2", "vmdk", "toast"
    )
    
    private val EXECUTABLES = setOf(
        "exe", "dll", "apk", "so", "dylib", "msi", "deb", "rpm",
        "app", "bin", "run", "jar", "class"
    )
    
    private val DATABASES = setOf(
        "db", "sqlite", "sqlite3", "mdb", "accdb", "dbf"
    )
    
    private val OTHER = setOf(
        "bin", "dat", "tmp", "cache", "bak", "backup", "old", "swp"
    )
    
    /**
     * Detect MediaType for given extension
     * @param extension File extension without dot
     * @return Appropriate MediaType for binary files
     */
    fun detectType(extension: String): MediaType {
        return when (extension.lowercase()) {
            in ARCHIVES -> MediaType.BINARY_ARCHIVE
            in DISK_IMAGES -> MediaType.BINARY_DISK
            in EXECUTABLES -> MediaType.BINARY_EXECUTABLE
            in DATABASES, in OTHER -> MediaType.BINARY_OTHER
            else -> MediaType.BINARY_OTHER
        }
    }
    
    /**
     * Check if given extension is a binary file type
     * @param ext File extension without dot
     * @return true if extension is recognized as binary
     */
    fun isBinaryExtension(ext: String): Boolean {
        val lower = ext.lowercase()
        return lower in ARCHIVES ||
               lower in DISK_IMAGES ||
               lower in EXECUTABLES ||
               lower in DATABASES ||
               lower in OTHER
    }
    
    /**
     * Get display color for binary file type (for thumbnails)
     * @param type MediaType of binary file
     * @return Pair of gradient colors (start, end)
     */
    fun getColorForType(type: MediaType): Pair<Int, Int> {
        return when (type) {
            MediaType.BINARY_ARCHIVE -> Pair(0xFF1E88E5.toInt(), 0xFF1565C0.toInt()) // Blue
            MediaType.BINARY_DISK -> Pair(0xFF43A047.toInt(), 0xFF2E7D32.toInt())    // Green
            MediaType.BINARY_EXECUTABLE -> Pair(0xFFE53935.toInt(), 0xFFC62828.toInt()) // Red
            else -> Pair(0xFF757575.toInt(), 0xFF424242.toInt())  // Gray
        }
    }
}
