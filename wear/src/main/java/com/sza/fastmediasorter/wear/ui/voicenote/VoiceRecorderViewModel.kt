package com.sza.fastmediasorter.wear.ui.voicenote

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingState
import com.sza.fastmediasorter.wear.domain.recorder.VoiceRecordingStateHolder
import com.sza.fastmediasorter.wear.domain.repository.VoiceNoteRepository
import com.sza.fastmediasorter.wear.service.VoiceRecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The module's convention for a screen-scoped flow: outlive a rotation, not a departure. */
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * S1862: what the recorder screen renders, assembled from the service's state and the free space
 * the repository reports.
 *
 * [hasRoomToRecord] starts true so the first frame does not accuse a healthy watch of being full;
 * the real answer arrives with the first emission, before the button can be pressed.
 */
data class VoiceRecorderUiState(
    val recording: VoiceRecordingState = VoiceRecordingState.Idle,
    val hasRoomToRecord: Boolean = true
)

/**
 * S1862: the recorder screen's only route to the session.
 *
 * The screen never binds to [VoiceRecordingService] (ADR-4) - it starts and stops it by intent and
 * reads its state through the application-scoped holder, so a screen going dark cannot end a
 * recording that is still being spoken into.
 */
@HiltViewModel
class VoiceRecorderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateHolder: VoiceRecordingStateHolder,
    private val noteRepository: VoiceNoteRepository
) : ViewModel() {

    /**
     * Free space is re-read when a session opens or closes, not on every published state: the
     * service republishes Recording twice a second to move the elapsed figure, and asking the
     * filesystem at that rate buys nothing, because the answer can only change across a recording.
     */
    private val roomToRecord: Flow<Boolean> = stateHolder.state
        .map { it is VoiceRecordingState.Recording }
        .distinctUntilChanged()
        .map { noteRepository.hasRoomToRecord() }

    val uiState: StateFlow<VoiceRecorderUiState> =
        combine(stateHolder.state, roomToRecord) { recording, hasRoom ->
            VoiceRecorderUiState(recording = recording, hasRoomToRecord = hasRoom)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = VoiceRecorderUiState()
        )

    fun startRecording() {
        // The holder is application-scoped, so a previous failure is still the current state when
        // the screen is reopened. Clearing it here keeps the old reason from sitting over a new
        // attempt for as long as the service takes to publish its own.
        if (stateHolder.state.value is VoiceRecordingState.Error) {
            stateHolder.publish(VoiceRecordingState.Idle)
        }
        ContextCompat.startForegroundService(context, VoiceRecordingService.startIntent(context))
    }

    /**
     * Plain `startService`, unlike the start above: stopping ends with the service stopping itself
     * without ever calling `startForeground`, and `startForegroundService` promises the platform
     * that it will - a promise the stop path cannot keep. The screen only offers Stop while a
     * session is open, so the app is in the foreground and this call is allowed.
     */
    fun stopRecording() {
        context.startService(VoiceRecordingService.stopIntent(context))
    }
}
