package com.sza.fastmediasorter.domain.model.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2599: the one rule the repository and the renderer both seat a cell by.
 *
 * The defect this covers was invisible to both sides on its own - each answered consistently, and only
 * the pair disagreed. So every case here states the drawn rectangle, which is the thing the user sees
 * and the thing the occupancy check has to be asking about.
 */
class LauncherCellSeatingTest {

    @Test
    fun `a cell that already fits is left where it was asked for`() {
        assertEquals(2, LauncherCellSeating.seatColumn(colIndex = 2, spanW = 1, columns = 5))
        assertEquals(2, LauncherCellSeating.seatColumn(colIndex = 2, spanW = 2, columns = 5))
        assertEquals(2, LauncherCellSeating.seatSpanW(spanW = 2, columns = 5))
    }

    @Test
    fun `a two-wide cell asked for the last column is pulled back one`() {
        // The reported case: five columns, the "+" tapped in the last one, a 2-wide gadget placed there.
        assertEquals(3, LauncherCellSeating.seatColumn(colIndex = 4, spanW = 2, columns = 5))
    }

    @Test
    fun `a one-wide cell may sit in the last column`() {
        assertEquals(4, LauncherCellSeating.seatColumn(colIndex = 4, spanW = 1, columns = 5))
    }

    @Test
    fun `a cell wider than the grid is narrowed to it and seated at zero`() {
        assertEquals(3, LauncherCellSeating.seatSpanW(spanW = 7, columns = 3))
        assertEquals(0, LauncherCellSeating.seatColumn(colIndex = 2, spanW = 7, columns = 3))
    }

    @Test
    fun `a negative column is floored at zero`() {
        assertEquals(0, LauncherCellSeating.seatColumn(colIndex = -4, spanW = 2, columns = 5))
    }

    @Test
    fun `an empty span is widened to one square`() {
        // A zero-width rectangle intersects nothing, so the table would report its square free forever.
        assertEquals(1, LauncherCellSeating.seatSpanW(spanW = 0, columns = 5))
        assertEquals(4, LauncherCellSeating.seatColumn(colIndex = 4, spanW = 0, columns = 5))
    }

    @Test
    fun `an unmeasured grid behaves as a single column`() {
        assertEquals(1, LauncherCellSeating.seatSpanW(spanW = 3, columns = 0))
        assertEquals(0, LauncherCellSeating.seatColumn(colIndex = 3, spanW = 3, columns = 0))
    }
}
