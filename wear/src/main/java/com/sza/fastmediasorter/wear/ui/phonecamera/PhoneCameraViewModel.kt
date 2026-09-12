package com.sza.fastmediasorter.wear.ui.phonecamera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.wear.CameraSessionCommandSender
import com.sza.fastmediasorter.wear.domain.model.CameraLensDto
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraFailure
import com.sza.fastmediasorter.wear.domain.model.PhoneCameraSessionState
import com.sza.fastmediasorter.wear.domain.model.StreamChannelReason
import com.sza.fastmediasorter.wear.domain.model.StreamChannelVerdict
import com.sza.fastmediasorter.wear.domain.model.WearStreamPlaybackTarget
import com.sza.fastmediasorter.wear.domain.repository.PhoneCameraSessionHolder
import com.sza.fastmediasorter.wear.domain.usecase.EvaluateStreamStartUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PrepareEphemeralStreamPlaybackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the phone-camera screen draws.
 *
 * [playbackTarget] is separate from the state rather than a case of it: the screen navigates once on
 * it, and folding it into the state would re-fire the navigation on every recomposition that
 * re-read the same Live value.
 */
data class PhoneCameraUiState(
    val session: PhoneCameraSessionState = PhoneCameraSessionState.Idle,
    val playbackTarget: WearStreamPlaybackTarget? = null,
)

/**
 * S2551: the watch's side of a phone camera session - ask, watch, switch lens, stop.
 *
 * The link is judged BEFORE anything is sent. Strategic criterion 4 forbids the endless load, and a
 * watch on Bluetooth that asked first would open a camera on the phone that nothing here could ever
 * reach - the phone would serve, the owner would be shown a spinner, and the only visible symptom
 * would be a warm phone.
 */
@HiltViewModel
class PhoneCameraViewModel @Inject constructor(
    private val holder: PhoneCameraSessionHolder,
    private val sender: CameraSessionCommandSender,
    private val evaluateStreamStart: EvaluateStreamStartUseCase,
    private val prepareEphemeralPlayback: PrepareEphemeralStreamPlaybackUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(PhoneCameraUiState())
    val state: StateFlow<PhoneCameraUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            holder.state.collect { session -> _state.value = uiStateFor(session) }
        }
    }

    fun start() {
        val verdict = evaluateStreamStart.forPhoneCamera()
        if (verdict is StreamChannelVerdict.Refuse) {
            // Refused locally, so nothing is sent: the phone must not open a camera for a watch that
            // has already established it cannot reach the address the phone would answer with.
            holder.markRefused(verdict.reason.asSessionFailure())
            return
        }
        sender.start()
    }

    fun switchLens(lensId: String) {
        sender.switchLens(lensId)
    }

    fun stop() {
        sender.stop()
    }

    /** Leaving the screen ends the session, which is strategic criterion 3 read literally. */
    override fun onCleared() {
        if (holder.state.value is PhoneCameraSessionState.Live) {
            sender.stop()
        }
        super.onCleared()
    }

    private fun uiStateFor(session: PhoneCameraSessionState): PhoneCameraUiState =
        PhoneCameraUiState(
            session = session,
            playbackTarget = (session as? PhoneCameraSessionState.Live)?.let(::prepareTargetFor)
        )

    private fun prepareTargetFor(live: PhoneCameraSessionState.Live): WearStreamPlaybackTarget =
        prepareEphemeralPlayback(url = live.url, title = titleOf(live.lenses, live.activeLensId))

    /**
     * The live lens names the session, so the player's title changes with the lens rather than
     * reading "phone camera" through every switch.
     */
    private fun titleOf(lenses: List<CameraLensDto>, activeLensId: String?): String =
        lenses.firstOrNull { it.id == activeLensId }?.labelKey ?: PHONE_CAMERA_TITLE

    private companion object {
        const val PHONE_CAMERA_TITLE = "phone_camera"
    }
}

/**
 * The link verdict as this screen states it.
 *
 * `NOT_ON_WIFI` stays distinct from the narrow-link reason all the way to the words: strategic
 * criterion 4 requires the owner to be told to turn Wi-Fi on, not that the channel is too slow.
 */
private fun StreamChannelReason.asSessionFailure(): PhoneCameraFailure = when (this) {
    StreamChannelReason.NOT_ON_WIFI -> PhoneCameraFailure.NOT_ON_WIFI
    StreamChannelReason.NO_LINK -> PhoneCameraFailure.NO_NETWORK
    StreamChannelReason.NARROW_LINK -> PhoneCameraFailure.NARROW_LINK
    StreamChannelReason.BANDWIDTH_UNKNOWN, StreamChannelReason.UNVALIDATED_LINK ->
        PhoneCameraFailure.UNKNOWN
}
