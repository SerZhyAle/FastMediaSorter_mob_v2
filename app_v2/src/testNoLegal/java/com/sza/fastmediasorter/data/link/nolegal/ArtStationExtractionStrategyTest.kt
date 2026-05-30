package com.sza.fastmediasorter.data.link.nolegal

import com.sza.fastmediasorter.data.link.DirectFileExtractionStrategy
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

/**
 * S0300 Phase 07: JVM unit tests for [ArtStationExtractionStrategy] - host probing,
 * projects-JSON parsing (video-clip preference over image fallback), error mapping,
 * and delegation to the direct downloader. OkHttp is mocked; no real network.
 *
 * Robolectric supplies a real org.json implementation (the stubbed android.jar one returns
 * null from every opt* call, which would make the JSON-parse branches unreachable).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ArtStationExtractionStrategyTest {

    private lateinit var direct: DirectFileExtractionStrategy
    private val noProgress: (Long, Long?) -> Unit = { _, _ -> }

    @Before
    fun setUp() {
        direct = mockk()
    }

    private fun strategy(client: OkHttpTestSupport.MockedClient) =
        ArtStationExtractionStrategy(client.client, direct)

    @Test
    fun probe_artStationHost_applicable() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, "{}")))
        val result = strategy.probe("https://www.artstation.com/artwork/abc")
        assertTrue(result is ProbeResult.Applicable)
    }

    @Test
    fun probe_subdomainHost_applicable() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, "{}")))
        val result = strategy.probe("https://cdn.artstation.com/p/x")
        assertTrue(result is ProbeResult.Applicable)
    }

    @Test
    fun probe_foreignHost_notApplicable() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, "{}")))
        assertEquals(ProbeResult.NotApplicable, strategy.probe("https://vimeo.com/123"))
    }

    @Test
    fun probe_invalidUrl_notApplicable() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, "{}")))
        assertEquals(ProbeResult.NotApplicable, strategy.probe("not a url"))
    }

    @Test
    fun open_invalidUrl_notFound() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, "{}")))
        val result = strategy.open("not a url", noProgress)
        assertEquals("artstation_invalid_url", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_apiNonSuccess_notFoundWithCode() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(404, "")))
        val result = strategy.open("https://www.artstation.com/artwork/slug", noProgress)
        assertEquals("artstation_api_error_404", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_ioException_returnsError() = runTest {
        val io = IOException("boom")
        val strategy = strategy(OkHttpTestSupport.clientThrowing(io))
        val result = strategy.open("https://www.artstation.com/artwork/slug", noProgress)
        assertEquals(io, (result as OpenResult.Error).cause)
    }

    @Test
    fun open_emptyAssets_parseFailed() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, """{"assets":[]}""")))
        val result = strategy.open("https://www.artstation.com/artwork/slug", noProgress)
        assertEquals("artstation_parse_failed", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_videoClipPreferredOverImage() = runTest {
        val json = """
            {"assets":[
              {"asset_type":"image","image_url":"https://cdn.artstation.com/img.jpg"},
              {"asset_type":"video_clip","video_clip_url":"https://cdn.artstation.com/clip.mp4"}
            ]}
        """.trimIndent()
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, json)))
        val delegated = OpenResult.NotFound("delegated")
        val urlSlot = slot<String>()
        coEvery { direct.open(capture(urlSlot), any(), any()) } returns delegated

        val result = strategy.open("https://www.artstation.com/artwork/slug", noProgress)

        assertEquals(delegated, result)
        assertEquals("https://cdn.artstation.com/clip.mp4", urlSlot.captured)
        coVerify {
            direct.open(
                "https://cdn.artstation.com/clip.mp4",
                any(),
                match { it["Referer"] == "https://www.artstation.com/" },
            )
        }
    }

    @Test
    fun open_imageFallbackWhenNoVideo() = runTest {
        val json = """{"assets":[{"asset_type":"image","image_url":"https://cdn.artstation.com/pic.png"}]}"""
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, json)))
        val urlSlot = slot<String>()
        coEvery { direct.open(capture(urlSlot), any(), any()) } returns OpenResult.NotFound("x")

        strategy.open("https://www.artstation.com/artwork/slug", noProgress)

        assertEquals("https://cdn.artstation.com/pic.png", urlSlot.captured)
    }

    @Test
    fun open_malformedJson_parseFailed() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, "not json")))
        val result = strategy.open("https://www.artstation.com/artwork/slug", noProgress)
        assertEquals("artstation_parse_failed", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun open_requestTargetsProjectsApi() = runTest {
        val client = OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, """{"assets":[]}"""))
        val strategy = strategy(client)
        strategy.open("https://www.artstation.com/artwork/my-slug", noProgress)
        assertEquals("https://www.artstation.com/projects/my-slug.json", client.lastRequest.url.toString())
    }

    @Test
    fun open_noSlug_notFound() = runTest {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, "{}")))
        val result = strategy.open("https://www.artstation.com/", noProgress)
        assertEquals("artstation_no_slug", (result as OpenResult.NotFound).reason)
    }

    @Test
    fun id_isArtstation() {
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, "{}")))
        assertEquals("artstation", strategy.id)
    }

    @Test
    fun open_emptyBody_notFound() = runTest {
        // 200 with body that JSONObject can parse to no assets is parse_failed; verify the
        // distinct empty-body branch by returning a blank body the server marked successful.
        val strategy = strategy(OkHttpTestSupport.clientReturning(OkHttpTestSupport.response(200, "")))
        val result = strategy.open("https://www.artstation.com/artwork/slug", noProgress)
        // Empty string body is non-null so it reaches parseMediaUrl → JSONObject("") throws → parse_failed.
        assertEquals("artstation_parse_failed", (result as OpenResult.NotFound).reason)
        assertNull((result as? OpenResult.Error)?.cause)
    }
}
