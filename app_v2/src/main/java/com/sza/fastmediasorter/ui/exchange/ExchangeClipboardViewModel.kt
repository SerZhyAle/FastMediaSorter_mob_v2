package com.sza.fastmediasorter.ui.exchange

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardFiles
import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardSource
import com.sza.fastmediasorter.core.di.IoDispatcher
import com.sza.fastmediasorter.domain.usecase.exchange.GetClipboardFromServerUseCase
import com.sza.fastmediasorter.domain.usecase.exchange.SendClipboardToServerUseCase
import com.sza.fastmediasorter.worker.ExchangeClipboardTransferWorker
import com.sza.fastmediasorter.worker.ExchangeClipboardWork
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/** What the clipboard window shows; it never holds the transfer itself (strategic S4226 §6 item 4). */
sealed interface ExchangeClipboardUiState {
    data object Checking : ExchangeClipboardUiState

    /** No held entry takes part: the window offers "My devices" instead of a dead action (strategic §3.3). */
    data object NoEntry : ExchangeClipboardUiState

    /** The phone's clipboard cannot be sent as it is; nothing was enqueued. */
    data class LocalRefusal(@StringRes val message: Int) : ExchangeClipboardUiState

    /** [total] is null while the size is not known yet. */
    data class Running(val done: Long, val total: Long?) : ExchangeClipboardUiState
    data class Finished(val headline: String, val detail: String?) : ExchangeClipboardUiState
}

/**
 * Starts one clipboard transfer through WorkManager and follows it by its unique name, so a window recreated by
 * a configuration change follows the same run and never starts a second one.
 */
@HiltViewModel
class ExchangeClipboardViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
    private val sendClipboard: SendClipboardToServerUseCase,
    private val getClipboard: GetClipboardFromServerUseCase,
    private val files: ExchangeClipboardFiles,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val mutableState = MutableStateFlow<ExchangeClipboardUiState>(ExchangeClipboardUiState.Checking)
    val state: StateFlow<ExchangeClipboardUiState> = mutableState.asStateFlow()

    /** True once this window started its action; a recreated window reads it and starts nothing. */
    val started: Boolean get() = savedState.get<Boolean>(KEY_STARTED) == true

    init {
        savedState.get<String>(KEY_WORK)?.let(::follow)
    }

    fun startGet() {
        if (!markStarted()) return
        Timber.d("S4226: get clipboard from server started")
        viewModelScope.launch {
            if (getClipboard.hasEntry()) {
                follow(ExchangeClipboardWork.enqueueGet(context))
            } else {
                mutableState.value = ExchangeClipboardUiState.NoEntry
            }
        }
    }

    fun startSend(source: ExchangeClipboardSource) {
        if (!markStarted()) return
        Timber.d("S4226: send clipboard read on focus: %s", source)
        viewModelScope.launch {
            val refusal = refusalOf(source)
            when {
                refusal != null -> mutableState.value = ExchangeClipboardUiState.LocalRefusal(refusal)
                !sendClipboard.hasEntry() -> mutableState.value = ExchangeClipboardUiState.NoEntry
                else -> follow(ExchangeClipboardWork.enqueueSend(context, workSource(source)))
            }
        }
    }

    /** Cancels the running transfer; closing the window alone never does. */
    fun cancel() {
        savedState.get<String>(KEY_WORK)?.let { WorkManager.getInstance(context).cancelUniqueWork(it) }
    }

    private fun markStarted(): Boolean {
        if (started) return false
        savedState[KEY_STARTED] = true
        return true
    }

    @StringRes
    private fun refusalOf(source: ExchangeClipboardSource): Int? = when (source) {
        ExchangeClipboardSource.Empty -> R.string.exchange_clipboard_phone_empty
        ExchangeClipboardSource.Several -> R.string.exchange_clipboard_several
        ExchangeClipboardSource.Unsupported -> R.string.exchange_clipboard_unsupported
        is ExchangeClipboardSource.Text, is ExchangeClipboardSource.Content -> null
    }

    private suspend fun workSource(source: ExchangeClipboardSource): ExchangeClipboardWork.Source = when (source) {
        is ExchangeClipboardSource.Text -> withContext(ioDispatcher) {
            val staged = files.newOutgoingFile(TEXT_EXTENSION)
            staged.writeText(source.text, Charsets.UTF_8)
            ExchangeClipboardWork.Source.StagedText(staged)
        }
        is ExchangeClipboardSource.Content ->
            ExchangeClipboardWork.Source.Content(source.uri, source.kind, source.mimeType)
        else -> error("a refused source is never enqueued")
    }

    private fun follow(uniqueName: String) {
        savedState[KEY_WORK] = uniqueName
        val sending = uniqueName == ExchangeClipboardWork.UNIQUE_SEND
        viewModelScope.launch {
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(uniqueName).collect { infos ->
                infos.lastOrNull()?.let { mutableState.value = stateOf(it, sending) }
            }
        }
    }

    private fun stateOf(info: WorkInfo, sending: Boolean): ExchangeClipboardUiState = when (info.state) {
        WorkInfo.State.SUCCEEDED -> finishedOf(info)
        WorkInfo.State.CANCELLED -> ExchangeClipboardUiState.Finished(
            context.getString(
                if (sending) R.string.exchange_clipboard_send_cancelled else R.string.exchange_clipboard_get_cancelled
            ),
            null,
        )
        WorkInfo.State.FAILED -> ExchangeClipboardUiState.Finished(
            context.getString(
                if (sending) R.string.exchange_clipboard_not_sent else R.string.exchange_clipboard_not_received
            ),
            null,
        )
        else -> ExchangeClipboardUiState.Running(
            done = info.progress.getLong(ExchangeClipboardTransferWorker.KEY_DONE, 0L),
            total = info.progress.getLong(ExchangeClipboardTransferWorker.KEY_TOTAL, -1L).takeIf { it > 0 },
        )
    }

    private fun finishedOf(info: WorkInfo): ExchangeClipboardUiState {
        val output = info.outputData
        return if (output.getString(ExchangeClipboardTransferWorker.KEY_OUTCOME) ==
            ExchangeClipboardTransferWorker.OUTCOME_NO_ENTRY
        ) {
            ExchangeClipboardUiState.NoEntry
        } else {
            ExchangeClipboardUiState.Finished(
                output.getString(ExchangeClipboardTransferWorker.KEY_HEADLINE).orEmpty(),
                output.getString(ExchangeClipboardTransferWorker.KEY_DETAIL),
            )
        }
    }

    private companion object {
        const val KEY_STARTED = "exchange_clipboard_started"
        const val KEY_WORK = "exchange_clipboard_work"
        const val TEXT_EXTENSION = "txt"
    }
}
