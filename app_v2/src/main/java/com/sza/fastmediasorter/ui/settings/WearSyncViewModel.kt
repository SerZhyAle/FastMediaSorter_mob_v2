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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    /** S1781: the owner has marked no resources for the watch, so nothing was sent - not a failure. */
    data object NothingSelected : WearSyncUiState()
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

    private var ackTimeoutJob: Job? = null

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
                    ackTimeoutJob?.cancel()
                    val applied = parseAppliedCount(ackJson)
                    _uiState.value = WearSyncUiState.Success(applied, 0)
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
        ackTimeoutJob?.cancel()
        _uiState.value = WearSyncUiState.Sending
        viewModelScope.launch {
            sendResourcesToWatchUseCase()
                .onSuccess { result ->
                    if (result.sent == 0) {
                        // S1781: nothing left the phone, so no ack can ever arrive - waiting out the
                        // timeout would report a watch failure for an empty selection instead.
                        _uiState.value = WearSyncUiState.NothingSelected
                    } else {
                        // S1682: the use case returns as soon as Play Services accepts the bytes, which is
                        // necessarily before any watch ack can travel back. Declaring Success here used to
                        // win that race every time, so the ack collector above could never fire and the
                        // green check meant "accepted locally", never "the watch applied them". Stay in
                        // Sending and let the ack decide; a watch that never answers ends in an error the
                        // user can act on rather than in a check mark that is not true.
                        startAckTimeout()
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Wear sync failed")
                    _uiState.value = WearSyncUiState.Error(context.getString(R.string.wear_sync_failed))
                }
        }
    }

    private fun startAckTimeout() {
        ackTimeoutJob = viewModelScope.launch {
            delay(ACK_TIMEOUT_MS)
            if (_uiState.value is WearSyncUiState.Sending) {
                Timber.w("Watch did not acknowledge the sync within $ACK_TIMEOUT_MS ms")
                _uiState.value = WearSyncUiState.Error(context.getString(R.string.wear_sync_no_ack))
            }
        }
    }

    fun reset() {
        ackTimeoutJob?.cancel()
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

    // S1682: the watch reports two numbers, `added` and `updated`. Reading only `added` showed
    // "0 resources" after a sync that in fact refreshed every existing one, which reads as a failure.
    private fun parseAppliedCount(json: String): Int =
        parseIntField(json, "added") + parseIntField(json, "updated")

    private fun parseIntField(json: String, field: String): Int = try {
        json.substringAfter("\"$field\":", "").substringBefore(",").substringBefore("}")
            .trim().toIntOrNull() ?: 0
    } catch (_: Exception) { 0 }

    companion object {
        private const val PREFS = "wear_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"

        // Long enough for a Bluetooth-linked watch to wake and apply the payload, short enough that
        // a user staring at the dialog is not left guessing. The verified round trip of 2026-08-15
        // acked well inside this window.
        private const val ACK_TIMEOUT_MS = 15_000L
    }
}
