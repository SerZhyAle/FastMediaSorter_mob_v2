package com.sza.fastmediasorter.core.util

import java.util.Locale

/**
 * Formats a duration in milliseconds to a human-readable string.
 *
 * Returns null for null, zero, or negative input.
 * Format: "MM:SS" for durations < 1 hour, "H:MM:SS" for >= 1 hour.
 */
fun formatMediaDuration(durationMs: Long?): String? {
    val value = durationMs ?: return null
    if (value <= 0L) return null

    val totalSeconds = value / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
