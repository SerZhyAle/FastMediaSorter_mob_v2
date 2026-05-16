package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import org.junit.Assert.assertEquals
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
}