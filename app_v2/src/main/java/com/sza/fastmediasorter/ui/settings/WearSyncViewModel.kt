package com.sza.fastmediasorter.ui.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.WearPlaybackCommand
import com.sza.fastmediasorter.domain.model.WearPlaybackStatePayload
import com.sza.fastmediasorter.domain.model.WearSettingsPayload
import com.sza.fastmediasorter.domain.model.WearSourcesExportPayload
import com.sza.fastmediasorter.domain.usecase.ImportWatchSourcesUseCase
import com.sza.fastmediasorter.domain.usecase.PushWearSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.SendPlaybackCommandUseCase
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
    data object SettingsPushed : WearSyncUiState()
    data class Error(val message: String) : WearSyncUiState()
}

@HiltViewModel
class WearSyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sendResourcesToWatchUseCase: SendResourcesToWatchUseCase,
    private val pushWearSettingsUseCase: PushWearSettingsUseCase,
    private val importWatchSourcesUseCase: ImportWatchSourcesUseCase,
    private val sendPlaybackCommandUseCase: SendPlaybackCommandUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<WearSyncUiState>(WearSyncUiState.Idle)
    val uiState: StateFlow<WearSyncUiState> = _uiState.asStateFlow()

    private val _watchSettingsState = MutableStateFlow<WearSettingsPayload?>(null)
    val watchSettingsState: StateFlow<WearSettingsPayload?> = _watchSettingsState.asStateFlow()

    private val _pendingWatchSources = MutableStateFlow<WearSourcesExportPayload?>(null)
    val pendingWatchSources: StateFlow<WearSourcesExportPayload?> = _pendingWatchSources.asStateFlow()

    private val _watchPlaybackState = MutableStateFlow<WearPlaybackStatePayload?>(null)
    val watchPlaybackState: StateFlow<WearPlaybackStatePayload?> = _watchPlaybackState.asStateFlow()

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
        viewModelScope.launch {
            WearSyncEvents.watchSourcesReceivedFlow.collect { payload ->
                _pendingWatchSources.value = payload
            }
        }
        viewModelScope.launch {
            WearSyncEvents.watchPlaybackStateFlow.collect { state ->
                _watchPlaybackState.value = state
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
                    _uiState.value = WearSyncUiState.Error(context.getString(R.string.wear_sync_failed))
                }
        }
    }

    fun reset() {
        _uiState.value = WearSyncUiState.Idle
    }

    fun pushSettings(settings: WearSettingsPayload) {
        _watchSettingsState.value = settings
        _uiState.value = WearSyncUiState.Sending
        viewModelScope.launch {
            pushWearSettingsUseCase(settings)
                .onSuccess {
                    _uiState.value = WearSyncUiState.SettingsPushed
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to push watch settings")
                    _uiState.value = WearSyncUiState.Error(context.getString(R.string.wear_push_settings_failed))
                }
        }
    }

    fun updateWatchSettingsLocally(settings: WearSettingsPayload) {
        _watchSettingsState.value = settings
    }

    fun acceptWatchImport() {
        val payload = _pendingWatchSources.value ?: return
        viewModelScope.launch {
            importWatchSourcesUseCase(payload)
                .onSuccess { result ->
                    Timber.i("Watch import accepted: added=${result.added} skipped=${result.skipped}")
                }
                .onFailure { e ->
                    Timber.e(e, "Watch import failed")
                }
            _pendingWatchSources.value = null
        }
    }

    fun dismissWatchImport() {
        _pendingWatchSources.value = null
    }

    fun sendPlaybackCommand(command: WearPlaybackCommand) {
        viewModelScope.launch {
            sendPlaybackCommandUseCase(command).onFailure { e ->
                Timber.e(e, "Failed to send playback command $command")
            }
        }
    }

    private fun parseSentCount(json: String): Int = try {
        json.substringAfter("\"added\":").substringBefore(",").trim().toIntOrNull() ?: 0
    } catch (_: Exception) { 0 }

    companion object {
        private const val PREFS = "wear_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
    }
}
