package com.sza.fastmediasorter.vr.ui

import android.app.Activity
import com.sza.fastmediasorter.ui.player.contracts.PlaybackCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VrControlOverlayManagerTest {

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    @Test
    fun `toggle flips overlay visibility state`() {
        val manager = VrControlOverlayManager(activity = activity(), onCommand = {})

        assertFalse(manager.isOverlayVisible())
        manager.toggle()
        assertTrue(manager.isOverlayVisible())
        manager.toggle()
        assertFalse(manager.isOverlayVisible())
    }

    @Test
    fun `dispatchCommand forwards commands in the VR set including file operations`() {
        val received = mutableListOf<PlaybackCommand>()
        val manager = VrControlOverlayManager(activity = activity(), onCommand = received::add)

        // File operations are part of forVrPlayback() now — the immersive file-ops
        // panel routes Copy/Move/Delete/Rename through the same dispatch pipeline.
        manager.dispatchCommand(PlaybackCommand.Play)
        manager.dispatchCommand(PlaybackCommand.DeleteFile)

        assertEquals(listOf(PlaybackCommand.Play, PlaybackCommand.DeleteFile), received)
    }

    @Test
    fun `dispatchCommand drops commands outside the VR set`() {
        val received = mutableListOf<PlaybackCommand>()
        val manager = VrControlOverlayManager(activity = activity(), onCommand = received::add)

        // VolumeStep is a data-class command delivered by the controller input manager
        // directly to the activity; the overlay's hard-coded button set never issues one,
        // so it is correctly absent from PlaybackCommandSet.forVrPlayback() and must be
        // filtered out when passed through the overlay.
        manager.dispatchCommand(PlaybackCommand.VolumeStep(+1))
        manager.dispatchCommand(PlaybackCommand.Play)

        assertEquals(listOf(PlaybackCommand.Play), received)
    }
}