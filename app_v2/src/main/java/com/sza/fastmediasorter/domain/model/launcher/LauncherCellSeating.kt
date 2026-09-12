package com.sza.fastmediasorter.domain.model.launcher

/**
 * S2599: where a cell of a given width actually sits on a grid of a given column count.
 *
 * The data layer decides whether a square is free and the renderer decides where the rectangle is
 * drawn, and until this object existed each side carried its own copy of the right-edge arithmetic -
 * only the renderer's copy pulled a too-far-right cell back inside the grid. So a 2-wide gadget placed
 * in the last column was stored at a column the table found free and drawn one column to the left, on
 * top of its neighbour, both cards printing their text into the same squares.
 *
 * It lives in the domain rather than beside the renderer because the renderer's package sits in the
 * `launcherEnabled` source set, which the launcher-less flavors do not compile, while the repository
 * sits in shared code - the domain is the only place both sides and the shared unit tests can reach.
 * The same reason put the collapsed-section row arithmetic in [LauncherSectionMembership].
 */
object LauncherCellSeating {

    /** A grid narrower than this can seat nothing, so a smaller count is read as one column. */
    private const val MIN_COLUMNS = 1

    /** An empty rectangle intersects nothing, so a zero-width cell would be reported as a free square. */
    private const val MIN_SPAN = 1

    /**
     * The width the cell is drawn at: never below one square, never wider than the grid itself.
     *
     * Run this before [seatColumn]. The column a cell is pulled back to depends on the width it ends up
     * with, so a column seated against an unclamped width lands outside the grid again.
     */
    fun seatSpanW(spanW: Int, columns: Int): Int =
        spanW.coerceIn(MIN_SPAN, columns.coerceAtLeast(MIN_COLUMNS))

    /**
     * The column the cell is drawn at: [colIndex] pulled back far enough that a [spanW]-wide footprint
     * ends inside the grid, and never below zero.
     *
     * [spanW] is expected to have been through [seatSpanW]; a wider one still yields column zero rather
     * than a negative index, so a caller that forgets does not write a corrupt anchor.
     */
    fun seatColumn(colIndex: Int, spanW: Int, columns: Int): Int {
        val safeColumns = columns.coerceAtLeast(MIN_COLUMNS)
        val width = seatSpanW(spanW, safeColumns)
        return colIndex.coerceIn(0, safeColumns - width)
    }
}
