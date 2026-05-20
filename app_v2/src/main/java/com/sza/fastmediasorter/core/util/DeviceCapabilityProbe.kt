package com.sza.fastmediasorter.core.util

import android.content.Context
import android.os.StatFs
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Reports the device's real byte budget for pre-cache buffers.
 *
 * Three inputs are combined into a single [cacheBudgetBytes] value that
 * [com.sza.fastmediasorter.domain.playback.PrefetchFormula] uses as the hard ceiling
 * for `maxBufferSec`:
 *
 * - JVM heap remaining - 15% of free heap, to leave headroom for decoded frames,
 *   bitmaps, and transient allocations that spike during playback.
 * - App cache storage - 25% of free bytes on the internal cache-dir filesystem.
 * - Absolute cap - 256 MB regardless of how large the device is. Beyond that,
 *   the benefit of more pre-cache is dominated by OOM risk and wasted RAM.
 *
 * The [MemoryTier] is reused as a coarse label for the derivation-info dialog,
 * but is NOT used as a multiplicative factor in the formula (see ADR-4).
 */
@Singleton
class DeviceCapabilityProbe @Inject constructor(
    private val context: Context
) {

    /**
     * Snapshot of the current device budget. Recomputed on each call because
     * free heap and storage change over a session's lifetime.
     */
    data class Budget(
        val cacheBudgetBytes: Long,
        val freeHeapBytes: Long,
        val freeStorageBytes: Long,
        val memoryTier: MemoryTier
    )

    fun currentBudget(): Budget {
        val runtime = Runtime.getRuntime()
        val maxHeap = runtime.maxMemory()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        val freeHeap = (maxHeap - usedHeap).coerceAtLeast(0L)

        val freeStorage = try {
            val stat = StatFs(context.cacheDir.absolutePath)
            stat.availableBytes
        } catch (t: Throwable) {
            Timber.w(t, "DeviceCapabilityProbe: StatFs failed - falling back to 0 free storage")
            0L
        }

        val heapPortion = (freeHeap.toDouble() * HEAP_FRACTION).toLong()
        val storagePortion = (freeStorage.toDouble() * STORAGE_FRACTION).toLong()
        val budget = min(min(heapPortion, storagePortion), ABSOLUTE_CAP_BYTES)

        val tier = MemoryTier.detect(context)

        Timber.v(
            "DeviceCapabilityProbe: freeHeap=%dMB, freeStorage=%dMB, tier=%s → budget=%dMB",
            freeHeap / MB, freeStorage / MB, tier, budget / MB
        )

        return Budget(
            cacheBudgetBytes = budget,
            freeHeapBytes = freeHeap,
            freeStorageBytes = freeStorage,
            memoryTier = tier
        )
    }

    companion object {
        internal const val HEAP_FRACTION = 0.15
        internal const val STORAGE_FRACTION = 0.25
        internal const val ABSOLUTE_CAP_BYTES = 256L * 1024 * 1024
        private const val MB = 1024L * 1024L
    }
}
