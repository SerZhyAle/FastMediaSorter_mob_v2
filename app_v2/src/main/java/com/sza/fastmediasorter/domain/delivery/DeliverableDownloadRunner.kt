package com.sza.fastmediasorter.domain.delivery

import kotlinx.coroutines.flow.Flow

/**
 * Interface to enqueue elements' downloads and observe progress from anywhere in the app (S0397).
 */
interface DeliverableDownloadRunner {

    /** Enqueues the download task in WorkManager. This operation is idempotent. */
    fun enqueue(set: DeliverableSet)

    /** Returns a flow tracking the download progress of a [DeliverableSet]. */
    fun progressOf(set: DeliverableSet): Flow<DownloadProgress>
}
