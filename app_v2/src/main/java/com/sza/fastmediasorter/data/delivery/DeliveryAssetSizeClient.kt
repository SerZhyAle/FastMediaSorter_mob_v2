package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.DeliveryAssetSizeSource
import com.sza.fastmediasorter.domain.delivery.DeliveryAssets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * S2652: asks the mirror how large `stream-catalog.zip` currently is, with a HEAD request.
 *
 * The archive is rebuilt and re-uploaded by the publisher with no build involved, so a size compiled
 * into the app describes whichever publish happened to be current when someone last edited the
 * constant - measured 2026-09-06, that was 2.5 MB against a real 6.99 MB. `Content-Length` on the
 * same URL the import fetches cannot drift from it, because it is the same file.
 *
 * Failure is silence, exactly as in [ArtworkManifestClient]: every failure path returns null and the
 * caller falls back to the compiled estimate. A mirror outage must not blank a row that is only
 * telling the user roughly how much data a download costs.
 *
 * Cached for the process once a request succeeds - the published size changes only when the
 * maintainer republishes, and the Extensions row rebuilds its label on every screen entry.
 */
@Singleton
class DeliveryAssetSizeClient @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
) : DeliveryAssetSizeSource {

    private val mutex = Mutex()
    private var cachedBytes: Long? = null

    override suspend fun streamCatalogBytes(): Long? = mutex.withLock {
        cachedBytes?.let { return it }
        val measured = head(DeliveryAssets.STREAM_CATALOG_URL)
        if (measured == null) return null
        cachedBytes = measured
        measured
    }

    private suspend fun head(url: String): Long? = withContext(Dispatchers.IO) {
        try {
            // Its own short deadline: the shared download client carries callTimeout(0) so a large
            // payload is never cut off mid-stream, and a size probe that inherited that would keep the
            // row's label pending for as long as the socket stays open.
            val client = httpClient.newBuilder()
                .callTimeout(HEAD_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.i("Delivery asset size: http %d for %s - using the compiled estimate", response.code, url)
                    return@withContext null
                }
                // A redirect chain that ends without a length, or a server that answers HEAD with a
                // zero, tells us nothing - the estimate is a better answer than "0 KB".
                response.header(CONTENT_LENGTH)?.toLongOrNull()?.takeIf { it > 0L }
            }
        } catch (e: IOException) {
            Timber.i(e, "Delivery asset size unreachable - using the compiled estimate")
            null
        } catch (e: CancellationException) {
            // The caller gave up; reporting it as "unknown size" would hide a torn-down probe behind a
            // legitimate-looking result (S1889).
            throw e
        } catch (e: IllegalStateException) {
            // OkHttp raises this for a call reused or closed out of order; still just "no answer".
            Timber.i(e, "Delivery asset size call failed - using the compiled estimate")
            null
        }
    }

    private companion object {
        const val HEAD_TIMEOUT_SECONDS = 12L
        const val CONTENT_LENGTH = "Content-Length"
    }
}
