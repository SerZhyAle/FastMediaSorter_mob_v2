package com.sza.fastmediasorter.ui.launcher.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1466: a long press on the empty desktop now places a new cell where the finger was, so
 * [LauncherGridGeometry.slotAt] decides which square that is. An off-by-one here is invisible to the
 * compiler and shows up only as an item appearing next to the press - the "looks like a miss" the
 * owner's ruling of 2026-08-17 exists to avoid.
 *
 * The seam case is pinned deliberately: the arithmetic floors, so a point exactly on the boundary
 * belongs to the later cell. That is a choice, not an accident, and a silent flip of it would move
 * every edge press one square.
 */
class LauncherGridSlotAtTest {

    private companion object {
        const val CELL = 100
        const val COLUMNS = 4
        const val ROWS = 3
        const val WIDTH = CELL * COLUMNS
        const val HEIGHT = CELL * ROWS
        const val HALF_CELL = CELL / 2
    }

    private fun slotAt(x: Int, y: Int) = LauncherGridGeometry.slotAt(
        xPx = x,
        yPx = y,
        cellSize = CELL,
        columns = COLUMNS,
        rows = ROWS,
    )

    @Test
    fun `a point in the middle of the first cell is row 0 column 0`() {
        assertEquals(LauncherGridGeometry.Slot(row = 0, col = 0), slotAt(HALF_CELL, HALF_CELL))
    }

    @Test
    fun `a point in the middle of an inner cell is that cell`() {
        val slot = slotAt(CELL * 2 + HALF_CELL, CELL + HALF_CELL)

        assertEquals(LauncherGridGeometry.Slot(row = 1, col = 2), slot)
    }

    @Test
    fun `a point on the seam between two cells belongs to the later one`() {
        assertEquals(LauncherGridGeometry.Slot(row = 0, col = 2), slotAt(CELL * 2, HALF_CELL))
        assertEquals(LauncherGridGeometry.Slot(row = 1, col = 0), slotAt(HALF_CELL, CELL))
    }

    @Test
    fun `a point left of the grid has no slot`() {
        assertNull(slotAt(-1, HALF_CELL))
    }

    @Test
    fun `a point above the grid has no slot`() {
        assertNull(slotAt(HALF_CELL, -1))
    }

    @Test
    fun `a point right of the grid has no slot`() {
        assertNull(slotAt(WIDTH, HALF_CELL))
    }

    @Test
    fun `a point below the grid has no slot`() {
        assertNull(slotAt(HALF_CELL, HEIGHT))
    }

    @Test
    fun `an unmeasured grid has no slot`() {
        assertNull(
            LauncherGridGeometry.slotAt(
                xPx = HALF_CELL,
                yPx = HALF_CELL,
                cellSize = 0,
                columns = COLUMNS,
                rows = ROWS,
            ),
        )
    }
}
