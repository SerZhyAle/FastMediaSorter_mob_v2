package com.sza.fastmediasorter.wear.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2166: every combination of the three inputs, because the rule's whole value is that the one
 * combination which hands off is distinguishable from the seven that do not.
 */
class WearBackgroundPlaybackPolicyTest {

    @Test
    fun `setting on, audio, playing - hands off`() {
        assertEquals(
            HostStopAction.HandOff,
            WearBackgroundPlaybackPolicy.onHostStopped(
                backgroundPlaybackEnabled = true,
                isAudioContent = true,
                isPlaying = true
            )
        )
    }

    @Test
    fun `setting on, audio, paused - pauses`() {
        assertEquals(
            HostStopAction.Pause,
            WearBackgroundPlaybackPolicy.onHostStopped(
                backgroundPlaybackEnabled = true,
                isAudioContent = true,
                isPlaying = false
            )
        )
    }

    @Test
    fun `setting on, video, playing - pauses`() {
        assertEquals(
            HostStopAction.Pause,
            WearBackgroundPlaybackPolicy.onHostStopped(
                backgroundPlaybackEnabled = true,
                isAudioContent = false,
                isPlaying = true
            )
        )
    }

    @Test
    fun `setting on, video, paused - pauses`() {
        assertEquals(
            HostStopAction.Pause,
            WearBackgroundPlaybackPolicy.onHostStopped(
                backgroundPlaybackEnabled = true,
                isAudioContent = false,
                isPlaying = false
            )
        )
    }

    @Test
    fun `setting off, audio, playing - pauses`() {
        assertEquals(
            HostStopAction.Pause,
            WearBackgroundPlaybackPolicy.onHostStopped(
                backgroundPlaybackEnabled = false,
                isAudioContent = true,
                isPlaying = true
            )
        )
    }

    @Test
    fun `setting off, audio, paused - pauses`() {
        assertEquals(
            HostStopAction.Pause,
            WearBackgroundPlaybackPolicy.onHostStopped(
                backgroundPlaybackEnabled = false,
                isAudioContent = true,
                isPlaying = false
            )
        )
    }

    @Test
    fun `setting off, video, playing - pauses`() {
        assertEquals(
            HostStopAction.Pause,
            WearBackgroundPlaybackPolicy.onHostStopped(
                backgroundPlaybackEnabled = false,
                isAudioContent = false,
                isPlaying = true
            )
        )
    }

    @Test
    fun `setting off, video, paused - pauses`() {
        assertEquals(
            HostStopAction.Pause,
            WearBackgroundPlaybackPolicy.onHostStopped(
                backgroundPlaybackEnabled = false,
                isAudioContent = false,
                isPlaying = false
            )
        )
    }

    @Test
    fun `an explicit exit stops the background session`() {
        assertEquals(
            true,
            WearBackgroundPlaybackPolicy.stopsBackgroundSession(HostTeardownReason.ExplicitExit)
        )
    }

    @Test
    fun `minimizing does not stop the background session`() {
        assertEquals(
            false,
            WearBackgroundPlaybackPolicy.stopsBackgroundSession(HostTeardownReason.Minimized)
        )
    }

    @Test
    fun `the screen going off does not stop the background session`() {
        assertEquals(
            false,
            WearBackgroundPlaybackPolicy.stopsBackgroundSession(HostTeardownReason.ScreenOff)
        )
    }

    @Test
    fun `leaving the player screen does not stop the background session`() {
        assertEquals(
            false,
            WearBackgroundPlaybackPolicy.stopsBackgroundSession(HostTeardownReason.LeftPlayerScreen)
        )
    }

    /** A reason added later without a case here would otherwise be silently un-decided. */
    @Test
    fun `every teardown reason has a case above`() {
        assertEquals(4, HostTeardownReason.entries.size)
    }

    @Test
    fun `a playing session keeps the background service`() {
        assertEquals(true, WearBackgroundPlaybackPolicy.keepsBackgroundSession(isPlaying = true))
    }

    @Test
    fun `a paused session releases the background service`() {
        assertEquals(false, WearBackgroundPlaybackPolicy.keepsBackgroundSession(isPlaying = false))
    }
}
