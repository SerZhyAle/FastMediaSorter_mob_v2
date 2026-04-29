package com.sza.fastmediasorter.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCompletionDetectorTest {

    @Test
    fun `30s file at 28s is not near-end (28000 below cutoff 28500)`() {
        assertFalse(PlaybackCompletionDetector.isNearEnd(28_000L, 30_000L))
    }

    @Test
    fun `30s file at 29s is near-end`() {
        assertTrue(PlaybackCompletionDetector.isNearEnd(29_000L, 30_000L))
    }

    @Test
    fun `30s file at end is near-end`() {
        assertTrue(PlaybackCompletionDetector.isNearEnd(30_000L, 30_000L))
    }

    @Test
    fun `1h file at 3595000ms is near-end (5s before end)`() {
        assertTrue(PlaybackCompletionDetector.isNearEnd(3_595_000L, 3_600_000L))
    }

    @Test
    fun `1h file at 3400000ms is not near-end`() {
        assertFalse(PlaybackCompletionDetector.isNearEnd(3_400_000L, 3_600_000L))
    }

    @Test
    fun `8s file at 4100ms is not near-end (cutoff is 7600ms)`() {
        assertFalse(PlaybackCompletionDetector.isNearEnd(4_100L, 8_000L))
    }

    @Test
    fun `8s file at 7700ms is near-end`() {
        assertTrue(PlaybackCompletionDetector.isNearEnd(7_700L, 8_000L))
    }

    @Test
    fun `non-positive duration is never near-end`() {
        assertFalse(PlaybackCompletionDetector.isNearEnd(0L, 0L))
        assertFalse(PlaybackCompletionDetector.isNearEnd(100L, 0L))
        assertFalse(PlaybackCompletionDetector.isNearEnd(100L, -1L))
    }

    @Test
    fun `negative position is never near-end`() {
        assertFalse(PlaybackCompletionDetector.isNearEnd(-1L, 30_000L))
    }

    @Test
    fun `nearEndThresholdMs returns 0 for non-positive duration`() {
        assertEquals(0L, PlaybackCompletionDetector.nearEndThresholdMs(0L))
        assertEquals(0L, PlaybackCompletionDetector.nearEndThresholdMs(-1L))
    }

    @Test
    fun `nearEndThresholdMs caps at 5000ms for long files`() {
        assertEquals(5_000L, PlaybackCompletionDetector.nearEndThresholdMs(3_600_000L))
        assertEquals(5_000L, PlaybackCompletionDetector.nearEndThresholdMs(100_000L))
    }

    @Test
    fun `nearEndThresholdMs is 5 percent for medium files`() {
        assertEquals(1_500L, PlaybackCompletionDetector.nearEndThresholdMs(30_000L))
        assertEquals(2_500L, PlaybackCompletionDetector.nearEndThresholdMs(50_000L))
    }
}
