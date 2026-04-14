package com.sza.fastmediasorter.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.usecase.SendResourcesToWatchUseCase
import com.sza.fastmediasorter.service.WearSyncEvents
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class WearSyncUiState {
    data object Idle : WearSyncUiState()
    data object Sending : WearSyncUiState()
    data class Success(val sent: Int, val skipped: Int) : WearSyncUiState()
    data class Error(val message: String) : WearSyncUiState()
}

@HiltViewModel
class WearSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sendResourcesToWatchUseCase: SendResourcesToWatchUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<WearSyncUiState>(WearSyncUiState.Idle)
    val uiState: StateFlow<WearSyncUiState> = _uiState.asStateFlow()

    val lastSyncTimestamp: Long
        get() = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC, 0L)

    init {
        // Observe ack events emitted by PhoneWearListenerService
        viewModelScope.launch {
            WearSyncEvents.ackFlow.collect { ackJson ->
                val current = _uiState.value
                if (current is WearSyncUiState.Sending) {
                    val sent = parseSentCount(ackJson)
                    _uiState.value = WearSyncUiState.Success(sent, 0)
                    Timber.i("Wear sync ack received: $ackJson")
                }
            }
        }
    }

    fun startPush() {
        _uiState.value = WearSyncUiState.Sending
        viewModelScope.launch {
            sendResourcesToWatchUseCase()
                .onSuccess { result ->
                    // State transitions to Success via ack flow; set it here as fallback if ack not received
                    if (_uiState.value is WearSyncUiState.Sending) {
                        _uiState.value = WearSyncUiState.Success(result.sent, result.skipped)
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Wear sync failed")
                    _uiState.value = WearSyncUiState.Error(e.message ?: "Sync failed")
                }
        }
    }

    fun reset() {
        _uiState.value = WearSyncUiState.Idle
    }

    private fun parseSentCount(json: String): Int = try {
        json.substringAfter("\"added\":").substringBefore(",").trim().toIntOrNull() ?: 0
    } catch (_: Exception) { 0 }

    companion object {
        private const val PREFS = "wear_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
    }
}
