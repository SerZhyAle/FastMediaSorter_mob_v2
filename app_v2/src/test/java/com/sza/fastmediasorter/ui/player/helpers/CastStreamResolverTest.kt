package com.sza.fastmediasorter.ui.player.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class CastStreamResolverTest {

    private val resolver = CastStreamResolver()

    @Test
    fun `http hls url casts direct as hls`() {
        assertEquals(
            CastStreamDecision.Direct("application/x-mpegurl"),
            resolver.resolve("http://h/live.m3u8")
        )
    }

    @Test
    fun `https dash url casts direct as dash`() {
        assertEquals(
            CastStreamDecision.Direct("application/dash+xml"),
            resolver.resolve("https://h/live.mpd")
        )
    }

    @Test
    fun `https mp4 url casts direct as progressive mp4`() {
        assertEquals(
            CastStreamDecision.Direct("video/mp4"),
            resolver.resolve("http://h/movie.mp4")
        )
    }

    @Test
    fun `extensionless http url defaults to hls`() {
        assertEquals(
            CastStreamDecision.Direct("application/x-mpegurl"),
            resolver.resolve("https://h/live")
        )
    }

    @Test
    fun `query string does not break extension detection`() {
        assertEquals(
            CastStreamDecision.Direct("application/x-mpegurl"),
            resolver.resolve("https://h/path/live.m3u8?token=abc")
        )
    }

    @Test
    fun `rtsp url is unsupported`() {
        assertEquals(CastStreamDecision.UnsupportedProtocol, resolver.resolve("rtsp://h/live"))
    }

    @Test
    fun `local path is not a stream`() {
        assertEquals(CastStreamDecision.NotAStream, resolver.resolve("/storage/emulated/0/v.mp4"))
    }

    @Test
    fun `smb path is not a stream`() {
        assertEquals(CastStreamDecision.NotAStream, resolver.resolve("smb://server/share/v.mp4"))
    }

    @Test
    fun `cloud path is not a stream`() {
        assertEquals(CastStreamDecision.NotAStream, resolver.resolve("cloud://drive/v.mp4"))
    }
}
