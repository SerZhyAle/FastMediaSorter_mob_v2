package com.sza.fastmediasorter.core.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import timber.log.Timber

/**
 * Feature-capability predicates derived from device hardware and OS facts.
 *
 * Sits next to [MemoryTier] but answers a different question:
 *
 * - [MemoryTier] classifies how memory-bound subsystems should be tuned
 *   (image format, animations, cover previews, prefetch sizes). The per-process
 *   Java heap limit dominates because Glide and the thumbnail cache live there.
 *
 * - [DeviceCapabilities] classifies whether an optional feature can run at all
 *   on this device. The signal here is physical hardware (total RAM, API level,
 *   isLowRamDevice) - **not** the per-process heap limit.
 *
 * Mixing the two led to a regression: Quest 3 (7 GB RAM, 512 MB heap) is classified
 * as [MemoryTier.LOW] for image sizing - which is correct - but the same tier was also
 * gating OCR, even though ML Kit text recognition needs only ~50-100 MB and runs largely
 * on native code. As a result OCR was forced off on capable hardware. Feature-availability
 * checks must use this object; heap-bound subsystem tuning must keep using [MemoryTier].
 */
object DeviceCapabilities {

    /** Minimum Android API level required by ML Kit text recognition used for OCR. */
    const val MIN_OCR_API_LEVEL = 26

    /**
     * Minimum total device RAM for OCR. ML Kit itself needs ~50-100 MB of working set;
     * the 3 GB floor excludes truly low-end devices where any extra subsystem competes
     * with the foreground UI for memory.
     */
    const val MIN_OCR_RAM_GB = 3.0

    enum class OcrUnavailableReason {
        /** API level below [MIN_OCR_API_LEVEL]. */
        OS_TOO_OLD,

        /** Device flagged `isLowRamDevice` or total RAM below [MIN_OCR_RAM_GB] GB. */
        DEVICE_TOO_WEAK,
    }

    sealed interface OcrSupport {
        object Supported : OcrSupport
        data class Unsupported(
            val reason: OcrUnavailableReason,
            val totalRamGb: Double,
            val apiLevel: Int,
            val isLowRamDevice: Boolean,
        ) : OcrSupport
    }

    /**
     * Resolve OCR availability for the current device. Returns either [OcrSupport.Supported]
     * or a structured [OcrSupport.Unsupported] carrying the reason and the inputs that
     * produced it (useful for the settings explanation and for logs).
     */
    fun ocrSupport(context: Context): OcrSupport {
        val apiLevel = Build.VERSION.SDK_INT
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowRamDevice = activityManager.isLowRamDevice
        val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val totalRamGb = memoryInfo.totalMem / (1024.0 * 1024.0 * 1024.0)

        if (apiLevel < MIN_OCR_API_LEVEL) {
            Timber.i(
                "DeviceCapabilities: OCR unavailable - API=%d < %d, totalRAM=%.2fGB",
                apiLevel, MIN_OCR_API_LEVEL, totalRamGb
            )
            return OcrSupport.Unsupported(
                reason = OcrUnavailableReason.OS_TOO_OLD,
                totalRamGb = totalRamGb,
                apiLevel = apiLevel,
                isLowRamDevice = isLowRamDevice,
            )
        }
        if (isLowRamDevice || totalRamGb < MIN_OCR_RAM_GB) {
            Timber.i(
                "DeviceCapabilities: OCR unavailable - totalRAM=%.2fGB (min %.1f), isLowRamDevice=%b, API=%d",
                totalRamGb, MIN_OCR_RAM_GB, isLowRamDevice, apiLevel
            )
            return OcrSupport.Unsupported(
                reason = OcrUnavailableReason.DEVICE_TOO_WEAK,
                totalRamGb = totalRamGb,
                apiLevel = apiLevel,
                isLowRamDevice = isLowRamDevice,
            )
        }
        Timber.i(
            "DeviceCapabilities: OCR supported - totalRAM=%.2fGB, API=%d, isLowRamDevice=%b",
            totalRamGb, apiLevel, isLowRamDevice
        )
        return OcrSupport.Supported
    }

    /** Convenience predicate for callers that don't need the reason or context details. */
    fun isOcrSupported(context: Context): Boolean = ocrSupport(context) is OcrSupport.Supported
}
