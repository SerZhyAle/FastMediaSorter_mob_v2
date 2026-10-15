package com.sza.fastmediasorter.ui.settings.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketEntry
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePacketManifest
import com.sza.fastmediasorter.domain.model.transfer.CrossDevicePayloadKind
import com.sza.fastmediasorter.domain.model.transfer.CrossDeviceTransferOption
import com.sza.fastmediasorter.domain.model.transfer.DriveChannelOff
import com.sza.fastmediasorter.domain.model.transfer.DriveDailyLimit
import com.sza.fastmediasorter.domain.model.transfer.DriveQueueFull
import com.sza.fastmediasorter.domain.model.transfer.DriveSignInRequired
import com.sza.fastmediasorter.domain.model.transfer.OutgoingPacketFile
import com.sza.fastmediasorter.domain.model.transfer.PacketTooLarge
import com.sza.fastmediasorter.domain.model.transfer.TransferDataKind
import com.sza.fastmediasorter.domain.port.LocalDeviceIdentitySource
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.transfer.DrivePollState
import com.sza.fastmediasorter.domain.transfer.DriveQueueSnapshots
import com.sza.fastmediasorter.domain.transfer.PollDemand
import com.sza.fastmediasorter.domain.usecase.transfer.BuildTransferPayloadUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.CleanExpiredCrossDevicePacketsUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.DeleteCrossDevicePacketUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.GetPendingCrossDevicePacketsUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.ReceiveCrossDevicePacketUseCase
import com.sza.fastmediasorter.domain.usecase.transfer.SendCrossDevicePacketUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** S3040: what the queue screen has to say after an action, in the vocabulary the dialog renders. */
sealed interface CrossDeviceQueueNotice {

    data object Failed : CrossDeviceQueueNotice

    data object ReceivedFiles : CrossDeviceQueueNotice

    data object ReceivedSettings : CrossDeviceQueueNotice

    data object SettingsSent : CrossDeviceQueueNotice

    data class ExpiredRemoved(val removed: Int) : CrossDeviceQueueNotice

    /** S4231: the Drive channel failures a person can act on (contract DEVICE-EXCHANGE items CS and CT). */
    data object ChannelOff : CrossDeviceQueueNotice

    data object SignInRequired : CrossDeviceQueueNotice

    data object DailyLimit : CrossDeviceQueueNotice

    data class NotEnoughSpace(val freeBytes: Long?) : CrossDeviceQueueNotice

    data class TooLarge(val limitBytes: Long) : CrossDeviceQueueNotice

    data object Deleted : CrossDeviceQueueNotice
}

/** S3040: the queue screen's whole state - what is offered, whether a call is in flight, what to say. */
data class CrossDeviceQueueUiState(
    val loading: Boolean = false,
    val packets: List<CrossDevicePacketEntry> = emptyList(),
    val outgoing: List<CrossDevicePacketEntry> = emptyList(),
    val notice: CrossDeviceQueueNotice? = null
)

/**
 * S3040: drives the pending-packet list, the two accept options, the manual expiry purge and the
 * send-my-settings upload.
 *
 * The local device name is read here rather than passed in from the dialog: it is the one piece of
 * identity every call needs, and the domain layer deliberately has none.
 */
