package com.sza.fastmediasorter.ui.player.helpers

import timber.log.Timber

/**
 * S0760: single source of truth for the horizontal swipe font-resize step shared by the text
 * viewer (SP float, range 6..72) and the EPUB viewer (Int px, range 6..144).
 *
 * Each engine keeps its own unit system and persistence; this controller only owns the *policy*:
 * one swipe applies a step proportional to the engine's own range instead of a flat value. A flat
 * step (e.g. 10) feels coarse on the small SP range and weak on the large px range, so the step is
 * derived as `range / RESIZE_STOPS`, giving the same number of swipes end-to-end on both scales.
 *
 * Callers pass and receive `Float`; the px engine rounds the result to `Int`.
 */
internal object FontResizeController {

    // Number of swipe steps to traverse a font-size range end to end. Replaces the previous
    // per-engine step of 2, which the owner reported as too slow (S0760).
    private const val RESIZE_STOPS = 7f

    /** Proportional step for a range; always >= a small positive amount so resize never stalls. */
    fun step(min: Float, max: Float): Float {
        val value = ((max - min) / RESIZE_STOPS).coerceAtLeast(1f)
        Timber.d("S0760: font resize step=$value range=$min..$max")
        return value
    }

    /** Next size up, clamped to [min, max]. */
    fun increase(current: Float, min: Float, max: Float): Float =
        (current + step(min, max)).coerceIn(min, max)

    /** Next size down, clamped to [min, max]. */
    fun decrease(current: Float, min: Float, max: Float): Float =
        (current - step(min, max)).coerceIn(min, max)
}
