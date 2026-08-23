package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.WearFileTransferState
import kotlinx.coroutines.flow.StateFlow

/**
 * S1861: owner of every in-flight phone -> watch file transfer.
 *
 * Bound as a `@Singleton` in `SingletonComponent` and driven from an application-level scope, so a
 * transfer survives the screen that started it - a 32 MB send must not die because the user backed
 * out of the picker. It deliberately does not survive process death: no foreground service is
 * started for it (ADR of the tactical plan, phase 1 section 4).
 *
 * Mounted per flavor exactly like [WearableDataLayerRepository] - the real implementation lives in
 * `src/wearGms`, the inert twin in `src/wearStub`, so `src/main` carries no build-variant check.
 */
interface WearFileTransferRepository {

    /** The whole queue as one snapshot; a late subscriber gets the current picture immediately. */
    val transfers: StateFlow<WearFileTransferState>

    /**
     * Queues [sourcePath] for delivery to the paired watch under [displayName] and returns the id
     * the queue entry can later be cancelled by.
     */
    fun enqueue(sourcePath: String, displayName: String): String

    /**
     * Stops the queued or running transfer [transferId].
     *
     * Cancellation is a call and not an unsubscribe: the collector is a screen that comes and goes,
     * while the transfer belongs to the application scope, so dropping the subscription must never be
     * read as "stop sending".
     */
    fun cancel(transferId: String)

    /** Drops finished entries from the queue snapshot once the user has seen their outcome. */
    fun clearFinished()
}
