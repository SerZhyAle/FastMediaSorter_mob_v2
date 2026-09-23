package com.sza.fastmediasorter.domain.usecase.streams

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamMediaKindClassifierTest {

    private val classifier = StreamMediaKindClassifier()

    @Test
    fun `classify picks the kind off the scheme and extension`() {
        assertEquals("RTSP", classifier.classify("rtsp://example.com/cam1"))
        assertEquals("VIDEO", classifier.classify("https://example.com/stream.m3u8"))
        assertEquals("AUDIO", classifier.classify("https://example.com/radio.mp3"))
        assertEquals("AUDIO", classifier.classify("https://example.com/live"))
    }

    @Test
    fun `resolve keeps a recognised declared kind whatever its case`() {
        assertEquals("VIDEO", classifier.resolve("VIDEO", "https://example.com/radio.mp3"))
        assertEquals("AUDIO", classifier.resolve(" audio ", "https://example.com/stream.m3u8"))
        assertEquals("RTSP", classifier.resolve("Rtsp", "https://example.com/radio.mp3"))
    }

    @Test
    fun `resolve reclassifies a blank or unrecognised declared kind`() {
        assertEquals("VIDEO", classifier.resolve("", "https://example.com/stream.m3u8"))
        assertEquals("AUDIO", classifier.resolve("PODCAST", "https://example.com/radio.mp3"))
        assertEquals("VIDEO", classifier.resolve("LIVE", "https://example.com/live.mpd"))
        assertEquals("RTSP", classifier.resolve("CAMERA", "rtsp://example.com/cam1"))
    }
}
