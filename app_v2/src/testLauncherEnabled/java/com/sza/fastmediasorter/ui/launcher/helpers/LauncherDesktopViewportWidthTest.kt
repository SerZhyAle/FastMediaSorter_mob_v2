package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.ui.launcher.grid.LauncherGridGeometry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S4492: the desktop's columns divide the viewport beside the taskbar, so the count must come from that
 * width. The S25 FE case: a 411dp window, a side taskbar leaving a 290dp viewport, density factor 1.5.
 */
class LauncherDesktopViewportWidthTest {

    @Test
    fun `a measured viewport answers with its own width minus padding`() {
        val width = LauncherDesktopGeometryManager.viewportContentWidthDp(
            screenWidthDp = 411,
            measuredOuterWidthPx = 600,
            chromeWidthDp = 0f,
            paddingPx = 20,
            density = 2f,
        )
        assertEquals(290f, width, DELTA)
    }

    @Test
    fun `without a measurement the last chrome comes off the window width`() {
        val width = LauncherDesktopGeometryManager.viewportContentWidthDp(
            screenWidthDp = 411,
            measuredOuterWidthPx = null,
            chromeWidthDp = 111f,
            paddingPx = 0,
            density = 2f,
        )
        assertEquals(300f, width, DELTA)
    }

    @Test
    fun `a side taskbar viewport gets cells of the promised size`() {
        val width = LauncherDesktopGeometryManager.viewportContentWidthDp(
            screenWidthDp = 411,
            measuredOuterWidthPx = 290,
            chromeWidthDp = 0f,
            paddingPx = 0,
            density = 1f,
        )
        val columns = LauncherGridGeometry.columns(availableWidthDp = width, densityFactor = 1.5f)
        assertEquals(4, columns)
    }

    @Test
    fun `chrome wider than the window never yields a negative width`() {
        val width = LauncherDesktopGeometryManager.viewportContentWidthDp(
            screenWidthDp = 100,
            measuredOuterWidthPx = null,
            chromeWidthDp = 150f,
            paddingPx = 8,
            density = 1f,
        )
        assertEquals(0f, width, DELTA)
    }

    private companion object {
        const val DELTA = 0.01f
    }
}
