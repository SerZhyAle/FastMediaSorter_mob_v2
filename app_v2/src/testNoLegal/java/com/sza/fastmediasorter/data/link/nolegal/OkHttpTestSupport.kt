package com.sza.fastmediasorter.data.link.nolegal

import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * S0300 Phase 07: shared OkHttp stubbing helpers for noLegal extraction-strategy tests.
 *
 * Builds real [Response] objects (avoids fighting OkHttp's final fields) and wires a mocked
 * [OkHttpClient] so a captured [Request] resolves to a queued response. No real network.
 */
internal object OkHttpTestSupport {

    /** Builds a genuine [Response] for an arbitrary request URL. */
    fun response(code: Int, body: String, url: String = "https://example.test/"): Response {
        val request = Request.Builder().url(url).build()
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body.toResponseBody(null))
            .build()
    }

    /**
     * Mocked [OkHttpClient] whose every `newCall(..).execute()` returns [response].
     * The captured [Request] is exposed via [requestSlot] for header/url assertions.
     */
    fun clientReturning(response: Response): MockedClient {
        val client = mockk<OkHttpClient>()
        val call = mockk<Call>()
        val requests = mutableListOf<Request>()
        every { client.newCall(capture(requests)) } returns call
        every { call.execute() } returns response
        return MockedClient(client, requests)
    }

    /** Mocked client that throws [error] from `execute()` to drive IOException branches. */
    fun clientThrowing(error: Throwable): MockedClient {
        val client = mockk<OkHttpClient>()
        val call = mockk<Call>()
        val requests = mutableListOf<Request>()
        every { client.newCall(capture(requests)) } returns call
        every { call.execute() } throws error
        return MockedClient(client, requests)
    }

    /** Mocked client that returns queued responses in submission order (one per `newCall`). */
    fun clientReturningSequence(vararg responses: Response): MockedClient {
        val client = mockk<OkHttpClient>()
        val requests = mutableListOf<Request>()
        var index = 0
        every { client.newCall(capture(requests)) } answers {
            val call = mockk<Call>()
            val current = responses[index++]
            every { call.execute() } returns current
            call
        }
        return MockedClient(client, requests)
    }

    class MockedClient(val client: OkHttpClient, val requests: List<Request>) {
        val lastRequest: Request get() = requests.last()
        val firstRequest: Request get() = requests.first()
    }
}
