package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.DeliveryAssets
import com.sza.fastmediasorter.domain.delivery.PublishedStreamCatalog
import com.sza.fastmediasorter.domain.delivery.StreamCatalogPublicationSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * S4459: asks the mirror which `stream-catalog.zip` it serves now, with a `HEAD` request on the very URL
 * the import downloads.
 *
 * The mirror answers a `HEAD` through its redirect with the blob's `ETag` and `Last-Modified`, and the
 * `ETag` stays the same across requests while the signed redirect address changes - measured 2026-10-26.
 * That makes it a publication stamp that costs a few hundred bytes instead of the 7 MB archive.
 *
 * Not cached: the caller asks once per screen open, and a process-wide cache would hide a publish made
 * while the app process stays alive for days.
 */
@Singleton
class StreamCatalogPublicationClient @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
) : StreamCatalogPublicationSource {

    override suspend fun published(): PublishedStreamCatalog? = withContext(Dispatchers.IO) {
        try {
            // Its own short deadline: the shared download client carries callTimeout(0) so a large payload
            // is never cut off mid-stream, and a probe that inherited that would hold the open indefinitely.
            val client = httpClient.newBuilder()
                .callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(DeliveryAssets.STREAM_CATALOG_URL).head().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.i("Stream catalog probe: http %d - nothing to offer", response.code)
                    return@withContext null
                }
                val stamp = PublishedStreamCatalog.stampOf(
                    etag = response.header(ETAG),
                    lastModified = response.header(LAST_MODIFIED),
                ) ?: return@withContext null
                PublishedStreamCatalog(
                    stamp = stamp,
                    lastModifiedMillis = response.headers.getDate(LAST_MODIFIED)?.time,
                )
            }
        } catch (e: IOException) {
            Timber.i(e, "Stream catalog probe unreachable - nothing to offer")
            null
        } catch (e: CancellationException) {
            // The caller gave up; reporting it as "nothing to offer" would hide a torn-down probe behind a
            // legitimate-looking result (S1889).
            throw e
        } catch (e: IllegalStateException) {
            // OkHttp raises this for a call reused or closed out of order; still just "no answer".
            Timber.i(e, "Stream catalog probe call failed - nothing to offer")
            null
        }
    }

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 12L
        const val ETAG = "ETag"
        const val LAST_MODIFIED = "Last-Modified"
    }
}
