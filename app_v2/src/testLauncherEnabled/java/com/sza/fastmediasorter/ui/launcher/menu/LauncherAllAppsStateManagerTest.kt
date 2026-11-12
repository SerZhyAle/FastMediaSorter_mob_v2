package com.sza.fastmediasorter.ui.launcher.menu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherAllAppsStateManagerTest {
    @Test
    fun `restore follows the stable key when items move`() {
        val anchor = LauncherAllAppsStateManager.Anchor("app:category:a", 1, -12)
        assertEquals(2, LauncherAllAppsStateManager.resolvePosition(anchor, listOf("x", "y", anchor.key)))
    }

    @Test
    fun `removed anchor falls back to nearest available position`() {
        val anchor = LauncherAllAppsStateManager.Anchor("missing", 8, 0)
        assertEquals(1, LauncherAllAppsStateManager.resolvePosition(anchor, listOf("x", "y")))
        assertNull(LauncherAllAppsStateManager.resolvePosition(anchor, emptyList()))
    }
}
