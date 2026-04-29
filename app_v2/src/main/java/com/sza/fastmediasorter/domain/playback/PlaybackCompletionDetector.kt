package com.sza.fastmediasorter.domain.playback

import kotlin.math.min

/**
 * Decides whether a playback position is inside the "near-end" zone — used to
 * recognise that the user effectively watched the file to its end even when
 * ExoPlayer never fires STATE_ENDED (e.g. user closes the activity 1 second
 * before the natural end).
 *
 * Threshold = min(5% duration, 5000 ms), clamped by half the duration so very
 * short files cannot get a threshold larger than half of their length.
 */
object PlaybackCompletionDetector {

    private const val MAX_THRESHOLD_MS: Long = 5000L
    private const val PERCENT_DIVISOR: Long = 20L

    fun nearEndThresholdMs(durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        return min(durationMs / PERCENT_DIVISOR, MAX_THRESHOLD_MS)
            .coerceAtMost(durationMs / 2)
            .coerceAtLeast(0L)
    }

    fun isNearEnd(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L || positionMs < 0L) return false
        return positionMs >= durationMs - nearEndThresholdMs(durationMs)
    }
}
