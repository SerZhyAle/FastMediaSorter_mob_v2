package com.sza.fastmediasorter.wear.ui.apps.motionmonitor.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.MotionAnalytics
import com.sza.fastmediasorter.wear.domain.repository.MotionHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S3014: Collects activity history from the repository and exposes it as UI state.
 */
@HiltViewModel
class MotionHistoryViewModel @Inject constructor(
    private val repository: MotionHistoryRepository
) : ViewModel() {

    val state: StateFlow<MotionHistoryUiState> = repository.observeAll()
        .map { entries ->
            MotionHistoryUiState(
                entries = entries,
                isEmpty = entries.isEmpty(),
                summary = MotionAnalytics.computeSummary(entries)
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            MotionHistoryUiState()
        )

    fun clearHistory() {
        viewModelScope.launch { repository.deleteAll() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
