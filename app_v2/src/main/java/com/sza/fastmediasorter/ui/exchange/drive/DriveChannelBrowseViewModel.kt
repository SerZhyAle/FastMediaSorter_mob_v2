package com.sza.fastmediasorter.ui.exchange.drive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.transfer.DriveDailyLimit
import com.sza.fastmediasorter.domain.model.transfer.DriveListingEntry
import com.sza.fastmediasorter.domain.model.transfer.DriveRefusalReason
import com.sza.fastmediasorter.domain.model.transfer.DriveSignInRequired
import com.sza.fastmediasorter.domain.transfer.DriveFileChannel
import com.sza.fastmediasorter.domain.transfer.DriveProducerTarget
import com.sza.fastmediasorter.domain.transfer.DriveRequestProgress
import com.sza.fastmediasorter.domain.usecase.transfer.ResolveDriveProducerUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S4231: what the browse-via-Google-Drive dialog says above its list (strategic 3.4). */
sealed interface DriveBrowseStatus {
    data class Waiting(val producerName: String, val lastSeenMs: Long?) : DriveBrowseStatus

    data class TimedOut(val producerName: String) : DriveBrowseStatus

    data class Saved(val count: Int) : DriveBrowseStatus

    data object Empty : DriveBrowseStatus

    data object Locked : DriveBrowseStatus

    data object NotPermitted : DriveBrowseStatus

    data object Unavailable : DriveBrowseStatus

    data object TooLarge : DriveBrowseStatus

    data object DriveFull : DriveBrowseStatus

    data object ChannelOff : DriveBrowseStatus

    data object SignInRequired : DriveBrowseStatus

    data object DailyLimit : DriveBrowseStatus

    data object Failed : DriveBrowseStatus
}

/** S4231: the folder shown, its entries (folders first), the files picked and the line above them. */
data class DriveBrowseUiState(
    val path: List<String> = emptyList(),
    val entries: List<DriveListingEntry> = emptyList(),
    val selected: Set<String> = emptySet(),
    val status: DriveBrowseStatus? = null,
    val busy: Boolean = false
) {
    val canGoUp: Boolean get() = path.isNotEmpty()
}

/**
 * S4231: browses a folder another own device published, through `REQUEST`/`REPLY` packets on Google
 * Drive (contract DEVICE-EXCHANGE item CO), and saves picked files into the incoming folder. Leaving the
 * screen cancels the request in flight, which deletes it from the queue (item CR).
 */
