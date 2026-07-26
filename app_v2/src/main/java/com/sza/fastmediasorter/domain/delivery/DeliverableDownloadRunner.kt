package com.sza.fastmediasorter.domain.delivery

import kotlinx.coroutines.flow.Flow

/**
 * Interface to enqueue elements' downloads and observe progress from anywhere in the app (S0397).
 */
interface DeliverableDownloadRunner {

    /** Enqueues the download task in WorkManager. This operation is idempotent. */
    /**
     * Queue the download for [set]. Skipped when the payload is already on disk, unless [force] -
     * S1200: re-downloading a stale payload is the whole point of an update, and the installed-payload
     * guard here would otherwise swallow it exactly as the offer used to.
     */
    fun enqueue(set: DeliverableSet, force: Boolean = false)

    /** Returns a flow tracking the download progress of a [DeliverableSet]. */
    fun progressOf(set: DeliverableSet): Flow<DownloadProgress>
}
