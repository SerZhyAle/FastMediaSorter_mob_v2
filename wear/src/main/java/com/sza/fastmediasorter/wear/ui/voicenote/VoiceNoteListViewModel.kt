package com.sza.fastmediasorter.wear.ui.voicenote

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.db.WearDatabaseResetNotice
import com.sza.fastmediasorter.wear.data.files.WearMediaStoreFileWriter
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.model.VoiceNote
import com.sza.fastmediasorter.wear.domain.model.VoiceNoteSendResult
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
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
    val resetNotice: WearDatabaseResetNotice.PendingReset? = null,
    /** S2495: the open action menu and what it may offer, or null while no menu is open. */
    val actions: VoiceNoteActions? = null,
    /** S2495: set when a rename left the note's two halves as they were, cleared once reported. */
    val lastRenameFailed: Boolean = false
)

/**
 * S2495: one note's action menu, resolved before the menu opens.
 *
 * The allowed set is carried rather than asked for during composition because answering it walks the
 * filesystem - the capability policy canonicalises the file's path to decide which storage class it
 * belongs to - and that is not a question a frame may ask.
 */
data class VoiceNoteActions(
    val note: VoiceNote,
    val file: WearMediaFile,
    val allowed: Set<WearFileOperationKind>
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
    private val sendVoiceNote: SendVoiceNoteUseCase,
    private val capabilityPolicy: WearFileCapabilityPolicy,
    private val mediaStoreWriter: WearMediaStoreFileWriter
) : ViewModel() {

    private val sendingNoteId = MutableStateFlow<Long?>(null)
    private val lastSendResult = MutableStateFlow<VoiceNoteSendResult?>(null)
    private val resetNotice = MutableStateFlow<WearDatabaseResetNotice.PendingReset?>(null)

    /**
     * The two pieces of state this screen owns itself, held as one value rather than two flows:
     * `combine` takes five sources and the note list already uses all five.
     */
    private val localState = MutableStateFlow(LocalState())

    private data class LocalState(
        val actions: VoiceNoteActions? = null,
        val renameFailed: Boolean = false
    )

    // S2356: read here rather than in the provider that wrote it, because consuming the notice is
    // what marks it seen, and this list is the surface strategic 3.3 chose to show it on. Off the
    // main thread: it is a SharedPreferences read on the path to the first frame.
    init {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = WearDatabaseResetNotice.consumePending(context)
            resetNotice.value = pending
        }
    }

    val uiState: StateFlow<VoiceNoteListUiState> = combine(
        noteRepository.observeNotes(),
        sendingNoteId,
        lastSendResult,
        resetNotice,
        localState
    ) { notes, sending, result, notice, local ->
        VoiceNoteListUiState(
            notes = notes,
            isLoading = false,
            sendingNoteId = sending,
            lastSendResult = result,
            resetNotice = notice,
            actions = local.actions,
            lastRenameFailed = local.renameFailed
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

    /**
     * S2495: resolves what the shared actions dialog may offer for [note], then opens it.
     *
     * The policy is asked rather than answered here, so a note offers exactly what an ordinary
     * app-owned file offers and cannot drift from it - strategic ADR-2's whole point. Off the main
     * thread because classifying a file canonicalises its path.
     */
    fun openActions(note: VoiceNote) {
        viewModelScope.launch(Dispatchers.IO) {
            val mapped = note.toMediaFile()
            val storageClass = capabilityPolicy.classify(mapped.file, isNetworkSource = false)
            val allowed = capabilityPolicy.allowedOperations(storageClass) - WITHHELD_OPERATIONS
            Timber.d("S2495: note actions for a %s note: %s", storageClass, allowed)
            localState.value = localState.value.copy(
                actions = VoiceNoteActions(note = note, file = mapped.file, allowed = allowed)
            )
        }
    }

    fun dismissActions() {
        localState.value = localState.value.copy(actions = null)
    }

    /**
     * S2495: renames both halves of the note, or leaves both as they were.
     *
     * The decision itself lives in [renameVoiceNote], which is where it can be exercised; this only
     * supplies the two collaborators and reports the answer to the screen.
     */
    fun rename(noteId: Long, newName: String) {
        viewModelScope.launch {
            val note = noteRepository.noteById(noteId) ?: return@launch
            val outcome = renameVoiceNote(
                note = note,
                newName = newName,
                renamePublished = { uri, name ->
                    mediaStoreWriter.rename(uri, name) == WearMediaStoreFileWriter.Result.Succeeded
                },
                renamePrivate = { name -> noteRepository.rename(noteId, name) != null }
            )
            Timber.d("S2495: rename of note %d ended as %s", noteId, outcome)
            if (outcome != VoiceNoteRenameOutcome.SUCCEEDED) {
                Timber.w("Rename of note %d ended as %s; both halves keep the old name", noteId, outcome)
                localState.value = localState.value.copy(renameFailed = true)
            }
        }
    }

    fun acknowledgeRenameFailure() {
        localState.value = localState.value.copy(renameFailed = false)
    }

    fun acknowledgeSendResult() {
        lastSendResult.value = null
    }

    /** The record was already cleared on read; this only takes the dialog off the screen. */
    fun acknowledgeResetNotice() {
        resetNotice.value = null
    }

    private companion object {
        /**
         * The two operations the policy allows an app-owned file that this screen cannot yet serve.
         *
         * Withheld rather than offered and refused, which is the rule the capability policy already
         * applies to every unavailable action: an entry that ends in a refusal is the trust failure
         * the policy exists to prevent. Moving to the phone needs the send and the source removal
         * sequenced behind one result, and sending to a receiver needs the receiver picker and the
         * batch run state - both live in the browse screen's operations manager, which this list does
         * not bind. The three a note does offer - send, rename, delete - are served here directly, so
         * a note's delivery state and its two halves stay the note list's own business.
         */
        val WITHHELD_OPERATIONS = setOf(
            WearFileOperationKind.MOVE_TO_PHONE,
            WearFileOperationKind.SEND_TO_RECEIVER
        )
    }
}
