package com.sza.fastmediasorter.data.link.streaming

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
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
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar I (segment download via Media3).
 *
 * Wraps Media3 segment download via [DefaultDownloaderFactory] in a coroutine-friendly API.
 * Cache layer is a per-session [SimpleCache] rooted at `cacheDir/url-stream/<id>/cache/`
 * with no eviction (cleanup happens via [StreamingCacheCleaner] after remux).
 *
 * Variant selection ([CachedStreamAssembler]): one video rendition, the tallest within
 * [MediaQualityPreference.maxResolutionPx], plus its separate audio rendition; with
 * [MediaQualityPreference.audioOnly] only that audio rendition when the stream has one.
 * The pick reaches the downloader as stream keys, so nothing else is fetched.
 *
 * Failure modes raise [StreamingDownloadException]; callers handle these and map
 * to [com.sza.fastmediasorter.domain.usecase.link.streaming.PipelineOutcome.NetworkError].
 */
// S1685: `@OptIn` did nothing here - UnstableApi is not a `@RequiresOptIn` marker in this Media3 version,
// and the compiler said so on every build. `@UnstableApi` is what the rest of the tree uses.
@UnstableApi
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
        accountId: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): SegmentBundle = withContext(Dispatchers.IO) {
        sessionDir.mkdirs()
        // S1776: the three-arg constructor is the non-deprecated form. The DB index is as
        // session-scoped as the old file index was - this cache roots at a per-download
        // sessionDir and is released at the end of every call, so nothing persists to migrate.
        val cache = SimpleCache(File(sessionDir, CACHE_DIR), NoOpCacheEvictor(), StandaloneDatabaseProvider(context))

        // S0116 §5.1 pillar K: inject saved domain cookies into the Media3 HTTP source
        // so authenticated streams continue to work after Phase 05 WebView login.
        val host = Uri.parse(manifest.manifestUrl).host ?: ""
        // S1776: cookies of the account the request actually carried; null keeps the previous
        // most-recently-used-account behaviour.
        val cookieList =
            if (host.isNotBlank()) cookieStore.loadForHostAccountOrBest(host, accountId) else emptyList()
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
        val assembler = CachedStreamAssembler(
            networkSource = cacheFactory.createDataSource(),
            cacheOnlySource = CacheDataSource(cache, null),
        )
        try {
            val selection = assembler.select(manifest, quality)
            // S2914: DefaultDownloaderFactory infers the downloader type (HLS/DASH) from the
            // DownloadRequest MIME type, replacing the deprecated direct HlsDownloader/DashDownloader
            // constructors. The direct executor runs factory-internal tasks on the calling thread;
            // the blocking download itself runs via runInterruptible on Dispatchers.IO below.
            val mimeType = when (manifest) {
                is StreamingManifest.Hls -> MimeTypes.APPLICATION_M3U8
                is StreamingManifest.Dash -> MimeTypes.APPLICATION_MPD
            }
            val request = DownloadRequest.Builder(
                /* id = */
                "s0116-${System.currentTimeMillis()}",
                /* uri = */
                Uri.parse(manifest.manifestUrl),
            )
                .setMimeType(mimeType)
                .setStreamKeys(selection.streamKeys)
                .build()
            val downloaderFactory = DefaultDownloaderFactory(cacheFactory, Executor { it.run() })
            val downloader: Downloader = downloaderFactory.createDownloader(request)

            LinkDownloadTrace.verbose(
                "media3-segment-downloader start manifest=${manifest::class.simpleName} " +
                    "quality=${quality.maxResolutionPx}px audioOnly=${quality.audioOnly} " +
                    "streams=${selection.streamKeys.size} session=${sessionDir.name}",
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

            val segmentFiles = assembler.assemble(selection, File(sessionDir, ASSEMBLED_DIR))
                .filter { it.length() > 0 }
            if (segmentFiles.isEmpty()) {
                throw StreamingDownloadException(
                    "media3 downloader produced 0 stream files for ${manifest.manifestUrl}",
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

    private companion object {
        // Separate trees: the remuxer input must never share a directory with Media3's private cache layout.
        const val CACHE_DIR = "cache"
        const val ASSEMBLED_DIR = "assembled"
    }
}

/**
 * S0116 pillar I: files produced by [Media3SegmentDownloader] and consumed by [MediaMuxerRemuxer] -
 * one file per selected stream, holding its init segment and media segments in playlist order.
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
