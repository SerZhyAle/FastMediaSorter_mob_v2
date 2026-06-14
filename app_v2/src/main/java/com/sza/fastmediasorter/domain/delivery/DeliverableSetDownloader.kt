package com.sza.fastmediasorter.domain.delivery

import kotlinx.coroutines.flow.Flow

/**
 * Downloads a [DeliverableSet]'s payload from its ordered sources with auto-failover, verifies it,
 * and persists it outside the cache (S0386 strategic Pillar C). The UX layer collects the returned
 * [DownloadProgress] flow to drive the offer/progress/result states (Phase 06).
 */
interface DeliverableSetDownloader {

    /** Cold flow that performs the download when collected and reports progress until terminal. */
    fun download(set: DeliverableSet): Flow<DownloadProgress>
}

/** Progress of one [DeliverableSet] download. [Installed] and [Failed] are terminal. */
sealed interface DownloadProgress {

    /** Accepted, not yet transferring. */
    data object Queued : DownloadProgress

    /** Transferring: [percent] is 0..99, [bytesDownloaded]/[totalBytes] are cumulative across files. */
    data class Running(val percent: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadProgress

    /** All files transferred, integrity check in progress. */
    data object Verifying : DownloadProgress

    /** Verified, atomically installed, marker persisted - the set is now usable. */
    data object Installed : DownloadProgress

    /** Every source failed or verification failed; no partial payload remains. */
    data class Failed(val reason: String) : DownloadProgress
}