@HiltViewModel
class CrossDeviceTransferViewModel @Inject constructor(
    private val getPendingPackets: GetPendingCrossDevicePacketsUseCase,
    private val receivePacket: ReceiveCrossDevicePacketUseCase,
    private val cleanExpiredPackets: CleanExpiredCrossDevicePacketsUseCase,
    private val buildTransferPayload: BuildTransferPayloadUseCase,
    private val sendPacket: SendCrossDevicePacketUseCase,
    private val deletePacket: DeleteCrossDevicePacketUseCase,
    private val identity: LocalDeviceIdentitySource,
    private val settings: DriveChannelSettings,
    private val queue: DriveQueueSnapshots
) : ViewModel() {

    private val _state = MutableStateFlow(CrossDeviceQueueUiState())
    val state: StateFlow<CrossDeviceQueueUiState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var visibleJob: Job? = null
    private var demand: AutoCloseable? = null

    val channelEnabled: StateFlow<Boolean> =
        settings.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val backgroundPoll: StateFlow<Boolean> =
        settings.backgroundPoll.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val pollState: StateFlow<DrivePollState> = queue.pollState

    /**
     * S4231: the open queue screen is an active demand - the shared poll runs every 10 seconds while it
     * shows (contract item CQ) - and its list follows that poll instead of a timer of its own.
     */
    fun onVisible() {
        Timber.d("S4231: Drive queue dialog visible")
        if (demand != null) return
        demand = queue.acquire(PollDemand.ACTIVE)
        visibleJob = viewModelScope.launch {
            queue.snapshots.collect { snapshot ->
                val view = GetPendingCrossDevicePacketsUseCase.viewOf(
                    snapshot,
                    identity.current(),
                    settings.receivedPacketIds()
                )
                _state.update { it.copy(packets = view.incoming, outgoing = view.outgoing) }
            }
        }
    }

    fun onHidden() {
        visibleJob?.cancel()
        visibleJob = null
        demand?.close()
        demand = null
    }

    fun setChannelEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setEnabled(enabled) }
    }

    fun setBackgroundPoll(enabled: Boolean) {
        viewModelScope.launch { settings.setBackgroundPoll(enabled) }
    }

    fun refresh() {
        refreshJob?.cancel()
        _state.update { it.copy(loading = true) }
        refreshJob = viewModelScope.launch {
            val result = getPendingPackets(identity.current())
            currentCoroutineContext().ensureActive()
            result
                .onSuccess { view ->
                    _state.update { it.copy(loading = false, packets = view.incoming, outgoing = view.outgoing) }
                }
                .onFailure { error -> failed(error, "listing the cross-device queue") }
        }
    }

    fun receive(entry: CrossDevicePacketEntry, option: CrossDeviceTransferOption) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            receivePacket(entry, identity.current(), option)
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
                .onFailure { error -> failed(error, "receiving packet ${entry.packetId}") }
        }
    }

    /**
     * S3040: the manual counterpart of the TTL purge that runs on sync - it drops packets older than
     * [CrossDevicePacketManifest.DEFAULT_TTL_DAYS] and nothing else. A packet still inside its TTL
     * belongs to the device that sent it and is claimed per row, never swept from here.
     */
    fun removeExpiredPackets() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            cleanExpiredPackets()
                .onSuccess { removed ->
                    _state.update {
                        it.copy(loading = false, notice = CrossDeviceQueueNotice.ExpiredRemoved(removed))
                    }
                    refresh()
                }
                .onFailure { error -> failed(error, "removing expired cross-device packets") }
        }
    }

    /**
     * S3040: export the local settings through the same builder the S1565 menu uses, then leave
     * them in the Drive queue as a broadcast packet any of the user's devices may claim. The
     * receiver applies the bytes through [ReceiveCrossDevicePacketUseCase], so the payload file
     * name must stay [TransferDataKind.SETTINGS] - it is resolved by exact name there.
     */
    fun sendSettings() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val sent = buildTransferPayload(TransferDataKind.SETTINGS).fold(
                onSuccess = { bytes ->
                    sendPacket(
                        payloadKind = CrossDevicePayloadKind.SETTINGS,
                        sender = identity.current(),
                        target = null,
                        files = listOf(
                            OutgoingPacketFile(
                                name = TransferDataKind.SETTINGS.driveFileName,
                                size = bytes.size.toLong(),
                                mimeType = TransferDataKind.SETTINGS.mimeType,
                                open = bytes::inputStream
                            )
                        )
                    ).map { }
                },
                onFailure = { error -> Result.failure(error) }
            )
            sent.fold(
                onSuccess = {
                    _state.update { it.copy(loading = false, notice = CrossDeviceQueueNotice.SettingsSent) }
                },
                onFailure = { error -> failed(error, "sending the settings packet") }
            )
        }
    }

    /** S4231: the person deletes any packet the queue shows, received or not (contract item CS). */
    fun delete(entry: CrossDevicePacketEntry) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            deletePacket(entry)
                .onSuccess {
                    _state.update { it.copy(notice = CrossDeviceQueueNotice.Deleted) }
                    refresh()
                }
                .onFailure { error -> failed(error, "deleting packet ${entry.packetId}") }
        }
    }

    /** Drop the notice once the dialog has shown it, so a rotation does not repeat the message. */
    fun consumeNotice() {
        _state.update { it.copy(notice = null) }
    }

    private fun failed(error: Throwable, what: String) {
        Timber.e(error, "Cross-device transfer failed while $what")
        _state.update { it.copy(loading = false, notice = noticeFor(error)) }
    }

    override fun onCleared() {
        onHidden()
        super.onCleared()
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        fun noticeFor(error: Throwable): CrossDeviceQueueNotice = when (error) {
            is DriveChannelOff -> CrossDeviceQueueNotice.ChannelOff
            is DriveSignInRequired -> CrossDeviceQueueNotice.SignInRequired
            is DriveDailyLimit -> CrossDeviceQueueNotice.DailyLimit
            is DriveQueueFull -> CrossDeviceQueueNotice.NotEnoughSpace(error.freeBytes)
            is PacketTooLarge -> CrossDeviceQueueNotice.TooLarge(error.limitBytes)
            else -> CrossDeviceQueueNotice.Failed
        }
    }
}
