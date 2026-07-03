package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrefetchLoadControlFactoryTest {

    @Test
    fun `local audio fallback uses compact audio buffer profile`() {
        val buffer = PrefetchLoadControlFactory.legacyBufferDurations(
            useCloudDefaults = false,
            isAudio = true,
            useNetworkAudioDefaults = false,
        )

        assertEquals(VideoPlayerManager.AUDIO_MIN_BUFFER_MS, buffer.minMs)
        assertEquals(VideoPlayerManager.AUDIO_MAX_BUFFER_MS, buffer.maxMs)
        assertEquals(VideoPlayerManager.AUDIO_BUFFER_FOR_PLAYBACK_MS, buffer.playbackMs)
        assertEquals(VideoPlayerManager.AUDIO_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS, buffer.rebufferMs)
    }

    @Test
    fun `network audio fallback uses larger audio network profile`() {
        val buffer = PrefetchLoadControlFactory.legacyBufferDurations(
            useCloudDefaults = false,
            isAudio = true,
            useNetworkAudioDefaults = true,
        )

        assertEquals(VideoPlayerManager.AUDIO_NETWORK_MIN_BUFFER_MS, buffer.minMs)
        assertEquals(VideoPlayerManager.AUDIO_NETWORK_MAX_BUFFER_MS, buffer.maxMs)
        assertEquals(VideoPlayerManager.AUDIO_NETWORK_BUFFER_FOR_PLAYBACK_MS, buffer.playbackMs)
        assertEquals(VideoPlayerManager.AUDIO_NETWORK_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS, buffer.rebufferMs)
    }

    // S0772: heap-bounded video buffer cap.

    @Test
    fun `quest3 512MB heap caps video buffer at one eighth of heap`() {
        // Quest 3 / canonical emulator report maxHeapMb == 512 exactly (LOW tier).
        val cap = PrefetchLoadControlFactory.videoBufferCapBytesForHeap(maxHeapMb = 512)

        assertEquals(64 * 1024 * 1024, cap)
    }

    @Test
    fun `high heap device keeps unbounded time-priority default`() {
        // > 512 MB heap is not heap-constrained - no explicit byte cap, preserves prior behaviour.
        assertNull(PrefetchLoadControlFactory.videoBufferCapBytesForHeap(maxHeapMb = 513))
        assertNull(PrefetchLoadControlFactory.videoBufferCapBytesForHeap(maxHeapMb = 1024))
    }

    @Test
    fun `very small heap is clamped to the minimum cap`() {
        // 128 MB / 8 = 16 MB < 24 MB floor -> clamp up so the buffer can still hold playback start.
        val cap = PrefetchLoadControlFactory.videoBufferCapBytesForHeap(maxHeapMb = 128)

        assertEquals(24 * 1024 * 1024, cap)
    }

    @Test
    fun `large but still-constrained heap is clamped to the maximum cap`() {
        // A 512 MB-threshold boundary stays at 64 MB, but the ceiling guards any future widening.
        val cap = PrefetchLoadControlFactory.videoBufferCapBytesForHeap(maxHeapMb = 512)

        assertEquals(true, cap!! <= 96 * 1024 * 1024)
    }
}