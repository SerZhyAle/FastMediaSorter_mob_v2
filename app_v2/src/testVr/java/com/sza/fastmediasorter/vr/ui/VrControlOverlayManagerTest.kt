package com.sza.fastmediasorter.vr.ui

import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VrControlOverlayManagerTest {

    @Test
    fun `toggle flips overlay visibility state`() {
        val manager = VrControlOverlayManager(onCommand = {})

        assertFalse(manager.isOverlayVisible())
        manager.toggle()
        assertTrue(manager.isOverlayVisible())
        manager.toggle()
        assertFalse(manager.isOverlayVisible())
    }

    @Test
    fun `dispatchCommand ignores unavailable file operations`() {
        val received = mutableListOf<PlaybackCommand>()
        val manager = VrControlOverlayManager(onCommand = received::add)

        manager.dispatchCommand(PlaybackCommand.Play)
        manager.dispatchCommand(PlaybackCommand.DeleteFile)

        assertEquals(listOf(PlaybackCommand.Play), received)
    }
}