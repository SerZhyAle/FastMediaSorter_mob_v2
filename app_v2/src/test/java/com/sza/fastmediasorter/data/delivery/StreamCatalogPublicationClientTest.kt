package com.sza.fastmediasorter.data.delivery

import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * S4459: the probe's failure paths matter more than its happy path - each one must read as "nothing to
 * offer", because a probe that throws or that guesses a stamp would either break the Streams screen or
 * bring back the offer-for-nothing the ticket removes. No network: a canned answer is injected through an
 * interceptor, so the fixed production URL is never contacted.
 */
class StreamCatalogPublicationClientTest {

    private fun clientAnswering(code: Int, vararg headers: Pair<String, String>): StreamCatalogPublicationClient {
        val interceptor = Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
                .body("".toResponseBody())
                .build()
        }
        return StreamCatalogPublicationClient(OkHttpClient.Builder().addInterceptor(interceptor).build())
    }

    @Test
    fun `an answer with both headers yields the entity tag and the modification time`() = runTest {
        val published = clientAnswering(
            200,
            "ETag" to "\"0x8DF2EB1C1AD5242\"",
            "Last-Modified" to "Tue, 20 Oct 2026 13:55:10 GMT",
        ).published()

        assertNotNull(published)
        assertEquals("\"0x8DF2EB1C1AD5242\"", published!!.stamp)
        assertEquals(1_792_504_510_000L, published.lastModifiedMillis)
    }

    @Test
    fun `an answer with only a modification time still gives a stamp`() = runTest {
        val published = clientAnswering(200, "Last-Modified" to "Tue, 20 Oct 2026 13:55:10 GMT").published()

        assertEquals("lm:Tue, 20 Oct 2026 13:55:10 GMT", published?.stamp)
    }

    @Test
    fun `an answer with neither header gives no stamp`() = runTest {
        assertNull(clientAnswering(200).published())
    }

    @Test
    fun `an http error gives no stamp`() = runTest {
        assertNull(clientAnswering(404, "ETag" to "\"x\"").published())
    }

    @Test
    fun `a dropped connection gives no stamp instead of throwing`() = runTest {
        val failing = Interceptor { throw IOException("unreachable") }
        val client = StreamCatalogPublicationClient(OkHttpClient.Builder().addInterceptor(failing).build())

        assertNull(client.published())
    }

    @Test
    fun `the probe is a head request`() = runTest {
        var method: String? = null
        val recording = Interceptor { chain ->
            method = chain.request().method
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("test")
                .addHeader("ETag", "\"x\"")
                .body("".toResponseBody())
                .build()
        }
        StreamCatalogPublicationClient(OkHttpClient.Builder().addInterceptor(recording).build()).published()

        assertTrue(method == "HEAD")
    }
}
