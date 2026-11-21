package com.sza.fastmediasorter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.remotecontrol.ManageRemoteControlUseCase
import com.sza.fastmediasorter.domain.remotecontrol.RemoteCommandLogEntry
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentMode
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerCandidates
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerChoice
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerGrant
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * What the "Accept commands from my devices" rows show: the switch, whether a held exchange entry can carry
 * commands (without one the rows are disabled with a reason, but a switch already on can still be turned off),
 * and the log, oldest first as stored.
 */
data class RemoteControlUiState(
    val listening: Boolean = false,
    val canListen: Boolean = false,
    val log: List<RemoteCommandLogEntry> = emptyList(),
)

sealed interface RemoteControllersUiState {
    data object Loading : RemoteControllersUiState

    /** Entries were asked, none answered, and no grant is kept to show instead. */
    data object Failed : RemoteControllersUiState

    data class Loaded(val choices: List<RemoteControllerChoice>) : RemoteControllersUiState
}

@HiltViewModel
class RemoteControlSettingsViewModel @Inject constructor(
    private val manage: ManageRemoteControlUseCase,
) : ViewModel() {

    val state: StateFlow<RemoteControlUiState> = combine(
        manage.listening,
        manage.canListen,
        manage.recentLog,
        ::RemoteControlUiState,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RemoteControlUiState())

    // Null until the account directory answered; the grants flow keeps the list live once it did.
    private val candidates = MutableStateFlow<RemoteControllerCandidates?>(null)

    val controllers: StateFlow<RemoteControllersUiState> = combine(candidates, manage.grants, ::controllersOf)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RemoteControllersUiState.Loading)

    fun setListening(on: Boolean) {
        Timber.d("S4701: setListening on=%b", on)
        viewModelScope.launch { manage.setListening(on) }
    }

    fun loadControllers() {
        candidates.value = null
        viewModelScope.launch { candidates.value = manage.candidates() }
    }

    fun setGranted(choice: RemoteControllerChoice, granted: Boolean) {
        viewModelScope.launch {
            if (granted) manage.grant(choice.deviceId, choice.deviceName) else manage.revoke(choice.deviceId)
        }
    }

    fun setClassAllowed(deviceId: String, riskClass: RemoteRiskClass, allowed: Boolean) {
        viewModelScope.launch {
            if (allowed) manage.allowClass(deviceId, riskClass) else manage.denyClass(deviceId, riskClass)
        }
    }

    fun setMode(deviceId: String, riskClass: RemoteRiskClass, mode: RemoteConsentMode) {
        viewModelScope.launch { manage.setMode(deviceId, riskClass, mode) }
    }

    private fun controllersOf(
        loaded: RemoteControllerCandidates?,
        grants: List<RemoteControllerGrant>,
    ): RemoteControllersUiState = when {
        loaded == null -> RemoteControllersUiState.Loading
        loaded.unreachable && grants.isEmpty() -> RemoteControllersUiState.Failed
        else -> RemoteControllersUiState.Loaded(RemoteControllerChoice.merge(loaded, grants))
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
