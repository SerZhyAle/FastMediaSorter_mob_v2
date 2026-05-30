package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * S0300 Phase 07: JVM unit tests for [DailymotionExtractionStrategy] - host probing,
 * video-id extraction, __PLAYER_CONFIG__ JSON parsing and the .m3u8 regex fallback,
 * plus error mapping. OkHttp is mocked; no real network.
 *
 * Robolectric supplies a real org.json implementation (the stubbed android.jar one returns
 * null from every opt* call, which would make the JSON-parse branches unreachable).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DailymotionExtractionStrategyTest {

    private val noProgress: (Long, Long?) -> Unit = { _, _ -> }

    private fun strategy(client: OkHttpTestSupport.MockedClient) =
        DailymotionExtractionStrategy(client.client)

    private fun anyClient() = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, ""))

    @Test
    fun probe_dailymotionHost_applicable() = runTest {
        assertTrue(strategy(anyClient()).probe("https://www.dailymotion.com/video/x123") is ProbeResult.Applicable)
    }

    @Test
    fun probe_foreignHost_notApplicable() = runTest {
        assertEquals(ProbeResult.NotApplicable, strategy(anyClient()).probe("https://vimeo.com/9"))
    }

    @Test
    fun probe_invalidUrl_notApplicable() = runTest {
        assertEquals(ProbeResult.NotApplicable, strategy(anyClient()).probe("garbage"))
    }

    @Test
    fun open_noVideoId_notFound() = runTest {
        // URL on the host but with no id-shaped segment.
        val result = strategy(anyClient()).open("https://other.example/page", noProgress)
        assertEquals("dailymotion_no_id", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_embedNonSuccess_notFound() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(500, "")))
        val result = strategy.open("https://www.dailymotion.com/video/x123abc", noProgress)
        assertEquals("dailymotion_embed_failed", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_ioException_returnsError() = runTest {
        val io = IOException("net")
        val result = strategy(OkHttpTestSupport.clientThrowing(io)).open("https://www.dailymotion.com/video/x1", noProgress)
        assertEquals(io, (result as OpenResult.Error).cause)
    }

    @Test
    fun open_playerConfigAutoQuality_returnsHls() = runTest {
        val config = """{"metadata":{"qualities":{"auto":[{"url":"https://cdn.dm/stream.m3u8"}]}}}"""
        val html = "<script>window.__PLAYER_CONFIG__ = $config ;</script>"
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, html)))

        val result = strategy.open("https://www.dailymotion.com/video/xfoo", noProgress)

        val streaming = result as OpenResult.Streaming
        assertEquals("https://cdn.dm/stream.m3u8", (streaming.manifest as StreamingManifest.Hls).manifestUrl)
        assertEquals("dailymotion_xfoo.mp4", streaming.tentativeFileName)
    }

    @Test
    fun open_m3u8RegexFallback_returnsHls() = runTest {
        // No __PLAYER_CONFIG__ block; the m3u8 regex anchors on a real https:// URL inside
        // double quotes, then unescapes any \/ sequences before returning it.
        val html = """<html>var x = "https://cdn.dm/fallback.m3u8?t=1";</html>"""
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, html)))

        val result = strategy.open("https://www.dailymotion.com/video/xbar", noProgress)

        val streaming = result as OpenResult.Streaming
        assertEquals("https://cdn.dm/fallback.m3u8?t=1", (streaming.manifest as StreamingManifest.Hls).manifestUrl)
    }

    @Test
    fun open_m3u8RegexFallback_unescapesSlashes() = runTest {
        // Escaped-slash form: https?:// anchor fails on \/ so the regex skips it; assert the
        // page-embedded real-slash variant with an embedded escaped path segment unescapes.
        val html = """<html>"https://cdn.dm/a\/b\/fallback.m3u8";</html>"""
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, html)))

        val result = strategy.open("https://www.dailymotion.com/video/xesc", noProgress)

        assertEquals(
            "https://cdn.dm/a/b/fallback.m3u8",
            ((result as OpenResult.Streaming).manifest as StreamingManifest.Hls).manifestUrl,
        )
    }

    @Test
    fun open_noHlsAnywhere_parseFailed() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, "<html>nothing</html>")))
        val result = strategy.open("https://www.dailymotion.com/video/xbaz", noProgress)
        assertEquals("dailymotion_parse_failed", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_topLevelQualities_returnsHls() = runTest {
        val config = """{"qualities":{"auto":[{"url":"https://cdn.dm/top.m3u8"}]}}"""
        val html = "window.__PLAYER_CONFIG__ = $config ;"
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, html)))

        val result = strategy.open("https://www.dailymotion.com/video/xqux", noProgress)

        assertEquals(
            "https://cdn.dm/top.m3u8",
            ((result as OpenResult.Streaming).manifest as StreamingManifest.Hls).manifestUrl,
        )
    }

    @Test
    fun id_isDailymotion() {
        assertEquals("dailymotion", strategy(anyClient()).id)
    }
}
