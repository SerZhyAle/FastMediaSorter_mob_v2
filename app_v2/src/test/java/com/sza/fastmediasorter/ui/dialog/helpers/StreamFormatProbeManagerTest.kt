package com.sza.fastmediasorter.ui.dialog.helpers

import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.VideoSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1474: covers the pure half of the measurement - what the engine reported turned into the holder the
 * window renders. The engine itself is not exercised here; these are the rules that decide whether a
 * number reaches the user or an honest "not reported" does.
 */
class StreamFormatProbeManagerTest {

    @Test
    fun `a populated format fills every matching field`() {
        val measured = formatsFrom(
            videoFormat = videoFormat(width = 1280, height = 720),
            audioFormat = audioFormat(),
            videoSize = VideoSize.UNKNOWN,
            bitrateEstimate = ESTIMATE_BPS,
        )

        assertEquals(MimeTypes.VIDEO_H264, measured.videoMimeType)
        assertEquals(1280, measured.videoWidth)
        assertEquals(720, measured.videoHeight)
        assertEquals(FRAME_RATE, measured.frameRate)
        assertEquals(VIDEO_BITRATE, measured.videoBitrate)
        assertEquals(MimeTypes.AUDIO_AAC, measured.audioMimeType)
        assertEquals(CHANNELS, measured.audioChannelCount)
        assertEquals(SAMPLE_RATE, measured.audioSampleRate)
        assertEquals(ESTIMATE_BPS, measured.observedBitrate)
    }

    @Test
    fun `a format without a declared picture size falls back to the video size`() {
        val measured = formatsFrom(
            videoFormat = videoFormat(width = Format.NO_VALUE, height = Format.NO_VALUE),
            audioFormat = null,
            videoSize = VideoSize(1920, 1080),
            bitrateEstimate = null,
        )

        assertEquals(1920, measured.videoWidth)
        assertEquals(1080, measured.videoHeight)
    }

    @Test
    fun `a non-positive bandwidth estimate is reported as unknown rather than as zero`() {
        val measured = formatsFrom(null, null, null, bitrateEstimate = 0L)

        assertNull(measured.observedBitrate)
    }

    @Test
    fun `extraction from a null format yields an all-null holder instead of throwing`() {
        val measured = formatsFrom(null, null, null, null)

        assertEquals(StreamMeasuredFormats(), measured)
    }

    @Test
    fun `a later reading never blanks a field an earlier one filled`() {
        val first = StreamMeasuredFormats(videoMimeType = MimeTypes.VIDEO_H264, videoWidth = 640)
        val second = StreamMeasuredFormats(videoWidth = 1280, audioMimeType = MimeTypes.AUDIO_AAC)

        val merged = first.mergedWith(second)

        assertEquals(MimeTypes.VIDEO_H264, merged.videoMimeType)
        assertEquals(1280, merged.videoWidth)
        assertEquals(MimeTypes.AUDIO_AAC, merged.audioMimeType)
    }

    private fun videoFormat(width: Int, height: Int): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.VIDEO_H264)
        .setWidth(width)
        .setHeight(height)
        .setFrameRate(FRAME_RATE)
        .setAverageBitrate(VIDEO_BITRATE)
        .build()

    private fun audioFormat(): Format = Format.Builder()
        .setSampleMimeType(MimeTypes.AUDIO_AAC)
        .setChannelCount(CHANNELS)
        .setSampleRate(SAMPLE_RATE)
        .setAverageBitrate(AUDIO_BITRATE)
        .build()

    private companion object {
        const val FRAME_RATE = 25f
        const val VIDEO_BITRATE = 2_500_000
        const val AUDIO_BITRATE = 128_000
        const val CHANNELS = 2
        const val SAMPLE_RATE = 48_000
        const val ESTIMATE_BPS = 3_100_000L
    }
}
