package com.sza.fastmediasorter.data.link.streaming

import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar I (DRM pre-flight).
 *
 * Pre-flights an HLS / DASH manifest to detect DRM markers before the streaming
 * pipeline starts downloading segments. Best-effort: any network or parse failure
 * returns `false` so the pipeline can proceed and surface a more specific error
 * downstream — DRM detection is an upfront optimization, not a security boundary.
 *
 * - HLS: `#EXT-X-KEY:METHOD=` other than `NONE`.
 * - DASH: presence of any `<ContentProtection` element in the MPD.
 *
 * Manifest body is capped at 256 KiB to avoid pulling oversized debug artefacts
 * into memory.
 */
@Singleton
class ManifestDrmDetector @Inject constructor(
    @Named("linkDownload") private val httpClient: OkHttpClient,
) {

    suspend fun isDrmProtected(manifestUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(manifestUrl).get().build()
            httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    LinkDownloadTrace.verbose(
                        "drm-detector manifest non-200 status=${resp.code} for ${LinkDownloadTrace.truncateUrl(manifestUrl)}",
                    )
                    return@withContext false
                }
                val body = resp.peekBody(MAX_MANIFEST_BYTES).string()
                val drm = body.contains("EXT-X-KEY:METHOD=") && !body.contains("EXT-X-KEY:METHOD=NONE")
                val contentProtection = body.contains("<ContentProtection")
                val result = drm || contentProtection
                LinkDownloadTrace.verbose(
                    "drm-detector for ${LinkDownloadTrace.truncateUrl(manifestUrl)} → drm=$result",
                )
                result
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            LinkDownloadTrace.verbose(
                "drm-detector failed for ${LinkDownloadTrace.truncateUrl(manifestUrl)}: ${t::class.simpleName}",
            )
            false
        }
    }

    private companion object {
        const val MAX_MANIFEST_BYTES: Long = 256L * 1024L
    }
}
