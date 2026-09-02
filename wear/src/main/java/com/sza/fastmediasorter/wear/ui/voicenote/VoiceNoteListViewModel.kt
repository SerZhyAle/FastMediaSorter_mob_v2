package com.sza.fastmediasorter.wear.ui.voicenote

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.db.WearDatabaseResetNotice
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendResult
import com.sza.fastmediasorter.wear.domain.repository.VoiceNoteRepository
import com.sza.fastmediasorter.wear.domain.usecase.SendVoiceNoteUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** The module's convention for a screen-scoped flow: outlive a rotation, not a departure. */
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/**
 * S1862: the note list as the screen renders it.
 *
 * [isLoading] separates "the store has not answered yet" from "there is nothing here", so the empty
 * invitation is never shown over a list that is about to arrive.
 */
data class VoiceNoteListUiState(
    val notes: List<VoiceNote> = emptyList(),
    val isLoading: Boolean = true,
    val sendingNoteId: Long? = null,
    val lastSendResult: VoiceNoteSendResult? = null,
    /** S2356: set once after a database recovery, cleared as soon as the user has seen it. */
    val resetNotice: WearDatabaseResetNotice.PendingReset? = null
)

/**
 * S1862: every decision about a note goes through here, never through a composable.
 *
 * Delivery itself was settled in phase 02 - this holds no policy of its own. Manual sending is
 * offered under both policies on purpose: under MANUAL it is the only way out, and under AUTOMATIC
 * it is what a user reaches for when a note has been sitting in FAILED.
 */
@HiltViewModel
class VoiceNoteListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val noteRepository: VoiceNoteRepository,
    private val sendVoiceNote: SendVoiceNoteUseCase
) : ViewModel() {

    private val sendingNoteId = MutableStateFlow<Long?>(null)
    private val lastSendResult = MutableStateFlow<VoiceNoteSendResult?>(null)
    private val resetNotice = MutableStateFlow<WearDatabaseResetNotice.PendingReset?>(null)

    // S2356: read here rather than in the provider that wrote it, because consuming the notice is
    // what marks it seen, and this list is the surface strategic 3.3 chose to show it on. Off the
    // main thread: it is a SharedPreferences read on the path to the first frame.
    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = WearDatabaseResetNotice.consumePending(context)
            Timber.d("S2356: note list consumed reset notice: %s", pending ?: "none pending")
            resetNotice.value = pending
        }
    }

    val uiState: StateFlow<VoiceNoteListUiState> = combine(
        noteRepository.observeNotes(),
        sendingNoteId,
        lastSendResult,
        resetNotice
    ) { notes, sending, result, notice ->
        VoiceNoteListUiState(
            notes = notes,
            isLoading = false,
            sendingNoteId = sending,
            lastSendResult = result,
            resetNotice = notice
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = VoiceNoteListUiState()
    )

    /**
     * One transfer at a time: the bridge is one link for every note, so a second request started
     * while the first is in flight would queue behind it anyway and leave two rows both claiming to
     * be sending.
     */
    fun send(noteId: Long) {
        Timber.d("S1862: manual send requested for note %d", noteId)
        if (sendingNoteId.value != null) {
            Timber.i("Ignoring a send for note %d: another transfer is still running", noteId)
            return
        }
        sendingNoteId.value = noteId
        viewModelScope.launch {
            val result = sendVoiceNote(noteId)
            sendingNoteId.value = null
            lastSendResult.value = result
        }
    }

    fun delete(noteId: Long) {
        viewModelScope.launch { noteRepository.delete(noteId) }
    }

    fun acknowledgeSendResult() {
        lastSendResult.value = null
    }

    /** The record was already cleared on read; this only takes the dialog off the screen. */
    fun acknowledgeResetNotice() {
        resetNotice.value = null
    }
}
