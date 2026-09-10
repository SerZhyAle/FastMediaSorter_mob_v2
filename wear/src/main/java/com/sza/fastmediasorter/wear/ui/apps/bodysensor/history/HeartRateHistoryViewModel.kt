package com.sza.fastmediasorter.wear.ui.apps.bodysensor.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.repository.HeartRateHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S2808: collects heart-rate measurement history from the repository and exposes it as UI state.
 *
 * The collection runs in `viewModelScope`, so leaving the screen cancels it - no leaked
 * subscription. [clearHistory] deletes all entries in one call.
 */
@HiltViewModel
class HeartRateHistoryViewModel @Inject constructor(
    private val repository: HeartRateHistoryRepository
) : ViewModel() {

    val state: StateFlow<HeartRateHistoryUiState> = repository.observeAll()
        .map { entries -> HeartRateHistoryUiState(entries = entries, isEmpty = entries.isEmpty()) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            HeartRateHistoryUiState()
        )

    fun clearHistory() {
        viewModelScope.launch { repository.deleteAll() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5000L
    }
}
