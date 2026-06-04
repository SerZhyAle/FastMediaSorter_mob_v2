package com.sza.fastmediasorter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.usecase.ClearScheduledOperationsLogUseCase
import com.sza.fastmediasorter.domain.usecase.ClearScheduledOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.DeleteScheduledOperationUseCase
import com.sza.fastmediasorter.domain.usecase.GetScheduledOperationsLogUseCase
import com.sza.fastmediasorter.domain.usecase.GetScheduledOperationsUseCase
import com.sza.fastmediasorter.domain.usecase.UpdateScheduledOperationUseCase
import com.sza.fastmediasorter.domain.usecase.UpsertScheduledOperationUseCase
import com.sza.fastmediasorter.worker.WorkManagerScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val operations: StateFlow<List<ScheduledOperation>> = getScheduledOperationsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPaused: StateFlow<Boolean> = settingsRepository.getSettings()
        .map { it.scheduledOperationsPaused }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun upsert(operation: ScheduledOperation) {
        viewModelScope.launch {
            val id = upsertScheduledOperationUseCase(operation)
            val saved = operation.copy(id = if (operation.id == 0L) id else operation.id)
            val scheduled = if (saved.isEnabled && saved.nextRunAt == null) {
                val withNext = saved.copy(nextRunAt = computeInitialNextRunAt(saved.startTimeHour, saved.startTimeMinute))
                updateScheduledOperationUseCase(withNext)
                withNext
            } else saved
            if (scheduled.isEnabled) {
                workManagerScheduler.scheduleOperation(scheduled)
            } else {
                workManagerScheduler.cancelOperation(scheduled.id)
            }
        }
    }

    private fun computeInitialNextRunAt(hour: Int, minute: Int): Long {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, hour)
            set(java.util.Calendar.MINUTE, minute)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return cal.timeInMillis
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
            workManagerScheduler.cancelOperation(operationId)
            deleteScheduledOperationUseCase(operationId)
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
