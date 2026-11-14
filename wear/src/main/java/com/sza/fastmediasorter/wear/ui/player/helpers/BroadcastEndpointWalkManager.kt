package com.sza.fastmediasorter.wear.ui.player.helpers

import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastRoute
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannel
import com.sza.fastmediasorter.wear.domain.model.WearNetworkChannelKind
import com.sza.fastmediasorter.wear.domain.playback.WearStreamFailure
import com.sza.fastmediasorter.wear.domain.usecase.WalkWearBroadcastEndpointsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * S4610: walks a playing broadcast's endpoint list for a player screen (contract LIVE-BROADCAST 12, DEVICE-EXCHANGE
 * 7.7). A failed endpoint moves the player to the next one, and a change of the network kind resolves the broadcast
 * from the top again, so a watch that leaves Wi-Fi reaches the relay and one that comes home is found on the LAN.
 *
 * [reconnectRoute] answers, at the moment the network changes, which broadcast to resolve again - null when nothing
 * is playing that should be reloaded. [onMoved] gets the endpoint to play; [onExhausted] the failure to show when
 * no endpoint after the failed one plays.
 */
class BroadcastEndpointWalkManager(
    private val scope: CoroutineScope,
    private val walkEndpoints: WalkWearBroadcastEndpointsUseCase,
    networkChannel: Flow<WearNetworkChannel>,
    private val reconnectRoute: () -> WearBroadcastRoute?,
    private val onMoved: suspend (WearBroadcastPlayback.Playable) -> Unit,
    private val onExhausted: (WearStreamFailure) -> Unit,
) {

    private var job: Job? = null

    init {
        // A link lost on the way is not a change: the kind the watch lands on is compared with the kind it left, so
        // Wi-Fi -> none -> Wi-Fi reloads nothing and Wi-Fi -> none -> LTE resolves again from the top.
        scope.launch {
            networkChannel
                .map { it.kind }
                .filter { it != WearNetworkChannelKind.NONE }
                .distinctUntilChanged()
                .drop(1)
                .collect { kind ->
                    val route = reconnectRoute() ?: return@collect
                    Timber.i("BroadcastEndpointWalkManager: network kind is now %s, resolving from the top", kind)
                    walk(route, fromTop = true, failure = WearStreamFailure.FAILED)
                }
        }
    }

    /** Replaces any walk in flight; the newest failure or reconnect is the one that decides what plays. */
    fun walk(route: WearBroadcastRoute, fromTop: Boolean, failure: WearStreamFailure) {
        Timber.d("S4610: broadcast endpoint walk fromTop=$fromTop after index ${route.endpointIndex}")
        job?.cancel()
        job = scope.launch {
            val next = walkEndpoints(route, fromTop)
            if (next == null) {
                Timber.i("BroadcastEndpointWalkManager: no endpoint after %s plays", route)
                onExhausted(failure)
            } else {
                onMoved(next)
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
