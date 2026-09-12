package com.sza.fastmediasorter.wear.ui.sync

import com.sza.fastmediasorter.wear.data.wear.WatchSyncEvents
import com.sza.fastmediasorter.wear.domain.model.WearSyncLeg
import com.sza.fastmediasorter.wear.domain.model.WearSyncLegResult
import com.sza.fastmediasorter.wear.domain.model.WearSyncOutcome
import com.sza.fastmediasorter.wear.domain.usecase.SyncWithPhoneUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val IMPORT_TIMEOUT_MS = 15_000L

/**
 * S2484: the state of one unified exchange as both watch screens see it.
 */
sealed class UnifiedSyncState {
    data object Idle : UnifiedSyncState()
    data object Running : UnifiedSyncState()
    data class Finished(val outcome: WearSyncOutcome) : UnifiedSyncState()
}

/**
 * S2484: one exchange, one state, shared by the resources screen and the settings screen.
 *
 * A singleton rather than per-screen state because the same control appears twice by design
 * (strategic 3.1), and two independent flags would let a sync started in one section look idle in
 * the other.
 *
 * It also closes the signalling gap strategic 4 records: the watch publishes resource-import failures
 * to `importErrorFlow`, which nothing collected, so that leg used to fail with nothing on screen and
 * a combined outcome built over it would have reported success for an exchange that never arrived.
 */
@Singleton
class WatchSyncCoordinator @Inject constructor(
    private val syncWithPhone: SyncWithPhoneUseCase
) {

    private val _state = MutableStateFlow<UnifiedSyncState>(UnifiedSyncState.Idle)
    val state: StateFlow<UnifiedSyncState> = _state.asStateFlow()

    /**
     * @param scope the caller's lifecycle scope. The coordinator outlives any one screen, so it must
     *   not own a scope of its own that would keep an exchange running after the watch left the app.
     */
    fun syncEverything(scope: CoroutineScope) {
        if (_state.value is UnifiedSyncState.Running) {
            Timber.d("Unified sync already running - ignoring the repeat tap")
            return
        }
        _state.value = UnifiedSyncState.Running
        scope.launch {
            val outcome = syncWithPhone()
            _state.value = UnifiedSyncState.Finished(awaitInboundResources(outcome))
        }
    }

    fun reset() {
        _state.value = UnifiedSyncState.Idle
    }

    /**
     * Turns "the phone was asked" into "the resources arrived and were applied". The request leg is
     * confirmed by the message being accepted, which says nothing about what came back, so the real
     * verdict is whichever of the two import flows speaks first.
     */
    private suspend fun awaitInboundResources(outcome: WearSyncOutcome): WearSyncOutcome {
        if (outcome.legs[WearSyncLeg.RESOURCES_IN] !is WearSyncLegResult.Succeeded) {
            return outcome
        }
        val imported = merge(
            WatchSyncEvents.importResultFlow.map { result ->
                WearSyncLegResult.Succeeded(result.added + result.updated)
            },
            WatchSyncEvents.importErrorFlow.map { message -> WearSyncLegResult.Failed(message) }
        )
        val result = withTimeoutOrNull(IMPORT_TIMEOUT_MS) { imported.first() }
        if (result == null) {
            Timber.w("Unified sync: the phone did not answer with resources within $IMPORT_TIMEOUT_MS ms")
            return outcome.withLeg(WearSyncLeg.RESOURCES_IN, WearSyncLegResult.Failed(REASON_NO_ANSWER))
        }
        return outcome.withLeg(WearSyncLeg.RESOURCES_IN, result)
    }

    private companion object {
        const val REASON_NO_ANSWER = "the phone did not answer"
    }
}
