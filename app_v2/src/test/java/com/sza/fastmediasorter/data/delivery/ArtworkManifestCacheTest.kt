package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * S4616: the manifest is cached for the process, so a republish made after the first read is invisible
 * until the cache is dropped. These tests pin the sequence - fetch, republish, refresh - and the two
 * things the drop must not break: the per-repaint path stays one request, and a response that was in
 * flight while the refresh happened never becomes the cached answer.
 */
// Robolectric supplies a real org.json - see ArtworkManifestParseTest.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // Robolectric maxSdkVersion=34; targetSdkVersion=36 needs an explicit pin.
class ArtworkManifestCacheTest {

    private var served: Served = Served.Body(manifest(stamp = "aaa", size = FIRST_SIZE))
    private var requests = 0

    /** Runs inside the request, after the response body was chosen - the "refresh lands mid-fetch" seam. */
    private var duringRequest: (() -> Unit)? = null

    private val client = ArtworkManifestClient(
        OkHttpClient.Builder().addInterceptor(Interceptor(::serve)).build(),
    )

    @Test
    fun `a republish stays invisible until the cache is dropped`() = runTest {
        assertEquals("aaa", client.stampOf(DeliverableSet.STREAM_LOGO_ATLAS))

        served = Served.Body(manifest(stamp = "bbb", size = SECOND_SIZE))

        assertEquals("aaa", client.stampOf(DeliverableSet.STREAM_LOGO_ATLAS))
        assertEquals(FIRST_SIZE, client.sizeOf(DeliverableSet.STREAM_LOGO_ATLAS))
        assertEquals(1, requests)

        client.invalidate()

        assertEquals("bbb", client.stampOf(DeliverableSet.STREAM_LOGO_ATLAS))
        assertEquals(SECOND_SIZE, client.sizeOf(DeliverableSet.STREAM_LOGO_ATLAS))
        assertEquals(2, requests)
    }

    @Test
    fun `reads between refreshes cost one request`() = runTest {
        repeat(REPAINTS) {
            client.stampOf(DeliverableSet.STREAM_LOGO_ATLAS)
            client.sizeOf(DeliverableSet.CHANNEL_PREVIEW_ATLAS)
        }

        assertEquals(1, requests)
    }

    @Test
    fun `dropping the cache twice in a row costs one refetch`() = runTest {
        client.stampOf(DeliverableSet.STREAM_LOGO_ATLAS)

        client.invalidate()
        client.invalidate()
        client.stampOf(DeliverableSet.STREAM_LOGO_ATLAS)
        client.sizeOf(DeliverableSet.STREAM_LOGO_ATLAS)

        assertEquals(2, requests)
    }

    @Test
    fun `a failed fetch is not cached and the next read tries again`() = runTest {
        served = Served.Failure(SERVICE_UNAVAILABLE)

        assertNull(client.stampOf(DeliverableSet.STREAM_LOGO_ATLAS))

        served = Served.Body(manifest(stamp = "ccc", size = FIRST_SIZE))

        assertEquals("ccc", client.stampOf(DeliverableSet.STREAM_LOGO_ATLAS))
        assertEquals(2, requests)
    }

    @Test
    fun `a response in flight during the refresh is not kept as the answer`() = runTest {
        duringRequest = {
            duringRequest = null
            client.invalidate()
            served = Served.Body(manifest(stamp = "bbb", size = SECOND_SIZE))
        }

        client.stampOf(DeliverableSet.STREAM_LOGO_ATLAS)

        assertEquals("bbb", client.stampOf(DeliverableSet.STREAM_LOGO_ATLAS))
        assertEquals(2, requests)
    }

    @Test
    fun `a set that is not an artwork payload never reaches the network`() = runTest {
        assertNull(client.stampOf(DeliverableSet.TRANSLATION))

        assertEquals(0, requests)
    }

    private fun serve(chain: Interceptor.Chain): Response {
        requests++
        val answer = served
        duringRequest?.invoke()
        val builder = Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
        return when (answer) {
            is Served.Body -> builder.code(HTTP_OK).message("OK")
                .body(answer.json.toResponseBody(JSON)).build()
            is Served.Failure -> builder.code(answer.code).message("failure")
                .body("".toResponseBody(JSON)).build()
        }
    }

    private sealed interface Served {
        class Body(val json: String) : Served
        class Failure(val code: Int) : Served
    }

    private companion object {
        val JSON = "application/json".toMediaType()
        const val FIRST_SIZE = 10L
        const val SECOND_SIZE = 20L
        const val REPAINTS = 5
        const val HTTP_OK = 200
        const val SERVICE_UNAVAILABLE = 503

        fun manifest(stamp: String, size: Long): String =
            """
            {
              "schemaVersion": 1,
              "generatedAt": "2026-11-14T00:00:00Z",
              "sets": {
                "channelPreview": { "stamp": "$stamp", "files": [ { "name": "p.zip", "size": $size } ] },
                "streamLogo": { "stamp": "$stamp", "files": [ { "name": "l.zip", "size": $size } ] }
              }
            }
            """.trimIndent()
    }
}
