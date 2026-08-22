package com.sza.fastmediasorter.data.link.streaming

import android.content.Context
import com.sza.fastmediasorter.core.log.LinkDownloadTrace
import com.sza.fastmediasorter.domain.model.link.MediaQualityPreference
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import com.sza.fastmediasorter.domain.usecase.link.streaming.PipelineOutcome
import com.sza.fastmediasorter.domain.usecase.link.streaming.StreamingPipeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0116 §5.1 pillar I (orchestrator).
 *
 * Real `streamingEnabled/` implementation of [StreamingPipeline]. The flow:
 *
 * 1. DRM pre-flight via [ManifestDrmDetector] - abort early if marker present.
 * 2. Generate session id + ensure cache space via [StreamingCacheCleaner].
 * 3. Download segments through Media3 ([Media3SegmentDownloader]) into the session dir.
 * 4. Remux segments → standard MP4 via [MediaMuxerRemuxer] (sample-copy).
 * 5. Cleanup the session dir; only the final MP4 survives.
 *
 * Errors are caught and projected to [PipelineOutcome.NetworkError] /
 * [PipelineOutcome.MuxFailed] / [PipelineOutcome.DrmBlocked]. `CancellationException`
 * always propagates so structured concurrency works.
 */
@Singleton
class StreamingDownloadStrategy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val drmDetector: ManifestDrmDetector,
    private val segmentDownloader: Media3SegmentDownloader,
    private val remuxer: MediaMuxerRemuxer,
    private val cacheCleaner: StreamingCacheCleaner,
) : StreamingPipeline {

    override suspend fun fetchAndRemux(
        manifest: StreamingManifest,
        fileName: String,
        quality: MediaQualityPreference,
        accountId: String?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): PipelineOutcome {
        val sessionId = cacheCleaner.newSessionId()
        val sessionDir = cacheCleaner.sessionDir(context.cacheDir, sessionId)
        LinkDownloadTrace.tag(
            "streaming-downloader started, manifest=${if (manifest is StreamingManifest.Hls) "hls" else "dash"}, " +
                "target=$fileName session=$sessionId",
        )

        try {
            // 1. DRM check.
            if (drmDetector.isDrmProtected(manifest.manifestUrl)) {
                return PipelineOutcome.DrmBlocked
            }

            // 2. Pre-flight cache space (best-effort; we do not know exact size yet).
            //    Use a coarse 64 MiB lower bound - short clips fit; longer ones will hit
            //    a real out-of-space error during download which is also surfaced cleanly.
            val ok = cacheCleaner.preflightCheck(context.cacheDir, requiredBytes = PRE_FLIGHT_RESERVE)
            if (!ok) {
                return PipelineOutcome.NetworkError(
                    cause = StreamingDownloadException("insufficient cache space (<64 MiB)"),
                )
            }

            // 3. Segment download.
            val bundle = segmentDownloader.downloadVariant(manifest, quality, sessionDir, accountId, onProgress)

            // 4. Remux.
            LinkDownloadTrace.tag(
                "streaming-downloader remux start, codec=${bundle.videoMime}/${bundle.audioMime}, " +
                    "segments=${bundle.segmentFiles.size}",
            )
            val outputFile = java.io.File(sessionDir, fileName)
            val muxed = remuxer.remux(bundle, outputFile)
            return when (muxed) {
                is RemuxResult.Success -> PipelineOutcome.Success(file = muxed.file, mime = "video/mp4")
                is RemuxResult.MuxFailed -> PipelineOutcome.MuxFailed(codec = muxed.codec)
            }
        } catch (ce: CancellationException) {
            // Cleanup before propagating cancellation.
            runCatching { cacheCleaner.cleanupSession(context.cacheDir, sessionId) }
            throw ce
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            LinkDownloadTrace.verbose("fallback=streaming-network-error session=$sessionId reason=${t::class.simpleName}")
            runCatching { cacheCleaner.cleanupSession(context.cacheDir, sessionId) }
            return PipelineOutcome.NetworkError(cause = t)
        }
    }

    private companion object {
        const val PRE_FLIGHT_RESERVE: Long = 64L * 1024L * 1024L
    }
}
