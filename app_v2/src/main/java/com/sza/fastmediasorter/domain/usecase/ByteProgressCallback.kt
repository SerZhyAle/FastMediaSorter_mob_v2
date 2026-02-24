package com.sza.fastmediasorter.domain.usecase

/**
 * Callback interface for tracking byte-level progress during file operations.
 * Reports progress every ~100KB to avoid UI overhead.
 */
interface ByteProgressCallback {
    /**
     * Called periodically during file transfer to report progress
     * @param bytesTransferred Number of bytes transferred so far
     * @param totalBytes Total number of bytes to transfer (0 if unknown)
     * @param speedBytesPerSecond Current transfer speed in bytes per second
     */
    suspend fun onProgress(bytesTransferred: Long, totalBytes: Long, speedBytesPerSecond: Long)

    /**
     * Called once at the start of each file being processed.
     * Used to update the file counter in progress UI.
     * @param index 1-based index of the current file
     * @param fileName Name of the current file
     * @param total Total number of files in the operation
     */
    suspend fun onFileStarted(index: Int, fileName: String, total: Int) {}

    companion object {
        /**
         * Report progress every 100KB to avoid too frequent UI updates
         */
        const val PROGRESS_REPORT_INTERVAL = 100 * 1024L // 100KB
    }
}
