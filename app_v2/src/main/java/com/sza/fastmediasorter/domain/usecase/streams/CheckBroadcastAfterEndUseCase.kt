package com.sza.fastmediasorter.domain.usecase.streams

import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastEndpointProbe
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoint
import com.sza.fastmediasorter.data.broadcast.exchange.BroadcastExchangeEndpoints
import com.sza.fastmediasorter.domain.player.AfterEndAnswer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * S4444, LIVE-BROADCAST item T: the one reconnect after a played broadcast stopped, asked of every endpoint of its
 * row in play order. One endpoint that still answers wins - a sign-out ends only the exchange-facing streams and the
 * LAN keeps playing - and a refusal anywhere outranks silence everywhere else.
 */
class CheckBroadcastAfterEndUseCase @Inject constructor(
    private val probe: BroadcastEndpointProbe,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** [storedUrl] and [endpoints] are the stored row's `url` and `broadcastEndpoints` columns. */
    suspend operator fun invoke(storedUrl: String, endpoints: String?): AfterEndAnswer = withContext(ioDispatcher) {
        val answers = mutableListOf<AfterEndAnswer>()
        for (endpoint in BroadcastExchangeEndpoints.playOrder(endpoints, storedUrl)) {
            val answer = ask(endpoint)
            answers += answer
            if (answer == AfterEndAnswer.ALIVE) break
        }
        when {
            AfterEndAnswer.ALIVE in answers -> AfterEndAnswer.ALIVE
            AfterEndAnswer.ENDED in answers -> AfterEndAnswer.ENDED
            else -> AfterEndAnswer.NO_ANSWER
        }
    }

    private fun ask(endpoint: BroadcastExchangeEndpoint): AfterEndAnswer = when (endpoint) {
        is BroadcastExchangeEndpoint.Lan -> probe.lanAfterEnd(endpoint.url)
        is BroadcastExchangeEndpoint.Relay -> probe.relayAfterEnd(endpoint)
        is BroadcastExchangeEndpoint.Tunnel -> probe.tunnelAfterEnd(endpoint)
    }
}
