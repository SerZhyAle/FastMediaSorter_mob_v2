package com.sza.fastmediasorter.core.util

import android.content.Context
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper for checking and logging Glide disk cache status.
 * Used at app startup to diagnose cache persistence issues.
 */
object CacheStatusHelper {
    
    /**
     * Log Glide disk cache status at startup to diagnose cache persistence issues.
     * Checks the image_cache directory for file count and total size.
     */
    fun logGlideDiskCacheStatus(context: Context) {
        try {
            // Glide stores disk cache in internal cache dir under "image_cache"
            val glideCacheDir = File(context.cacheDir, "image_cache")

            if (glideCacheDir.exists() && glideCacheDir.isDirectory) {
                val files = glideCacheDir.walkTopDown().filter { it.isFile }.toList()
                val totalSize = files.sumOf { it.length() }
                val totalSizeMb = totalSize / 1024.0 / 1024.0

                // Get oldest and newest file timestamps
                val timestamps = files.mapNotNull { it.lastModified().takeIf { t -> t > 0 } }
                val oldestFile = timestamps.minOrNull()?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
                } ?: "N/A"
                val newestFile = timestamps.maxOrNull()?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
                } ?: "N/A"

                Timber.i("=== GLIDE DISK CACHE STATUS AT STARTUP ===")
                Timber.i("Cache directory: ${glideCacheDir.absolutePath}")
                Timber.i("File count: ${files.size}")
                Timber.i("Total size: %.2f MB".format(totalSizeMb))
                Timber.i("Oldest file: $oldestFile")
                Timber.i("Newest file: $newestFile")
                Timber.i("===========================================")
            } else {
                // S1322: absence here is not evidence of a lost cache. Glide creates this directory
                // during its lazy init, on the first image load - and this check runs from the
                // deferred startup worker, which routinely wins that race. The old text claimed
                // "no thumbnails were cached from previous sessions", which sent a log reader
                // hunting a cache-persistence defect that did not exist.
                Timber.i("=== GLIDE DISK CACHE STATUS AT STARTUP ===")
                Timber.i("Cache directory not created yet: ${glideCacheDir.absolutePath}")
                Timber.i("Glide creates it lazily on first image load - not a cache-loss signal.")
                Timber.i("===========================================")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check Glide disk cache status")
        }
    }
}
