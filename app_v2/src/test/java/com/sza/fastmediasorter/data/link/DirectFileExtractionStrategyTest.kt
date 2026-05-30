package com.sza.fastmediasorter.data.link

import com.sza.fastmediasorter.domain.usecase.link.BlockedReason
import com.sza.fastmediasorter.domain.usecase.link.OpenResult
import com.sza.fastmediasorter.domain.usecase.link.ProbeResult
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [DirectFileExtractionStrategy]: probe HEAD-success / ranged-GET fallback /
 * MIME-whitelist + path-extension gating, and open() block reasons (non-http, auth 401/403,
 * MIME-not-allowed) plus a successful stream with Content-Disposition filename derivation.
 * OkHttp Call is mocked - no real network.
 */
class DirectFileExtractionStrategyTest {

    private val client = mockk<OkHttpClient>()

    private fun strategy() = DirectFileExtractionStrategy(client)

    private fun response(
        url: String,
        code: Int,
        headers: Map<String, String> = emptyMap(),
        body: String = "",
        bodyMime: String = "application/octet-stream",
    ): Response {
        val builder = Response.Builder()
            .request(Request.Builder().url(url).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("msg")
            .body(body.toResponseBody(bodyMime.toMediaType()))
        headers.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    /** Queue distinct responses for sequential newCall().execute() invocations. */
    private fun enqueue(vararg responses: Response) {
        val calls = responses.map { resp ->
            mockk<Call>().also { every { it.execute() } returns resp }
        }
        every { client.newCall(any()) } returnsMany calls
    }

    // region probe

    @Test
    fun `probe returns NotApplicable for non-http url`() = runBlocking {
        val result = strategy().probe("ftp://host/file")
        assertEquals(ProbeResult.NotApplicable, result)
    }

    @Test
    fun `probe HEAD success with allowed mime is Applicable`() = runBlocking {
        enqueue(
            response(
                "https://cdn/file.bin",
                200,
                headers = mapOf("Content-Type" to "video/mp4", "Content-Length" to "12345")
            )
        )
        val result = strategy().probe("https://cdn/file.bin")
        assertTrue(result is ProbeResult.Applicable)
        assertEquals(12345L, (result as ProbeResult.Applicable).tentativeSizeBytes)
    }

    @Test
    fun `probe HEAD success non-media mime but media path extension is Applicable`() = runBlocking {
        enqueue(
            response("https://cdn/video.mp4", 200, headers = mapOf("Content-Type" to "application/octet-stream"))
        )
        val result = strategy().probe("https://cdn/video.mp4")
        assertTrue(result is ProbeResult.Applicable)
    }

    @Test
    fun `probe falls back to ranged GET when HEAD fails`() = runBlocking {
        enqueue(
            response("https://cdn/clip.mp4", 405), // HEAD rejected
            response(
                "https://cdn/clip.mp4",
                206,
                headers = mapOf("Content-Type" to "video/mp4", "Content-Range" to "bytes 0-0/9999")
            )
        )
        val result = strategy().probe("https://cdn/clip.mp4")
        assertTrue(result is ProbeResult.Applicable)
        assertEquals(9999L, (result as ProbeResult.Applicable).tentativeSizeBytes)
    }

    @Test
    fun `probe network error is TransientError`() = runBlocking {
        val call = mockk<Call>()
        every { call.execute() } throws java.io.IOException("offline")
        every { client.newCall(any()) } returns call

        val result = strategy().probe("https://cdn/x.mp4")
        assertTrue(result is ProbeResult.TransientError)
    }

    // endregion

    // region open

    @Test
    fun `open blocks non-http scheme`() = runBlocking {
        val result = strategy().open("mailto:user@x.com") { _, _ -> }
        assertTrue(result is OpenResult.Blocked)
        assertEquals(BlockedReason.NonHttpScheme, (result as OpenResult.Blocked).reason)
    }

    @Test
    fun `open returns AuthRequired on 401`() = runBlocking {
        enqueue(response("https://cdn/f.mp4", 401))
        val result = strategy().open("https://cdn/f.mp4") { _, _ -> }
        assertEquals(BlockedReason.AuthRequired, (result as OpenResult.Blocked).reason)
    }

    @Test
    fun `open blocks disallowed mime`() = runBlocking {
        enqueue(response("https://cdn/f.exe", 200, headers = mapOf("Content-Type" to "application/x-msdownload")))
        val result = strategy().open("https://cdn/f.exe") { _, _ -> }
        assertEquals(BlockedReason.MimeNotAllowed, (result as OpenResult.Blocked).reason)
    }

    @Test
    fun `open streams allowed file and derives filename from Content-Disposition`() = runBlocking {
        enqueue(
            response(
                "https://cdn/dl",
                200,
                headers = mapOf(
                    "Content-Type" to "video/mp4",
                    "Content-Disposition" to "attachment; filename=\"My Movie.mp4\""
                ),
                body = "binarydata",
                bodyMime = "video/mp4"
            )
        )
        val result = strategy().open("https://cdn/dl") { _, _ -> }
        assertTrue(result is OpenResult.Stream)
        val stream = result as OpenResult.Stream
        assertEquals("video/mp4", stream.mime)
        // spaces sanitised to underscores
        assertEquals("My_Movie.mp4", stream.fileName)
        stream.close()
    }

    @Test
    fun `open derives filename from url path when no disposition`() = runBlocking {
        enqueue(
            response(
                "https://cdn/clip.mp4",
                200,
                headers = mapOf("Content-Type" to "video/mp4"),
                body = "data",
                bodyMime = "video/mp4"
            )
        )
        val result = strategy().open("https://cdn/clip.mp4") { _, _ -> }
        assertEquals("clip.mp4", (result as OpenResult.Stream).fileName)
        result.close()
    }

    // endregion
}
