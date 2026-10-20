package com.sza.fastmediasorter.broadcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenStreamGeometryTest {

    @Test
    fun `portrait screen keeps its aspect with the long side at the cap`() {
        val size = ScreenStreamGeometry.fit(SCREEN_SHORT, SCREEN_LONG, CAP)

        assertEquals(CAP, size.height)
        assertEquals(PORTRAIT_FITTED_WIDTH, size.width)
    }

    @Test
    fun `landscape screen keeps its aspect with the long side at the cap`() {
        val size = ScreenStreamGeometry.fit(SCREEN_LONG, SCREEN_SHORT, CAP)

        assertEquals(CAP, size.width)
        assertEquals(PORTRAIT_FITTED_WIDTH, size.height)
    }

    @Test
    fun `screen smaller than the cap is not scaled up`() {
        val size = ScreenStreamGeometry.fit(SMALL_WIDTH, SMALL_HEIGHT, CAP)

        assertEquals(SMALL_WIDTH, size.width)
        assertEquals(SMALL_HEIGHT, size.height)
    }

    @Test
    fun `odd screen sides come out even`() {
        val size = ScreenStreamGeometry.fit(ODD_WIDTH, ODD_HEIGHT, CAP)

        assertEquals(0, size.width % 2)
        assertEquals(0, size.height % 2)
        assertTrue(size.width <= CAP && size.height <= CAP)
    }

    @Test
    fun `degenerate input still yields a positive even size`() {
        val size = ScreenStreamGeometry.fit(0, -5, 0)

        assertTrue(size.width >= 2 && size.height >= 2)
        assertEquals(0, size.width % 2)
        assertEquals(0, size.height % 2)
    }

    private companion object {
        const val CAP = 1280
        const val SCREEN_SHORT = 1080
        const val SCREEN_LONG = 2400
        const val PORTRAIT_FITTED_WIDTH = 576
        const val SMALL_WIDTH = 640
        const val SMALL_HEIGHT = 360
        const val ODD_WIDTH = 1081
        const val ODD_HEIGHT = 2401
    }
}
