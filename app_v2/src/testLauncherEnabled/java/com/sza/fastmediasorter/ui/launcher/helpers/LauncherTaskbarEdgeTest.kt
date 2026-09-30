package com.sza.fastmediasorter.ui.launcher.helpers

import com.sza.fastmediasorter.domain.model.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherTaskbarEdgeTest {

    @Test
    fun `every stored placement option decodes to its own edge`() {
        val decoded = AppSettings.LAUNCHER_TASKBAR_PLACEMENT_OPTIONS.map { LauncherTaskbarEdge.fromToken(it) }

        assertEquals(LauncherTaskbarEdge.entries.toList(), decoded)
    }

    @Test
    fun `an unknown or missing token degrades to the bottom edge`() {
        assertEquals(LauncherTaskbarEdge.BOTTOM, LauncherTaskbarEdge.fromToken("DIAGONAL"))
        assertEquals(LauncherTaskbarEdge.BOTTOM, LauncherTaskbarEdge.fromToken(null))
    }

    @Test
    fun `only the side edges draw a column`() {
        assertTrue(LauncherTaskbarEdge.LEFT.isVertical)
        assertTrue(LauncherTaskbarEdge.RIGHT.isVertical)
        assertFalse(LauncherTaskbarEdge.TOP.isVertical)
        assertFalse(LauncherTaskbarEdge.BOTTOM.isVertical)
    }
}
