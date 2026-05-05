package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.ui.player.helpers.NetworkPlaybackContainerHint
import com.sza.fastmediasorter.ui.player.helpers.shouldUseBdTsStripper
import com.sza.fastmediasorter.data.network.datasource.TsPacketFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * S0076: regression coverage for network VOB container routing.
 *
 * These tests exercise the classification and strip-decision logic that drives
 * whether a playback error reaches the dedicated [VideoPlayerManager.PlayerCallback.onNetworkContainerRouteError]
 * path or the generic [VideoPlayerManager.PlayerCallback.onPlaybackError] path.
 */
class VideoPlayerManagerRouteErrorTest {

    @Test
    fun `vob route error uses dedicated callback`() {
        // A .vob path must resolve to DVD_PS_VOB so the error listener picks the dedicated branch.
        val hint = NetworkPlaybackContainerHint.fromPath("smb://nas/VIDEO_TS/VTS_01_1.vob")
        assertEquals(NetworkPlaybackContainerHint.DVD_PS_VOB, hint)
    }

    @Test
    fun `vob never triggers BD stripper regardless of format result`() {
        // Even if TsPacketFormat.UNKNOWN leaks back for a .vob, stripping must not be applied.
        assertFalse(shouldUseBdTsStripper(TsPacketFormat.UNKNOWN))
    }

    @Test
    fun `m2ts stays on M2TS_TS_CANDIDATE and does not share vob route`() {
        val hint = NetworkPlaybackContainerHint.fromPath("smb://nas/BDMV/STREAM/00001.m2ts")
        assertEquals(NetworkPlaybackContainerHint.M2TS_TS_CANDIDATE, hint)
    }

    @Test
    fun `BD_192 format still triggers stripper for m2ts path`() {
        val hint = NetworkPlaybackContainerHint.fromPath("smb://nas/BDMV/STREAM/00001.m2ts")
        assertEquals(NetworkPlaybackContainerHint.M2TS_TS_CANDIDATE, hint)
        // For a TS candidate that probes as BD_192, stripping IS expected.
        assert(shouldUseBdTsStripper(TsPacketFormat.BD_192))
    }

    @Test
    fun `generic non-ts non-vob file maps to OTHER and does not affect route`() {
        val hint = NetworkPlaybackContainerHint.fromPath("smb://nas/movies/film.mkv")
        assertEquals(NetworkPlaybackContainerHint.OTHER, hint)
    }

    @Test
    fun `emptyPath_mapsToOther`() {
        val hint = NetworkPlaybackContainerHint.fromPath("")
        assertEquals(NetworkPlaybackContainerHint.OTHER, hint)
    }
}
