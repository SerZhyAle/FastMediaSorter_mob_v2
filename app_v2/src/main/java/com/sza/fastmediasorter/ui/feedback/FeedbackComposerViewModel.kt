package com.sza.fastmediasorter.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.ticket.TicketAttachmentSource
import com.sza.fastmediasorter.domain.usecase.feedback.FeedbackFolderChoice
import com.sza.fastmediasorter.domain.usecase.feedback.ObserveFeedbackFoldersUseCase
import com.sza.fastmediasorter.domain.usecase.feedback.SelectFeedbackFolderUseCase
import com.sza.fastmediasorter.domain.usecase.feedback.SendFeedbackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** S4573: a one-shot message of the feedback form. */
sealed interface FeedbackNotice {
    data object NoImageInClipboard : FeedbackNotice
    data object VoiceFailed : FeedbackNotice
    data object Empty : FeedbackNotice
    data object Queued : FeedbackNotice
    data class StageFailed(val reason: String) : FeedbackNotice
}

/** S4573: the composed feedback handed to the Send to.. menu once Send succeeded. */
data class FeedbackHandoff(
    val text: String,
    val attachments: List<TicketAttachmentSource>,
    val settings: AppSettings,
)

/** S4573: what the feedback form shows. */
data class FeedbackUiState(
    val attachments: List<TicketAttachmentSource> = emptyList(),
    val busy: Boolean = false,
    val notice: FeedbackNotice? = null,
    val handoff: FeedbackHandoff? = null,
)

/** S4573: holds the pictures and voice notes the owner gathered until Send, and runs Send. */
@HiltViewModel
class FeedbackComposerViewModel @Inject constructor(
    private val sendFeedback: SendFeedbackUseCase,
    private val selectFolder: SelectFeedbackFolderUseCase,
    observeFolders: ObserveFeedbackFoldersUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow(FeedbackUiState())
    val state: StateFlow<FeedbackUiState> = mutableState.asStateFlow()

    val folders: StateFlow<FeedbackFolderChoice> = observeFolders().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        FeedbackFolderChoice(),
    )

    fun addAttachments(sources: List<TicketAttachmentSource>) {
        mutableState.update { state ->
            state.copy(attachments = (state.attachments + sources).distinctBy { it.uri })
        }
    }

    fun removeAttachment(uri: String) {
        mutableState.update { state -> state.copy(attachments = state.attachments.filterNot { it.uri == uri }) }
    }

    fun chooseFolder(resourceId: Long?) {
        if (resourceId == folders.value.selectedId) return
        viewModelScope.launch { selectFolder(resourceId) }
    }

    fun send(text: String) {
        Timber.d("S4573: feedback send pressed attachments=${mutableState.value.attachments.size}")
        val current = mutableState.value
        if (current.busy) return
        val attachments = current.attachments
        if (text.isBlank() && attachments.isEmpty()) {
            notify(FeedbackNotice.Empty)
            return
        }
        mutableState.update { it.copy(busy = true) }
        viewModelScope.launch {
            sendFeedback(text.trim(), attachments).fold(
                onSuccess = { result ->
                    mutableState.update {
                        it.copy(
                            busy = false,
                            attachments = emptyList(),
                            notice = if (result.packetId != null) FeedbackNotice.Queued else null,
                            handoff = FeedbackHandoff(text.trim(), attachments, result.settings),
                        )
                    }
                },
                onFailure = { error ->
                    val reason = error.message ?: error.javaClass.simpleName
                    mutableState.update { it.copy(busy = false, notice = FeedbackNotice.StageFailed(reason)) }
                },
            )
        }
    }

    fun notify(notice: FeedbackNotice) {
        mutableState.update { it.copy(notice = notice) }
    }

    fun consumeNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    fun consumeHandoff() {
        mutableState.update { it.copy(handoff = null) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
