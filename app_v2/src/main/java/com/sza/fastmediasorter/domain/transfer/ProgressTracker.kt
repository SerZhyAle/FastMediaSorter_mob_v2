package com.sza.fastmediasorter.domain.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized progress tracking with throttling to avoid UI overhead.
 *
 * S1225: the throttle map is a [ConcurrentHashMap] (callers report from arbitrary coroutines)
 * and is self-cleaning - a terminal report (last bytes / 100%) removes its own entry, and a
 * size-capped prune sweeps entries abandoned by failed or cancelled operations, so the
 * singleton no longer accumulates one entry per operation for the life of the process.
 */
@Singleton
class ProgressTracker @Inject constructor() {

    private companion object {
        // Throttle progress updates to every 100ms minimum.
        const val THROTTLE_MS = 100L

        // S1225: prune safety-net for operations that never reach a terminal report.
        const val PRUNE_THRESHOLD = 64
        val STALE_ENTRY_MS = TimeUnit.MINUTES.toMillis(1)
    }

    // Last update time per operation id; entries are removed on terminal reports and by pruning.
    private val lastUpdateTimes = ConcurrentHashMap<String, Long>()

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

        val percentage = if (total > 0) {
            ((current.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val isTerminal = current >= total || percentage == 100
        val shouldReport = isTerminal || current == 0L || throttleElapsed(operationId)
        if (!shouldReport) return

        withContext(Dispatchers.Main) {
            try {
                onProgress(percentage)
                recordReport(operationId, isTerminal)
            } catch (e: Exception) {
                Timber.e(e, "Error reporting progress for $operationId")
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

        val isTerminal = current >= total
        val shouldReport = isTerminal || current == 0L || throttleElapsed(operationId)
        if (!shouldReport) return

        withContext(Dispatchers.Main) {
            try {
                onProgress(current, total)
                recordReport(operationId, isTerminal)
            } catch (e: Exception) {
                Timber.e(e, "Error reporting byte progress for $operationId")
            }
        }
    }

    /**
     * Clear tracking for a completed operation - still the right call on abort/cancel
     * paths that never emit a terminal report.
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

    private fun throttleElapsed(operationId: String): Boolean {
        val now = System.currentTimeMillis()
        val lastUpdate = lastUpdateTimes[operationId] ?: 0L
        return now - lastUpdate >= THROTTLE_MS
    }

    /** Terminal reports remove their own entry; non-terminal ones refresh it and may prune. */
    private fun recordReport(operationId: String, isTerminal: Boolean) {
        if (isTerminal) {
            lastUpdateTimes.remove(operationId)
            return
        }
        val now = System.currentTimeMillis()
        lastUpdateTimes[operationId] = now
        if (lastUpdateTimes.size > PRUNE_THRESHOLD) {
            val cutoff = now - STALE_ENTRY_MS
            lastUpdateTimes.entries.removeIf { it.value < cutoff }
        }
    }
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
