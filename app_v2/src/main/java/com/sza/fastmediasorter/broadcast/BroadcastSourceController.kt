package com.sza.fastmediasorter.broadcast

import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import kotlinx.coroutines.flow.StateFlow

/**
 * Why a broadcast could not start or could not continue. Kept as a closed set so the UI can say
 * what went wrong in the user's language - the technical detail alone is untranslatable log text.
 */
enum class BroadcastFailure {
    MICROPHONE_PERMISSION,
    NETWORK_UNAVAILABLE,
    ENCODER_UNAVAILABLE,
    CAPTURE_ERROR,
}

sealed interface BroadcastState {
    data object Idle : BroadcastState

    /**
     * [startedAtElapsedRealtimeMs] is `SystemClock.elapsedRealtime()` taken when the session went live.
     * It travels with the session rather than with the screen: the main screen stops and restarts while
     * the broadcast keeps running, so a screen-local clock would restart the elapsed time on every return.
     */
    data class Live(
        val descriptor: BroadcastDescriptorDto,
        val startedAtElapsedRealtimeMs: Long,
    ) : BroadcastState
    data class Failed(val failure: BroadcastFailure, val detail: String) : BroadcastState
}

/**
 * Controller managing broadcast session lifecycle (capturing audio/video and serving it over network).
 */
interface BroadcastSourceController {
    val isAvailable: Boolean
    val state: StateFlow<BroadcastState>

    fun start(mode: BroadcastMode)
    fun stop()

    /**
     * Returns a failed session to idle once the UI has reported it, so a rotation or a return to the
     * screen does not replay a message the user already saw and the next start begins from a clean state.
     */
    fun acknowledgeFailure()
}
