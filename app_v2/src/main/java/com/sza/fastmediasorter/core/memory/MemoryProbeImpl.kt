package com.sza.fastmediasorter.core.memory

import android.os.Debug
import com.sza.fastmediasorter.BuildConfig
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [MemoryProbe] implementation.
 *
 * Emits a single canonical Timber line per [record] call:
 *
 * ```
 * MEM_PROBE | checkpoint=<NAME> | scenario=<tag-or-NONE> | heap=<used>MB/<max>MB(<pct>%) | native=<alloc>MB/<reserved>MB(free=<free>MB)
 * ```
 *
 * The format is fixed - log readers (devs, future log-aggregator tooling, S0207
 * acceptance scripts) parse it positionally. Do not change field names or
 * separators without updating downstream parsers.
 *
 * Implementation uses `Runtime` (Java heap) and `android.os.Debug` (native heap).
 * Sizes are converted to MB by integer division (`/ 1024 / 1024`).
 *
 * S0207 - Phase 01 (memory-instrumentation).
 */
@Singleton
class MemoryProbeImpl @Inject constructor() : MemoryProbe {

    override fun record(checkpoint: MemoryCheckpoint, scenarioTag: String?) {
        // S0207: debug-builds only - no measurement cost in release per strategic §3.1 item 4.
        if (!BuildConfig.DEBUG) return
        val runtime = Runtime.getRuntime()
        val totalMb = runtime.totalMemory() / BYTES_PER_MB
        val freeMb = runtime.freeMemory() / BYTES_PER_MB
        val maxMb = runtime.maxMemory() / BYTES_PER_MB
        val usedMb = totalMb - freeMb
        val pct = if (maxMb > 0L) (usedMb * 100L) / maxMb else 0L

        val nativeAllocMb = Debug.getNativeHeapAllocatedSize() / BYTES_PER_MB
        val nativeReservedMb = Debug.getNativeHeapSize() / BYTES_PER_MB
        val nativeFreeMb = Debug.getNativeHeapFreeSize() / BYTES_PER_MB

        val tag = scenarioTag ?: "NONE"

        Timber.i(
            "MEM_PROBE | checkpoint=%s | scenario=%s | heap=%dMB/%dMB(%d%%) | native=%dMB/%dMB(free=%dMB)",
            checkpoint.name,
            tag,
            usedMb,
            maxMb,
            pct,
            nativeAllocMb,
            nativeReservedMb,
            nativeFreeMb,
        )
    }

    private companion object {
        const val BYTES_PER_MB: Long = 1024L * 1024L
    }
}
