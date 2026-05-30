package com.sza.fastmediasorter.data.link.nolegal

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request as OkHttpRequest
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.downloader.Request as NpRequest

/**
 * S0300 Phase 07: JVM unit tests for [NewPipeOkHttpDownloader.execute] - the NewPipe
 * Request → OkHttp Request mapping (method, headers) and the OkHttp Response → NewPipe
 * Response mapping (code, message, headers, body, latestUrl). OkHttp is mocked end to end.
 */
class NewPipeOkHttpDownloaderTest {

    private fun npRequest(
        method: String,
        url: String,
        headers: Map<String, List<String>> = emptyMap(),
        data: ByteArray? = null,
    ): NpRequest = mockk {
        every { httpMethod() } returns method
        every { url() } returns url
        every { headers() } returns headers
        every { dataToSend() } returns data
    }

    private fun okResponse(
        code: Int,
        message: String,
        url: String,
        body: String,
        headers: Map<String, String> = emptyMap(),
    ): OkHttpResponse {
        val request = OkHttpRequest.Builder().url(url).build()
        val builder = OkHttpResponse.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(message)
            .body(body.toResponseBody(null))
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        return builder.build()
    }

    private fun clientReturning(response: OkHttpResponse): Pair<OkHttpClient, MutableList<OkHttpRequest>> {
        val client = mockk<OkHttpClient>()
        val call = mockk<Call>()
        val captured = mutableListOf<OkHttpRequest>()
        every { client.newCall(capture(captured)) } returns call
        every { call.execute() } returns response
        return client to captured
    }

    @Test
    fun execute_getRequest_mapsResponseFields() {
        val response = okResponse(200, "OK", "https://np.test/watch", "payload-body")
        val (client, _) = clientReturning(response)
        val downloader = NewPipeOkHttpDownloader(client)

        val result = downloader.execute(npRequest("GET", "https://np.test/watch"))

        assertEquals(200, result.responseCode())
        assertEquals("OK", result.responseMessage())
        assertEquals("payload-body", result.responseBody())
        assertEquals("https://np.test/watch", result.latestUrl())
    }

    @Test
    fun execute_lowercaseMethod_isUppercasedToGet() {
        val response = okResponse(200, "OK", "https://np.test/", "x")
        val (client, captured) = clientReturning(response)
        val downloader = NewPipeOkHttpDownloader(client)

        downloader.execute(npRequest("get", "https://np.test/"))

        assertEquals("GET", captured.single().method)
    }

    @Test
    fun execute_headRequest_usesHeadMethod() {
        val response = okResponse(200, "OK", "https://np.test/", "")
        val (client, captured) = clientReturning(response)
        val downloader = NewPipeOkHttpDownloader(client)

        downloader.execute(npRequest("HEAD", "https://np.test/"))

        assertEquals("HEAD", captured.single().method)
    }

    @Test
    fun execute_forwardsRequestHeaders() {
        val response = okResponse(200, "OK", "https://np.test/", "")
        val (client, captured) = clientReturning(response)
        val downloader = NewPipeOkHttpDownloader(client)

        downloader.execute(
            npRequest(
                "GET",
                "https://np.test/",
                headers = mapOf("X-Test" to listOf("a", "b"), "User-Agent" to listOf("np-ua")),
            ),
        )

        val sent = captured.single()
        assertEquals(listOf("a", "b"), sent.headers.values("X-Test"))
        assertEquals("np-ua", sent.header("User-Agent"))
    }

    @Test
    fun execute_responseHeadersAreMapped() {
        val response = okResponse(
            200, "OK", "https://np.test/", "body",
            headers = mapOf("Content-Type" to "application/json"),
        )
        val (client, _) = clientReturning(response)
        val downloader = NewPipeOkHttpDownloader(client)

        val result = downloader.execute(npRequest("GET", "https://np.test/"))

        assertEquals(listOf("application/json"), result.responseHeaders()["Content-Type"])
    }

    @Test
    fun execute_postRequest_sendsBodyWithMethod() {
        val response = okResponse(200, "OK", "https://np.test/api", "ok")
        val (client, captured) = clientReturning(response)
        val downloader = NewPipeOkHttpDownloader(client)

        downloader.execute(
            npRequest(
                "POST",
                "https://np.test/api",
                headers = mapOf("Content-Type" to listOf("application/json")),
                data = """{"q":1}""".toByteArray(),
            ),
        )

        val sent = captured.single()
        assertEquals("POST", sent.method)
        assertEquals(7L, sent.body?.contentLength())
    }

    @Test
    fun execute_postWithNullData_sendsEmptyBody() {
        val response = okResponse(200, "OK", "https://np.test/api", "ok")
        val (client, captured) = clientReturning(response)
        val downloader = NewPipeOkHttpDownloader(client)

        downloader.execute(npRequest("PUT", "https://np.test/api", data = null))

        val sent = captured.single()
        assertEquals("PUT", sent.method)
        assertEquals(0L, sent.body?.contentLength())
    }

    @Test
    fun execute_emptyResponseBody_mapsToEmptyString() {
        // OkHttp response with no body section still maps to empty string, not null.
        val request = OkHttpRequest.Builder().url("https://np.test/").build()
        val response = OkHttpResponse.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(204)
            .message("No Content")
            .body("".toResponseBody(null))
            .build()
        val (client, _) = clientReturning(response)
        val downloader = NewPipeOkHttpDownloader(client)

        val result = downloader.execute(npRequest("GET", "https://np.test/"))

        assertEquals("", result.responseBody())
        assertEquals(204, result.responseCode())
    }

    @Test
    fun execute_latestUrlReflectsResolvedRequestUrl() {
        // The mapped latestUrl comes from the OkHttp response's request url (post-redirect).
        val response = okResponse(200, "OK", "https://np.test/final", "x")
        val (client, _) = clientReturning(response)
        val downloader = NewPipeOkHttpDownloader(client)

        val result = downloader.execute(npRequest("GET", "https://np.test/start"))

        assertTrue(result.latestUrl().endsWith("/final"))
    }
}
