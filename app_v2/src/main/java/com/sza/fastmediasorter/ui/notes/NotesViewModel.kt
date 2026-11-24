package com.sza.fastmediasorter.ui.notes

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.exchange.NotesSyncTriggerManager
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.notes.NoteSummary
import com.sza.fastmediasorter.domain.model.notes.NotesSyncOutcome
import com.sza.fastmediasorter.domain.usecase.CreateTextNoteUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.GetSettingsSnapshotUseCase
import com.sza.fastmediasorter.domain.usecase.notes.DeleteNoteUseCase
import com.sza.fastmediasorter.domain.usecase.notes.EnsureNotesResourceUseCase
import com.sza.fastmediasorter.domain.usecase.notes.ListNotesUseCase
import com.sza.fastmediasorter.domain.usecase.notes.SyncNotesThroughExchangeUseCase
import com.sza.fastmediasorter.util.NotesFolderPolicy
import com.sza.fastmediasorter.util.TextNoteFileNameProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

data class NotesUiState(
    val resourceId: Long? = null,
    val query: String = "",
    val notes: List<NoteSummary> = emptyList(),
    val loading: Boolean = true,
    val sync: NotesSyncStatus = NotesSyncStatus.Off,
    /** File names of the notes above the sync payload limit, marked on their rows. */
    val tooLarge: Set<String> = emptySet(),
    /** S4865: paths of the selected notes; the selection mode is on exactly while this is not empty. */
    val selected: Set<String> = emptySet(),
)

/** S4572: the notes sync as the Notes screen shows it; [Off] hides "Sync now" and the status line. */
sealed interface NotesSyncStatus {
    data object Off : NotesSyncStatus
    data object Syncing : NotesSyncStatus
    data class Synced(val host: String) : NotesSyncStatus
    data object Failed : NotesSyncStatus
    data object ServerNoE2e : NotesSyncStatus
    data object LimitReached : NotesSyncStatus
}

sealed interface NotesEvent {
    data class OpenEditor(val path: String, val resourceId: Long) : NotesEvent
    data class ShowError(val kind: NotesError) : NotesEvent
    data class ShowSyncMessage(@StringRes val text: Int) : NotesEvent
}

enum class NotesError { FOLDER_UNAVAILABLE, DELETE_FAILED, DELETE_SELECTED_FAILED }

/**
 * S4568: state of the Notes screen.
 *
 * Every note operation goes through a use case; the screen only renders [state] and replays [events].
 * Creating a note reuses the Browse create flow against the notes resource, so the editor gets the same
 * deferred-create contract it has there.
 */
