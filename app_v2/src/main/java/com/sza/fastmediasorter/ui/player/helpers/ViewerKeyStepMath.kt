package com.sza.fastmediasorter.ui.player.helpers

/** Index, speed, interval and track arithmetic behind the VIEWER-KEYBOARD player keys. */
object ViewerKeyStepMath {

    const val SLIDESHOW_FLOOR_MS = 1_000L
    private const val SPEED_STEP = 0.25f
    private const val MIN_SPEED = 0.25f
    private const val MAX_SPEED = 2.0f
    private const val SPEED_EPSILON = 0.001f

    /** Target of a step of [offset] files, clamped to the list; null when the list is empty or nothing moves. */
    fun offsetTarget(current: Int, size: Int, offset: Int): Int? {
        if (size <= 0) return null
        val from = current.coerceIn(0, size - 1)
        val target = (from + offset).coerceIn(0, size - 1)
        return target.takeIf { it != current }
    }

    /** First or last index; null when the list is empty or the current file is already there. */
    fun edgeTarget(current: Int, size: Int, last: Boolean): Int? {
        if (size <= 0) return null
        val target = if (last) size - 1 else 0
        return target.takeIf { it != current }
    }

    /** Next speed on the 0.25 grid between 0.25x and 2x; an off-grid speed snaps to its neighbour first. */
    fun nextSpeed(current: Float, faster: Boolean): Float {
        val steps = current / SPEED_STEP
        val snapped = if (faster) {
            kotlin.math.floor(steps + SPEED_EPSILON) + 1
        } else {
            kotlin.math.ceil(steps - SPEED_EPSILON) - 1
        }
        return (snapped * SPEED_STEP).coerceIn(MIN_SPEED, MAX_SPEED)
    }

    /** A running slideshow's interval halved, never below the MEDIA-VIEWER 1 s floor. */
    fun fasterSlideshowIntervalMs(currentMs: Long): Long = (currentMs / 2).coerceAtLeast(SLIDESHOW_FLOOR_MS)

    /** Index of the track after [selected], wrapping; null when there is nothing to switch to. */
    fun nextTrackIndex(selected: Int, count: Int): Int? {
        if (count < 2) return null
        return (selected.coerceAtLeast(-1) + 1) % count
    }
}
