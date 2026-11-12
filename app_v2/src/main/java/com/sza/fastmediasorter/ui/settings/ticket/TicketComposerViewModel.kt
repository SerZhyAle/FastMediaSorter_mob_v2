package com.sza.fastmediasorter.ui.settings.ticket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.ticket.TicketOutboxEntry
import com.sza.fastmediasorter.domain.model.ticket.TicketProject
import com.sza.fastmediasorter.domain.ticket.TicketAttachmentSource
import com.sza.fastmediasorter.domain.ticket.TicketDraft
import com.sza.fastmediasorter.domain.usecase.ticket.FileTicketUseCase
import com.sza.fastmediasorter.domain.usecase.ticket.GetTicketProjectsUseCase
import com.sza.fastmediasorter.domain.usecase.ticket.ObserveTicketOutboxUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S4554: a one-shot message of the composer. */
sealed interface TicketComposerNotice {
    data object Queued : TicketComposerNotice
    data object EmptyDraft : TicketComposerNotice
    data class StageFailed(val reason: String) : TicketComposerNotice
}

/** S4554: what the composer shows; [projects] null means `projects.json` is not available. */
data class TicketComposerUiState(
    val attachments: List<TicketAttachmentSource> = emptyList(),
    val projects: List<TicketProject>? = null,
    val projectsLoaded: Boolean = false,
    val busy: Boolean = false,
    val notice: TicketComposerNotice? = null,
    val sentCount: Int = 0
)

@HiltViewModel
class TicketComposerViewModel @Inject constructor(
    private val fileTicket: FileTicketUseCase,
    private val getProjects: GetTicketProjectsUseCase,
    private val observeOutbox: ObserveTicketOutboxUseCase
) : ViewModel() {

    private val mutableState = MutableStateFlow(TicketComposerUiState())
    val state: StateFlow<TicketComposerUiState> = mutableState.asStateFlow()

    val outbox: StateFlow<List<TicketOutboxEntry>> = observeOutbox()

    init {
        viewModelScope.launch {
            val projects = getProjects()
            mutableState.update { it.copy(projects = projects, projectsLoaded = true) }
        }
        refresh()
    }

    fun addAttachments(sources: List<TicketAttachmentSource>) {
        mutableState.update { state ->
            state.copy(attachments = (state.attachments + sources).distinctBy { it.uri })
        }
    }

    fun removeAttachment(uri: String) {
        mutableState.update { state -> state.copy(attachments = state.attachments.filterNot { it.uri == uri }) }
    }

    /** [projectIndex] is the spinner position; 0 is "no project". */
    fun send(text: String, projectIndex: Int) {
        val current = mutableState.value
        if (current.busy) return
        if (text.isBlank() && current.attachments.isEmpty()) {
            mutableState.update { it.copy(notice = TicketComposerNotice.EmptyDraft) }
            return
        }
        val project = current.projects?.getOrNull(projectIndex - 1)?.key
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val staged = fileTicket(TicketDraft(text, project, current.attachments))
            mutableState.update { state ->
                staged.fold(
                    onSuccess = {
                        state.copy(
                            busy = false,
                            attachments = emptyList(),
                            notice = TicketComposerNotice.Queued,
                            sentCount = state.sentCount + 1
                        )
                    },
                    onFailure = { error ->
                        val reason = error.message ?: error.javaClass.simpleName
                        state.copy(busy = false, notice = TicketComposerNotice.StageFailed(reason))
                    }
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { observeOutbox.refresh() }
    }

    fun discard(packetId: String) {
        viewModelScope.launch { observeOutbox.discard(packetId) }
    }

    fun consumeNotice() {
        mutableState.update { it.copy(notice = null) }
    }
}
