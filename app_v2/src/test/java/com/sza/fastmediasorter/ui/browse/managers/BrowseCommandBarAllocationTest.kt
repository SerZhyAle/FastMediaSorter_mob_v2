package com.sza.fastmediasorter.ui.browse.managers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure command-bar allocation core (S0374). */
class BrowseCommandBarAllocationTest {

    private fun slot(id: Int, width: Int, priority: Int) = CommandSlot(id, width, priority)

    @Test
    fun `empty input yields empty buckets`() {
        val result = allocateCommandBar(emptyList(), availableWidthPx = 1000, reservedWidthPx = 100)
        assertTrue(result.visibleIds.isEmpty())
        assertTrue(result.overflowIds.isEmpty())
    }

    @Test
    fun `all fit when width is ample`() {
        val slots = listOf(
            slot(1, 100, 0),
            slot(2, 100, 1),
            slot(3, 100, 2)
        )
        val result = allocateCommandBar(slots, availableWidthPx = 1000, reservedWidthPx = 100)
        assertEquals(listOf(1, 2, 3), result.visibleIds)
        assertTrue(result.overflowIds.isEmpty())
    }

    @Test
    fun `nothing fits when available not greater than reserved`() {
        val slots = listOf(slot(1, 100, 0), slot(2, 100, 1))
        val result = allocateCommandBar(slots, availableWidthPx = 100, reservedWidthPx = 100)
        assertTrue(result.visibleIds.isEmpty())
        assertEquals(listOf(1, 2), result.overflowIds)
    }

    @Test
    fun `partial fit keeps highest priority and overflows the rest`() {
        // Budget after reserve = 350 - 50 = 300 -> fits 3 of the 100px buttons.
        val slots = listOf(
            slot(10, 100, 0),
            slot(20, 100, 1),
            slot(30, 100, 2),
            slot(40, 100, 3),
            slot(50, 100, 4)
        )
        val result = allocateCommandBar(slots, availableWidthPx = 350, reservedWidthPx = 50)
        assertEquals(listOf(10, 20, 30), result.visibleIds)
        assertEquals(listOf(40, 50), result.overflowIds)
    }

    @Test
    fun `visible bucket preserves original input order not priority order`() {
        // Priorities are inverted vs input order; visible bucket must still read in input order.
        val slots = listOf(
            slot(1, 100, 2),
            slot(2, 100, 0),
            slot(3, 100, 1)
        )
        // Budget = 250 - 0 = 250 -> 2 highest-priority slots (ids 2 and 3) fit.
        val result = allocateCommandBar(slots, availableWidthPx = 250, reservedWidthPx = 0)
        assertEquals(listOf(2, 3), result.visibleIds)
        assertEquals(listOf(1), result.overflowIds)
    }

    @Test
    fun `larger reserved width shifts the cut`() {
        val slots = listOf(
            slot(1, 100, 0),
            slot(2, 100, 1),
            slot(3, 100, 2)
        )
        // Same slots, reserve grows from 0 to 150 -> budget 350-150=200 fits only 2.
        val result = allocateCommandBar(slots, availableWidthPx = 350, reservedWidthPx = 150)
        assertEquals(listOf(1, 2), result.visibleIds)
        assertEquals(listOf(3), result.overflowIds)
    }
}
