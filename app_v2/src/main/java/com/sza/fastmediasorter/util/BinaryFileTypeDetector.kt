package com.sza.fastmediasorter.util

import com.sza.fastmediasorter.domain.model.MediaType

/**
 * Utility for detecting binary file types by extension.
 * Used to display custom thumbnails and handle binary files appropriately.
 * 
 * Task 6: Binary file support
 */
object BinaryFileTypeDetector {
    
    internal val ARCHIVES = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tar.gz", "tgz",
        "tbz2", "txz", "cab", "arj", "lzh", "ace", "zipx"
    )

    internal val DISK_IMAGES = setOf(
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
        "bin", "dat", "tmp", "cache", "bak", "backup", "old", "swp", "avi"
    )
    
    /**
     * S1058: media types for [ARCHIVES] / [DISK_IMAGES] extensions, as defined by a registry -
     * IANA, freedesktop shared-mime-info, or Debian `mime.types`. Nothing is coined here: an
     * extension with no registry type is listed in [NO_REGISTRY_MIME] and resolved by the caller
     * instead, because a fabricated `application/<ext>` matches only wildcard intent filters and so
     * hides exactly the apps that declare a concrete type.
     *
     * Values are pinned here rather than read from `MimeTypeMap` so they stay identical across
     * API 23..35 - the platform map is a small hardcoded table before API 29 and a `mime.types`
     * merge after it.
     */
    internal val MIME_BY_EXTENSION = mapOf(
        "zip" to "application/zip",
        "rar" to "application/vnd.rar",
        "7z" to "application/x-7z-compressed",
        "tar" to "application/x-tar",
        "gz" to "application/gzip",
        "bz2" to "application/x-bzip2",
        "xz" to "application/x-xz",
        "cab" to "application/vnd.ms-cab-compressed",
        "iso" to "application/x-iso9660-image",
        "dmg" to "application/x-apple-diskimage",
    )

    /**
     * S1058: archive/disk extensions deliberately left out of [MIME_BY_EXTENSION] because no
     * registry defines a type for them - inventing `application/x-<ext>` would just re-create the
     * fabricated-MIME bug behind a respectable prefix. The set is explicit, not an omission, so the
     * completeness test can tell "decided to skip" from "forgot".
     *
     * `tar.gz` is unreachable on top of that: every caller passes a `substringAfterLast('.')`
     * result, which yields `gz` at most. Kept here only to keep the set arithmetic total.
     */
    internal val NO_REGISTRY_MIME = setOf(
        "tar.gz", "tgz", "tbz2", "txz", "arj", "lzh", "ace", "zipx",
        "img", "vhd", "vdi", "qcow2", "vmdk", "toast"
    )

    /**
     * Registry media type for a binary archive / disk-image extension.
     * @param extension File extension without dot
     * @return the registry type, or null when none is defined - callers fall back to the platform
     *   map and ultimately `application/octet-stream`. Never returns a coined type.
     */
    fun mimeTypeForExtension(extension: String): String? = MIME_BY_EXTENSION[extension.lowercase()]

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
