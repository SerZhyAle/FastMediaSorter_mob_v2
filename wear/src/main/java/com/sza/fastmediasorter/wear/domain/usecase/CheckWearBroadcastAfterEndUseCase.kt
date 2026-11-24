package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastAfterEndProbe
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastEndpoint
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastEndpoints
import com.sza.fastmediasorter.wear.data.exchange.WearDirectoryBroadcast
import com.sza.fastmediasorter.wear.data.exchange.basicAuthorizationHeader
import com.sza.fastmediasorter.wear.domain.playback.WearAfterEndAnswer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * DEVICE-EXCHANGE item DY on the watch: the one reconnect after a played broadcast's endpoint walk ran out, asked of
 * every endpoint the watch can play in the producer's order - the LAN and the relay; the watch declares no tunnel
 * (item CE). One endpoint that answers wins, and a refusal anywhere outranks silence everywhere else, as on the phone.
 * A protected broadcast is asked only on the relay, with the header it plays with; without credentials nothing is
 * asked.
 */
class CheckWearBroadcastAfterEndUseCase @Inject constructor(
    private val probe: WearBroadcastAfterEndProbe,
) {

    suspend operator fun invoke(record: WearDirectoryBroadcast): WearAfterEndAnswer = withContext(Dispatchers.IO) {
        val isProtected = WearBroadcastEndpoints.isProtected(record.descriptor)
        val authorization = record.credentials?.takeIf { isProtected }?.basicAuthorizationHeader()
        val askable = playableEndpoints(record).filter { endpoint ->
            when (endpoint) {
                is WearBroadcastEndpoint.Lan -> !isProtected
                is WearBroadcastEndpoint.Relay -> !isProtected || authorization != null
            }
        }
        val answers = mutableListOf<WearAfterEndAnswer>()
        for (endpoint in askable) {
            val answer = when (endpoint) {
                is WearBroadcastEndpoint.Lan -> probe.lan(endpoint)
                is WearBroadcastEndpoint.Relay -> probe.relay(endpoint, authorization)
            }
            answers += answer
            if (answer == WearAfterEndAnswer.ALIVE) break
        }
        when {
            WearAfterEndAnswer.ALIVE in answers -> WearAfterEndAnswer.ALIVE
            WearAfterEndAnswer.ENDED in answers -> WearAfterEndAnswer.ENDED
            else -> WearAfterEndAnswer.NO_ANSWER
        }
    }

    private fun playableEndpoints(record: WearDirectoryBroadcast): List<WearBroadcastEndpoint> =
        WearBroadcastEndpoints.parse(record.broadcastId, record.descriptor, record.mode)
            .filter { WearBroadcastEndpoints.isPlayableMode(it.mode ?: record.mode) }
}
