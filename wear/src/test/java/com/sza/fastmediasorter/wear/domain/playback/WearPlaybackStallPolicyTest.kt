package com.sza.fastmediasorter.wear.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * S2848: every combination of the three inputs, because the rule's whole value is that the one
 * combination nothing else in the service reacts to - wants to play, makes no sound, not ended - is
 * distinguishable from the seven that are already handled.
 */
class WearPlaybackStallPolicyTest {

    @Test
    fun `wants to play, no sound, not ended - stalled`() {
        assertEquals(
            BackgroundPlaybackActivity.Stalled,
            WearPlaybackStallPolicy.activityOf(
                playWhenReady = true,
                isPlaying = false,
                isEndedOrIdle = false
            )
        )
    }

    @Test
    fun `wants to play and sounds - playing`() {
        assertEquals(
            BackgroundPlaybackActivity.Playing,
            WearPlaybackStallPolicy.activityOf(
                playWhenReady = true,
                isPlaying = true,
                isEndedOrIdle = false
            )
        )
    }

    @Test
    fun `paused with no sound - settled`() {
        assertEquals(
            BackgroundPlaybackActivity.Settled,
            WearPlaybackStallPolicy.activityOf(
                playWhenReady = false,
                isPlaying = false,
                isEndedOrIdle = false
            )
        )
    }

    @Test
    fun `paused while still sounding - settled`() {
        assertEquals(
            BackgroundPlaybackActivity.Settled,
            WearPlaybackStallPolicy.activityOf(
                playWhenReady = false,
                isPlaying = true,
                isEndedOrIdle = false
            )
        )
    }

    @Test
    fun `ended outranks wanting to play`() {
        assertEquals(
            BackgroundPlaybackActivity.Settled,
            WearPlaybackStallPolicy.activityOf(
                playWhenReady = true,
                isPlaying = false,
                isEndedOrIdle = true
            )
        )
    }

    @Test
    fun `ended while still sounding - settled`() {
        assertEquals(
            BackgroundPlaybackActivity.Settled,
            WearPlaybackStallPolicy.activityOf(
                playWhenReady = true,
                isPlaying = true,
                isEndedOrIdle = true
            )
        )
    }

    @Test
    fun `ended and paused - settled`() {
        assertEquals(
            BackgroundPlaybackActivity.Settled,
            WearPlaybackStallPolicy.activityOf(
                playWhenReady = false,
                isPlaying = false,
                isEndedOrIdle = true
            )
        )
    }

    @Test
    fun `ended, paused, still sounding - settled`() {
        assertEquals(
            BackgroundPlaybackActivity.Settled,
            WearPlaybackStallPolicy.activityOf(
                playWhenReady = false,
                isPlaying = true,
                isEndedOrIdle = true
            )
        )
    }
}
