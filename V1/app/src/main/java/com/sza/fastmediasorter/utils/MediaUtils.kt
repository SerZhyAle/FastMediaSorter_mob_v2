package com.sza.fastmediasorter.utils

/**
 * Utility object providing media file detection, validation, and formatting functions.
 * Supports identification of image and video files by extension, handles Android content URIs,
 * and provides file size formatting for user-friendly display.
 */
object MediaUtils {
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "webm", "3gp")

    /**
     * Determines if a filename represents an image file based on its extension.
     * Handles both regular file paths and Android content URIs.
     *
     * @param filename The filename or URI to check
     * @return true if the file is an image, false otherwise
     */
    fun isImage(filename: String): Boolean {
        // Handle Android content URIs by extracting filename from URI
        val fileName =
            if (filename.startsWith("content://")) {
                // For content URIs, extract the display name from the URI
                filename.substringAfterLast('/', "").substringAfterLast("%2F", "")
            } else {
                // For regular file paths, use the filename as-is
                filename.substringAfterLast('/')
            }

        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS
    }

    /**
     * Determines if a filename represents a video file based on its extension.
     * Handles both regular file paths and Android content URIs.
     *
     * @param filename The filename or URI to check
     * @return true if the file is a video, false otherwise
     */
    fun isVideo(filename: String): Boolean {
        // Handle Android content URIs by extracting filename from URI
        val fileName =
            if (filename.startsWith("content://")) {
                // For content URIs, extract the display name from the URI
                filename.substringAfterLast('/', "").substringAfterLast("%2F", "")
            } else {
                // For regular file paths, use the filename as-is
                filename.substringAfterLast('/')
            }

        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in VIDEO_EXTENSIONS
    }

    // Alias methods for backward compatibility and testing
    fun isImageFile(filename: String): Boolean = isImage(filename)

    fun isVideoFile(filename: String): Boolean = isVideo(filename)

    fun getFileExtension(filename: String): String = filename.substringAfterLast('.', "")

    fun getImageExtensions(): Set<String> = IMAGE_EXTENSIONS

    fun getVideoExtensions(): Set<String> = VIDEO_EXTENSIONS

    fun getAllMediaExtensions(): Set<String> = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS

    /**
     * Formats a file size in bytes to a human-readable string.
     * Uses appropriate units (B, KB, MB, GB) with one decimal place for precision.
     *
     * @param bytes The file size in bytes
     * @return Formatted file size string (e.g., "1.5 MB", "256 KB")
     */
    fun formatFileSize(bytes: Long): String =
        when {
            bytes == 0L -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> {
                val kb = bytes / 1024.0
                if (kb == kb.toInt().toDouble()) "${kb.toInt()} KB" else "${"%.1f".format(kb)} KB"
            }
            bytes < 1024 * 1024 * 1024 -> {
                val mb = bytes / (1024.0 * 1024.0)
                if (mb == mb.toInt().toDouble()) "${mb.toInt()} MB" else "${"%.1f".format(mb)} MB"
            }
            else -> {
                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                "${"%.1f".format(gb)} GB"
            }
        }
}
