package com.sza.fastmediasorter.data.transfer

/**
 * Strategy for generating temporary filenames during atomic file transfers.
 * 
 * During atomic copy operations, files are first transferred with a temporary name
 * and then renamed to the final name after transfer completes. This prevents
 * external systems from detecting incomplete files.
 * 
 * **Naming Convention**: `{original_name}.temp_copy`
 * 
 * Examples:
 * - `photo.jpg` → `photo.jpg.temp_copy`
 * - `video.mp4` → `video.mp4.temp_copy`
 * - `archive.tar.gz` → `archive.tar.gz.temp_copy`
 * - `README` → `README.temp_copy`
 */
object TempFileNamingStrategy {
    
    private const val TEMP_SUFFIX = ".temp_copy"
    
    /**
     * Generate temporary filename for atomic transfer.
     * 
     * @param originalPath Original file path (full path or filename)
     * @return Temporary path with `.temp_copy` suffix
     * 
     * Examples:
     * - `/storage/photo.jpg` → `/storage/photo.jpg.temp_copy`
     * - `smb://server/share/video.mp4` → `smb://server/share/video.mp4.temp_copy`
     * - `ftp://host/path/file` → `ftp://host/path/file.temp_copy`
     */
    fun getTempPath(originalPath: String): String {
        return originalPath + TEMP_SUFFIX
    }
    
    /**
     * Extract original path from temporary filename.
     * 
     * @param tempPath Temporary path ending with `.temp_copy`
     * @return Original path, or same path if not a temp file
     * 
     * Examples:
     * - `photo.jpg.temp_copy` → `photo.jpg`
     * - `video.mp4` → `video.mp4` (unchanged)
     */
    fun getOriginalPath(tempPath: String): String {
        return if (isTempFile(tempPath)) {
            tempPath.removeSuffix(TEMP_SUFFIX)
        } else {
            tempPath
        }
    }
    
    /**
     * Check if path is a temporary transfer file.
     * 
     * @param path File path to check
     * @return true if path ends with `.temp_copy`
     */
    fun isTempFile(path: String): Boolean {
        return path.endsWith(TEMP_SUFFIX)
    }
    
    /**
     * Get filename (without path) from full path.
     * 
     * @param fullPath Full file path
     * @return Filename only
     * 
     * Examples:
     * - `/storage/photo.jpg` → `photo.jpg`
     * - `smb://server/share/video.mp4` → `video.mp4`
     */
    fun getFileName(fullPath: String): String {
        return fullPath.substringAfterLast('/')
    }
    
    /**
     * Get directory path (without filename) from full path.
     * 
     * @param fullPath Full file path
     * @return Directory path, or empty string if no directory
     * 
     * Examples:
     * - `/storage/photo.jpg` → `/storage`
     * - `photo.jpg` → ``
     * - `smb://server/share/video.mp4` → `smb://server/share`
     */
    fun getDirectoryPath(fullPath: String): String {
        val lastSlashIndex = fullPath.lastIndexOf('/')
        return if (lastSlashIndex >= 0) {
            fullPath.substring(0, lastSlashIndex)
        } else {
            ""
        }
    }
    
    /**
     * Generate temporary filename for a file in the same directory.
     * 
     * @param fullPath Full file path
     * @return Full temporary path
     * 
     * Examples:
     * - `/storage/photo.jpg` → `/storage/photo.jpg.temp_copy`
     * - `photo.jpg` → `photo.jpg.temp_copy`
     */
    fun getTempPathInSameDir(fullPath: String): String {
        return getTempPath(fullPath)
    }
}