@HiltViewModel
class DriveChannelBrowseViewModel @Inject constructor(
    private val channel: DriveFileChannel,
    private val resolveProducer: ResolveDriveProducerUseCase,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val exchangeResourceId: String? = savedState[ARG_EXCHANGE_RESOURCE_ID]
    private val resourceUri: String = savedState[ARG_RESOURCE_URI] ?: ""
    private var producer: DriveProducerTarget? = null
    private val rootPath: List<String>
        get() = producer?.rootPath.orEmpty().split('/').filter { it.isNotEmpty() }

    private val _state = MutableStateFlow(DriveBrowseUiState())
    val state: StateFlow<DriveBrowseUiState> = _state.asStateFlow()

    private var request: Job? = null
    private var lastAction: () -> Unit = { open() }

    /** The name of the device that answers, once it is known. */
    val producerName: String get() = producer?.deviceName.orEmpty()

    /** Find who published the resource, then list its root; a resource nobody publishes is said in words. */
    fun open() {
        lastAction = { open() }
        _state.update { it.copy(busy = true, status = null) }
        viewModelScope.launch {
            val target = producer ?: resolveProducer(exchangeResourceId, resourceUri)
            producer = target
            if (target == null) {
                _state.update { it.copy(busy = false, status = DriveBrowseStatus.Unavailable) }
            } else {
                list(emptyList())
            }
        }
    }

    fun enter(folder: String) = list(_state.value.path + folder)

    /** Never above the resource root: the root is all the producer serves (item CO). */
    fun up() {
        val path = _state.value.path
        if (path.isNotEmpty()) list(path.dropLast(1))
    }

    fun toggle(name: String) {
        _state.update { state ->
            val selected = if (name in state.selected) state.selected - name else state.selected + name
            state.copy(selected = selected)
        }
    }

    fun retry() = lastAction()

    fun saveSelected() {
        val state = _state.value
        if (state.selected.isEmpty()) return
        val paths = state.selected.map { joined(rootPath + state.path + it) }
        lastAction = { saveSelected() }
        val target = producer ?: return
        follow { channel.fetch(target.deviceId, target.resourceId, paths) }
    }

    private fun list(path: List<String>) {
        lastAction = { list(path) }
        _state.update { it.copy(path = path, entries = emptyList(), selected = emptySet()) }
        val target = producer ?: return
        follow { channel.list(target.deviceId, target.resourceId, joined(rootPath + path)) }
    }

    private fun follow(start: () -> Flow<DriveRequestProgress>) {
        request?.cancel()
        _state.update { it.copy(busy = true, status = null) }
        var placed = 0
        request = viewModelScope.launch {
            start().collect { progress ->
                if (progress is DriveRequestProgress.Files) placed += progress.placed.size
                _state.update { reduce(it, progress, placed) }
            }
        }
    }

    private fun reduce(state: DriveBrowseUiState, progress: DriveRequestProgress, placed: Int): DriveBrowseUiState =
        when (progress) {
            is DriveRequestProgress.Waiting ->
                state.copy(status = DriveBrowseStatus.Waiting(progress.producerName, progress.lastSeenMs))
            is DriveRequestProgress.Listing -> {
                val entries = progress.listing.entries.sortedWith(compareBy({ !it.dir }, { it.name.lowercase() }))
                state.copy(
                    entries = entries,
                    busy = false,
                    status = if (entries.isEmpty()) DriveBrowseStatus.Empty else null
                )
            }
            is DriveRequestProgress.Files -> state.copy(
                busy = !progress.final,
                selected = if (progress.final) emptySet() else state.selected,
                status = if (progress.final) DriveBrowseStatus.Saved(placed) else state.status
            )
            is DriveRequestProgress.Refused -> state.copy(busy = false, status = statusFor(progress.reason))
            is DriveRequestProgress.TimedOut ->
                state.copy(busy = false, status = DriveBrowseStatus.TimedOut(producerName))
            DriveRequestProgress.ChannelOff -> state.copy(busy = false, status = DriveBrowseStatus.ChannelOff)
            is DriveRequestProgress.Failed -> state.copy(busy = false, status = statusFor(progress.error))
        }

    companion object {
        const val ARG_EXCHANGE_RESOURCE_ID = "exchangeResourceId"
        const val ARG_RESOURCE_URI = "resourceUri"

        private fun joined(segments: List<String>): String = segments.joinToString("/")

        fun statusFor(reason: DriveRefusalReason): DriveBrowseStatus = when (reason) {
            DriveRefusalReason.PIN_REQUIRED -> DriveBrowseStatus.Locked
            DriveRefusalReason.NOT_PERMITTED -> DriveBrowseStatus.NotPermitted
            DriveRefusalReason.UNAVAILABLE -> DriveBrowseStatus.Unavailable
            DriveRefusalReason.TOO_LARGE -> DriveBrowseStatus.TooLarge
            DriveRefusalReason.DRIVE_FULL -> DriveBrowseStatus.DriveFull
        }

        fun statusFor(error: Throwable): DriveBrowseStatus = when (error) {
            is DriveSignInRequired -> DriveBrowseStatus.SignInRequired
            is DriveDailyLimit -> DriveBrowseStatus.DailyLimit
            else -> DriveBrowseStatus.Failed
        }
    }
}
