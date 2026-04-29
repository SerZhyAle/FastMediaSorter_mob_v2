package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.playback.PlaybackCompletionDetector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S0029: documents the contract that [PlayerLifecycleManager.saveCurrentPlaybackPosition]
 * relies on to choose between `savePosition` and `markPlaybackCompleted`. The decision
 * delegates to [PlaybackCompletionDetector.isNearEnd]; behaviour proven via that helper.
 *
 * Strategic-criterion mirror:
 * - 30 s file, position 28 s → NOT near-end → branch chooses `savePosition`.
 * - 30 s file, position 29 s → near-end → branch chooses `markPlaybackCompleted`.
 */
class PlayerLifecycleManagerNearEndTest {

    @Test
    fun `30s file at 28s is mid-playback so manager chooses savePosition`() {
        assertFalse(PlaybackCompletionDetector.isNearEnd(28_000L, 30_000L))
    }

    @Test
    fun `30s file at 29s is near-end so manager chooses markPlaybackCompleted`() {
        assertTrue(PlaybackCompletionDetector.isNearEnd(29_000L, 30_000L))
    }

    @Test
    fun `1h file at 5min is mid-playback so manager chooses savePosition`() {
        assertFalse(PlaybackCompletionDetector.isNearEnd(300_000L, 3_600_000L))
    }

    @Test
    fun `1h file at 5s before end is near-end so manager chooses markPlaybackCompleted`() {
        assertTrue(PlaybackCompletionDetector.isNearEnd(3_595_000L, 3_600_000L))
    }
}
