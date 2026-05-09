package com.sza.fastmediasorter.domain.usecase.link

import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import java.io.InputStream

/**
 * S0003 — strategic §5.1, pillar D: contract for "given a URL, return a downloadable stream".
 * Strategies are ordered by [LinkExtractionRegistry] and queried by
 * [LinkAutoDownloadCoordinator]; the first `Applicable` probe wins.
 */
interface UrlExtractionStrategy {
    /** Unique short id used for ordering / logs. */
    val id: String

    suspend fun probe(url: String): ProbeResult

    suspend fun open(url: String, onProgress: (bytesRead: Long, total: Long?) -> Unit): OpenResult
}

sealed interface ProbeResult {
    object NotApplicable : ProbeResult
    data class Applicable(val tentativeMime: String?, val tentativeSizeBytes: Long?) : ProbeResult
    data class TransientError(val cause: Throwable) : ProbeResult
}

sealed interface OpenResult {
    data class Stream(
        val body: InputStream,
        val contentLength: Long?,
        val mime: String,
        val fileName: String,
        val close: () -> Unit,
    ) : OpenResult

    /**
     * S0116 §5.1 pillar I: a streaming manifest (HLS/DASH) was discovered. The
     * coordinator routes this outcome to the streaming pipeline (Phase 03), which
     * downloads segments and remuxes to a standard MP4 before continuing through
     * [com.sza.fastmediasorter.data.link.LinkDownloadWriter].
     */
    data class Streaming(
        val manifest: StreamingManifest,
        val tentativeFileName: String,
    ) : OpenResult

    data class NotFound(val reason: String) : OpenResult
    data class Blocked(val reason: BlockedReason) : OpenResult
    data class Error(val cause: Throwable) : OpenResult
}

enum class BlockedReason {
    MimeNotAllowed,
    NonHttpScheme,
    RedirectToNonHttp,
    // S0116 §5.1 pillars I/L: streaming pipeline outcomes surface through the same
    // Blocked enumeration so the coordinator can map them to dedicated user UX.
    DrmProtected,
    StreamingDisabled,
    MuxFailed,
    AuthRequired,
}
