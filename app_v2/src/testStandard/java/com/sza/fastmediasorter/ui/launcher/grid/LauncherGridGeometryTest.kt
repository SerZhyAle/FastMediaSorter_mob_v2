package com.sza.fastmediasorter.ui.launcher.grid

import com.sza.fastmediasorter.domain.model.launcher.LauncherCell
import com.sza.fastmediasorter.domain.model.launcher.LauncherCellKind
import com.sza.fastmediasorter.domain.model.launcher.LauncherOrientation
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S0404: the desktop grid is a 2D canvas whose column count is resolved at render time, while every
 * cell's span and column were persisted against whatever count was current when the user placed it.
 * Rotation and the density factor both change that count underneath, so [LauncherGridGeometry] is the
 * single point where a stored layout meets a different grid - which is exactly what is worth testing.
 *
 * Lives in `src/testStandard` because `LauncherGridGeometry` ships in the `launcherEnabled` source
 * set, mounted only into the `standard`/`noLegal` flavors: in shared `src/test` this class would not
 * compile for `lite`/`photos`/`legacy`/`vr`. Precedent: `src/testNoLegal`, `src/testVr`.
 *
 * Launcher-mode UI stays untested by owner decision (iteration-1 boundary); this object is not UI -
 * it is pure Kotlin with no Android imports.
 */
class LauncherGridGeometryTest {

    @Test
    fun `rowsFor an empty desktop is one row`() {
        assertEquals(1, LauncherGridGeometry.rowsFor(emptyList()))
    }

    @Test
    fun `rowsFor counts a cell's own height, not just its row`() {
        // A 2-row gadget starting at row 3 occupies rows 3 and 4, so the canvas needs 5 rows.
        assertEquals(5, LauncherGridGeometry.rowsFor(listOf(cell(row = 3, spanH = 2))))
    }

    @Test
    fun `rowsFor takes the lowest occupied row across all cells`() {
        val cells = listOf(cell(row = 0, spanH = 1), cell(row = 4, spanH = 1), cell(row = 2, spanH = 2))
        assertEquals(5, LauncherGridGeometry.rowsFor(cells))
    }

    @Test
    fun `boundsFor places a plain cell at its row and column`() {
        val bounds = LauncherGridGeometry.boundsFor(cell(row = 2, col = 1), cellSize = 100, columns = 4)
        assertEquals(LauncherGridGeometry.CellBounds(left = 100, top = 200, width = 100, height = 100), bounds)
    }

    @Test
    fun `boundsFor sizes a gadget by both spans`() {
        val bounds = LauncherGridGeometry.boundsFor(
            cell(row = 1, col = 0, spanW = 2, spanH = 2),
            cellSize = 100,
            columns = 4,
        )
        assertEquals(LauncherGridGeometry.CellBounds(left = 0, top = 100, width = 200, height = 200), bounds)
    }

    @Test
    fun `boundsFor clamps a span wider than the grid`() {
        // Saved on a 6-column grid, rendered on a 3-column one: the cell may not be wider than the grid.
        val bounds = LauncherGridGeometry.boundsFor(cell(col = 0, spanW = 5), cellSize = 100, columns = 3)
        assertEquals(300, bounds.width)
        assertEquals(0, bounds.left)
    }

    @Test
    fun `boundsFor pulls a cell back on-canvas when its column would overflow`() {
        // Saved at column 5 of a wide grid; on a 3-column grid it would lay out past the right edge and
        // vanish. It lands on the last column that still fits instead.
        val bounds = LauncherGridGeometry.boundsFor(cell(col = 5, spanW = 1), cellSize = 100, columns = 3)
        assertEquals(200, bounds.left)
        assertEquals(100, bounds.width)
    }

    @Test
    fun `boundsFor keeps a two-wide cell fully on-canvas`() {
        val bounds = LauncherGridGeometry.boundsFor(cell(col = 9, spanW = 2), cellSize = 50, columns = 4)
        // Last column that fits a 2-wide cell on a 4-column grid is 2, so left = 2 * 50.
        assertEquals(100, bounds.left)
        assertEquals(100, bounds.width)
    }

    @Test
    fun `columns falls back to the minimum when the density factor is zero`() {
        assertEquals(LauncherGridGeometry.MIN_COLUMNS, LauncherGridGeometry.columns(360f, 0f))
    }

    @Test
    fun `columns is clamped to the supported range`() {
        assertEquals(LauncherGridGeometry.MIN_COLUMNS, LauncherGridGeometry.columns(100f, 1f))
        assertEquals(LauncherGridGeometry.MAX_COLUMNS, LauncherGridGeometry.columns(4000f, 1f))
    }

    @Test
    fun `cellSizePx divides the content width across the columns`() {
        assertEquals(90, LauncherGridGeometry.cellSizePx(360, 4))
    }

    private fun cell(
        row: Int = 0,
        col: Int = 0,
        spanW: Int = 1,
        spanH: Int = 1,
    ) = LauncherCell(
        id = 1L,
        orientation = LauncherOrientation.PORTRAIT,
        rowIndex = row,
        colIndex = col,
        spanW = spanW,
        spanH = spanH,
        kind = LauncherCellKind.SHORTCUT,
        target = "app:com.example",
        labelOverride = null,
        addedAt = 0L,
    )
}
