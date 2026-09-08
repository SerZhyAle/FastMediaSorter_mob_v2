package com.sza.fastmediasorter.wear.domain.listen

import com.sza.fastmediasorter.wear.domain.model.LiveAudioEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** S2550: what the listening session is doing, as the confirmation screen is allowed to see it. */
sealed interface ListenSessionState {

    /** No session. Also the state a declined, expired or finished session returns to. */
    data object Idle : ListenSessionState

    /** The confirmation was given and the microphone is opening; the address is not known yet. */
    data object Starting : ListenSessionState

    /** The microphone is live and [endpoint] is where the phone can hear it. */
    data class Live(val endpoint: LiveAudioEndpoint) : ListenSessionState

    /** The microphone or the server refused to start; nothing is running and nothing is served. */
    data object Failed : ListenSessionState

    /**
     * True while something is actually running - the microphone, the server, or the moment between.
     *
     * The distinction that matters is against [Failed], which is deliberately sticky so the screen
     * can still say what went wrong: read as "not idle" it would make a watch that once failed refuse
     * every later request as busy and try to stop a service that is not there.
     */
    val isActive: Boolean
        get() = this is Starting || this is Live
}

/**
 * The single application-scoped publisher of [ListenSessionState].
 *
 * The service writes and both the confirmation screen and the Data Layer answer read, which is why
 * none of them meet: the screen must be free to go dark without ending a session ADR-4 keeps in a
 * foreground service, and Phase 04 answers the phone with the endpoint published here rather than by
 * reaching into the service that produced it.
 */
class ListenSessionStateHolder {

    private val mutableState = MutableStateFlow<ListenSessionState>(ListenSessionState.Idle)

    val state: StateFlow<ListenSessionState> = mutableState.asStateFlow()

    fun publish(next: ListenSessionState) {
        mutableState.value = next
    }
}
