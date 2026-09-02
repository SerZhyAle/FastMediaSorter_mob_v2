package com.sza.fastmediasorter.wear.domain.recorder

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** S1862: what the microphone session is doing, as the screen is allowed to see it. */
sealed interface VoiceRecordingState {

    /** No session. Also the state a finished recording returns to once the note is stored. */
    data object Idle : VoiceRecordingState

    /**
     * [startedAtMillis] is wall clock, for showing when the note began; [elapsedMillis] is measured
     * monotonically by the service, because a wall clock can be stepped by the network mid-recording
     * and the elapsed figure would then jump on screen.
     */
    data class Recording(val startedAtMillis: Long, val elapsedMillis: Long) : VoiceRecordingState

    /** Stop was asked for and the file is being closed. Brief, but a distinct state so the screen
     * can refuse a second stop instead of racing the first one. */
    data object Finishing : VoiceRecordingState

    data class Error(val reason: VoiceRecordingErrorReason) : VoiceRecordingState
}

/**
 * A reason rather than a message: section 3.2 requires every state to be distinguishable without
 * colour and reachable by TalkBack, which means the screen picks the localized wording. A string
 * built in the service could not be localized by the screen that displays it.
 */
enum class VoiceRecordingErrorReason {

    /** RECORD_AUDIO is not granted, or was revoked while the service was starting. */
    PERMISSION_DENIED,

    /** The recording directory is below the headroom the repository requires. */
    NO_FREE_SPACE,

    /** The microphone was refused - held by another app, or the codec would not start. */
    RECORDER_UNAVAILABLE,

    /** The session ran but produced nothing usable, so no note was stored. */
    NOTHING_RECORDED
}

/**
 * The single application-scoped publisher of [VoiceRecordingState].
 *
 * The service writes and the screen reads, which is why they never meet: a screen holding a binder
 * to the recording service would keep the session tied to the screen's lifetime, and ADR-4 exists
 * precisely because a watch screen dies when the wrist drops.
 */
class VoiceRecordingStateHolder {

    private val mutableState = MutableStateFlow<VoiceRecordingState>(VoiceRecordingState.Idle)

    val state: StateFlow<VoiceRecordingState> = mutableState.asStateFlow()

    fun publish(next: VoiceRecordingState) {
        mutableState.value = next
    }
}
