package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.PhoneCameraFailure
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2551: where the phone's answer lands, since it does not arrive at the screen.
 *
 * The ack is delivered to a `WearableListenerService`, which the system starts and stops on its own
 * and which outlives every screen - so the listener and the screen need a third place both can
 * reach. The phone solves the mirror problem the same way in S2550.
 *
 * It touches no Data Layer type: this is state, and sending is the sender's job.
 */
@Singleton
class PhoneCameraSessionHolder @Inject constructor() {

    private val _state = MutableStateFlow<PhoneCameraSessionState>(PhoneCameraSessionState.Idle)
    val state: StateFlow<PhoneCameraSessionState> = _state.asStateFlow()

    /**
     * The id of the command still waiting for its answer, or null when nothing is outstanding.
     *
     * The listener drops an ack that names anything else. Only one session exists at a time, so a
     * mismatch means the answer belongs to a request the owner already walked away from, and acting
     * on it opens a player onto an address that has since closed.
     */
    val awaitingRequestId: String?
        get() = (_state.value as? PhoneCameraSessionState.Requested)?.requestId

    /**
     * S3223: the id of the session being served right now, or null when none is.
     *
     * The phone names this id when it announces on its own that the broadcast is over, and the
     * listener has nothing else to tie that announcement to: [awaitingRequestId] is null the moment
     * the session goes live.
     */
    val liveRequestId: String?
        get() = (_state.value as? PhoneCameraSessionState.Live)?.requestId

    fun markRequested(requestId: String) {
        _state.value = PhoneCameraSessionState.Requested(requestId)
    }

    fun markLive(live: PhoneCameraSessionState.Live) {
        _state.value = live
    }

    fun markRefused(reason: PhoneCameraFailure) {
        _state.value = PhoneCameraSessionState.Refused(reason)
    }

    /** Back to the entrance, so a screen reopened after a refusal does not show yesterday's reason. */
    fun reset() {
        _state.value = PhoneCameraSessionState.Idle
    }
}
