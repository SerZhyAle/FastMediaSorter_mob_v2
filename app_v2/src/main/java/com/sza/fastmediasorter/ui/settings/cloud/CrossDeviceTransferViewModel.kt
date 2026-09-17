package com.sza.fastmediasorter.ui.settings.cloud

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTransferOption
import com.sza.fastmediasorter.domain.usecase.transfer.CleanExpiredCrossDevicePacketsUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.GetPendingCrossDevicePacketsUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.ReceiveCrossDevicePacketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** S3040: what the queue screen has to say after an action, in the vocabulary the dialog renders. */
sealed interface CrossDeviceQueueNotice {

    data object Failed : CrossDeviceQueueNotice

    data object ReceivedFiles : CrossDeviceQueueNotice

    data object ReceivedSettings : CrossDeviceQueueNotice

    data class QueueCleared(val removed: Int) : CrossDeviceQueueNotice
}

/** S3040: the queue screen's whole state - what is offered, whether a call is in flight, what to say. */
data class CrossDeviceQueueUiState(
    val loading: Boolean = false,
    val packets: List<CrossDevicePacketManifest> = emptyList(),
    val notice: CrossDeviceQueueNotice? = null
)

/**
 * S3040: drives the pending-packet list, the two accept options and the manual queue purge.
 *
 * The local device name is read here rather than passed in from the dialog: it is the one piece of
 * identity every call needs, and the domain layer deliberately has none.
 */
@HiltViewModel
class CrossDeviceTransferViewModel @Inject constructor(
    private val getPendingPackets: GetPendingCrossDevicePacketsUseCase,
    private val receivePacket: ReceiveCrossDevicePacketUseCase,
    private val cleanExpiredPackets: CleanExpiredCrossDevicePacketsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(CrossDeviceQueueUiState())
    val state: StateFlow<CrossDeviceQueueUiState> = _state.asStateFlow()

    val localDeviceName: String = Build.MODEL ?: UNKNOWN_DEVICE_NAME

    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            getPendingPackets(localDeviceName)
                .onSuccess { packets -> _state.update { it.copy(loading = false, packets = packets) } }
                .onFailure { error -> failed(error, "listing the cross-device queue") }
        }
    }

    fun receive(manifest: CrossDevicePacketManifest, option: CrossDeviceTransferOption) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            receivePacket(manifest, option)
                .onSuccess { outcome ->
                    _state.update {
                        it.copy(
                            loading = false,
                            notice = if (outcome.settingsApplied) {
                                CrossDeviceQueueNotice.ReceivedSettings
                            } else {
                                CrossDeviceQueueNotice.ReceivedFiles
                            }
                        )
                    }
                    refresh()
                }
                .onFailure { error -> failed(error, "receiving packet ${manifest.packetId}") }
        }
    }

    fun clearQueue() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            cleanExpiredPackets()
                .onSuccess { removed ->
                    _state.update {
                        it.copy(loading = false, notice = CrossDeviceQueueNotice.QueueCleared(removed))
                    }
                    refresh()
                }
                .onFailure { error -> failed(error, "clearing the cross-device queue") }
        }
    }

    /** Drop the notice once the dialog has shown it, so a rotation does not repeat the message. */
    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun failed(error: Throwable, what: String) {
        Timber.e(error, "Cross-device transfer failed while $what")
        _state.update { it.copy(loading = false, notice = CrossDeviceQueueNotice.Failed) }
    }

    private companion object {
        const val UNKNOWN_DEVICE_NAME = "Android"
    }
}
