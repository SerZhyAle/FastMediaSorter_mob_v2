package com.sza.fastmediasorter.ui.player.helpers

import android.os.Looper
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class PlayerFrameStepManagerTest {
    private val player = mockk<ExoPlayer>(relaxed = true)
    private val metadata = slot<VideoFrameMetadataListener>()
    private val listener = slot<Player.Listener>()

    @Before
    fun attachRecorder() {
        every { player.applicationLooper } returns Looper.getMainLooper()
        every { player.setVideoFrameMetadataListener(capture(metadata)) } answers { }
        every { player.addListener(capture(listener)) } answers { }
        every { player.duration } returns 10_000L
        every { player.currentPosition } returns 30L
        every { player.volume } returns 0.7f
        every { player.playbackParameters } returns PlaybackParameters(1.25f)
        every { player.isCommandAvailable(any()) } returns true
        PlayerFrameStepManager.attach(player)
    }

    @After
    fun detachRecorder() {
        PlayerFrameStepManager.detach(player)
    }

    private fun frame(timestampUs: Long) {
        metadata.captured.onVideoFrameAboutToBeRendered(timestampUs, System.nanoTime(), Format.Builder().build(), null)
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `forward step plays muted and slowed until a later decoded frame, then restores playback`() {
        frame(20_731L)
        assertTrue(PlayerFrameStepManager.stepForward(player))
        verify { player.volume = 0f }
        verify { player.playbackParameters = PlaybackParameters(0.1f) }
        verify { player.play() }
        verify(exactly = 0) { player.seekTo(any<Long>()) }

        frame(20_731L)
        verify(exactly = 0) { player.pause() }

        frame(59_123L)
        verify(exactly = 1) { player.pause() }
        verify { player.playbackParameters = PlaybackParameters(1.25f) }
        verify { player.volume = 0.7f }
    }

    @Test
    fun `repeated requests serialize, each waiting for its own next frame`() {
        frame(20_731L)
        assertTrue(PlayerFrameStepManager.stepForward(player))
        assertTrue(PlayerFrameStepManager.stepForward(player))
        verify(exactly = 1) { player.play() }

        frame(59_123L)
        verify(exactly = 1) { player.pause() }
        verify(exactly = 2) { player.play() }

        frame(101_002L)
        verify(exactly = 2) { player.pause() }
        verify(exactly = 1) { player.playbackParameters = PlaybackParameters(1.25f) }
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

    @Test
    fun `a step at the last frame is refused`() {
        every { player.duration } returns 20L
        frame(20_731L)
        assertFalse(PlayerFrameStepManager.stepForward(player))
        verify(exactly = 0) { player.play() }
    }

    @Test
    fun `frames reported ahead of the clock before a pause are not taken as visible`() {
        frame(20_731L)
        frame(40_000L)
        frame(60_000L)
        assertTrue(PlayerFrameStepManager.stepForward(player))
        verify(exactly = 1) { player.createMessage(any()) }
        frame(40_000L)
        verify(exactly = 1) { player.pause() }
    }

    @Test
    fun `a user pause re-renders the frame at the clock, a step's own pause does not`() {
        every { player.playWhenReady } returns false
        every { player.playbackState } returns Player.STATE_READY
        every { player.videoFormat } returns Format.Builder().build()
        every { player.isCurrentMediaItemLive } returns false
        listener.captured.onIsPlayingChanged(false)
        verify { player.setSeekParameters(SeekParameters.EXACT) }
        verify(exactly = 1) { player.seekTo(30L) }

        frame(20_731L)
        assertTrue(PlayerFrameStepManager.stepForward(player))
        frame(59_123L)
        listener.captured.onIsPlayingChanged(false)
        verify(exactly = 1) { player.seekTo(any<Long>()) }
    }
}
