package com.sza.fastmediasorter.ui.common.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputHelpRegistryTest {

    @Test
    fun `every surface has at least the global section`() {
        for (surface in InputSurface.values()) {
            val sections = InputHelpRegistry.get(surface)
            assertFalse(
                "Surface $surface has no help sections",
                sections.isEmpty(),
            )
        }
    }

    @Test
    fun `main surface exposes file operations`() {
        val sections = InputHelpRegistry.get(InputSurface.MAIN)
        val combined = sections.flatMap { it.entries }.map { it.keys }
        assertTrue(combined.any { it.contains("F5") })
        assertTrue(combined.any { it.contains("F8") || it.contains("Del") })
    }

    @Test
    fun `player surface exposes playback shortcuts`() {
        val sections = InputHelpRegistry.get(InputSurface.PLAYER)
        val combined = sections.flatMap { it.entries }.map { it.keys }
        assertTrue("missing play-pause", combined.any { it.contains("Space") })
        assertTrue("missing mute", combined.any { it == "M" })
        assertTrue("missing fullscreen", combined.any { it == "F" })
    }

    @Test
    fun `link resolver produces a valid URL for every surface`() {
        for (surface in InputSurface.values()) {
            val url = InputHelpLinkResolver.urlFor(surface)
            assertNotNull(url)
            assertTrue(url.startsWith("https://"))
        }
    }

    @Test
    fun `VR player reuses player help entries`() {
        assertEquals(
            InputHelpRegistry.get(InputSurface.PLAYER),
            InputHelpRegistry.get(InputSurface.VR_PLAYER),
        )
    }
}
