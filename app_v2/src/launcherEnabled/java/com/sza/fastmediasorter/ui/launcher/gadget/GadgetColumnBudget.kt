package com.sza.fastmediasorter.ui.launcher.gadget

/**
 * S4476: which detail lines of a gadget tile may stay when the tile is shorter than all of its content.
 *
 * Kept free of views so the rule can be tested without a layout pass.
 */
object GadgetColumnBudget {

    /**
     * How many of the leading [lineHeightsPx] fit under a headline value that keeps at least
     * [minValuePx] of the [availablePx] column.
     *
     * Stops at the first line that does not fit instead of skipping to a smaller later one: the lines
     * come in priority order, and a caption that survives only because the line above it was dropped
     * would describe a value the tile no longer shows.
     */
    fun keptLines(availablePx: Int, minValuePx: Int, lineHeightsPx: IntArray): Int {
        var room = availablePx - minValuePx
        var kept = 0
        for (height in lineHeightsPx) {
            if (height > room) break
            room -= height
            kept++
        }
        return kept
    }
}
