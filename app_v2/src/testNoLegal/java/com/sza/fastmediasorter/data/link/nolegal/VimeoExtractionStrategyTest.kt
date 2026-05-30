package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * S0300 Phase 07: JVM unit tests for [VimeoExtractionStrategy] - host probing,
 * numeric video-id extraction across URL shapes, player-config parsing (progressive
 * highest-width preference, HLS fallback), and error mapping. OkHttp + direct mocked.
 *
 * Robolectric supplies a real org.json implementation (the stubbed android.jar one returns
 * null from every opt* call, which would make the JSON-parse branches unreachable).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VimeoExtractionStrategyTest {

    private lateinit var direct: DirectFileExtractionStrategy
    private val noProgress: (Long, Long?) -> Unit = { _, _ -> }

    @Before
    fun setUp() {
        direct = mockk()
    }

    private fun strategy(client: OkHttpTestSupport.MockedClient) =
        VimeoExtractionStrategy(client.client, direct)

    private fun anyClient() = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, ""))

    @Test
    fun probe_vimeoHost_applicable() = runTest {
        assertTrue(strategy(anyClient()).probe("https://vimeo.com/123456") is ProbeResult.Applicable)
    }

    @Test
    fun probe_playerSubdomain_applicable() = runTest {
        assertTrue(strategy(anyClient()).probe("https://player.vimeo.com/video/1") is ProbeResult.Applicable)
    }

    @Test
    fun probe_foreignHost_notApplicable() = runTest {
        assertEquals(ProbeResult.NotApplicable, strategy(anyClient()).probe("https://dailymotion.com/x"))
    }

    @Test
    fun open_noNumericId_notFound() = runTest {
        val result = strategy(anyClient()).open("https://vimeo.com/user/profile-name", noProgress)
        assertEquals("vimeo_no_id", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_configNonSuccess_notFound() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(403, "")))
        val result = strategy.open("https://vimeo.com/123456", noProgress)
        assertEquals("vimeo_config_failed", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_ioException_returnsError() = runTest {
        val io = IOException("net")
        val result = strategy(OkHttpTestSupport.clientThrowing(io)).open("https://vimeo.com/123456", noProgress)
        assertEquals(io, (result as OpenResult.Error).cause)
    }

    @Test
    fun open_progressivePicksHighestWidth_delegates() = runTest {
        val config = """
            {"request":{"files":{"progressive":[
              {"url":"https://cdn.v/low.mp4","width":640},
              {"url":"https://cdn.v/high.mp4","width":1920}
            ]}}}
        """.trimIndent()
        val client = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, config))
        val strategy = strategy(client)
        val urlSlot = slot<String>()
        coEvery { direct.open(capture(urlSlot), any(), any()) } returns OpenResult.NotFound("d")

        strategy.open("https://vimeo.com/123456", noProgress)

        assertEquals("https://cdn.v/high.mp4", urlSlot.captured)
    }

    @Test
    fun open_hlsFallbackWhenNoProgressive_returnsStreaming() = runTest {
        val config = """{"request":{"files":{"hls":{"url":"https://cdn.v/master.m3u8"}}}}"""
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, config)))

        val result = strategy.open("https://vimeo.com/777", noProgress)

        val streaming = result as OpenResult.Streaming
        assertEquals("https://cdn.v/master.m3u8", (streaming.manifest as StreamingManifest.Hls).manifestUrl)
        assertEquals("vimeo_777.mp4", streaming.tentativeFileName)
    }

    @Test
    fun open_noFilesObject_parseFailed() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, """{"request":{}}""")))
        val result = strategy.open("https://vimeo.com/888", noProgress)
        assertEquals("vimeo_parse_failed", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_channelUrlShape_extractsId() = runTest {
        val config = """{"request":{"files":{"hls":{"url":"https://cdn.v/m.m3u8"}}}}"""
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, config)))

        val result = strategy.open("https://vimeo.com/channels/staffpicks/555111", noProgress)

        assertEquals("vimeo_555111.mp4", (result as OpenResult.Streaming).tentativeFileName)
    }

    @Test
    fun open_configUrlTargetsPlayerConfig() = runTest {
        val client = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, """{"request":{}}"""))
        strategy(client).open("https://vimeo.com/424242", noProgress)
        assertEquals("https://player.vimeo.com/video/424242/config", client.lastRequest.url.toString())
    }

    @Test
    fun id_isVimeo() {
        assertEquals("vimeo", strategy(anyClient()).id)
    }
}
