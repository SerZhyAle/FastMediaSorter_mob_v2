package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerZoomMathTest {

    @Test
    fun `step in then out round trips the scale`() {
        val zoomed = ViewerZoomMath.stepIn(2f, max = 100f)
        assertEquals(2.5f, zoomed, EPSILON)
        assertEquals(2f, ViewerZoomMath.stepOut(zoomed, min = 0.5f), EPSILON)
    }

    @Test
    fun `step in clamps at max`() {
        assertEquals(8f, ViewerZoomMath.stepIn(7.5f, max = 8f), EPSILON)
        assertEquals(8f, ViewerZoomMath.stepIn(8f, max = 8f), EPSILON)
    }

    @Test
    fun `step out clamps at min`() {
        assertEquals(1f, ViewerZoomMath.stepOut(1.1f, min = 1f), EPSILON)
        assertEquals(1f, ViewerZoomMath.stepOut(1f, min = 1f), EPSILON)
    }

    @Test
    fun `actual size exceeds fit for a large image in a small view`() {
        val relative = ViewerZoomMath.actualSizeScale(4000, 3000, fitWidthPx = 1080f, fitHeightPx = 2400f)
        assertTrue("expected actual size above fit, got $relative", relative > 1f)
    }

    @Test
    fun `actual size sits below fit for a small image in a large view`() {
        val relative = ViewerZoomMath.actualSizeScale(270, 270, fitWidthPx = 1080f, fitHeightPx = 2400f)
        assertTrue("expected actual size below fit, got $relative", relative < 1f)
    }

    @Test
    fun `actual size equals one when the view matches the content`() {
        assertEquals(1f, ViewerZoomMath.actualSizeScale(1080, 2400, fitWidthPx = 1080f, fitHeightPx = 2400f), EPSILON)
    }

    @Test
    fun `actual size returns one for non-positive inputs`() {
        assertEquals(1f, ViewerZoomMath.actualSizeScale(0, 100, 100f, 100f), EPSILON)
        assertEquals(1f, ViewerZoomMath.actualSizeScale(100, -1, 100f, 100f), EPSILON)
        assertEquals(1f, ViewerZoomMath.actualSizeScale(100, 100, 0f, 100f), EPSILON)
        assertEquals(1f, ViewerZoomMath.actualSizeScale(100, 100, 100f, Float.NEGATIVE_INFINITY), EPSILON)
    }
}

private const val EPSILON = 1e-4f
