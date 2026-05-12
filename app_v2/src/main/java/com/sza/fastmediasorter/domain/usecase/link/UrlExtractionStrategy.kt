package com.sza.fastmediasorter.domain.usecase.link

import com.sza.fastmediasorter.domain.model.link.StreamingManifest
import java.io.InputStream

/** Contract for one URL-extraction strategy in the auto-download pipeline. */
interface UrlExtractionStrategy {
    val id: String

    suspend fun probe(url: String): ProbeResult

    suspend fun open(url: String, onProgress: (bytesRead: Long, total: Long?) -> Unit): OpenResult
}

sealed interface ProbeResult {
    data object NotApplicable : ProbeResult
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

    data class Streaming(
        val manifest: StreamingManifest,
        val tentativeFileName: String,
    ) : OpenResult

    data class Batch(
        val items: List<SiteBatchItem>,
        val label: String? = null,
    ) : OpenResult

    /**
     * Returned when a known auth-sensitive host yielded only preview metadata,
     * but no real downloadable media.
     */
    data class SocialPreviewOnly(val host: String) : OpenResult

    data class NotFound(val reason: String) : OpenResult
    data class Blocked(val reason: BlockedReason) : OpenResult
    data class Error(val cause: Throwable) : OpenResult
}

data class SiteBatchItem(
    val url: String,
    val title: String? = null,
)

enum class BlockedReason {
    MimeNotAllowed,
    NonHttpScheme,
    RedirectToNonHttp,
    DrmProtected,
    StreamingDisabled,
    MuxFailed,
    AuthRequired,
}