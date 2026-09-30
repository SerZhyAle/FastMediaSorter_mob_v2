package com.sza.fastmediasorter.data.networkmonitor

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * A body that breaks mid-read must fall through to the next echo service at once. The [Call.Factory] is faked
 * because MockWebServer is on the instrumentation classpath only; the fake calls `onResponse` synchronously,
 * which is exactly where OkHttp would swallow an escaping `IOException` and leave the lookup hanging.
 */
class ExternalIpDataSourceTest {

    private val factory = mockk<Call.Factory>()

    private fun serve(url: String, body: ResponseBody) {
        val call = mockk<Call>()
        every { call.cancel() } just runs
        every { call.enqueue(any()) } answers {
            firstArg<Callback>().onResponse(call, response(Request.Builder().url(url).build(), body))
        }
        every { factory.newCall(match { it.url.toString() == url }) } returns call
    }

    private fun response(request: Request, body: ResponseBody) = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(HTTP_OK)
        .message("OK")
        .body(body)
        .build()

    @Test
    fun `a body that breaks mid-read falls through to the next service`() = runBlocking {
        serve(BROKEN, BrokenSource().buffer().asResponseBody(TEXT, CONTENT_LENGTH))
        serve(HEALTHY, "$ADDRESS\n".toResponseBody(TEXT))

        val result = ExternalIpDataSource(factory, listOf(BROKEN, HEALTHY)).resolve()

        assertEquals(ExternalIpResult.Resolved(ADDRESS), result)
    }

    @Test
    fun `every body breaking yields Unavailable without throwing`() = runBlocking {
        serve(BROKEN, BrokenSource().buffer().asResponseBody(TEXT, CONTENT_LENGTH))

        val result = ExternalIpDataSource(factory, listOf(BROKEN)).resolve()

        assertEquals(ExternalIpResult.Unavailable, result)
    }

    private class BrokenSource : Source {
        override fun read(sink: Buffer, byteCount: Long): Long = throw IOException("connection reset mid-body")
        override fun timeout(): Timeout = Timeout.NONE
        override fun close() = Unit
    }

    private companion object {
        const val BROKEN = "https://broken.example/"
        const val HEALTHY = "https://healthy.example/"
        const val ADDRESS = "203.0.113.7"
        const val HTTP_OK = 200
        const val CONTENT_LENGTH = 64L
        val TEXT = "text/plain".toMediaType()
    }
}
