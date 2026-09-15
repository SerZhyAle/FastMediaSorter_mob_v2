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
        // S3038: current toggle state so the UI reflects whether camera and microphone are active.
        val cameraEnabled: Boolean = false,
        val microphoneEnabled: Boolean = true,
        /** [BroadcastLensOption.id] of the lens on air; null for audio-only sessions. */
        val activeLensId: String? = null,
    ) : BroadcastState
    data class Failed(val failure: BroadcastFailure, val detail: String) : BroadcastState
}

/**
 * Controller managing broadcast session lifecycle (capturing audio/video and serving it over network).
 */
interface BroadcastSourceController {
    val isAvailable: Boolean
    val state: StateFlow<BroadcastState>

    // S3038: live count of connected listeners, published by the server.
    val listenerCount: StateFlow<Int>

    /** [lensId] is a [BroadcastLensOption.id]; null opens the phone's main back lens. Ignored by AUDIO_ONLY. */
    fun start(mode: BroadcastMode, lensId: String? = null)
    fun stop()

    // S3038: toggle camera and microphone during a live broadcast.
    fun toggleCamera()
    fun toggleMicrophone()

    /** Switches a live video broadcast to any lens the phone offers, sub-lenses included. */
    fun selectLens(lensId: String)

    /**
     * Returns a failed session to idle once the UI has reported it, so a rotation or a return to the
     * screen does not replay a message the user already saw and the next start begins from a clean state.
     */
    fun acknowledgeFailure()
}
