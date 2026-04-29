package com.sza.fastmediasorter.domain.usecase.link

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

    data class NotFound(val reason: String) : OpenResult
    data class Blocked(val reason: BlockedReason) : OpenResult
    data class Error(val cause: Throwable) : OpenResult
}

enum class BlockedReason {
    MimeNotAllowed,
    NonHttpScheme,
    RedirectToNonHttp,
}
