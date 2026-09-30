package com.sza.fastmediasorter.ui.scheduledops

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledOperationDraft
import com.sza.fastmediasorter.domain.model.computeNextRunAt
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.CheckLocalFolderWritableUseCase
import com.sza.fastmediasorter.domain.usecase.CleanupHiddenResourceUseCase
import com.sza.fastmediasorter.domain.usecase.ClearScheduledOperationsLogUseCase
import com.sza.fastmediasorter.domain.usecase.ClearScheduledOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.DeleteScheduledOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetResourcesUseCase
import com.sza.fastmediasorter.domain.usecase.GetScheduledOperationsLogUseCase
import com.sza.fastmediasorter.domain.usecase.GetScheduledOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.ResolveLocalFolderResourceUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateScheduledOperationUseCase
import com.sza.fastmediasorter.domain.usecase.UpsertScheduledOperationUseCase
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ScheduledOperationsViewModel @Inject constructor(
    private val getScheduledOperationsUseCase: GetScheduledOperationsUseCase,
    private val upsertScheduledOperationUseCase: UpsertScheduledOperationUseCase,
    private val updateScheduledOperationUseCase: UpdateScheduledOperationUseCase,
    private val deleteScheduledOperationUseCase: DeleteScheduledOperationUseCase,
    private val clearScheduledOperationsUseCase: ClearScheduledOperationsUseCase,
    private val getScheduledOperationsLogUseCase: GetScheduledOperationsLogUseCase,
    private val clearScheduledOperationsLogUseCase: ClearScheduledOperationsLogUseCase,
    private val workManagerScheduler: WorkManagerScheduler,
    private val settingsRepository: SettingsRepository,
    private val resolveLocalFolderResourceUseCase: ResolveLocalFolderResourceUseCase,
    private val checkLocalFolderWritableUseCase: CheckLocalFolderWritableUseCase,
    private val getResourcesUseCase: GetResourcesUseCase,
    private val cleanupHiddenResourceUseCase: CleanupHiddenResourceUseCase
) : ViewModel() {

    // Null until storage answers: the public flows below start from placeholders that look exactly
    // like a real "no operations" list and a real "switch on", so the reconcile reads these instead.
    private val storedOperations: StateFlow<List<ScheduledOperation>?> = getScheduledOperationsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val storedEnabled: StateFlow<Boolean?> = settingsRepository.getSettings()
        .map { it.enableScheduledOperations }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val operations: StateFlow<List<ScheduledOperation>> = storedOperations
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // A StateFlow drops a value equal to its current one, so a loaded empty list would never re-emit
    // after the emptyList() placeholder of [operations]. This one first emits once both the list and
    // the switch are stored, then on list changes only; [reconcileTarget] then acts on an
    // empty <-> non-empty transition alone, so a switch flip must not be reverted.
    val loadedOperations: Flow<List<ScheduledOperation>> = combine(
        storedOperations.filterNotNull(),
        storedEnabled.filterNotNull(),
    ) { ops, _ -> ops }.distinctUntilChanged()

    // S3365: the screen's picker and list labels read the same resource list the settings screen
    // fed the dialog; hidden-FK augmentation stays in the caller. Destinations are read at
    // dialog-open time by the screen - the constructor keeps its baselined parameter count.
    val resources: StateFlow<List<MediaResource>> = getResourcesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // S3365: the program's off-switch - the same enableScheduledOperations setting the settings
    // card owned, now the registry disable target (strategic S3365 §6.2 resolution).
    val isEnabled: StateFlow<Boolean> = storedEnabled
        .map { it ?: true }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isPaused: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.scheduledOperationsPaused }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /**
     * The open create/edit dialog, kept here because the activity - and the manager that shows the
     * dialog - is recreated on rotation while this object is not. Null while no dialog is open.
     */
    var dialogSession: ScheduledOpDialogSession? = null

    /** A SAF result delivered before the recreated screen could take it waits for the reopened dialog. */
    fun deferFolderPick(uri: Uri?) {
        if (uri != null) dialogSession?.pendingPickedUri = uri
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(enableScheduledOperations = enabled) }
        }
    }

    // Emptiness of the list at the previous reconcile; null until the first real observation.
    private var observedEmpty: Boolean? = null

    /**
     * The switch value the program should follow after the list went empty <-> non-empty, or null.
     * Only a transition observed by this screen writes: the first observation is a baseline, so a
     * switch turned off elsewhere (settings) with operations still stored is not turned back on
     * when the screen opens. Also null while either value is still a placeholder, so a slow first
     * read never writes over the user's choice.
     */
    fun reconcileTarget(): Boolean? {
        val ops = storedOperations.value
        val enabled = storedEnabled.value
        if (ops == null || enabled == null) return null
        val previous = observedEmpty
        observedEmpty = ops.isEmpty()
        return if (previous == null || previous == ops.isEmpty()) null else ops.isNotEmpty().takeIf { it != enabled }
    }

    fun setPaused(paused: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(scheduledOperationsPaused = paused) }
        }
    }

    fun upsert(operation: ScheduledOperation) {
        viewModelScope.launch { persistAndSchedule(operation) }
    }

    // S1009: resolve staged ad-hoc local folders to resource ids (reuse visible or create hidden), then persist.
    fun saveOperation(draft: ScheduledOperationDraft) {
        viewModelScope.launch {
            // S1009: snapshot the pre-edit FK ids so a re-pointed hidden resource can be cleaned up.
            val previous = operations.value.firstOrNull { it.id == draft.operation.id }
            var op = draft.operation
            draft.sourceFolderPath?.let { path ->
                val id = resolveLocalFolderResourceUseCase(
                    path,
                    draft.sourceFolderName ?: path,
                    isWritable = !draft.sourceFolderReadOnly,
                )
                op = op.copy(sourceResourceId = id)
            }
            draft.targetFolderPath?.let { path ->
                val id = resolveLocalFolderResourceUseCase(
                    path,
                    draft.targetFolderName ?: path,
                    isWritable = true,
                )
                op = op.copy(targetResourceId = id)
            }
            persistAndSchedule(op)
            previous?.let { prev ->
                if (prev.sourceResourceId != op.sourceResourceId) {
                    cleanupHiddenResourceUseCase(prev.sourceResourceId)
                }
                if (prev.targetResourceId != op.targetResourceId) {
                    cleanupHiddenResourceUseCase(prev.targetResourceId)
                }
            }
        }
    }

    // S1009: writability probe for the folder picker (receiver must be writable; read-only sender -> COPY).
    suspend fun isFolderWritable(folderPath: String): Boolean = checkLocalFolderWritableUseCase(folderPath)

    // S1009: unfiltered resolve so an existing hidden FK can be shown when editing an operation.
    suspend fun resolveResourceById(id: Long): MediaResource? = getResourcesUseCase.getById(id)

    private suspend fun persistAndSchedule(operation: ScheduledOperation) {
        val id = upsertScheduledOperationUseCase(operation)
        val saved = operation.copy(id = if (operation.id == 0L) id else operation.id)
        val scheduled = if (saved.isEnabled && saved.nextRunAt == null) {
            val withNext = saved.copy(
                nextRunAt = computeNextRunAt(
                    saved.startTimeHour,
                    saved.startTimeMinute,
                    saved.intervalHours,
                    saved.intervalMinutes,
                    System.currentTimeMillis()
                )
            )
            updateScheduledOperationUseCase(withNext)
            withNext
        } else {
            saved
        }
        if (scheduled.isEnabled) {
            workManagerScheduler.scheduleOperation(scheduled)
        } else {
            workManagerScheduler.cancelOperation(scheduled.id)
        }
    }

    fun toggleEnabled(operation: ScheduledOperation) {
        viewModelScope.launch {
            val updated = operation.copy(isEnabled = !operation.isEnabled)
            updateScheduledOperationUseCase(updated)
            if (updated.isEnabled) {
                workManagerScheduler.scheduleOperation(updated)
            } else {
                workManagerScheduler.cancelOperation(updated.id)
            }
        }
    }

    fun delete(operationId: Long) {
        viewModelScope.launch {
            // S1009: capture FK ids before the row is gone, so an owned hidden resource can be cleaned up.
            val removed = operations.value.firstOrNull { it.id == operationId }
            workManagerScheduler.cancelOperation(operationId)
            deleteScheduledOperationUseCase(operationId)
            removed?.let {
                cleanupHiddenResourceUseCase(it.sourceResourceId)
                if (it.targetResourceId != it.sourceResourceId) {
                    cleanupHiddenResourceUseCase(it.targetResourceId)
                }
            }
        }
    }

    fun runNow(operationId: Long) {
        viewModelScope.launch {
            workManagerScheduler.runNow(operationId)
        }
    }

    fun runAllNow() { viewModelScope.launch { workManagerScheduler.runAllNow() } }

    fun pauseAll() { viewModelScope.launch { workManagerScheduler.pauseAll() } }

    fun resumeAll() { viewModelScope.launch { workManagerScheduler.resumeAll() } }

    // The run log may grow to 1 MB before AppendToScheduledLogUseCase trims it, so the file read and
    // parse stay off the main thread and only the newest rows are handed to the view-built list.
    suspend fun loadHistory(): List<ScheduledLogEntry> = withContext(Dispatchers.IO) {
        ScheduledLogEntryParser.parse(getScheduledOperationsLogUseCase())
            .takeLast(MAX_HISTORY_ROWS)
            .asReversed()
    }

    // Returned so the screen re-renders the history only after the file is really gone.
    fun clearLog(): Job = viewModelScope.launch {
        withContext(Dispatchers.IO) { clearScheduledOperationsLogUseCase() }
    }

    companion object {
        const val MAX_HISTORY_ROWS = 200
    }
}
