package com.sza.fastmediasorter.domain.usecase.link.streaming

import com.sza.fastmediasorter.domain.model.link.MediaQualityPreference
import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import java.io.File

/**
 * S0116 §5.1 pillar I: contract for downloading a streaming manifest (HLS/DASH)
 * and producing a standard MP4 file the existing [com.sza.fastmediasorter.data.link.LinkDownloadWriter]
 * can copy into the user's resource.
 *
 * Two implementations exist as flavor-scoped source-sets:
 *
 * - `streamingEnabled/` (standard, legacy, vr, vrUnlicensed) - backed by Media3
 *   `HlsDownloader`/`DashDownloader` and an Android-native `MediaMuxer` remux step.
 * - `streamingDisabled/` (lite, photos) - `NoOpStreamingPipeline` returning
 *   [PipelineOutcome.Disabled] so the coordinator surfaces a localized
 *   "streaming not available in this build" toast instead of crashing.
 */
interface StreamingPipeline {
    suspend fun fetchAndRemux(
        manifest: StreamingManifest,
        fileName: String,
        quality: MediaQualityPreference,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): PipelineOutcome
}

sealed interface PipelineOutcome {
    /** Pipeline succeeded; [file] is a standard MP4 ready for [com.sza.fastmediasorter.data.link.LinkDownloadWriter]. */
    data class Success(val file: File, val mime: String) : PipelineOutcome

    /** Manifest carries DRM (`EXT-X-KEY METHOD=…` or `<ContentProtection>`). */
    data object DrmBlocked : PipelineOutcome

    /** Active flavor does not include the streaming downloader (lite, photos). */
    data object Disabled : PipelineOutcome

    /** Sample-copy mux failed because the stream uses an unsupported codec. */
    data class MuxFailed(val codec: String) : PipelineOutcome

    /** Network-level failure or any other recoverable error during segment download. */
    data class NetworkError(val cause: Throwable) : PipelineOutcome
}
