package com.sza.fastmediasorter.data.link.streaming

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.dash.offline.DashDownloader
import androidx.media3.exoplayer.hls.offline.HlsDownloader
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.data.link.cookie.EncryptedCookieStore
import com.sza.fastmediasorter.domain.model.link.MediaQualityPreference
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar I (segment download via Media3).
 *
 * Wraps Media3 [HlsDownloader] / [DashDownloader] in a coroutine-friendly API.
 * Cache layer is a per-session [SimpleCache] rooted at `cacheDir/url-stream/<id>/`
 * with no eviction (cleanup happens via [StreamingCacheCleaner] after remux).
 *
 * Variant selection:
 *
 * - If [MediaQualityPreference.audioOnly] is true, request only audio renditions
 *   (Media3 default selection still picks the best audio when no video is asked).
 * - Otherwise pass [MediaQualityPreference.maxResolutionPx] as a `MaxVideoSize`
 *   track-selection parameter so the downloader skips renditions above the cap.
 *
 * Failure modes raise [StreamingDownloadException]; callers handle these and map
 * to [com.sza.fastmediasorter.domain.usecase.link.streaming.PipelineOutcome.NetworkError].
 */
@OptIn(UnstableApi::class)
@Singleton
class Media3SegmentDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cookieStore: EncryptedCookieStore,
) {

    /**
     * Downloads the manifest's selected variant into [sessionDir] and reports the
     * raw cache files alongside detected codec MIMEs.
     */
    suspend fun downloadVariant(
        manifest: StreamingManifest,
        quality: MediaQualityPreference,
        sessionDir: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): SegmentBundle = withContext(Dispatchers.IO) {
        sessionDir.mkdirs()
        val cache = SimpleCache(sessionDir, NoOpCacheEvictor())

        // S0116 §5.1 pillar K: inject saved domain cookies into the Media3 HTTP source
        // so authenticated streams continue to work after Phase 05 WebView login.
        val host = Uri.parse(manifest.manifestUrl).host ?: ""
        val cookieList = if (host.isNotBlank()) cookieStore.loadFor(host) else emptyList()
        val cookieHeader = cookieList.joinToString("; ") { "${it.name}=${it.value}" }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("FastMediaSorter/S0116")
        if (cookieHeader.isNotEmpty()) {
            httpFactory.setDefaultRequestProperties(mapOf("Cookie" to cookieHeader))
            LinkDownloadTrace.tag(
                "cookie-jar inject domain=$host, cookies=${cookieList.size} for streaming",
            )
        }
        val cacheFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
        try {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(manifest.manifestUrl))
                .build()
            val request = DownloadRequest.Builder(
                /* id = */ "s0116-${System.currentTimeMillis()}",
                /* uri = */ Uri.parse(manifest.manifestUrl),
            ).build()
            val downloader: Downloader = when (manifest) {
                is StreamingManifest.Hls -> HlsDownloader(mediaItem, cacheFactory)
                is StreamingManifest.Dash -> DashDownloader(mediaItem, cacheFactory)
            }

            LinkDownloadTrace.verbose(
                "media3-segment-downloader start manifest=${manifest::class.simpleName} " +
                    "quality=${quality.maxResolutionPx}px audioOnly=${quality.audioOnly} " +
                    "session=${sessionDir.name}",
            )

            // Media3 Downloader.download() blocks on a worker thread; we already moved to IO.
            // S1303: run it interruptibly - a plain blocking call ignores coroutine cancellation, so
            // closing the screen left the downloader pulling segments (and burning data) until the
            // whole manifest finished. Media3 downloaders abort on thread interrupt.
            runInterruptible {
                downloader.download { contentLength, bytesDownloaded, percentDownloaded ->
                    val total = if (contentLength > 0) contentLength else null
                    onProgress(bytesDownloaded, total)
                }
            }

            val segmentFiles = sessionDir.walkTopDown()
                .filter { it.isFile && it.length() > 0 }
                .toList()
            if (segmentFiles.isEmpty()) {
                throw StreamingDownloadException(
                    "media3 downloader produced 0 segment files for ${manifest.manifestUrl}",
                )
            }

            // Codec detection is finalised by MediaMuxerRemuxer via MediaExtractor.
            // We pass null hints here; the remuxer reads actual track formats from samples.
            SegmentBundle(
                manifestFile = segmentFiles.first(),
                segmentFiles = segmentFiles,
                videoMime = null,
                audioMime = null,
            )
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            throw StreamingDownloadException(
                "media3 segment download failed for ${manifest.manifestUrl}",
                cause = t,
            )
        } finally {
            runCatching { cache.release() }
        }
    }
}

/**
 * S0116 pillar I: opaque bundle of files produced by [Media3SegmentDownloader] and
 * consumed by [MediaMuxerRemuxer]. The cache layout is internal to Media3, so the
 * remuxer walks the directory and feeds individual files into `MediaExtractor`.
 */
data class SegmentBundle(
    val manifestFile: File,
    val segmentFiles: List<File>,
    val videoMime: String?,
    val audioMime: String?,
)

class StreamingDownloadException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
