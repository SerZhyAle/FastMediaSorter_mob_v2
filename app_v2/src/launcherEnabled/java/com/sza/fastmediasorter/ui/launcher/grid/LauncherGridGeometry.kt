package com.sza.fastmediasorter.ui.launcher.grid

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellUi
import com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership
import timber.log.Timber

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
     * The width a cell is actually drawn at.
     *
     * S1642: a section header is normalised to [LauncherSectionMembership.HEADER_SPAN_W] rather than to the
     * column count. Normalised, not merely read: a desktop written by an S1428 build stored its headers at
     * the widest grid there is, and drawing one of those at its stored span would cover a strip of squares
     * the table has since freed. Reading the constant here narrows them on sight, without a write.
     *
     * Every caller that positions a cell or counts occupied squares goes through this. If the renderer and
     * the empty-slot sweep read different spans, edit mode draws "tap to add" squares on top of a live
     * header - the failure [footprint]'s own KDoc describes.
     */
    fun renderSpanW(cell: LauncherCell, columns: Int): Int =
        if (cell.kind == LauncherCellKind.SECTION) {
            LauncherSectionMembership.HEADER_SPAN_W.coerceIn(1, columns.coerceAtLeast(1))
        } else {
            cell.spanW
        }

    fun footprintOf(cell: LauncherCell, columns: Int): CellFootprint =
        footprint(cell.rowIndex, cell.colIndex, renderSpanW(cell, columns), cell.spanH, columns)

    /**
     * S1428: a cell paired with the row it is drawn on once collapsed sections are folded shut.
     *
     * S1645 adds the column for the same reason the row exists: a packed header is drawn beside the
     * previous one rather than at the column it is stored at. Every other cell reports its stored
     * column here, so callers read one field and never have to know which case they are in.
     */
    data class RenderedCell(val item: LauncherCellUi, val renderRow: Int, val renderCol: Int)

    /**
     * S1428: the desktop as it is drawn - every cell inside a collapsed section dropped, everything
     * below one lifted by that section's height. Storage is untouched: this is a projection of the
     * stored rows, computed on every render (strategic §5.1.6).
     *
     * The row arithmetic itself lives in
     * [LauncherSectionMembership][com.sza.fastmediasorter.domain.model.launcher.LauncherSectionMembership]
     * rather than here, and deliberately: this object is in `src/launcherEnabled`, which the four
     * launcher-less flavors do not compile, so a shared unit test cannot reach it. Only the plumbing
     * that needs [LauncherCellUi] stays on this side.
     *
     * A section is addressed by the encoded target of its header cell, never by its row: a header the
     * user drags to another row is the same section, and a row that later carries a different header is
     * not.
     */
    fun renderPlan(
        cells: List<LauncherCellUi>,
        collapsedSections: Set<String>,
        columns: Int,
    ): List<RenderedCell> {
        val stored = cells.map { it.cell }
        val headerRows = LauncherSectionMembership.headerRows(stored)
        val collapsedHeaderRows = stored
            .filter { it.kind == LauncherCellKind.SECTION && it.target in collapsedSections }
            .map { it.rowIndex.coerceAtLeast(0) }
            .toSet()
        val drawnRowOf = { cell: LauncherCell ->
            LauncherSectionMembership.renderRowFor(
                row = cell.rowIndex,
                isHeader = cell.kind == LauncherCellKind.SECTION,
                headerRows = headerRows,
                collapsedHeaderRows = collapsedHeaderRows,
            )
        }
        // S1645: packing runs on the folded rows, so it continues the lift instead of fighting it.
        val packed = LauncherSectionMembership.packedHeaderPositions(
            cells = stored,
            collapsedTargets = collapsedSections,
            columns = columns,
            renderRowOf = drawnRowOf,
        )
        Timber.d("S1645: render plan packed ${packed.size} collapsed header(s) across $columns columns")
        return cells.mapNotNull { item ->
            val drawnRow = drawnRowOf(item.cell) ?: return@mapNotNull null
            val packedPosition = packed[item.cell.target]
                ?.takeIf { item.cell.kind == LauncherCellKind.SECTION }
            RenderedCell(
                item = item,
                renderRow = packedPosition?.row ?: drawnRow,
                renderCol = packedPosition?.col ?: item.cell.colIndex,
            )
        }
            // S1645: reading order, not storage order. The container adds children in this order and
            // TalkBack follows that, so a packed header must be announced where it is seen - which the
            // order the cells arrive in from the database does not describe once packing moves one.
            .sortedWith(compareBy({ it.renderRow }, { it.renderCol }))
    }

    /**
     * Rows the canvas needs for an already folded desktop - [rowsFor] over drawn rows instead of stored
     * ones. Folding has to shrink the canvas with it, or the desktop keeps scrolling over the empty
     * height the hidden cells used to occupy.
     */
    fun rowsForRendered(rendered: List<RenderedCell>): Int =
        rendered.maxOfOrNull { safeRow(it.renderRow) + safeSpanH(it.item.cell.spanH) } ?: 1

    /** [footprintOf] at the drawn row, so layout and the empty-slot sweep agree on a folded desktop. */
    fun footprintOfRendered(rendered: RenderedCell, columns: Int): CellFootprint = footprint(
        row = rendered.renderRow,
        col = rendered.renderCol,
        spanW = renderSpanW(rendered.item.cell, columns),
        spanH = rendered.item.cell.spanH,
        columns = columns,
    )

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
