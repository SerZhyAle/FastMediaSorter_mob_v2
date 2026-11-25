package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastEndpoint
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastEndpoints
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPassStore
import com.sza.fastmediasorter.wear.data.exchange.WearRelayStatusProbe
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastStartState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * S4692, LIVE-BROADCAST item S: asks the relay of a handed-off pending link whether its broadcast has started. It
 * probes only while collected - the waiting screen's STARTED boundary decides when - once per [PROBE_PERIOD_MS], and
 * stops at the link's `until`. A channel with no stored pending descriptor or no relay has nothing to wait on.
 */
class WatchWearBroadcastStartUseCase internal constructor(
    private val passStore: WearBroadcastPassStore,
    private val probe: WearRelayStatusProbe,
    private val nowMs: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(passStore: WearBroadcastPassStore, probe: WearRelayStatusProbe) :
        this(passStore, probe, System::currentTimeMillis, Dispatchers.IO)

    operator fun invoke(channelUrl: String): Flow<WearBroadcastStartState> = flow {
        val stored = passStore.get(channelUrl)
        val untilMs = WearBroadcastEndpoints.pendingUntilMs(stored?.descriptor)
        val relay = stored?.let {
            WearBroadcastEndpoints.parse(it.broadcastId, it.descriptor, null)
                .filterIsInstance<WearBroadcastEndpoint.Relay>()
                .firstOrNull()
        }
        var state: WearBroadcastStartState = WearBroadcastStartState.Expired
        if (untilMs != null && relay != null) state = WearBroadcastStartState.Waiting(untilMs)
        if (state == WearBroadcastStartState.Expired) emit(state)
        while (state is WearBroadcastStartState.Waiting) {
            state = when {
                nowMs() >= state.untilMs -> WearBroadcastStartState.Expired
                probe.status(requireNotNull(relay)) in ON_AIR_STATUSES -> WearBroadcastStartState.Started
                else -> state
            }
            emit(state)
            if (state is WearBroadcastStartState.Waiting) delay(PROBE_PERIOD_MS)
        }
    }.flowOn(ioDispatcher)

    companion object {
        const val PROBE_PERIOD_MS = 30_000L

        // A protected relay answers 401 to a request without its pair: that broadcast is on air too. A 503 is a
        // started broadcast with nothing uploaded yet, so Play is offered only once there is something to play.
        val ON_AIR_STATUSES = setOf(200, 401)
    }
}
