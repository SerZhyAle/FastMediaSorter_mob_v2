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
        val info = WearStationInfo(name = "Radio X", genre = "Jazz", bitrateKbps = 128, codec = "MP3")

        assertEquals(listOf("Radio X", "Jazz", "MP3", "128 kbps"), info.textParts(bitrateLabel))
    }

    @Test
    fun `blank and absent fields are dropped`() {
        val info = WearStationInfo(name = "  ", genre = null, bitrateKbps = 0, codec = "AAC")

        assertEquals(listOf("AAC"), info.textParts(bitrateLabel))
    }

    @Test
    fun `a station that said nothing is empty`() {
        assertTrue(WearStationInfo().isEmpty)
        assertTrue(WearStationInfo(name = " ", codec = "").isEmpty)
    }

    @Test
    fun `a station with one field is not empty`() {
        assertFalse(WearStationInfo(bitrateKbps = 96).isEmpty)
    }

    @Test
    fun `a known mime type becomes a codec name`() {
        assertEquals("MP3", WearStationInfo.codecLabel("audio/mpeg"))
        assertEquals("AAC", WearStationInfo.codecLabel("AUDIO/MP4A-LATM"))
        assertEquals("Vorbis", WearStationInfo.codecLabel(" audio/ogg "))
    }

    @Test
    fun `an unknown or absent mime type gives nothing to show`() {
        assertNull(WearStationInfo.codecLabel("audio/unknown-codec"))
        assertNull(WearStationInfo.codecLabel(null))
    }
}
