package com.sza.fastmediasorter.wear.ui.player.common

import android.content.IntentSender
import com.sza.fastmediasorter.wear.data.repository.WearSendToReceiversRepository
import com.sza.fastmediasorter.wear.domain.files.WearFileCapabilityPolicy
import com.sza.fastmediasorter.wear.domain.files.WearSendToReceiverFilter
import com.sza.fastmediasorter.wear.domain.model.WearFileOperation
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationKind
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileOperationResult
import com.sza.fastmediasorter.wear.domain.model.WearMediaFile
import com.sza.fastmediasorter.wear.domain.model.WearSendToReceiverEntry
import com.sza.fastmediasorter.wear.domain.repository.PlaybackSetManager
import com.sza.fastmediasorter.wear.domain.usecase.PerformWearFileOperationUseCase
import com.sza.fastmediasorter.wear.ui.browse.MediaStoreConsentManager
import com.sza.fastmediasorter.wear.ui.browse.WearFileOperationRunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val RECEIVER_SUBSCRIPTION_MS = 5_000L

/**
 * Result of running a file operation in a player.
 */
sealed interface PlayerOperationResult {
    /** The operation was non-mutating or failed; remain on current file. */
    data object Stay : PlayerOperationResult

    /** Mutating operation succeeded; advance to [nextFile]. */
    data class Advance(val nextFile: WearMediaFile) : PlayerOperationResult

    /** Mutating operation succeeded and no files remain in the playback set. */
    data object SetEmpty : PlayerOperationResult
}

class PlayerFileOperationsManager @Inject constructor(
    private val capabilityPolicy: WearFileCapabilityPolicy,
    private val performFileOperation: PerformWearFileOperationUseCase,
    private val sendToReceiversRepository: WearSendToReceiversRepository,
    private val playbackSetManager: PlaybackSetManager
) {
    private lateinit var scope: CoroutineScope
    private lateinit var currentFile: StateFlow<WearMediaFile?>
    private var isNetworkSource: () -> Boolean = { false }

    private val _operationRun = MutableStateFlow(WearFileOperationRunState())
    val operationRun: StateFlow<WearFileOperationRunState> = _operationRun.asStateFlow()

    private val consentManager = MediaStoreConsentManager()
    val consentRequest: StateFlow<IntentSender?> = consentManager.request

    private var operationJob: Job? = null

    private lateinit var _allowedOperations: StateFlow<Set<WearFileOperationKind>>
    val allowedOperations: StateFlow<Set<WearFileOperationKind>> get() = _allowedOperations

    private lateinit var _sendToReceivers: StateFlow<List<WearSendToReceiverEntry>>
    val sendToReceivers: StateFlow<List<WearSendToReceiverEntry>> get() = _sendToReceivers

    private val _operationResult = MutableStateFlow<PlayerOperationResult?>(null)
    val operationResult: StateFlow<PlayerOperationResult?> = _operationResult.asStateFlow()

    fun bind(
        scope: CoroutineScope,
        currentFile: StateFlow<WearMediaFile?>,
        isNetworkSource: () -> Boolean = { false }
    ) {
        this.scope = scope
        this.currentFile = currentFile
        this.isNetworkSource = isNetworkSource

        _allowedOperations = combine(currentFile) { files ->
            val file = files[0]
            if (file == null) {
                emptySet()
            } else {
                capabilityPolicy.allowedOperations(capabilityPolicy.classify(file, isNetworkSource()))
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(RECEIVER_SUBSCRIPTION_MS), emptySet())

        _sendToReceivers = combine(sendToReceiversRepository.observe(), currentFile) { receivers, file ->
            if (file == null) {
                emptyList()
            } else {
                WearSendToReceiverFilter.apply(receivers, listOf(file))
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(RECEIVER_SUBSCRIPTION_MS), emptyList())
    }

    fun runOperation(operation: WearFileOperation) {
        val file = currentFile.value ?: return
        if (_operationRun.value.running) return
        startOperation(file, operation)
    }

    private fun startOperation(
        file: WearMediaFile,
        operation: WearFileOperation,
        keptResults: List<WearFileOperationResult> = emptyList()
    ) {
        operationJob?.cancel()
        operationJob = scope.launch {
            _operationRun.value = WearFileOperationRunState(
                running = true,
                total = 1,
                results = keptResults
            )
            try {
                collectRun(file, operation)
            } finally {
                finishRun(file)
            }
            consentManager.raiseIfBlocked(_operationRun.value.results, listOf(file), operation)
            val changed = _operationRun.value.results.any { it.outcome == WearFileOperationOutcome.SUCCEEDED }
            if (operation.mutatesList() && changed) {
                val next = playbackSetManager.removeAndSelectNext(file.id)
                _operationResult.value = if (next != null) {
                    PlayerOperationResult.Advance(next)
                } else {
                    PlayerOperationResult.SetEmpty
                }
            } else {
                _operationResult.value = PlayerOperationResult.Stay
            }
        }
    }

    private suspend fun collectRun(file: WearMediaFile, operation: WearFileOperation) {
        performFileOperation(listOf(file), operation, isNetworkSource())
            .catch { throwable ->
                Timber.e(throwable, "Player file operation failed")
                emit(WearFileOperationResult(file.name, WearFileOperationOutcome.FAILED))
            }
            .collect { result ->
                _operationRun.update { current ->
                    current.copy(completed = current.completed + 1, results = current.results + result)
                }
            }
    }

    private fun finishRun(file: WearMediaFile) {
        _operationRun.update { current ->
            val answered = current.results.map { it.fileName }.toSet()
            val cancelled = if (file.name !in answered) {
                listOf(WearFileOperationResult(file.name, WearFileOperationOutcome.CANCELLED))
            } else {
                emptyList()
            }
            current.copy(running = false, results = current.results + cancelled)
        }
    }

    fun onConsentAnswered(granted: Boolean) {
        val pending = consentManager.consume(granted) ?: return
        val file = pending.files.firstOrNull() ?: return
        val kept = _operationRun.value.results.filterNot { it.outcome == WearFileOperationOutcome.NEEDS_CONSENT }
        startOperation(file, pending.operation, keptResults = kept)
    }

    fun cancelOperation() {
        operationJob?.cancel()
    }

    fun dismissOperationResults() {
        _operationRun.value = WearFileOperationRunState()
        _operationResult.value = null
        consentManager.reset()
    }
}

private fun WearFileOperation.mutatesList(): Boolean = when (this) {
    WearFileOperation.SendToPhone -> false
    WearFileOperation.MoveToPhone -> true
    WearFileOperation.Delete -> true
    is WearFileOperation.Rename -> true
    is WearFileOperation.OpenOnPhone -> false
    is WearFileOperation.SendToReceiver -> false
}
