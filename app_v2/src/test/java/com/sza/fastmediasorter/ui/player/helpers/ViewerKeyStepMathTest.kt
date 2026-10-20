package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewerKeyStepMathTest {

    @Test
    fun `offset target clamps to the list and reports no move`() {
        assertEquals(15, ViewerKeyStepMath.offsetTarget(current = 5, size = 50, offset = 10))
        assertEquals(49, ViewerKeyStepMath.offsetTarget(current = 45, size = 50, offset = 100))
        assertEquals(0, ViewerKeyStepMath.offsetTarget(current = 3, size = 50, offset = -10))
        assertNull(ViewerKeyStepMath.offsetTarget(current = 49, size = 50, offset = 10))
        assertNull(ViewerKeyStepMath.offsetTarget(current = 0, size = 0, offset = 10))
    }

    @Test
    fun `edge target picks first or last`() {
        assertEquals(0, ViewerKeyStepMath.edgeTarget(current = 7, size = 10, last = false))
        assertEquals(9, ViewerKeyStepMath.edgeTarget(current = 7, size = 10, last = true))
        assertNull(ViewerKeyStepMath.edgeTarget(current = 9, size = 10, last = true))
        assertNull(ViewerKeyStepMath.edgeTarget(current = 0, size = 0, last = false))
    }

    @Test
    fun `speed walks the quarter grid within bounds`() {
        assertEquals(1.25f, ViewerKeyStepMath.nextSpeed(1.0f, faster = true), 0.0001f)
        assertEquals(0.75f, ViewerKeyStepMath.nextSpeed(1.0f, faster = false), 0.0001f)
        assertEquals(1.25f, ViewerKeyStepMath.nextSpeed(1.1f, faster = true), 0.0001f)
        assertEquals(1.0f, ViewerKeyStepMath.nextSpeed(1.1f, faster = false), 0.0001f)
        assertEquals(2.0f, ViewerKeyStepMath.nextSpeed(2.0f, faster = true), 0.0001f)
        assertEquals(0.25f, ViewerKeyStepMath.nextSpeed(0.25f, faster = false), 0.0001f)
    }

    @Test
    fun `slideshow interval halves down to the floor`() {
        assertEquals(5_000L, ViewerKeyStepMath.fasterSlideshowIntervalMs(10_000L))
        assertEquals(1_000L, ViewerKeyStepMath.fasterSlideshowIntervalMs(1_500L))
        assertEquals(1_000L, ViewerKeyStepMath.fasterSlideshowIntervalMs(1_000L))
    }

    @Test
    fun `track index wraps and needs two tracks`() {
        assertEquals(1, ViewerKeyStepMath.nextTrackIndex(selected = 0, count = 3))
        assertEquals(0, ViewerKeyStepMath.nextTrackIndex(selected = 2, count = 3))
        assertEquals(0, ViewerKeyStepMath.nextTrackIndex(selected = -1, count = 2))
        assertNull(ViewerKeyStepMath.nextTrackIndex(selected = 0, count = 1))
    }
}
