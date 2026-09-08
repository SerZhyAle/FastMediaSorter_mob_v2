package com.sza.fastmediasorter.wear.domain.broadcast

import com.sza.fastmediasorter.wear.domain.model.LiveAudioEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** S2509: why a broadcast could not start, in the terms the screen has to explain it in. */
enum class WearBroadcastFailure {

    /** No Wi-Fi the watch could bind a server to, so there is no address a listener could reach. */
    NO_USABLE_NETWORK,

    /** The microphone or the encoder refused to open; nothing is being captured. */
    CAPTURE_FAILED
}

/**
 * S2509: what the watch's own broadcast is doing.
 *
 * Deliberately separate from `ListenSessionState` rather than a mode of it. Both flows open the one
 * physical microphone, so sharing a holder would let the paired-phone listening session and an
 * owner-started broadcast overwrite each other's state and leave the screen describing a session that
 * belongs to the other feature. Keeping them apart makes the collision visible where it is decided -
 * in the service, which refuses the second start - instead of silent in a shared field.
 */
sealed interface WearBroadcastSessionState {

    /** No broadcast. Also where a stopped or acknowledged failed session returns to. */
    data object Idle : WearBroadcastSessionState

    /** The owner started it; the microphone, the network and the server are being taken. */
    data object Starting : WearBroadcastSessionState

    /**
     * On air. [endpoint] is where listeners connect and [descriptorJson] / [descriptorQrPayload] are
     * the two published forms of that address, built once here rather than at each surface that shows
     * one - the QR screen and the paired-phone relay must never encode two different addresses.
     */
    data class Live(
        val endpoint: LiveAudioEndpoint,
        val descriptorJson: String,
        val descriptorQrPayload: String
    ) : WearBroadcastSessionState

    /**
     * The start failed and [reason] says how. Sticky until the owner leaves or retries: cleared on its
     * own it would return the screen to an inviting Idle with no word that the microphone had refused.
     */
    data class Failed(val reason: WearBroadcastFailure) : WearBroadcastSessionState

    /** True while something is actually running - capture, the server, or the moment in between. */
    val isActive: Boolean
        get() = this is Starting || this is Live
}

/**
 * The single application-scoped publisher of [WearBroadcastSessionState].
 *
 * The foreground service writes and the control screen reads, and they never meet: strategic goal 3
 * lets the owner leave the screen while the broadcast continues, so a state owned by that screen's
 * ViewModel would die exactly when the session it describes is still on air.
 */
class WearBroadcastSessionStateHolder {

    private val mutableState = MutableStateFlow<WearBroadcastSessionState>(
        WearBroadcastSessionState.Idle
    )

    val state: StateFlow<WearBroadcastSessionState> = mutableState.asStateFlow()

    fun publish(next: WearBroadcastSessionState) {
        mutableState.value = next
    }
}
