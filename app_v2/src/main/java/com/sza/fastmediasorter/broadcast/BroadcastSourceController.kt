package com.sza.fastmediasorter.broadcast

import android.content.Intent
import android.net.Uri
import com.sza.fastmediasorter.data.broadcast.BroadcastDescriptorDto
import kotlinx.coroutines.flow.StateFlow

/**
 * The UI-side name of a broadcast source (LIVE-BROADCAST 0.21 item N). It never travels on the
 * wire - a source is not a kind, and the descriptor carries no member for it - so the control
 * screen is the only reader, and it uses the one value that hides capture-only controls.
 */
const val BROADCAST_SOURCE_FILE = "file"

/** S4233: the same UI-side name for a session serving the phone's own screen. */
const val BROADCAST_SOURCE_SCREEN = "screen"

/**
 * Why a broadcast could not start or could not continue. Kept as a closed set so the UI can say
 * what went wrong in the user's language - the technical detail alone is untranslatable log text.
 */
enum class BroadcastFailure {
    MICROPHONE_PERMISSION,
    NETWORK_UNAVAILABLE,
    ENCODER_UNAVAILABLE,
    CAPTURE_ERROR,

    // S4234: the picked file cannot be opened, or carries no track the pipeline can serve.
    FILE_UNREADABLE,
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
        /**
         * [BROADCAST_SOURCE_FILE] for a session serving a stored file, [BROADCAST_SOURCE_SCREEN] for one
         * serving the screen; null for a camera or microphone session.
         */
        val source: String? = null,
        /** S4281: whether the lens on air has a flash the torch button can light. */
        val torchAvailable: Boolean = false,
        /** S4281: whether that flash is lit right now, as the camera library reports it. */
        val torchEnabled: Boolean = false,
    ) : BroadcastState
    data class Failed(val failure: BroadcastFailure, val detail: String) : BroadcastState
}

/**
 * Controller managing broadcast session lifecycle (capturing audio/video and serving it over network).
 */
interface BroadcastSourceController {
    val isAvailable: Boolean

    /**
     * Whether a video broadcast keeps its camera after the app leaves the screen. Only a build that
     * declares the camera foreground-service type can; elsewhere the session lives while the app is visible.
     */
    val cameraSurvivesBackground: Boolean get() = false

    val state: StateFlow<BroadcastState>

    // S3038: live count of connected listeners, published by the server.
    val listenerCount: StateFlow<Int>

    /**
     * S3349: true while the acoustic-feedback guard is holding the microphone gain down. The screen
     * shows it so a dip the guard caused does not read as a broken microphone.
     */
    val feedbackSuppressed: StateFlow<Boolean>

    /** [lensId] is a [BroadcastLensOption.id]; null opens the phone's main back lens. Ignored by AUDIO_ONLY. */
    fun start(mode: BroadcastMode, lensId: String? = null)

    /**
     * S4234: starts a live broadcast of the stored file behind the content [uri]. The shape follows
     * the file's own tracks - music through the AUDIO_ONLY path, a film through the RTSP path - so
     * the descriptor stays the one every receiver already knows (LIVE-BROADCAST 0.21 item N). Any
     * live session stops first: the services are exclusive. No-op in builds without capture.
     */
    fun startFile(uri: Uri)

    /**
     * S4233: starts a live broadcast of the phone's screen from the platform consent result the caller
     * just obtained ([resultCode] and [data] of `createScreenCaptureIntent()`). The consent is good for
     * this one session: the broadcast ends when the platform stops the projection. Any live session
     * stops first. No-op in builds without capture.
     */
    fun startScreen(resultCode: Int, data: Intent)

    fun stop()

    // S3038: toggle camera and microphone during a live broadcast.
    fun toggleCamera()
    fun toggleMicrophone()

    /** S4281: lights or puts out the flash of the lens on air. No-op without a live video session or a flash. */
    fun toggleTorch() = Unit

    /** Switches a live video broadcast to any lens the phone offers, sub-lenses included. */
    fun selectLens(lensId: String)

    /**
     * Re-opens a live session in [mode] without the user stopping it. The transport or the SDP changes
     * with the mode, so every viewer reopens the stream by the new descriptor. No-op when idle or when
     * [mode] is already on air, and in every build that has no capture to re-open.
     */
    fun switchMode(mode: BroadcastMode, lensId: String?) = Unit

    /**
     * Returns a failed session to idle once the UI has reported it, so a rotation or a return to the
     * screen does not replay a message the user already saw and the next start begins from a clean state.
     */
    fun acknowledgeFailure()
}
