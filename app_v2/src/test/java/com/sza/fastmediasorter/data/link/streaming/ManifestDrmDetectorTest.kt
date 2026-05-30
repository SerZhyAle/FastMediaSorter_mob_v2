package com.sza.fastmediasorter.data.link.streaming

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ManifestDrmDetector]: HLS EXT-X-KEY (non-NONE) and DASH ContentProtection
 * detection, the METHOD=NONE negative case, plain manifests, non-200 responses, and network
 * failure - all best-effort false. OkHttp Call is mocked; no real network.
 */
class ManifestDrmDetectorTest {

    private fun detectorReturning(body: String, code: Int = 200): ManifestDrmDetector {
        val client = mockk<OkHttpClient>()
        val call = mockk<Call>()
        val request = Request.Builder().url("https://cdn/m.m3u8").build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "ERR")
            .body(body.toResponseBody("application/octet-stream".toMediaType()))
            .build()
        every { client.newCall(any()) } returns call
        every { call.execute() } returns response
        return ManifestDrmDetector(client)
    }

    @Test
    fun `detects HLS EXT-X-KEY method`() = runBlocking {
        val m3u8 = "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"key\"\n#EXTINF:6,\nseg0.ts\n"
        assertTrue(detectorReturning(m3u8).isDrmProtected("https://cdn/m.m3u8"))
    }

    @Test
    fun `METHOD NONE is not DRM`() = runBlocking {
        val m3u8 = "#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:6,\nseg0.ts\n"
        assertFalse(detectorReturning(m3u8).isDrmProtected("https://cdn/m.m3u8"))
    }

    @Test
    fun `detects DASH ContentProtection`() = runBlocking {
        val mpd = "<MPD><Period><AdaptationSet><ContentProtection schemeIdUri=\"x\"/></AdaptationSet></Period></MPD>"
        assertTrue(detectorReturning(mpd).isDrmProtected("https://cdn/m.mpd"))
    }

    @Test
    fun `plain manifest is not DRM`() = runBlocking {
        val m3u8 = "#EXTM3U\n#EXTINF:6,\nseg0.ts\n"
        assertFalse(detectorReturning(m3u8).isDrmProtected("https://cdn/m.m3u8"))
    }

    @Test
    fun `non-200 response returns false`() = runBlocking {
        assertFalse(detectorReturning("whatever", code = 404).isDrmProtected("https://cdn/m.m3u8"))
    }

    @Test
    fun `network failure returns false`() = runBlocking {
        val client = mockk<OkHttpClient>()
        val call = mockk<Call>()
        every { client.newCall(any()) } returns call
        every { call.execute() } throws java.io.IOException("offline")

        assertFalse(ManifestDrmDetector(client).isDrmProtected("https://cdn/m.m3u8"))
    }
}
