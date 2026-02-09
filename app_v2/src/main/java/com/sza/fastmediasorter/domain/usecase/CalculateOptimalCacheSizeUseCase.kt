package com.sza.fastmediasorter.domain.usecase

import android.os.Environment
import android.os.StatFs
import javax.inject.Inject

/**
 * Calculates optimal cache size based on available internal storage.
 * 
 * Strategy:
 * - > 50 GB available: 8 GB cache
 * - > 20 GB available: 4 GB cache
 * - > 10 GB available: 2 GB cache
 * - < 10 GB available: 1 GB cache
 * 
 * Returns cache size in MB.
 */
class CalculateOptimalCacheSizeUseCase @Inject constructor() {
    
    /**
     * Calculate optimal cache size in MB based on available storage.
     * 
     * @return Recommended cache size in MB
     */
    operator fun invoke(): Int {
        val availableGB = getAvailableInternalStorageGB()
        
        return when {
            availableGB > 50 -> 8 * 1024  // 8 GB
            availableGB > 20 -> 4 * 1024  // 4 GB
            availableGB > 10 -> 2 * 1024  // 2 GB
            else -> 1024                  // 1 GB
        }
    }
    
    /**
     * Get available internal storage in GB.
     * 
     * @return Available storage in gigabytes
     */
    private fun getAvailableInternalStorageGB(): Long {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes / (1024 * 1024 * 1024) // Convert to GB
    }
    
    /**
     * Get total internal storage in GB.
     * 
     * @return Total storage in gigabytes
     */
    fun getTotalInternalStorageGB(): Long {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        return totalBytes / (1024 * 1024 * 1024) // Convert to GB
    }
    
    /**
     * Get storage info as human-readable string.
     * 
     * @return String like "Available: 25 GB / Total: 128 GB"
     */
    fun getStorageInfo(): String {
        val available = getAvailableInternalStorageGB()
        val total = getTotalInternalStorageGB()
        return "Available: $available GB / Total: $total GB"
    }
}
