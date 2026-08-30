package com.sza.fastmediasorter.ui.launcher.signal

import org.junit.Assert.assertEquals
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
    fun `counts below capacity keep the half-share unchanged`() {
        // The row looked right whenever both halves fit; the fix must not move chips in that case.
        assertEquals(2, allocateStartGroupCount(chipCount = 3, startCapacity = 6, endCapacity = 2))
    }
}
