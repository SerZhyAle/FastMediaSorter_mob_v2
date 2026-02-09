package com.sza.fastmediasorter.core.constants

/**
 * Application-wide constants shared across multiple components.
 * Single source of truth for common configuration values.
 */
object AppConstants {
    
    /**
     * Metadata extraction timeout for media files (both EXIF and video metadata).
     * Prevents indefinite hanging when processing corrupted or problematic files.
     */
    const val METADATA_EXTRACTION_TIMEOUT_MS = 30_000L // 30 seconds
    
    /**
     * Default slideshow interval when not specified by user settings.
     * Used by slideshow controllers and managers.
     */
    const val DEFAULT_SLIDESHOW_INTERVAL_MS = 3000L // 3 seconds
    
    /**
     * Countdown tick interval for slideshow countdown timers.
     */
    const val SLIDESHOW_COUNTDOWN_TICK_MS = 1000L // 1 second
}
