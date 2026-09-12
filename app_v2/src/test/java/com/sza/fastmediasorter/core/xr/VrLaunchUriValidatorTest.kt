package com.sza.fastmediasorter.core.xr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1218: the table that tells a live channel apart from a local file. Strategic §7 names surviving
 * file assumptions as the risk whose consequence is a blank quad, and this decision is the single
 * place the two kinds are separated.
 */
class VrLaunchUriValidatorTest {

    @Test
    fun `local file uri is accepted for a local source`() {
        assertNull(validateVideoLaunchUri("file:///sdcard/Movies/a.mp4", VrLaunchSourceKind.LOCAL_FILE))
    }

    @Test
    fun `network address is refused for a local source`() {
        assertEquals(
            VrLaunchUnavailableReason.InvalidUri,
            validateVideoLaunchUri("https://cdn.example/live.m3u8", VrLaunchSourceKind.LOCAL_FILE),
        )
    }

    @Test
    fun `hls address is accepted for a network source`() {
        assertNull(
            validateVideoLaunchUri("https://cdn.example/live.m3u8", VrLaunchSourceKind.NETWORK_STREAM)
        )
    }

    @Test
    fun `dash address over plain http is accepted for a network source`() {
        assertNull(
            validateVideoLaunchUri("http://cdn.example/manifest.mpd", VrLaunchSourceKind.NETWORK_STREAM)
        )
    }

    @Test
    fun `rtsp address is not yet supported rather than invalid`() {
        assertEquals(
            VrLaunchUnavailableReason.NotYetSupported,
            validateVideoLaunchUri("rtsp://cam.example/stream", VrLaunchSourceKind.NETWORK_STREAM),
        )
    }

    @Test
    fun `file uri is refused for a network source`() {
        assertEquals(
            VrLaunchUnavailableReason.InvalidUri,
            validateVideoLaunchUri("file:///sdcard/Movies/a.mp4", VrLaunchSourceKind.NETWORK_STREAM),
        )
    }
}
