package com.sza.fastmediasorter.data.delivery

import com.sza.fastmediasorter.domain.delivery.ArtworkManifest
import com.sza.fastmediasorter.domain.delivery.ArtworkManifestSet
import com.sza.fastmediasorter.domain.delivery.ArtworkManifestSource
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * S1483: fetches `artwork-manifest.json` from the delivery mirror and answers "is there newer
 * artwork" from it, instead of from a hash compiled into this build.
 *
 * Failure is silence by contract. Network down, HTTP error, truncated body, malformed JSON, a schema
 * version this build does not know - every one of them returns null, which the caller reads as
 * "nothing new". A mirror outage must not surface as a broken Streams screen, and an exception here
 * would do exactly that: the staleness check runs inside the status flow the list collects.
 *
 * Cached for the process once a fetch succeeds. The manifest changes only when the maintainer
 * republishes artwork, so re-fetching it per status emission would spend a request per list repaint.
 */
@Singleton
class ArtworkManifestClient @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
) : ArtworkManifestSource {

    private val mutex = Mutex()
    private var cached: ArtworkManifest? = null

    override suspend fun stampOf(set: DeliverableSet): String? = entryOf(set)?.stamp

    override suspend fun sizeOf(set: DeliverableSet): Long? = entryOf(set)?.totalBytes

    private suspend fun entryOf(set: DeliverableSet): ArtworkManifestSet? {
        if (set !in ARTWORK_SETS) return null
        return manifest()?.sets?.get(set)
    }

    private suspend fun manifest(): ArtworkManifest? = mutex.withLock {
        cached?.let { return it }
        val fetched = fetch() ?: return null
        cached = fetched
        fetched
    }

    private suspend fun fetch(): ArtworkManifest? = withContext(Dispatchers.IO) {
        try {
            // Its own short-deadline client: the shared download client deliberately has no call
            // timeout so a large payload is never cut off mid-stream, and a manifest read that
            // inherited that would hang the staleness check for as long as the socket stays open.
            val client = httpClient.newBuilder()
                .callTimeout(MANIFEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(MANIFEST_URL).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.i("Artwork manifest: http %d - treating as no update", response.code)
                    return@withContext null
                }
                val body = response.body?.string()
                if (body.isNullOrBlank()) return@withContext null
                parse(body)
            }
        } catch (e: IOException) {
            // Network refusal, DNS failure, a socket that dies mid-body: all mean the same thing here.
            Timber.i(e, "Artwork manifest unreachable - treating as no update")
            null
        } catch (e: CancellationException) {
            // Cancellation is the caller giving up, not the manifest answering - reporting it as
            // "no update" would hide a torn-down fetch behind a legitimate-looking result (S1889).
            throw e
        } catch (e: IllegalStateException) {
            // OkHttp raises this for a call reused or closed out of order; still just "no answer".
            Timber.i(e, "Artwork manifest call failed - treating as no update")
            null
        }
    }

    /** `internal` so the failure paths can be unit-tested without standing up an HTTP server. */
    internal fun parse(body: String): ArtworkManifest? = try {
        val root = JSONObject(body)
        val schema = root.optInt("schemaVersion", -1)
        if (schema != ArtworkManifest.SUPPORTED_SCHEMA_VERSION) {
            Timber.i("Artwork manifest: unsupported schemaVersion %d - ignored", schema)
            null
        } else {
            val sets = root.optJSONObject("sets")
            ArtworkManifest(
                schemaVersion = schema,
                generatedAt = root.optString("generatedAt"),
                sets = buildMap {
                    setEntry(sets, ArtworkManifest.SET_CHANNEL_PREVIEW)
                        ?.let { put(DeliverableSet.CHANNEL_PREVIEW_ATLAS, it) }
                    setEntry(sets, ArtworkManifest.SET_STREAM_LOGO)
                        ?.let { put(DeliverableSet.STREAM_LOGO_ATLAS, it) }
                },
            )
        }
    } catch (e: JSONException) {
        // The only failure this parse can produce, and it is a verdict: a body we cannot read means
        // there is nothing new to offer.
        Timber.i(e, "Artwork manifest malformed - treating as no update")
        null
    }

    private fun setEntry(sets: JSONObject?, name: String): ArtworkManifestSet? {
        val entry = sets?.optJSONObject(name)
        val stamp = entry?.optString("stamp").orEmpty()
        // A set without a stamp is dropped rather than defaulted: an empty stamp would compare equal
        // to an empty installed stamp and report "current" for a payload we know nothing about.
        return if (stamp.isBlank()) null else ArtworkManifestSet(stamp = stamp, totalBytes = totalBytes(entry))
    }

    private fun totalBytes(entry: JSONObject?): Long {
        val files = entry?.optJSONArray("files") ?: return 0L
        var total = 0L
        for (index in 0 until files.length()) {
            total += files.optJSONObject(index)?.optLong("size") ?: 0L
        }
        return total
    }

    private companion object {
        const val MANIFEST_URL =
            "https://github.com/SerZhyAle/FastMediaSorter_mob_v2/releases/download/delivery-so-v1/artwork-manifest.json"
        const val MANIFEST_TIMEOUT_SECONDS = 12L
        val ARTWORK_SETS = setOf(DeliverableSet.CHANNEL_PREVIEW_ATLAS, DeliverableSet.STREAM_LOGO_ATLAS)
    }
}
