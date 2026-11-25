package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPlaybackResolver
import com.sza.fastmediasorter.wear.data.exchange.WearDirectoryBroadcast
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastRoute
import com.sza.fastmediasorter.wear.domain.playback.WearAfterEndAnswer
import javax.inject.Inject

/**
 * S4610: the endpoint a playing broadcast moves to (contract LIVE-BROADCAST 12, DEVICE-EXCHANGE 7.7). A failed
 * endpoint passes the walk to the one after it; a reconnect - a changed network or the wearer's Retry - starts at
 * the top again, so a watch that came home to the LAN is found on the LAN again.
 */
class WalkWearBroadcastEndpointsUseCase @Inject constructor(
    private val resolver: WearBroadcastPlaybackResolver,
    private val checkAfterEnd: CheckWearBroadcastAfterEndUseCase,
) {

    /** DEVICE-EXCHANGE item DY: the one reconnect asked when a walk after a played broadcast ran out. */
    suspend fun afterEnd(record: WearDirectoryBroadcast): WearAfterEndAnswer = checkAfterEnd(record)

    /** The next playable endpoint with its route, or null when the list holds no endpoint that plays. */
    suspend operator fun invoke(route: WearBroadcastRoute, fromTop: Boolean): WearBroadcastPlayback.Playable? {
        val startAt = if (fromTop) 0 else route.endpointIndex + 1
        val playback = resolver.resolve(route.record, startAt) as? WearBroadcastPlayback.Playable
        return playback?.takeIf { it.route != null }
    }
}
