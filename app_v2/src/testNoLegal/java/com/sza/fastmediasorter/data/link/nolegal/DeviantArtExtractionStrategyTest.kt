package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
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
 * S0300 Phase 07: JVM unit tests for [DeviantArtExtractionStrategy] - host probing,
 * __INITIAL_STATE__ primary parsing (baseUri+prettyName and types[].full), oEmbed
 * fallback, and error mapping. OkHttp + direct downloader mocked; no real network.
 *
 * Robolectric supplies a real org.json implementation (the stubbed android.jar one returns
 * null from every opt* call, which would make the JSON-parse branches unreachable).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DeviantArtExtractionStrategyTest {

    private lateinit var direct: DirectFileExtractionStrategy
    private val noProgress: (Long, Long?) -> Unit = { _, _ -> }

    @Before
    fun setUp() {
        direct = mockk()
    }

    private fun strategy(client: OkHttpTestSupport.MockedClient) =
        DeviantArtExtractionStrategy(client.client, direct)

    private fun anyClient() = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, ""))

    @Test
    fun probe_deviantArtHost_applicable() = runTest {
        assertTrue(strategy(anyClient()).probe("https://www.deviantart.com/x/art/y") is ProbeResult.Applicable)
    }

    @Test
    fun probe_foreignHost_notApplicable() = runTest {
        assertEquals(ProbeResult.NotApplicable, strategy(anyClient()).probe("https://artstation.com/a"))
    }

    @Test
    fun open_pageFetchError_notFoundWithCode() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(403, "")))
        val result = strategy.open("https://www.deviantart.com/x/art/y", noProgress)
        assertEquals("deviantart_fetch_error_403", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_ioException_returnsError() = runTest {
        val io = IOException("net")
        val result = strategy(OkHttpTestSupport.clientThrowing(io)).open("https://www.deviantart.com/x/art/y", noProgress)
        assertEquals(io, (result as OpenResult.Error).cause)
    }

    @Test
    fun open_initialStateBaseUriPrettyName_delegatesToDirect() = runTest {
        val state = """{"deviation":{"deviationId":1,"media":{"baseUri":"https://images.deviantart.net/f/","prettyName":"art123"}}}"""
        val html = "<script>window.__INITIAL_STATE__ = $state ;</script>"
        val client = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, html))
        val strategy = strategy(client)
        val urlSlot = slot<String>()
        coEvery { direct.open(capture(urlSlot), any(), any()) } returns OpenResult.NotFound("d")

        strategy.open("https://www.deviantart.com/x/art/y", noProgress)

        assertEquals("https://images.deviantart.net/f/art123", urlSlot.captured)
    }

    @Test
    fun open_initialStateTypesFull_delegatesToDirect() = runTest {
        val state = """{"deviation":{"deviationId":2,"media":{"baseUri":"https://img.da/b","types":[{"t":"full","c":"/full/file.jpg"}]}}}"""
        val html = "window.__INITIAL_STATE__ = $state ;</script>"
        val client = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, html))
        val strategy = strategy(client)
        val urlSlot = slot<String>()
        coEvery { direct.open(capture(urlSlot), any(), any()) } returns OpenResult.NotFound("d")

        strategy.open("https://www.deviantart.com/x/art/y", noProgress)

        // baseUri trimmed of trailing '/' then template appended.
        assertEquals("https://img.da/b/full/file.jpg", urlSlot.captured)
    }

    @Test
    fun open_primaryFails_oEmbedFallback() = runTest {
        // First response: page HTML with no usable initial state. Second: oEmbed JSON.
        val pageHtml = OkHttpTestSupport.response(200, "<html>no state here</html>")
        val oEmbed = OkHttpTestSupport.response(200, """{"url":"https://img.da/preview.jpg"}""")
        val client = OkHttpTestSupport.clientReturningSequence(pageHtml, oEmbed)
        val strategy = strategy(client)
        val urlSlot = slot<String>()
        coEvery { direct.open(capture(urlSlot), any(), any()) } returns OpenResult.NotFound("d")

        strategy.open("https://www.deviantart.com/x/art/y", noProgress)

        assertEquals("https://img.da/preview.jpg", urlSlot.captured)
    }

    @Test
    fun open_primaryAndFallbackFail_parseFailed() = runTest {
        val pageHtml = OkHttpTestSupport.response(200, "<html>no state</html>")
        val oEmbed = OkHttpTestSupport.response(404, "")
        val client = OkHttpTestSupport.clientReturningSequence(pageHtml, oEmbed)
        val strategy = strategy(client)

        val result = strategy.open("https://www.deviantart.com/x/art/y", noProgress)

        assertEquals("deviantart_parse_failed", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun id_isDeviantArt() {
        assertEquals("deviantart", strategy(anyClient()).id)
    }
}
