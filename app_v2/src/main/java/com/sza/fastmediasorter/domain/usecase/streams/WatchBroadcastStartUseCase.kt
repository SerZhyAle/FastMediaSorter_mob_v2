package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastEndpointProbe
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoint
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoints
import com.sza.fastmediasorter.domain.model.BroadcastStartState
import com.sza.fastmediasorter.domain.repository.BroadcastPendingStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

/**
 * S4247, research item 3: asks the relay of a waiting row whether its broadcast has started. It probes only while
 * collected - the screen's lifecycle decides when - once per [PROBE_PERIOD_MS], well above the server's 10 unknown
 * ids per minute per address, and stops at the link's expiry.
 */
class WatchBroadcastStartUseCase @Inject constructor(
    private val probe: BroadcastEndpointProbe,
    private val pendingStore: BroadcastPendingStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    operator fun invoke(streamId: String, endpoints: String?): Flow<BroadcastStartState> = flow {
        val untilMs = pendingStore.untilMs(streamId)
        val relay = BroadcastExchangeEndpoints.playOrder(endpoints, NO_FALLBACK_URL)
            .filterIsInstance<BroadcastExchangeEndpoint.Relay>()
            .firstOrNull()
        var state: BroadcastStartState = BroadcastStartState.Expired
        if (untilMs != null && relay != null) state = BroadcastStartState.Waiting(untilMs)
        while (state is BroadcastStartState.Waiting) {
            state = when {
                System.currentTimeMillis() >= state.untilMs -> BroadcastStartState.Expired
                probe.relayStatus(requireNotNull(relay)) in ON_AIR_STATUSES -> BroadcastStartState.Started
                else -> state
            }
            emit(state)
            if (state is BroadcastStartState.Waiting) delay(PROBE_PERIOD_MS)
        }
        if (untilMs != null) pendingStore.clear(streamId)
        if (untilMs == null || relay == null) emit(state)
    }.flowOn(ioDispatcher)

    companion object {
        const val PROBE_PERIOD_MS = 30_000L
        private const val NO_FALLBACK_URL = ""

        // A protected relay answers 401 to a request without its pair: that broadcast is on air too.
        val ON_AIR_STATUSES = setOf(200, 401)
    }
}
