package com.sza.fastmediasorter.domain.usecase.link

import com.sza.fastmediasorter.domain.usecase.link.YtMusicAudioOnlyContract.ValidationOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtMusicAudioOnlyContractTest {

    private val contract = YtMusicAudioOnlyContract()

    @Test
    fun `non ytmusic host without audioOnly flag is accepted regardless of artifact`() {
        val outcome = contract.validate(
            originalUrl = "https://example.com/x",
            canonicalAudioOnly = false,
            resultMime = "image/png",
            resultFileName = "thumb.png",
        )
        assertTrue(outcome is ValidationOutcome.Accept)
    }

    @Test
    fun `ytmusic host with audio mime is accepted`() {
        val outcome = contract.validate(
            originalUrl = "https://music.youtube.com/watch?v=abc",
            canonicalAudioOnly = false,
            resultMime = "audio/mpeg",
            resultFileName = "song.mp3",
        )
        assertTrue(outcome is ValidationOutcome.Accept)
    }

    @Test
    fun `audioOnly flag forces validation even on non ytmusic host`() {
        val outcome = contract.validate(
            originalUrl = "https://example.com/x",
            canonicalAudioOnly = true,
            resultMime = "audio/mp4",
            resultFileName = "song.m4a",
        )
        assertTrue(outcome is ValidationOutcome.Accept)
    }

    @Test
    fun `image artifact for ytmusic is rejected without fallback`() {
        val outcome = contract.validate(
            originalUrl = "https://music.youtube.com/watch?v=abc",
            canonicalAudioOnly = true,
            resultMime = "image/jpeg",
            resultFileName = "cover.jpg",
        )
        outcome as ValidationOutcome.Reject
        assertEquals("ytmusic_thumbnail_artifact", outcome.reasonCode)
        assertFalse(outcome.fallbackAllowed)
    }

    @Test
    fun `unknown artifact for ytmusic is rejected as non-audio`() {
        val outcome = contract.validate(
            originalUrl = "https://music.youtube.com/watch?v=abc",
            canonicalAudioOnly = true,
            resultMime = "application/octet-stream",
            resultFileName = "data.bin",
        )
        outcome as ValidationOutcome.Reject
        assertEquals("ytmusic_non_audio_artifact", outcome.reasonCode)
        assertFalse(outcome.fallbackAllowed)
    }

    @Test
    fun `audio extension without mime is accepted`() {
        val outcome = contract.validate(
            originalUrl = "https://music.youtube.com/watch?v=abc",
            canonicalAudioOnly = true,
            resultMime = null,
            resultFileName = "track.flac",
        )
        assertTrue(outcome is ValidationOutcome.Accept)
    }
}
