package com.sza.fastmediasorter.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.feedback.FeedbackFolderChoice
import com.sza.fastmediasorter.domain.ticket.TicketAttachmentSource
import com.sza.fastmediasorter.domain.usecase.feedback.AttachFeedbackLogsUseCase
import com.sza.fastmediasorter.domain.usecase.feedback.ObserveFeedbackFoldersUseCase
import com.sza.fastmediasorter.domain.usecase.feedback.SelectFeedbackFolderUseCase
import com.sza.fastmediasorter.domain.usecase.feedback.SendFeedbackUseCase
import com.sza.fastmediasorter.domain.usecase.ticket.DeliverQueuedTicketsUseCase
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
    data object NoLogs : FeedbackNotice
    data object LogsFailed : FeedbackNotice
    data class StageFailed(val reason: String) : FeedbackNotice
}

/**
 * S4573: the composed feedback handed to the Send to.. menu once Send succeeded. [clearForm] is true only
 * when a packet was staged in the outbox; a Send to.. only hand-off leaves the form as the sole copy.
 */
data class FeedbackHandoff(
    val text: String,
    val attachments: List<TicketAttachmentSource>,
    val settings: AppSettings,
    val clearForm: Boolean,
)

/** S4573: what the feedback form shows. */
data class FeedbackUiState(
    val attachments: List<TicketAttachmentSource> = emptyList(),
    val busy: Boolean = false,
    val buildingLogs: Boolean = false,
    val notice: FeedbackNotice? = null,
    val handoff: FeedbackHandoff? = null,
)

/** S4573: holds the pictures and voice notes the owner gathered until Send, and runs Send. */
@HiltViewModel
class FeedbackComposerViewModel @Inject constructor(
    private val sendFeedback: SendFeedbackUseCase,
    private val selectFolder: SelectFeedbackFolderUseCase,
    private val attachFeedbackLogs: AttachFeedbackLogsUseCase,
    private val deliverQueued: DeliverQueuedTicketsUseCase,
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

    /** S4685: builds the diagnostic archive off the main thread and adds it; the two empty-set and
     *  build-failure outcomes are distinct notices, so the form never attaches a placeholder. */
    fun attachLogs() {
        val current = mutableState.value
        if (current.buildingLogs || current.busy) return
        Timber.d("S4685: attach logs pressed")
        mutableState.update { it.copy(buildingLogs = true) }
        viewModelScope.launch {
            val outcome = runCatching { attachFeedbackLogs() }
            outcome.exceptionOrNull()?.rethrowIfCancellation()
            mutableState.update { state ->
                outcome.fold(
                    onSuccess = { source ->
                        if (source == null) {
                            state.copy(buildingLogs = false, notice = FeedbackNotice.NoLogs)
                        } else {
                            state.copy(
                                buildingLogs = false,
                                attachments = (state.attachments + source).distinctBy { it.uri },
                            )
                        }
                    },
                    onFailure = { state.copy(buildingLogs = false, notice = FeedbackNotice.LogsFailed) },
                )
            }
        }
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
                    val staged = result.packetId != null
                    Timber.d("S4651: feedback send done staged=$staged keepsForm=${!staged}")
                    mutableState.update {
                        it.copy(
                            busy = false,
                            attachments = if (staged) emptyList() else it.attachments,
                            notice = if (staged) FeedbackNotice.Queued else null,
                            handoff = FeedbackHandoff(text.trim(), attachments, result.settings, clearForm = staged),
                        )
                    }
                    // S4685: the upload starts in this session, not on the next worker pass; the
                    // worker enqueue from stage() stays the fallback, so a cancelled pass only
                    // leaves the packet QUEUED for it.
                    if (staged) {
                        Timber.d("S4685: immediate delivery pass started packetId=${result.packetId}")
                        launch { runCatching { deliverQueued() }.onFailure { it.rethrowIfCancellation() } }
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
