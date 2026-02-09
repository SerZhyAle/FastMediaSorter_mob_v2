package com.sza.fastmediasorter.domain.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized progress tracking with throttling to avoid UI overhead.
 * Thread-safe for concurrent operations.
 */
@Singleton
class ProgressTracker @Inject constructor() {
    
    // Throttle progress updates to every 100ms minimum
    private val throttleMs = 100L
    
    // Track last update time per operation (by operation ID)
    private val lastUpdateTimes = mutableMapOf<String, Long>()
    
    /**
     * Report progress with throttling to avoid excessive UI updates.
     * Always reports first (0%) and last (100%) progress.
     * 
     * @param operationId Unique operation identifier
     * @param current Current bytes transferred
     * @param total Total bytes to transfer
     * @param onProgress Callback with percentage (0-100)
     */
    suspend fun reportProgress(
        operationId: String,
        current: Long,
        total: Long,
        onProgress: ((Int) -> Unit)?
    ) {
        if (onProgress == null) return
        
        val now = System.currentTimeMillis()
        val lastUpdate = lastUpdateTimes[operationId] ?: 0L
        val timeSinceLastUpdate = now - lastUpdate
        
        // Calculate percentage
        val percentage = if (total > 0) {
            ((current.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        
        // Always report first and last progress
        val isFirstOrLast = current == 0L || current >= total || percentage == 100
        
        // Report if throttle time passed or first/last
        if (isFirstOrLast || timeSinceLastUpdate >= throttleMs) {
            withContext(Dispatchers.Main) {
                try {
                    onProgress(percentage)
                    lastUpdateTimes[operationId] = now
                } catch (e: Exception) {
                    Timber.e(e, "Error reporting progress for $operationId")
                }
            }
        }
    }
    
    /**
     * Report progress with raw byte counts.
     * Convenience method for providers that work with byte streams.
     * 
     * @param operationId Unique operation identifier
     * @param current Current bytes transferred
     * @param total Total bytes to transfer
     * @param onProgress Callback with (bytesTransferred, totalBytes)
     */
    suspend fun reportProgressBytes(
        operationId: String,
        current: Long,
        total: Long,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        if (onProgress == null) return
        
        val now = System.currentTimeMillis()
        val lastUpdate = lastUpdateTimes[operationId] ?: 0L
        val timeSinceLastUpdate = now - lastUpdate
        
        // Always report first and last progress
        val isFirstOrLast = current == 0L || current >= total
        
        // Report if throttle time passed or first/last
        if (isFirstOrLast || timeSinceLastUpdate >= throttleMs) {
            withContext(Dispatchers.Main) {
                try {
                    onProgress(current, total)
                    lastUpdateTimes[operationId] = now
                } catch (e: Exception) {
                    Timber.e(e, "Error reporting byte progress for $operationId")
                }
            }
        }
    }
    
    /**
     * Clear tracking for completed operation.
     * Prevents memory leak from long-running app sessions.
     * 
     * @param operationId Operation identifier
     */
    fun clearOperation(operationId: String) {
        lastUpdateTimes.remove(operationId)
    }
    
    /**
     * Clear all tracking data.
     * Useful for cleanup or testing.
     */
    fun clearAll() {
        lastUpdateTimes.clear()
    }
    
    /**
     * Get number of tracked operations.
     */
    fun getTrackedOperationCount(): Int = lastUpdateTimes.size
}

/**
 * Generate unique operation ID for progress tracking.
 * 
 * @param operationType Operation type (e.g., "copy", "move", "download")
 * @param sourcePath Source file path
 * @param destPath Destination file path
 * @return Unique operation ID
 */
fun generateOperationId(
    operationType: String,
    sourcePath: String,
    destPath: String? = null
): String {
    val paths = if (destPath != null) "${sourcePath}_${destPath}" else sourcePath
    return "${operationType}_${paths.hashCode()}_${System.currentTimeMillis()}"
}
