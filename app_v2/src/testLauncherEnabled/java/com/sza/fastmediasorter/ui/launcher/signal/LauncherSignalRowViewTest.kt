package com.sza.fastmediasorter.ui.launcher.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S2244 phase 01: the start/end split of signal chips is bounded by each side's own capacity against the
 * cutout span. The asymmetric case is the owner's device shape from research 01 - a wide end-pinned
 * indicator row used to push its chips across the cutout's right edge, under the camera.
 */
@Suppress("FunctionNaming") // backtick test names, project convention
class LauncherSignalRowViewTest {

    @Test
    fun `asymmetric capacities keep the end group within its side`() {
        // Start 6, end 1, seven chips: the end side takes one chip and no more.
        assertEquals(6, allocateStartGroupCount(chipCount = 7, startCapacity = 6, endCapacity = 1))
    }

    @Test
    fun `a counter slot on the end side pushes every chip to the start`() {
        // End capacity reduced to zero by the counter's own slot: all six chips stay on the start side.
        assertEquals(6, allocateStartGroupCount(chipCount = 6, startCapacity = 6, endCapacity = 0))
    }

    @Test
    fun `exact fit fills both sides to their capacities`() {
        assertEquals(6, allocateStartGroupCount(chipCount = 8, startCapacity = 6, endCapacity = 2))
    }

    @Test
    fun `a zero-capacity start side sends everything to the end`() {
        assertEquals(0, allocateStartGroupCount(chipCount = 4, startCapacity = 0, endCapacity = 5))
    }

    @Test
    fun `a wide row sharing the band with the status bar is bounded at five chips plus the counter`() {
        assertEquals(6, signalSlots(capacity = 12, topStatusBarMode = true))
    }

    @Test
    fun `a row exactly at the bound is left alone`() {
        assertEquals(6, signalSlots(capacity = 6, topStatusBarMode = true))
    }

    @Test
    fun `a narrow row keeps the width it measured`() {
        // The ceiling must never raise a capacity: three chips fit, and five would overlap.
        assertEquals(3, signalSlots(capacity = 3, topStatusBarMode = true))
        assertEquals(3, signalSlots(capacity = 3, topStatusBarMode = false))
    }

    @Test
    fun `a wide row owning the whole band is bounded at eleven chips plus the counter`() {
        // S2790: eleven chips and the counter slot are what make the button read "12+".
        assertEquals(12, signalSlots(capacity = 12, topStatusBarMode = false))
    }

    @Test
    fun `a row wider than the second ceiling is still bounded by it`() {
        assertEquals(12, signalSlots(capacity = 20, topStatusBarMode = false))
    }

    @Test
    fun `a wider start side keeps the single group`() {
        assertTrue(keepsStartSide(startSpan = 400, endSpan = 120))
    }

    @Test
    fun `a wider end side takes the single group`() {
        assertFalse(keepsStartSide(startSpan = 120, endSpan = 400))
    }

    @Test
    fun `equal spans keep the single group at the left edge`() {
        // S2734 criterion 4 put the newest chip at the left edge; a tie must not move it across the cutout.
        assertTrue(keepsStartSide(startSpan = 240, endSpan = 240))
    }

    @Test
    fun `counts below capacity keep the half-share unchanged`() {
        // The row looked right whenever both halves fit; the fix must not move chips in that case.
        assertEquals(2, allocateStartGroupCount(chipCount = 3, startCapacity = 6, endCapacity = 2))
    }
}
