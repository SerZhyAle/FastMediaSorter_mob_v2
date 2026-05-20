package com.sza.fastmediasorter.core.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import timber.log.Timber

/**
 * Memory tier classification for device compatibility.
 * Used to adjust image loading strategy and feature availability.
 */
enum class MemoryTier {
    /**
     * Low-end devices.
     * Criteria: `isLowRamDevice` OR total RAM < 3 GB OR Java heap-limit <= 512 MB.
     * Notes: heap-limit dominates physical RAM - Quest 3 and the canonical emulator both
     * report `maxHeapMb == 512` exactly and therefore land in LOW after S0207 Phase 02.
     * - Force RGB_565 image format (50% memory per pixel)
     * - Disable animations
     * - Reduce thumbnail resolution
     * - Disable PDF/EPUB cover previews
     * - Replace heavy ripples with simple state drawables
     */
    LOW,

    /**
     * Standard devices.
     * Criteria: not LOW and not HIGH - i.e. total RAM >= 3 GB AND Java heap-limit > 512 MB,
     * and total RAM < 6 GB (so HIGH is not reached).
     * - Default image loading strategy
     * - Standard animations
     * - Full feature set
     */
    STANDARD,

    /**
     * High-end devices.
     * Criteria: total RAM >= 6 GB AND Java heap-limit > 512 MB.
     * - High-quality image loading
     * - Full animations
     * - All features enabled
     */
    HIGH;
    
    companion object {
        /**
         * Detect memory tier for the current device.
         * 
         * @param context Application or Activity context
         * @return MemoryTier classification
         */
        fun detect(context: Context): MemoryTier {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            // Check if device is marked as low-RAM by system (API 19+)
            val isLowRamDevice = activityManager.isLowRamDevice

            // Get total device RAM in GB
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            val totalRamBytes = memoryInfo.totalMem
            val totalRamGb = totalRamBytes / (1024.0 * 1024.0 * 1024.0)

            // CRITICAL: Also check Java heap limit (maxMemory).
            // Devices with 6GB RAM may have a 512MB heap limit per process.
            // Physical RAM alone is NOT sufficient to avoid OOM - heap limit is the real bottleneck.
            val maxHeapMb = Runtime.getRuntime().maxMemory() / 1024 / 1024

            val tier = classify(
                isLowRamDevice = isLowRamDevice,
                totalRamGb = totalRamGb,
                maxHeapMb = maxHeapMb,
            )

            Timber.i("MemoryTier.detect: tier=$tier, totalRAM=${"%.2f".format(totalRamGb)}GB, heapMax=${maxHeapMb}MB, isLowRamDevice=$isLowRamDevice, API=${Build.VERSION.SDK_INT}")

            return tier
        }

        /**
         * Pure classifier - exposed `internal` to support unit tests without
         * requiring a real [Context] / [ActivityManager].
         *
         * S0207 Phase 02 contract:
         * - heap-limit dominates physical RAM,
         * - `maxHeapMb <= 512` => LOW (Quest 3, canonical emulator),
         * - HIGH requires `totalRamGb >= 6.0` AND `maxHeapMb > 512`.
         */
        internal fun classify(
            isLowRamDevice: Boolean,
            totalRamGb: Double,
            maxHeapMb: Long,
        ): MemoryTier = when {
            isLowRamDevice || totalRamGb < 3.0 || maxHeapMb <= 512 -> LOW
            totalRamGb >= 6.0 && maxHeapMb > 512 -> HIGH
            else -> STANDARD
        }
    }
}
