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
     * Low-end devices: < 3GB RAM or marked as low-RAM by system.
     * - Force RGB_565 image format (50% memory per pixel)
     * - Disable animations
     * - Reduce thumbnail resolution
     * - Disable PDF/EPUB cover previews
     * - Replace heavy ripples with simple state drawables
     */
    LOW,
    
    /**
     * Standard devices: 3GB - 6GB RAM.
     * - Default image loading strategy
     * - Standard animations
     * - Full feature set
     */
    STANDARD,
    
    /**
     * High-end devices: > 6GB RAM.
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
            
            val tier = when {
                isLowRamDevice || totalRamGb < 3.0 -> LOW
                totalRamGb < 6.0 -> STANDARD
                else -> HIGH
            }
            
            Timber.i("MemoryTier.detect: device=$tier, totalRAM=${"%.2f".format(totalRamGb)}GB, isLowRamDevice=$isLowRamDevice, API=${Build.VERSION.SDK_INT}")
            
            return tier
        }
    }
}
