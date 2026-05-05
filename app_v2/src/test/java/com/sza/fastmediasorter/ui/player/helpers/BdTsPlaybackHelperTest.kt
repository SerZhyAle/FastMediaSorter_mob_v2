package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.data.network.datasource.TsPacketFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BdTsPlaybackHelperTest {

    // ── NetworkPlaybackContainerHint.fromPath ─────────────────────────────

    @Test
    fun `m2ts maps to M2TS_TS_CANDIDATE`() {
        assertEquals(
            NetworkPlaybackContainerHint.M2TS_TS_CANDIDATE,
            NetworkPlaybackContainerHint.fromPath("/media/disc.m2ts")
        )
    }

    @Test
    fun `m2t maps to M2TS_TS_CANDIDATE`() {
        assertEquals(
            NetworkPlaybackContainerHint.M2TS_TS_CANDIDATE,
            NetworkPlaybackContainerHint.fromPath("/media/disc.m2t")
        )
    }

    @Test
    fun `vob maps to DVD_PS_VOB`() {
        assertEquals(
            NetworkPlaybackContainerHint.DVD_PS_VOB,
            NetworkPlaybackContainerHint.fromPath("/VIDEO_TS/VTS_01_1.VOB")
        )
    }

    @Test
    fun `vob maps to DVD_PS_VOB lowercase`() {
        assertEquals(
            NetworkPlaybackContainerHint.DVD_PS_VOB,
            NetworkPlaybackContainerHint.fromPath("/VIDEO_TS/VTS_01_1.vob")
        )
    }

    @Test
    fun `mixed-case VOB extension maps to DVD_PS_VOB`() {
        assertEquals(
            NetworkPlaybackContainerHint.DVD_PS_VOB,
            NetworkPlaybackContainerHint.fromPath("/VIDEO_TS/MOVIE.Vob")
        )
    }

    @Test
    fun `mixed-case M2TS extension maps to M2TS_TS_CANDIDATE`() {
        assertEquals(
            NetworkPlaybackContainerHint.M2TS_TS_CANDIDATE,
            NetworkPlaybackContainerHint.fromPath("/BDMV/STREAM/00001.M2TS")
        )
    }

    @Test
    fun `mp4 maps to OTHER`() {
        assertEquals(
            NetworkPlaybackContainerHint.OTHER,
            NetworkPlaybackContainerHint.fromPath("/movies/film.mp4")
        )
    }

    // ── shouldUseBdTsStripper ─────────────────────────────────────────────

    @Test
    fun `BD_192 requires stripper`() {
        assertTrue(shouldUseBdTsStripper(TsPacketFormat.BD_192))
    }

    @Test
    fun `STANDARD_188 does not require stripper`() {
        assertFalse(shouldUseBdTsStripper(TsPacketFormat.STANDARD_188))
    }

    @Test
    fun `UNKNOWN stays pass-through`() {
        assertFalse(shouldUseBdTsStripper(TsPacketFormat.UNKNOWN))
    }
}
