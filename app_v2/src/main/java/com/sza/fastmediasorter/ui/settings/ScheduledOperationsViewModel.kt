package com.sza.fastmediasorter.ui.settings

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
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

    val operations: StateFlow<List<ScheduledOperation>> = getScheduledOperationsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPaused: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.scheduledOperationsPaused }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun upsert(operation: ScheduledOperation) {
        viewModelScope.launch { persistAndSchedule(operation) }
    }

    // S1009: resolve staged ad-hoc local folders to resource ids (reuse visible or create hidden), then persist.
    fun saveOperation(draft: ScheduledOperationDraft) {
        viewModelScope.launch {
            // S1009: snapshot the pre-edit FK ids so a re-pointed hidden resource can be cleaned up.
            val previous = operations.value.firstOrNull { it.id == draft.operation.id }
            Timber.d("S1009: saveOperation op=%d", draft.operation.id)
            var op = draft.operation
            draft.sourceFolderPath?.let { path ->
                val id = resolveLocalFolderResourceUseCase(
                    path, draft.sourceFolderName ?: path, isWritable = !draft.sourceFolderReadOnly,
                )
                op = op.copy(sourceResourceId = id)
            }
            draft.targetFolderPath?.let { path ->
                val id = resolveLocalFolderResourceUseCase(
                    path, draft.targetFolderName ?: path, isWritable = true,
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
        } else saved
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
                Timber.d("S1009: op %d deleted, cleaning hidden FKs", operationId)
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

    fun getLog(): String = getScheduledOperationsLogUseCase()

    fun clearLog() {
        viewModelScope.launch {
            clearScheduledOperationsLogUseCase()
        }
    }
}
