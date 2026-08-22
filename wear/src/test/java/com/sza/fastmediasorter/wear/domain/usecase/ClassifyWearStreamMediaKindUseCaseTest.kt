package com.sza.fastmediasorter.wear.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifyWearStreamMediaKindUseCaseTest {

    private val classifier = ClassifyWearStreamMediaKindUseCase()

    @Test
    fun `isSupportedScheme validates http https and rtsp schemes`() {
        assertTrue(classifier.isSupportedScheme("http://stream.example.com/audio"))
        assertTrue(classifier.isSupportedScheme("https://stream.example.com/live.m3u8"))
        assertTrue(classifier.isSupportedScheme("rtsp://stream.example.com/camera1"))
        assertFalse(classifier.isSupportedScheme("ftp://example.com/file"))
        assertFalse(classifier.isSupportedScheme("file:///local/path"))
    }

    @Test
    fun `classify identifies rtsp schemes`() {
        assertEquals("RTSP", classifier.classify("rtsp://stream.example.com/live"))
    }

    @Test
    fun `classify identifies video extensions`() {
        assertEquals("VIDEO", classifier.classify("https://example.com/stream.m3u8"))
        assertEquals("VIDEO", classifier.classify("https://example.com/live.mpd"))
        assertEquals("VIDEO", classifier.classify("https://example.com/video.mp4"))
        assertEquals("VIDEO", classifier.classify("https://example.com/file.mkv?token=123"))
    }

    @Test
    fun `classify defaults to audio for non-video urls`() {
        assertEquals("AUDIO", classifier.classify("https://example.com/radio.mp3"))
        assertEquals("AUDIO", classifier.classify("https://example.com/live"))
    }
}
