package com.sza.fastmediasorter.wear.util

import com.sza.fastmediasorter.wear.domain.model.WearViewMode

/**
 * Decides how many columns a watch grid actually gets.
 *
 * Measured 2026-08-18 on a round emulator: a 240x240 dp display leaves an inscribed square of about
 * 170 dp, which gives ~54 dp per cell at three columns, while a 180 dp display gives ~40 dp - under
 * the interactive target. So the mode name is a request and this rule is the answer.
 */
object GridColumnFit {

    const val DEFAULT_GAP_DP: Int = 4
    const val DEFAULT_MIN_TARGET_DP: Int = 48

    private const val SINGLE_COLUMN = 1

    fun columnsFor(
        mode: WearViewMode,
        availableWidthDp: Int,
        gapDp: Int = DEFAULT_GAP_DP,
        minTargetDp: Int = DEFAULT_MIN_TARGET_DP
    ): Int {
        if (mode == WearViewMode.LIST) return SINGLE_COLUMN

        var columns = mode.requestedColumns
        while (columns > SINGLE_COLUMN && cellWidthDp(availableWidthDp, columns, gapDp) < minTargetDp) {
            columns--
        }
        return columns
    }

    private fun cellWidthDp(availableWidthDp: Int, columns: Int, gapDp: Int): Int {
        val gaps = gapDp * (columns - 1)
        return (availableWidthDp - gaps) / columns
    }
}
