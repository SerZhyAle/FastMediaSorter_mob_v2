package com.sza.fastmediasorter.ui.remotecontrol

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.notification.RemoteCommandConsentNotifier
import com.sza.fastmediasorter.domain.remotecontrol.ManageRemoteControlUseCase
import com.sza.fastmediasorter.domain.remotecontrol.RemoteConsentMode
import com.sza.fastmediasorter.domain.remotecontrol.RemoteControllerGrant
import com.sza.fastmediasorter.domain.remotecontrol.RemoteRiskClass
import com.sza.fastmediasorter.domain.remotecontrol.RemoteVerbHandler
import com.sza.fastmediasorter.ui.remotecontrol.helpers.ExternalControlDirectory
import com.sza.fastmediasorter.ui.remotecontrol.helpers.ExternalControlDirectoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

internal data class ExternalControlState(
    val listening: Boolean = false,
    val directory: ExternalControlDirectory = ExternalControlDirectory(),
    val grants: List<RemoteControllerGrant> = emptyList(),
    val failedRows: Set<String> = emptySet(),
)

@HiltViewModel
internal class ExternalControlViewModel @Inject constructor(
    private val manage: ManageRemoteControlUseCase,
    directoryManager: ExternalControlDirectoryManager,
    private val consent: RemoteCommandConsentNotifier,
    handlers: Set<@JvmSuppressWildcards RemoteVerbHandler>,
) : ViewModel() {
    private val writes = Mutex()
    private val failedRows = MutableStateFlow<Set<String>>(emptySet())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val directory = manage.listening.flatMapLatest { listening ->
        if (listening) directoryManager.observe() else flowOf(ExternalControlDirectory())
    }
    val state = combine(manage.listening, directory, manage.grants, failedRows, ::ExternalControlState)
        .onEach(::removeInvalidGrants)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ExternalControlState())

    val request = consent.prompt
    val log = manage.recentLog
    val commands = handlers.map(RemoteVerbHandler::spec).sortedBy { it.name }

    fun setListening(on: Boolean) = write(LISTENING) { manage.setListening(on) }

    fun setGranted(deviceId: String, granted: Boolean) = write(deviceId) {
        val device = state.value.directory.devices.firstOrNull { it.deviceId == deviceId }
        if (granted && device != null && state.value.listening) {
            manage.grant(deviceId, device.deviceName)
        } else if (!granted) {
            manage.revoke(deviceId)
        }
    }

    fun setClass(deviceId: String, risk: RemoteRiskClass, mode: RemoteConsentMode?) = write(deviceId) {
        Timber.d("S4817: external control class=%s mode=%s", risk.wire, mode?.wire)
        if (state.value.listening && state.value.directory.devices.any { it.deviceId == deviceId }) {
            if (mode == null) manage.denyClass(deviceId, risk) else manage.setMode(deviceId, risk, mode)
        }
    }

    fun answer(key: Int, accepted: Boolean) {
        Timber.d("S4817: external control consent answered allow=%b", accepted)
        viewModelScope.launch { consent.answer(key, accepted) }
    }

    private fun write(row: String, change: suspend () -> Unit) {
        viewModelScope.launch {
            writes.withLock {
                failedRows.update { it - row }
                try {
                    change()
                } catch (error: IOException) {
                    Timber.w(error, "Could not save an External control decision")
                    failedRows.update { it + row }
                }
            }
        }
    }

    private suspend fun removeInvalidGrants(value: ExternalControlState) {
        val snapshot = value.directory
        if (!value.listening) return
        val listedIds = snapshot.devices.mapTo(HashSet()) { it.deviceId }
        value.grants.forEach { grant ->
            if (grant.deviceId in snapshot.changedKeys || (snapshot.complete && grant.deviceId !in listedIds)) {
                revokeInvalidGrant(grant.deviceId)
            }
        }
    }

    private suspend fun revokeInvalidGrant(deviceId: String) {
        try {
            writes.withLock { manage.revoke(deviceId) }
        } catch (error: IOException) {
            Timber.w(error, "Could not remove an invalid External control grant")
        }
    }

    companion object {
        const val LISTENING = "listening"
    }
}
