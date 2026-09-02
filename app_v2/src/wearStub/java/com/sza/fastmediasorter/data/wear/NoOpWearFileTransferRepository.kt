package com.sza.fastmediasorter.data.wear

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.WearFileTransferOutcome
import com.sza.fastmediasorter.domain.model.WearFileTransferState
import com.sza.fastmediasorter.domain.repository.WearFileTransferRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1861: inert transfer queue for the `wearStub` source set.
 *
 * Mounted into the flavors that carry no Wear companion (lite, photos, legacy, vr). [enqueue] does
 * not queue
 * anything and returns an empty id, which callers can tell from a real one: there is no watch here to
 * send to, and a queue entry nobody could ever drain would be worse than an honest refusal.
 */
@Singleton
class NoOpWearFileTransferRepository @Inject constructor() : WearFileTransferRepository {

    private val emptyState = MutableStateFlow(WearFileTransferState())
    override val transfers: StateFlow<WearFileTransferState> = emptyState.asStateFlow()

    override fun enqueue(
        sourcePath: String,
        displayName: String,
        openNow: Boolean,
        requestId: String,
        mediaType: MediaType?
    ): String {
        Timber.i("Watch transfer requested in a flavor with no Wear companion, ignoring")
        return ""
    }

    override suspend fun awaitTransfer(transferId: String): WearFileTransferOutcome =
        WearFileTransferOutcome.WATCH_UNREACHABLE

    override fun cancel(transferId: String) = Unit

    override fun clearFinished() = Unit
}
