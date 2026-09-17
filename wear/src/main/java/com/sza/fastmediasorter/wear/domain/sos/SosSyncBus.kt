package com.sza.fastmediasorter.wear.domain.sos

import com.sza.fastmediasorter.wear.domain.model.SosMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S3216: carries the paired phone's two SOS commands from the Data Layer listener to the screen.
 *
 * The listener is a service and the screen is a composable, so they cannot call each other. A route
 * argument would carry the start but not the stop: a stop arrives while the screen is already open and
 * has to reach it, and navigating to a route again is not a way to say "leave".
 *
 * In the DOMAIN branch rather than beside the screen that reads it: the listener is data-layer code, and a
 * data class importing a screen type inverts the layer arrow (S2751, gate `wear-data-imports-ui`). What
 * travels through here is two commands and a mode, none of which is a drawing concern.
 *
 * [pendingStartMode] is state rather than an event because the order is not guaranteed - the command can
 * land before `MainActivity` has drawn the destination it asked for - so the screen reads whatever is
 * waiting when it appears and clears it. [stopRequests] is an event: there is nothing to replay, and a
 * stop nobody was listening for has already been honoured by the screen not being open.
 */
@Singleton
class SosSyncBus @Inject constructor() {

    private val pending = MutableStateFlow<SosMode?>(null)
    private val stops = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val pendingStartMode: StateFlow<SosMode?> = pending.asStateFlow()

    val stopRequests: SharedFlow<Unit> = stops.asSharedFlow()

    /** Records the mode the phone asked for, for the screen to adopt as it appears. */
    fun requestStart(mode: SosMode) {
        pending.value = mode
    }

    /** Clears the pending mode once a screen has taken it, so a later visit does not re-arm itself. */
    fun consumeStart() {
        pending.value = null
    }

    /** Asks a running signal to end. Dropped when nothing is listening, which is the correct answer. */
    fun requestStop() {
        pending.value = null
        stops.tryEmit(Unit)
    }
}
