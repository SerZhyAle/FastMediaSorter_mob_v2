package com.sza.fastmediasorter.ui.player.helpers

import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerFrameStepManagerTest {
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val metadata = slot<VideoFrameMetadataListener>()
    private val listener = slot<Player.Listener>()

    @Before
    fun attachRecorder() {
        every { player.setVideoFrameMetadataListener(capture(metadata)) } answers { }
        every { player.addListener(capture(listener)) } answers { }
        every { player.duration } returns 10_000L
        every { player.seekParameters } returns SeekParameters.CLOSEST_SYNC
        every { player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) } returns true
        PlayerFrameStepManager.attach(player)
    }

    @After
    fun detachRecorder() {
        PlayerFrameStepManager.detach(player)
    }

    private fun frame(timestampUs: Long) {
        metadata.captured.onVideoFrameAboutToBeRendered(timestampUs, System.nanoTime(), Format.Builder().build(), null)
    }

    @Test
    fun `forward step uses the rendered timestamp rather than nominal frame rate or playback position`() {
        frame(20_731L)
        assertTrue(PlayerFrameStepManager.stepForward(player))
        verify { player.pause() }
        verify { player.setSeekParameters(SeekParameters.EXACT) }
        verify { player.seekTo(21L) }
    }

    @Test
    fun `repeated requests serialize until a new frame arrives and restore seek policy`() {
        frame(20_731L)
        assertTrue(PlayerFrameStepManager.stepForward(player))
        assertTrue(PlayerFrameStepManager.stepForward(player))
        verify(exactly = 1) { player.seekTo(any()) }
        frame(59_123L)
        listener.captured.onRenderedFirstFrame()
        verify { player.seekTo(60L) }
        verify { player.setSeekParameters(SeekParameters.CLOSEST_SYNC) }
    }

    @Test
    fun `source changes invalidate the old frame and detach removes both listeners`() {
        frame(20_731L)
        listener.captured.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        assertFalse(PlayerFrameStepManager.stepForward(player))
        PlayerFrameStepManager.detach(player)
        verify { player.clearVideoFrameMetadataListener(metadata.captured) }
        verify { player.removeListener(listener.captured) }
        assertFalse(PlayerFrameStepManager.stepForward(player))
    }
}
