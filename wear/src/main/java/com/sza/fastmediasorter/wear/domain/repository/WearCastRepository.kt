package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearCastOutcome
import com.sza.fastmediasorter.wear.domain.model.WearCastRequest
import com.sza.fastmediasorter.wear.domain.model.WearCastState
import kotlinx.coroutines.flow.StateFlow

/**
 * S2531: the watch's end of the cast bridge - it names the content, the phone owns the session.
 */
interface WearCastRepository {

    /**
     * The phone's cast session as the phone last reported it.
     *
     * Never this watch's own guess: the phone is the single owner of the session, so a value here is
     * always something that was received, and a stale one is corrected by the next report rather than
     * by the watch inferring anything (strategic ADR-1).
     */
    val castState: StateFlow<WearCastState>

    /** Sends [request] and waits for the phone's answer, timing out into [WearCastOutcome.PHONE_BUSY]. */
    suspend fun requestCast(request: WearCastRequest): WearCastOutcome

    /** Asks the phone to end the session it is running. */
    suspend fun requestStop(): WearCastOutcome

    /** Feeds one acknowledgement received on the bridge back to the waiting [requestCast]. */
    fun onAckReceived(payload: ByteArray)

    /** Publishes one session state received on the bridge into [castState]. */
    fun onStateReceived(payload: ByteArray)
}
