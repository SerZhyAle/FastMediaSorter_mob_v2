package com.sza.fastmediasorter.domain.usecase

/**
 * Callback interface for reporting progress during media scanning operations
 */
interface ScanProgressCallback {
    /**
     * Called periodically during scanning with current progress
     * @param scannedCount Number of files scanned so far
     * @param currentFile Name of current file being processed (optional)
     */
    suspend fun onProgress(scannedCount: Int, currentFile: String? = null)
    
    /**
     * Called when scanning is complete
     * @param totalFiles Total number of media files found
     * @param durationMs Time taken for scanning in milliseconds
     */
    suspend fun onComplete(totalFiles: Int, durationMs: Long)
    
    /**
     * Called by scanner to check if it should stop gracefully
     * @return true if scanner should stop and return partial results
     */
    fun shouldStop(): Boolean = false
}
