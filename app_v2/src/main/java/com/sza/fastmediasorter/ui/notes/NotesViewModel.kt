package com.sza.fastmediasorter.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.repository.ResourceRepository
import com.sza.fastmediasorter.domain.usecase.CreateTextNoteUseCase
import com.sza.fastmediasorter.domain.usecase.notes.DeleteNoteUseCase
import com.sza.fastmediasorter.domain.usecase.notes.EnsureNotesResourceUseCase
import com.sza.fastmediasorter.domain.usecase.notes.ListNotesUseCase
import com.sza.fastmediasorter.domain.usecase.notes.NoteSummary
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
import javax.inject.Inject

data class NotesUiState(
    val resourceId: Long? = null,
    val query: String = "",
    val notes: List<NoteSummary> = emptyList(),
    val loading: Boolean = true,
)

sealed interface NotesEvent {
    data class OpenEditor(val path: String, val resourceId: Long) : NotesEvent
    data class ShowError(val kind: NotesError) : NotesEvent
}

enum class NotesError { FOLDER_UNAVAILABLE, DELETE_FAILED }

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
    private val resourceRepository: ResourceRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state.asStateFlow()

    private val _events = Channel<NotesEvent>(Channel.BUFFERED)
    val events: Flow<NotesEvent> = _events.receiveAsFlow()

    private var reloadJob: Job? = null

    /** @param resourceName the label a freshly created notes resource carries in the resource list. */
    fun start(resourceName: String) {
        if (_state.value.resourceId != null) {
            reload()
            return
        }
        viewModelScope.launch {
            ensureNotesResource(resourceName)
                .onSuccess { outcome ->
                    _state.update { it.copy(resourceId = outcome.resourceId) }
                    reload()
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
            _state.update { it.copy(notes = notes, loading = false) }
        }
    }

    fun open(note: NoteSummary) {
        val resourceId = _state.value.resourceId ?: return
        viewModelScope.launch { _events.send(NotesEvent.OpenEditor(note.path, resourceId)) }
    }

    fun createNote() {
        val resourceId = _state.value.resourceId ?: return
        Timber.d("S4568: create note requested")
        viewModelScope.launch {
            val resource = resourceRepository.getResourceById(resourceId)
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

    fun delete(note: NoteSummary) {
        viewModelScope.launch {
            deleteNote(note.path)
                .onSuccess { reload() }
                .onFailure { error ->
                    Timber.e(error, "NotesViewModel: note deletion failed")
                    _events.send(NotesEvent.ShowError(NotesError.DELETE_FAILED))
                }
        }
    }
}
