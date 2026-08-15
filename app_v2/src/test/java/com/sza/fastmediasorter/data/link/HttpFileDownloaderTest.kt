package com.sza.fastmediasorter.data.link

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * Unit tests for [HttpFileDownloader]: a successful body is written and reported, a non-2xx status
 * and a stream that breaks mid-transfer both fail without leaving a partial file behind.
 *
 * The OkHttp [Call] is mocked rather than served by MockWebServer, which this module declares only
 * on the instrumentation classpath - the same approach [DirectFileExtractionStrategyTest] uses.
 */
class HttpFileDownloaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val client = mockk<OkHttpClient>()

    private fun downloader() = HttpFileDownloader(client)

    private fun respondWith(code: Int, body: ResponseBody) {
        val response = Response.Builder()
            .request(Request.Builder().url(URL).build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("msg")
            .body(body)
            .build()
        val call = mockk<Call>()
        every { call.execute() } returns response
        every { client.newCall(any()) } returns call
    }

    @Test
    fun `successful response writes the body and reports percent`() = runBlocking {
        respondWith(HTTP_OK, PAYLOAD.toResponseBody(OCTET_STREAM))
        val target = File(temp.root, "out.bin")
        val percents = mutableListOf<Int>()

        val ok = downloader().download(URL, target) { percents.add(it) }

        assertTrue(ok)
        assertEquals(PAYLOAD, target.readText())
        assertEquals(PERCENT_COMPLETE, percents.last())
    }

    @Test
    fun `non-2xx response fails and leaves no file`() = runBlocking {
        respondWith(HTTP_NOT_FOUND, "not found".toResponseBody(OCTET_STREAM))
        val target = File(temp.root, "missing.bin")

        val ok = downloader().download(URL, target, null)

        assertFalse(ok)
        assertFalse(target.exists())
    }

    @Test
    fun `body failing mid-stream leaves no partial file`() = runBlocking {
        respondWith(HTTP_OK, brokenBody())
        val target = File(temp.root, "partial.bin")

        val ok = downloader().download(URL, target, null)

        assertFalse(ok)
        assertFalse(target.exists())
    }

    @Test
    fun `malformed url fails without a call`() = runBlocking {
        val target = File(temp.root, "never.bin")

        val ok = downloader().download("cloud://drive/x", target, null)

        assertFalse(ok)
        assertFalse(target.exists())
    }

    // Yields one chunk and then breaks, which is the shape of a connection dropped mid-transfer:
    // the declared length is larger than what is ever delivered.
    private fun brokenBody(): ResponseBody {
        val source = object : Source {
            private var emitted = false

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (emitted) {
                    throw IOException("stream broke")
                }
                emitted = true
                sink.writeUtf8(PAYLOAD)
                return PAYLOAD.length.toLong()
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() = Unit
        }
        return source.buffer().asResponseBody(OCTET_STREAM, DECLARED_LENGTH)
    }

    private companion object {
        const val URL = "https://cdn.example/file.bin"
        const val PAYLOAD = "shareable-bytes"
        const val PERCENT_COMPLETE = 100
        const val DECLARED_LENGTH = 4096L
        const val HTTP_OK = 200
        const val HTTP_NOT_FOUND = 404
        val OCTET_STREAM = "application/octet-stream".toMediaType()
    }
}
