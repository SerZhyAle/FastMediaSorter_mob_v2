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

    /**
     * Lowers [columns] until a cell is at least [widestLabelDp] wide, never below one (S4469).
     *
     * [columnsFor] only guards the tap target, which says nothing about a caption: the shrink loop stops
     * at its floor and truncates, so a label wider than its cell at that floor is only fixed by a wider
     * cell. [availableWidthDp] is the width the rows really get - the list's content width - because the
     * cell is a share of that, not of the screen.
     */
    fun columnsFittingLabel(
        columns: Int,
        availableWidthDp: Int,
        widestLabelDp: Int,
        gapDp: Int = DEFAULT_GAP_DP
    ): Int {
        var fitted = columns
        while (fitted > SINGLE_COLUMN && cellWidthDp(availableWidthDp, fitted, gapDp) < widestLabelDp) {
            fitted--
        }
        return fitted.coerceAtLeast(SINGLE_COLUMN)
    }

    private fun cellWidthDp(availableWidthDp: Int, columns: Int, gapDp: Int): Int {
        val gaps = gapDp * (columns - 1)
        return (availableWidthDp - gaps) / columns
    }
}
