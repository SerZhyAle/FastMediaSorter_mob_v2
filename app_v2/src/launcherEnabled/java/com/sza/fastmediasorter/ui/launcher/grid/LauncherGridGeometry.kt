package com.sza.fastmediasorter.ui.launcher.grid

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind

/**
 * S0404: desktop grid sizing. The column count is derived from the screen at render time and the
 * user's density factor only nudges it, because the target devices (head units, TV boxes) report
 * densities that make any fixed column count wrong somewhere.
 *
 * The resolved count is persisted per orientation, so seeding and edit mode place cells on the same
 * grid the surface renders.
 */
object LauncherGridGeometry {

    /** Nominal cell edge at density factor 1.0 - a comfortable touch target with room for a label. */
    const val BASE_CELL_DP = 96f

    const val MIN_COLUMNS = 3
    const val MAX_COLUMNS = 12

    /** Higher [densityFactor] shrinks cells, so more of them fit across. */
    fun columns(availableWidthDp: Float, densityFactor: Float): Int {
        val cellDp = BASE_CELL_DP / densityFactor
        if (cellDp <= 0f) return MIN_COLUMNS
        return (availableWidthDp / cellDp).toInt().coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }

    /** The square edge of one grid cell. Every cell rect derives from this (see [boundsFor]). */
    fun cellSizePx(availableWidthPx: Int, columns: Int): Int =
        if (columns <= 0) availableWidthPx else availableWidthPx / columns

    /**
     * How many rows the canvas needs to show every cell - the lowest occupied row, plus its own
     * height. The desktop is one screen plus downward scroll (strategic §3.3), so the canvas grows to
     * its content rather than being clipped to the viewport.
     */
    fun rowsFor(cells: List<LauncherCell>): Int =
        cells.maxOfOrNull { safeRow(it.rowIndex) + safeSpanH(it.spanH) } ?: 1

    /**
     * How many rows a viewport of [availableHeightPx] covers - the second axis of [columns], and the
     * only thing that lets edit mode fill the screen instead of stopping under the last cell (S1288).
     *
     * Rounded up on purpose: a partially visible row at the bottom edge costs nothing and doubles as
     * the sign that the desktop scrolls, while rounding down leaves exactly the strip of bare
     * wallpaper this exists to remove. Returns 0 when either input is still unknown, so a caller that
     * asks before the surface is measured falls back to its content-driven row count.
     */
    fun rowsForViewport(availableHeightPx: Int, cellSizePx: Int): Int =
        if (availableHeightPx <= 0 || cellSizePx <= 0) 0 else (availableHeightPx + cellSizePx - 1) / cellSizePx

    /** The squares a cell occupies once clamped to the grid it is being drawn on. */
    data class CellFootprint(val row: Int, val col: Int, val spanW: Int, val spanH: Int) {
        val rows: IntRange get() = row until row + spanH
        val cols: IntRange get() = col until col + spanW
    }

    /** Pixel rect of one cell on the canvas, relative to the grid's content box. */
    data class CellBounds(val left: Int, val top: Int, val width: Int, val height: Int)

    /**
     * The single definition of where a cell sits on the grid. Everything that needs a cell's squares -
     * layout, hit-testing, the empty-slot sweep - goes through this, and that is the point: this clamp
     * used to be re-typed at each call site, and one copy that forgot to floor the row at 0 would mark
     * a different square occupied than the one the renderer drew, leaving a "tap to add" slot sitting
     * on top of a live cell.
     *
     * The clamps are load-bearing, not defensive padding: `spanW`/`colIndex` were persisted against
     * whatever column count was current when the user placed the cell, and both the density factor and
     * rotation change that count underneath. Unclamped, a cell saved on a wider grid lays out past the
     * right edge and silently disappears.
     */
    fun footprint(row: Int, col: Int, spanW: Int, spanH: Int, columns: Int): CellFootprint {
        val safeColumns = columns.coerceAtLeast(1)
        val width = spanW.coerceIn(1, safeColumns)
        return CellFootprint(
            row = safeRow(row),
            col = col.coerceIn(0, safeColumns - width),
            spanW = width,
            spanH = safeSpanH(spanH),
        )
    }

    /**
     * S1428: the width a cell is actually drawn at. A section header always spans the whole row,
     * whatever span it was stored with: [footprint]'s clamp only ever narrows a span, so a header
     * saved on a three-column grid would keep a strip of empty space to its right once the density
     * factor or a rotation widened the grid (strategic §5.1.2).
     *
     * Every caller that positions a cell or counts occupied squares must go through this. If the
     * renderer widened the header while the empty-slot sweep still used the stored span, edit mode
     * would draw "tap to add" squares on top of a live header - the exact failure [footprint]'s own
     * KDoc describes.
     */
    fun renderSpanW(cell: LauncherCell, columns: Int): Int =
        if (cell.kind == LauncherCellKind.SECTION) columns.coerceAtLeast(1) else cell.spanW

    fun footprintOf(cell: LauncherCell, columns: Int): CellFootprint =
        footprint(cell.rowIndex, cell.colIndex, renderSpanW(cell, columns), cell.spanH, columns)

    /** Where a cell actually lands, given the column count resolved right now. */
    fun boundsFor(cell: LauncherCell, cellSize: Int, columns: Int): CellBounds =
        boundsOf(footprintOf(cell, columns), cellSize)

    fun boundsOf(footprint: CellFootprint, cellSize: Int): CellBounds = CellBounds(
        left = footprint.col * cellSize,
        top = footprint.row * cellSize,
        width = footprint.spanW * cellSize,
        height = footprint.spanH * cellSize,
    )

    /** Row and height clamps do not depend on the column count, so [rowsFor] can share them. */
    private fun safeRow(row: Int): Int = row.coerceAtLeast(0)

    private fun safeSpanH(spanH: Int): Int = spanH.coerceAtLeast(1)
}
