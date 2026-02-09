package com.sza.fastmediasorter.core.util

import com.sza.fastmediasorter.domain.usecase.ByteProgressCallback
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream

object InputStreamExt {
    // Optimized buffer size for network I/O (64KB reduces syscall overhead significantly)
    const val OPTIMIZED_BUFFER_SIZE = 65536
    
    /**
     * Copy InputStream to OutputStream with byte-level progress tracking.
     * Reports progress every ~100KB to avoid UI overhead.
     * 
     * @param output Target output stream
     * @param totalBytes Total number of bytes to copy (0 if unknown)
     * @param progressCallback Callback for progress updates
     * @param bufferSize Buffer size for copying (default 64KB for network efficiency)
     * @return Number of bytes copied
     */
    suspend fun InputStream.copyToWithProgress(
        output: OutputStream,
        totalBytes: Long = 0L,
        progressCallback: ByteProgressCallback? = null,
        bufferSize: Int = OPTIMIZED_BUFFER_SIZE
    ): Long {
        var bytesCopied = 0L
        var lastReportedBytes = 0L
        val buffer = ByteArray(bufferSize)
        var bytes = read(buffer)
        
        val startTime = System.currentTimeMillis()
        var lastReportTime = startTime
        
        while (bytes >= 0) {
            // Check for cancellation
            currentCoroutineContext().ensureActive()
            
            output.write(buffer, 0, bytes)
            bytesCopied += bytes
            
            // Report progress every ~100KB or at completion
            if (progressCallback != null && 
                (bytesCopied - lastReportedBytes >= ByteProgressCallback.PROGRESS_REPORT_INTERVAL || bytes == 0)) {
                
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - lastReportTime
                
                // Calculate speed (bytes per second)
                val speed = if (elapsedTime > 0) {
                    ((bytesCopied - lastReportedBytes) * 1000L) / elapsedTime
                } else {
                    0L
                }
                
                progressCallback.onProgress(bytesCopied, totalBytes, speed)
                
                lastReportedBytes = bytesCopied
                lastReportTime = currentTime
            }
            
            bytes = read(buffer)
        }
        
        // Final progress report
        if (progressCallback != null && bytesCopied > lastReportedBytes) {
            val totalTime = System.currentTimeMillis() - startTime
            val avgSpeed = if (totalTime > 0) {
                (bytesCopied * 1000L) / totalTime
            } else {
                0L
            }
            progressCallback.onProgress(bytesCopied, totalBytes, avgSpeed)
        }
        
        return bytesCopied
    }
}
