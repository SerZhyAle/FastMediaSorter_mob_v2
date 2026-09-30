package com.sza.fastmediasorter.ui.common.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputHelpRegistryTest {

    @Test
    fun `every surface has at least the global section`() {
        for (surface in UiSurface.values()) {
            val sections = InputHelpRegistry.get(surface)
            assertFalse(
                "Surface $surface has no help sections",
                sections.isEmpty(),
            )
        }
    }

    @Test
    fun `main surface exposes file operations`() {
        val sections = InputHelpRegistry.get(UiSurface.MAIN)
        val combined = sections.flatMap { it.entries }.map { it.keys }
        assertTrue(combined.any { it.contains("F5") })
        assertTrue(combined.any { it.contains("F8") || it.contains("Del") })
    }

    @Test
    fun `player surface exposes playback shortcuts`() {
        val sections = InputHelpRegistry.get(UiSurface.PLAYER)
        val combined = sections.flatMap { it.entries }.map { it.keys }
        assertTrue("missing play-pause", combined.any { it.contains("Space") })
        assertTrue("missing mute", combined.any { it == "M" })
        assertTrue("missing fullscreen", combined.any { it == "F" })
    }

    @Test
    fun `VR player reuses player help entries`() {
        assertEquals(
            InputHelpRegistry.get(UiSurface.PLAYER),
            InputHelpRegistry.get(UiSurface.VR_PLAYER),
        )
    }
}
