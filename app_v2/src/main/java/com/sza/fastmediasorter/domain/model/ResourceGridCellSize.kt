package com.sza.fastmediasorter.domain.model

import kotlin.math.roundToInt

// The owner specified the steps as ratios of the CURRENT cell size: small is 20/33 of it, large is
// 50/33. A cell is exactly one grid column wide, so its size is inversely proportional to the span
// count - which is why each ratio is stored here already inverted into a span multiplier.
private const val SMALL_SPAN_MULTIPLIER = 33.0 / 20.0
private const val MEDIUM_SPAN_MULTIPLIER = 1.0
private const val LARGE_SPAN_MULTIPLIER = 33.0 / 50.0

// A rounded-down multiplier on a one-column bucket would otherwise produce a grid with no columns.
private const val MIN_SPAN = 1

/**
 * User choice for the main-window resource grid cell size (S1285).
 *
 * The step is applied on top of the span count the device configuration already picked, rather than
 * replacing it, so every existing width and orientation qualifier keeps working. [MEDIUM] is the
 * identity multiplier, which is what leaves an install that never opened the setting on today's grid.
 *
 * Against the default portrait bucket of three columns the steps resolve to five, three and two
 * columns - the numbers the owner named at capture.
 */
enum class ResourceGridCellSize(val spanMultiplier: Double) {
    SMALL(SMALL_SPAN_MULTIPLIER),
    MEDIUM(MEDIUM_SPAN_MULTIPLIER),
    LARGE(LARGE_SPAN_MULTIPLIER);

    /** Effective span count for [baseSpan], never below a single column. */
    fun spanFor(baseSpan: Int): Int =
        (baseSpan * spanMultiplier).roundToInt().coerceAtLeast(MIN_SPAN)

    companion object {
        val DEFAULT: ResourceGridCellSize = MEDIUM

        fun fromName(name: String?): ResourceGridCellSize =
            values().firstOrNull { it.name == name } ?: DEFAULT
    }
}
