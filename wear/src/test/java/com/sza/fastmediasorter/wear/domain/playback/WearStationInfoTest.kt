package com.sza.fastmediasorter.wear.domain.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S3099: the station line is assembled from four fields a third party may leave empty in any
 * combination, so what the row shows is decided here rather than on the glass.
 */
class WearStationInfoTest {

    private val bitrateLabel: (Int) -> String = { "$it kbps" }

    @Test
    fun `parts follow the display order`() {
        val info = WearStationInfo(
            name = "Radio X",
            genre = "Jazz",
            bitrateKbps = 128,
            codec = "MP3",
            resolution = "1280x720"
        )

        assertEquals(listOf("Radio X", "Jazz", "1280x720", "MP3", "128 kbps"), info.textParts(bitrateLabel))
    }

    @Test
    fun `blank and absent fields are dropped`() {
        val info = WearStationInfo(name = "  ", genre = null, bitrateKbps = 0, codec = "AAC", resolution = " ")

        assertEquals(listOf("AAC"), info.textParts(bitrateLabel))
    }

    @Test
    fun `a station that said nothing is empty`() {
        assertTrue(WearStationInfo().isEmpty)
        assertTrue(WearStationInfo(name = " ", codec = "", resolution = "  ").isEmpty)
    }

    @Test
    fun `a station with one field is not empty`() {
        assertFalse(WearStationInfo(bitrateKbps = 96).isEmpty)
        assertFalse(WearStationInfo(resolution = "1920x1080").isEmpty)
    }

    @Test
    fun `a known mime type becomes a codec name`() {
        assertEquals("MP3", WearStationInfo.codecLabel("audio/mpeg"))
        assertEquals("AAC", WearStationInfo.codecLabel("AUDIO/MP4A-LATM"))
        assertEquals("Vorbis", WearStationInfo.codecLabel(" audio/ogg "))
        assertEquals("H.264", WearStationInfo.codecLabel("video/avc"))
        assertEquals("H.265", WearStationInfo.codecLabel("VIDEO/HEVC"))
        assertEquals("VP8", WearStationInfo.codecLabel("video/x-vnd.on2.vp8"))
        assertEquals("VP9", WearStationInfo.codecLabel("video/x-vnd.on2.vp9"))
        assertEquals("AV1", WearStationInfo.codecLabel("video/av01"))
        assertEquals("MPEG-4", WearStationInfo.codecLabel("video/mp4v-es"))
        assertEquals("H.263", WearStationInfo.codecLabel("video/3gpp"))
    }

    @Test
    fun `an unknown or absent mime type gives nothing to show`() {
        assertNull(WearStationInfo.codecLabel("audio/unknown-codec"))
        assertNull(WearStationInfo.codecLabel("video/unknown-video"))
        assertNull(WearStationInfo.codecLabel(null))
    }
}
