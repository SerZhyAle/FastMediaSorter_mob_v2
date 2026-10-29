package com.sza.fastmediasorter.ui.launcher.gadget

import org.junit.Assert.assertEquals
import org.junit.Test

/** S4476: which detail lines a gadget column keeps once the headline value has taken its floor. */
class GadgetColumnBudgetTest {

    @Test
    fun `every line is kept when the column is tall enough`() {
        assertEquals(3, GadgetColumnBudget.keptLines(availablePx = 100, minValuePx = 20, intArrayOf(20, 20, 20)))
    }

    @Test
    fun `no line is kept when the value floor takes the whole column`() {
        assertEquals(0, GadgetColumnBudget.keptLines(availablePx = 20, minValuePx = 20, intArrayOf(10, 10)))
    }

    @Test
    fun `a column shorter than the value floor keeps no line`() {
        assertEquals(0, GadgetColumnBudget.keptLines(availablePx = 10, minValuePx = 20, intArrayOf(5)))
    }

    @Test
    fun `lines are kept in order until one does not fit`() {
        assertEquals(1, GadgetColumnBudget.keptLines(availablePx = 50, minValuePx = 20, intArrayOf(16, 16)))
    }

    @Test
    fun `a smaller later line is not promoted past a dropped larger one`() {
        assertEquals(0, GadgetColumnBudget.keptLines(availablePx = 30, minValuePx = 20, intArrayOf(16, 4)))
    }

    @Test
    fun `a line that exactly fills the column is kept`() {
        assertEquals(1, GadgetColumnBudget.keptLines(availablePx = 36, minValuePx = 20, intArrayOf(16)))
    }

    @Test
    fun `an empty line list keeps nothing`() {
        assertEquals(0, GadgetColumnBudget.keptLines(availablePx = 100, minValuePx = 20, intArrayOf()))
    }
}