@HiltViewModel
class NotesViewModel @Inject constructor(
    private val ensureNotesResource: EnsureNotesResourceUseCase,
    private val listNotes: ListNotesUseCase,
    private val deleteNote: DeleteNoteUseCase,
    private val createTextNote: CreateTextNoteUseCase,
    private val getResources: GetResourcesUseCase,
    private val getSettingsSnapshot: GetSettingsSnapshotUseCase,
    private val syncNotes: SyncNotesThroughExchangeUseCase,
    syncTriggers: NotesSyncTriggerManager,
) : ViewModel() {

    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state.asStateFlow()

    private val _events = Channel<NotesEvent>(Channel.BUFFERED)
    val events: Flow<NotesEvent> = _events.receiveAsFlow()

    private var reloadJob: Job? = null
    private var syncJob: Job? = null

    init {
        // A sync the connect or a `notes_changed` started may write files while this screen is open.
        viewModelScope.launch { syncTriggers.synced.collect { reload() } }
    }

    /**
     * Opening the screen and returning from the editor are the moments a note may have changed (DEVICE-EXCHANGE
     * 1.13 item FM), so each one also runs the notes sync.
     *
     * @param resourceName the label a freshly created notes resource carries in the resource list.
     */
    fun start(resourceName: String) {
        if (_state.value.resourceId != null) {
            reload()
            syncNow()
            return
        }
        viewModelScope.launch {
            ensureNotesResource(resourceName)
                .onSuccess { outcome ->
                    _state.update { it.copy(resourceId = outcome.resourceId) }
                    reload()
                    syncNow()
                }
                .onFailure { error ->
                    Timber.e(error, "NotesViewModel: notes resource unavailable")
                    _state.update { it.copy(loading = false) }
                    _events.send(NotesEvent.ShowError(NotesError.FOLDER_UNAVAILABLE))
                }
        }
    }

    fun onQueryChanged(query: String) {
        if (query == _state.value.query) return
        _state.update { it.copy(query = query) }
        reload()
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            val notes = listNotes(_state.value.query)
            // A note the list no longer shows (deleted, synced away, filtered out) must not stay selected,
            // or Delete selected would reach a note the user cannot see.
            val shown = notes.mapTo(HashSet()) { it.path }
            _state.update { state ->
                state.copy(
                    notes = notes,
                    loading = false,
                    selected = state.selected.filterTo(HashSet()) { path -> path in shown },
                )
            }
        }
    }

    fun toggleSelection(note: NoteSummary) {
        if (_state.value.selected.isEmpty()) {
            Timber.d("S4865: selection mode entered")
        }
        _state.update { state ->
            val next = if (note.path in state.selected) state.selected - note.path else state.selected + note.path
            state.copy(selected = next)
        }
    }

    fun selectAll() {
        _state.update { state -> state.copy(selected = state.notes.mapTo(LinkedHashSet()) { it.path }) }
    }

    fun clearSelection() {
        _state.update { it.copy(selected = emptySet()) }
    }

    fun deleteSelected() {
        val paths = _state.value.selected.toList()
        if (paths.isEmpty()) return
        Timber.d("S4865: delete of ${paths.size} selected notes confirmed")
        viewModelScope.launch {
            val failed = deleteNote.deleteAll(paths)
            _state.update { it.copy(selected = emptySet()) }
            reload()
            // One sync for the whole batch: it reads the folder afresh, so every delete reaches the other devices.
            syncNow()
            if (failed > 0) {
                Timber.e("NotesViewModel: $failed of ${paths.size} notes could not be deleted")
                _events.send(NotesEvent.ShowError(NotesError.DELETE_SELECTED_FAILED))
            }
        }
    }

    /** A sync already running is not restarted: it reads the folder afresh for every note it sends. */
    fun syncNow() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            _state.update { if (it.sync == NotesSyncStatus.Off) it else it.copy(sync = NotesSyncStatus.Syncing) }
            val outcome = try {
                syncNotes()
            } catch (e: IOException) {
                Timber.w(e, "NotesViewModel: notes sync failed")
                null
            }
            applySync(outcome)
            reload()
        }
    }

    private suspend fun applySync(outcome: NotesSyncOutcome?) {
        val status = when (outcome) {
            NotesSyncOutcome.Off -> NotesSyncStatus.Off
            NotesSyncOutcome.ServerNoE2e -> NotesSyncStatus.ServerNoE2e
            is NotesSyncOutcome.Synced ->
                if (outcome.limitReached) NotesSyncStatus.LimitReached else NotesSyncStatus.Synced(outcome.host)
            else -> NotesSyncStatus.Failed
        }
        val synced = outcome as? NotesSyncOutcome.Synced
        _state.update { it.copy(sync = status, tooLarge = synced?.tooLarge?.toSet() ?: it.tooLarge) }
        if ((synced?.conflicts ?: 0) > 0) _events.send(NotesEvent.ShowSyncMessage(R.string.notes_sync_conflict))
    }

    fun open(note: NoteSummary) {
        val resourceId = _state.value.resourceId ?: return
        viewModelScope.launch { _events.send(NotesEvent.OpenEditor(note.path, resourceId)) }
    }

    fun createNote() {
        val resourceId = _state.value.resourceId ?: return
        viewModelScope.launch {
            val resource = getResources.getById(resourceId)
            val result = if (resource == null) {
                Result.failure(IllegalStateException("Notes resource $resourceId is gone"))
            } else {
                createTextNote(
                    resource,
                    NotesFolderPolicy.notesDirectory().absolutePath,
                    TextNoteFileNameProvider.defaultName(),
                )
            }
            result
                .onSuccess { path -> _events.send(NotesEvent.OpenEditor(path, resourceId)) }
                .onFailure { error ->
                    Timber.e(error, "NotesViewModel: note creation failed")
                    _events.send(NotesEvent.ShowError(NotesError.FOLDER_UNAVAILABLE))
                }
        }
    }

    /** S4571: the settings snapshot the Send to.. menu filters its receivers by. */
    suspend fun currentSettings(): AppSettings = getSettingsSnapshot()

    fun delete(note: NoteSummary) {
        viewModelScope.launch {
            deleteNote(note.path)
                .onSuccess {
                    reload()
                    // Item FM: a local delete is a sync moment, and a delete the other devices never hear of
                    // would let their copy come back on the next pull.
                    syncNow()
                }
                .onFailure { error ->
                    Timber.e(error, "NotesViewModel: note deletion failed")
                    _events.send(NotesEvent.ShowError(NotesError.DELETE_FAILED))
                }
        }
    }
}
