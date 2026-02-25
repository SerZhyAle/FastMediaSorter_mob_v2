package com.sza.fastmediasorter.core.metrics

import com.sza.fastmediasorter.domain.model.ResourceType
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory success/failure counters for key operations (C2-T2).
 *
 * Tracks:
 * - Connection test attempts (per [ResourceType])
 * - Resource save attempts (add / update)
 *
 * All counters are thread-safe via [AtomicLong]. The recorder is a Kotlin
 * object singleton so it can be called from any use case without injection,
 * following the same pattern as [ScanMetricsRecorder].
 *
 * ### Future: Firebase integration
 *
 * Replace [Timber] log calls with Firebase Analytics / Crashlytics custom events
 * when those SDKs are ready.
 */
object OperationMetricsRecorder {

    // ---- Connection test counters ----------------------------------------

    private val connectionTestSuccessTotal = AtomicLong(0)
    private val connectionTestFailureTotal = AtomicLong(0)

    // Per-type success/failure — keyed by ResourceType.ordinal for allocation-free increment.
    private val connectionTestSuccessByType = Array(ResourceType.entries.size) { AtomicLong(0) }
    private val connectionTestFailureByType = Array(ResourceType.entries.size) { AtomicLong(0) }

    // ---- Resource save counters -------------------------------------------

    private val resourceSaveSuccessTotal = AtomicLong(0)
    private val resourceSaveFailureTotal = AtomicLong(0)

    // -------------------------------------------------------------------------

    /**
     * Record one connection test result.
     *
     * @param resourceType  Resource type the test was performed against.
     * @param success       `true` → test passed; `false` → test failed.
     */
    fun recordConnectionTest(resourceType: ResourceType, success: Boolean) {
        val ordinal = resourceType.ordinal
        if (success) {
            connectionTestSuccessTotal.incrementAndGet()
            connectionTestSuccessByType[ordinal].incrementAndGet()
            Timber.d(
                "OperationMetrics: connection_test_ok type=${resourceType.name} " +
                    "total_ok=${connectionTestSuccessTotal.get()}"
            )
        } else {
            connectionTestFailureTotal.incrementAndGet()
            connectionTestFailureByType[ordinal].incrementAndGet()
            Timber.w(
                "OperationMetrics: connection_test_fail type=${resourceType.name} " +
                    "total_fail=${connectionTestFailureTotal.get()}"
            )
        }
    }

    /**
     * Record one resource save result (add or update).
     *
     * @param success `true` → saved successfully; `false` → save failed.
     */
    fun recordResourceSave(success: Boolean) {
        if (success) {
            resourceSaveSuccessTotal.incrementAndGet()
            Timber.d(
                "OperationMetrics: resource_save_ok " +
                    "total_ok=${resourceSaveSuccessTotal.get()}"
            )
        } else {
            resourceSaveFailureTotal.incrementAndGet()
            Timber.w(
                "OperationMetrics: resource_save_fail " +
                    "total_fail=${resourceSaveFailureTotal.get()}"
            )
        }
    }

    /**
     * Returns an immutable snapshot of all current counters.
     */
    fun snapshot(): OperationMetricsSnapshot = OperationMetricsSnapshot(
        connectionTestSuccess = connectionTestSuccessTotal.get(),
        connectionTestFailure = connectionTestFailureTotal.get(),
        resourceSaveSuccess   = resourceSaveSuccessTotal.get(),
        resourceSaveFailure   = resourceSaveFailureTotal.get(),
        connectionTestSuccessByType = connectionTestSuccessByType.map { it.get() },
        connectionTestFailureByType = connectionTestFailureByType.map { it.get() }
    )

    /** Resets all counters to zero. Intended for use in tests. */
    fun reset() {
        connectionTestSuccessTotal.set(0)
        connectionTestFailureTotal.set(0)
        resourceSaveSuccessTotal.set(0)
        resourceSaveFailureTotal.set(0)
        connectionTestSuccessByType.forEach { it.set(0) }
        connectionTestFailureByType.forEach { it.set(0) }
    }
}

/**
 * Immutable snapshot of all [OperationMetricsRecorder] counters at a point in time.
 *
 * @property connectionTestSuccessByType  Per-[ResourceType] success counts.
 *   Index matches [ResourceType.ordinal].
 * @property connectionTestFailureByType  Per-[ResourceType] failure counts.
 *   Index matches [ResourceType.ordinal].
 */
data class OperationMetricsSnapshot(
    val connectionTestSuccess: Long,
    val connectionTestFailure: Long,
    val resourceSaveSuccess: Long,
    val resourceSaveFailure: Long,
    val connectionTestSuccessByType: List<Long>,
    val connectionTestFailureByType: List<Long>
) {
    /** Total connection tests performed. */
    val connectionTestTotal: Long get() = connectionTestSuccess + connectionTestFailure

    /** Total resource saves attempted. */
    val resourceSaveTotal: Long get() = resourceSaveSuccess + resourceSaveFailure
}
