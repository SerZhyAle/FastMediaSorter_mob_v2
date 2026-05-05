package com.sza.fastmediasorter.core.metrics

import com.sza.fastmediasorter.domain.model.ResourceType
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory scan metrics recorder for timing instrumentation (C2-T1).
 *
 * Records scan duration, file count, and resource type for each completed scan.
 * Keeps the last [MAX_HISTORY] events in a thread-safe list.
 *
 * Follows the same object pattern as [com.sza.fastmediasorter.core.cache.MediaFilesCacheManager]
 * so it can be called without injection from use cases.
 *
 * ### Usage
 *
 * ```kotlin
 * val token  = ScanMetricsRecorder.beginScan(resource.id, resource.type, resource.fileCount)
 * val files  = scanner.scanFolder(...)
 * ScanMetricsRecorder.endScan(token, files.size)
 * ```
 *
 * ### Future: Firebase integration
 *
 * Replace the Timber log in [endScan] with a Firebase Analytics or
 * Firebase Performance custom trace when those SDKs are added to the project.
 */
object ScanMetricsRecorder {

    private val _events = CopyOnWriteArrayList<ScanEvent>()

    /** All recorded scan events (most recent last). */
    val events: List<ScanEvent> get() = _events.toList()

    /**
     * Starts a scan timer.
     *
     * @param resourceId  ID of the resource being scanned.
     * @param resourceType Type of the resource (LOCAL / SMB / CLOUD etc.).
     * @return Opaque [ScanToken] to pass to [endScan].
     */
    fun beginScan(resourceId: Long, resourceType: ResourceType, expectedFileCount: Int = 0): ScanToken {
        val startMs = System.currentTimeMillis()
        Timber.d(
            "ScanMetrics: begin scan resourceId=$resourceId type=${resourceType.name} " +
                "expected_file_count=$expectedFileCount"
        )
        return ScanToken(
            resourceId = resourceId,
            resourceType = resourceType,
            startMs = startMs,
            expectedFileCount = expectedFileCount
        )
    }

    /**
     * Finalises a scan timer, records the event, and logs structured timing info.
     *
     * @param token      Token returned by [beginScan].
     * @param fileCount  Number of files returned by the scan.
     */
    fun endScan(token: ScanToken, fileCount: Int) {
        val durationMs = System.currentTimeMillis() - token.startMs
        val event = ScanEvent(
            resourceId   = token.resourceId,
            resourceType = token.resourceType,
            durationMs   = durationMs,
            fileCount    = fileCount,
            timestampMs  = System.currentTimeMillis()
        )
        _events.add(event)
        if (_events.size > MAX_HISTORY) {
            _events.removeAt(0)
        }

        // Structured Timber log — easy to grep / send to analytics later.
        Timber.d(
            "ScanMetrics: scan_complete " +
                "resourceId=${token.resourceId} " +
                "type=${token.resourceType.name} " +
                "duration_ms=$durationMs " +
                "file_count=$fileCount"
        )

        val thresholdMs = effectiveThreshold(token.expectedFileCount)
        if (durationMs > thresholdMs) {
            Timber.w(
                "ScanMetrics: SLOW SCAN detected — ${durationMs}ms " +
                    "threshold=${thresholdMs}ms " +
                    "expected_file_count=${token.expectedFileCount} " +
                    "actual_file_count=$fileCount " +
                    "resourceId=${token.resourceId} " +
                    "type=${token.resourceType.name}"
            )
        }
    }

    /**
     * Returns aggregate statistics across all recorded events.
     * Returns null when no events have been recorded yet.
     */
    fun summary(): ScanMetricsSummary? {
        val snapshot = _events.toList()
        if (snapshot.isEmpty()) return null
        val sorted = snapshot.sortedBy { it.durationMs }
        return ScanMetricsSummary(
            totalScans   = sorted.size,
            medianMs     = sorted[sorted.size / 2].durationMs,
            p95Ms        = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)].durationMs,
            maxMs        = sorted.last().durationMs,
            minMs        = sorted.first().durationMs,
            avgFileCount = sorted.map { it.fileCount }.average().toLong()
        )
    }

    /** Clears all recorded events. Intended for use in tests. */
    fun reset() {
        _events.clear()
    }

    // WHY: empirical 2-3x parallelism baseline gives ~0.4 ms / file (S0056 §6.2).
    private fun effectiveThreshold(expectedFileCount: Int): Long =
        maxOf(SLOW_SCAN_BASE_THRESHOLD_MS, (expectedFileCount * SLOW_SCAN_PER_FILE_MS).toLong())

    private const val MAX_HISTORY = 200
    private const val SLOW_SCAN_BASE_THRESHOLD_MS = 6_000L
    private const val SLOW_SCAN_PER_FILE_MS = 0.4
}

/** Opaque scan timer token returned by [ScanMetricsRecorder.beginScan]. */
data class ScanToken(
    val resourceId: Long,
    val resourceType: ResourceType,
    val startMs: Long,
    val expectedFileCount: Int
)

/** A single completed scan measurement. */
data class ScanEvent(
    val resourceId: Long,
    val resourceType: ResourceType,
    val durationMs: Long,
    val fileCount: Int,
    val timestampMs: Long
)

/** Aggregate statistics over all recorded [ScanEvent]s. */
data class ScanMetricsSummary(
    val totalScans: Int,
    val medianMs: Long,
    val p95Ms: Long,
    val maxMs: Long,
    val minMs: Long,
    val avgFileCount: Long
)
