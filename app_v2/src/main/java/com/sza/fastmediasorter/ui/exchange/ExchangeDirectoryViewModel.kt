package com.sza.fastmediasorter.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.AttachmentPin
import com.sza.fastmediasorter.domain.model.ExchangeAttachResult
import com.sza.fastmediasorter.domain.model.ExchangeAttachmentState
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryView
import com.sza.fastmediasorter.domain.model.ExchangeListedResource
import com.sza.fastmediasorter.domain.model.ExchangePinAttempt
import com.sza.fastmediasorter.domain.model.ExchangeResourceAccess
import com.sza.fastmediasorter.domain.transfer.DriveChannelSettings
import com.sza.fastmediasorter.domain.usecase.exchange.AttachExchangeResourcesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line of the "From my devices" list. */
sealed interface ExchangeDirectoryRow {

    data class Device(
        val title: String,
        val product: String?,
        val online: Boolean,
        /** Android paused the device's sharing (26 item DC): the row says so instead of plain offline. */
        val paused: Boolean = false,
    ) : ExchangeDirectoryRow

    data class Resource(
        val item: ExchangeListedResource,
        val attached: Boolean,
        val checked: Boolean,
        /** Attached, and its owner wants the PIN again (17 item AI). */
        val pinNeeded: Boolean = false,
        /** Protected, its access withheld because this entry never declared `pin-v1`: signing in again fixes it. */
        val signInNeeded: Boolean = false,
    ) : ExchangeDirectoryRow {
        val binding: ExchangeBinding get() = ExchangeBinding(item.sourceKey, item.record.resourceId)
        val checkable: Boolean
            get() = !attached &&
                (item.access == ExchangeResourceAccess.ATTACHABLE || item.access == ExchangeResourceAccess.PIN_REQUIRED)

        /** A tap on an attached resource that waits for its PIN opens the prompt. */
        val asksPin: Boolean get() = attached && pinNeeded
    }

    data object AttachedHeader : ExchangeDirectoryRow

    /** An attached resource the open listing does not show as live: offline, unread or gone (9.3). */
    data class Attached(val name: String, val state: ExchangeAttachmentState) : ExchangeDirectoryRow
}

sealed interface ExchangeDirectoryState {
    data object Closed : ExchangeDirectoryState
    data object Loading : ExchangeDirectoryState

    /** [unreachable] counts the stores that could not be read; [pending] - some have not answered yet. */
    data class Loaded(
        val rows: List<ExchangeDirectoryRow>,
        val unreachable: Int,
        val pending: Boolean,
        val anyAttachable: Boolean,
    ) : ExchangeDirectoryState
}

/** Where the PIN request stands; the PIN itself is never part of a state (17 item AI). */
enum class ExchangePinPhase { IDLE, CHECKING, REFUSED, UNREACHABLE, FAILED }

/** What the PIN dialog shows. [renewing] is a resource that is already attached and asks for its PIN again. */
sealed interface ExchangePinPrompt {
    data object None : ExchangePinPrompt

    /** [ticket] tells one request from the next one for a resource of the same name. */
    data class Asking(
        val resourceName: String,
        val renewing: Boolean,
        val ticket: Int,
        val phase: ExchangePinPhase = ExchangePinPhase.IDLE,
    ) : ExchangePinPrompt
}

/**
 * The "From my devices" dialog of the add-resource wizard (S4116 phase 05, strategic 6.3): the account's resources
 * by device with presence, marks, and attach. The directory is watched only between [open] and [close].
 *
 * A protected resource is attached through the PIN prompt (S4201): after the unprotected marks are attached,
 * each protected one asks for its PIN in list order; a skipped one stays unattached.
 */
@HiltViewModel
class ExchangeDirectoryViewModel @Inject constructor(
    private val directory: AttachExchangeResourcesUseCase,
    driveChannelSettings: DriveChannelSettings,
) : ViewModel() {

    /** S4231: with the Drive channel on, a resource listed only by its Drive record is marked so. */
    val driveChannelOn: StateFlow<Boolean> = driveChannelSettings.enabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _state = MutableStateFlow<ExchangeDirectoryState>(ExchangeDirectoryState.Closed)
    val state: StateFlow<ExchangeDirectoryState> = _state.asStateFlow()

    private val _pinPrompt = MutableStateFlow<ExchangePinPrompt>(ExchangePinPrompt.None)
    val pinPrompt: StateFlow<ExchangePinPrompt> = _pinPrompt.asStateFlow()

    private val _messages = Channel<MyDevicesMessage>(Channel.BUFFERED)
    val messages: Flow<MyDevicesMessage> = _messages.receiveAsFlow()

    private var view: ExchangeDirectoryView = ExchangeDirectoryView.EMPTY
    private val selection = MutableStateFlow<Set<ExchangeBinding>>(emptySet())
    private var watch: Job? = null

    // Set before the collector starts: a listing that is already there is delivered while launch is still running.
    private var watching = false
    private var proving: Job? = null
    private val waiting = ArrayDeque<ExchangeBinding>()
    private var promptFor: ExchangeBinding? = null
    private var renewing = false
    private var tickets = 0

    fun open() {
        watch?.cancel()
        selection.value = emptySet()
        _state.value = ExchangeDirectoryState.Loading
        watching = true
        watch = viewModelScope.launch {
            directory.observe().collect { fresh ->
                view = fresh
                render()
            }
        }
    }

    fun close() {
        watch?.cancel()
        watch = null
        watching = false
        proving?.cancel()
        waiting.clear()
        promptFor = null
        _pinPrompt.value = ExchangePinPrompt.None
        view = ExchangeDirectoryView.EMPTY
        selection.value = emptySet()
        _state.value = ExchangeDirectoryState.Closed
    }

    fun toggle(row: ExchangeDirectoryRow.Resource, checked: Boolean) {
        if (!row.checkable) return
        selection.update { if (checked) it + row.binding else it - row.binding }
        render()
    }

    fun attachSelected() {
        val chosen = selection.value
        val items = attachable().filter { ExchangeBinding(it.sourceKey, it.record.resourceId) in chosen }
        if (items.isEmpty()) {
            _messages.trySend(MyDevicesMessage(R.string.exchange_directory_nothing_selected))
            return
        }
        attach(items)
    }

    fun attachAll() {
        val items = attachable()
        if (items.isEmpty()) {
            _messages.trySend(MyDevicesMessage(R.string.exchange_directory_empty))
            return
        }
        attach(items)
    }

    /** Presents [pin] for the resource the prompt is open for; the PIN is not kept here. */
    fun submitPin(pin: String) {
        val binding = promptFor
        val item = binding?.let { view.listing.resource(it.sourceKey, it.resourceId) }
        if (item == null) {
            skipPin()
        } else if (pin.isNotEmpty() && proving?.isActive != true) {
            setPhase(ExchangePinPhase.CHECKING)
            proving = viewModelScope.launch { present(item, binding, pin) }
        }
    }

    /** Drops the open prompt; the resource stays unattached, or keeps asking from the list. */
    fun skipPin() {
        proving?.cancel()
        nextPrompt()
    }

    /** A tap on an attached resource that waits for its PIN. */
    fun askPin(row: ExchangeDirectoryRow.Resource) {
        if (!row.asksPin || proving?.isActive == true) return
        waiting.clear()
        promptFor = row.binding
        renewing = true
        _pinPrompt.value = ExchangePinPrompt.Asking(row.item.record.name, renewing = true, ticket = ++tickets)
    }

    private fun attach(items: List<ExchangeListedResource>) {
        viewModelScope.launch {
            val result = directory.attach(items)
            report(result)
            selection.value = emptySet()
            view = directory.sync(view.listing)
            render()
            waiting.addAll(result.needPin)
            if (_pinPrompt.value == ExchangePinPrompt.None) nextPrompt()
        }
    }

    private suspend fun present(item: ExchangeListedResource, binding: ExchangeBinding, pin: String) {
        val outcome = if (renewing) directory.renewPin(item, pin) else directory.attachProtected(item, pin)
        when (outcome) {
            ExchangePinAttempt.ACCEPTED -> {
                if (!renewing) {
                    _messages.trySend(MyDevicesMessage(R.string.exchange_directory_attached_result, listOf(1)))
                }
                selection.update { it - binding }
                view = directory.sync(view.listing)
                render()
                nextPrompt()
            }
            ExchangePinAttempt.REFUSED -> setPhase(ExchangePinPhase.REFUSED)
            ExchangePinAttempt.UNREACHABLE -> setPhase(ExchangePinPhase.UNREACHABLE)
            ExchangePinAttempt.FAILED -> setPhase(ExchangePinPhase.FAILED)
        }
    }

    // A resource the listing no longer shows cannot be proven, so its prompt is dropped without asking.
    private fun nextPrompt() {
        var next = waiting.removeFirstOrNull()
        while (next != null && view.listing.resource(next.sourceKey, next.resourceId) == null) {
            next = waiting.removeFirstOrNull()
        }
        promptFor = next
        renewing = false
        val name = next?.let { view.listing.resource(it.sourceKey, it.resourceId)?.record?.name }
        _pinPrompt.value = name?.let { ExchangePinPrompt.Asking(it, renewing = false, ticket = ++tickets) }
            ?: ExchangePinPrompt.None
    }

    private fun setPhase(phase: ExchangePinPhase) {
        _pinPrompt.update { current ->
            if (current is ExchangePinPrompt.Asking) current.copy(phase = phase) else current
        }
    }

    private fun report(result: ExchangeAttachResult) {
        if (result.attached > 0) {
            _messages.trySend(MyDevicesMessage(R.string.exchange_directory_attached_result, listOf(result.attached)))
        }
        if (result.failed > 0) {
            _messages.trySend(MyDevicesMessage(R.string.exchange_directory_attach_failed, listOf(result.failed)))
        }
    }

    private fun attachable(): List<ExchangeListedResource> = view.listing.devices
        .flatMap { it.resources }
        .filter {
            (it.access == ExchangeResourceAccess.ATTACHABLE || it.access == ExchangeResourceAccess.PIN_REQUIRED) &&
                !view.isAttached(it)
        }

    private fun render() {
        if (!watching) return
        val chosen = selection.value
        val listed = view.listing.devices.flatMap { device ->
            listOf<ExchangeDirectoryRow>(
                ExchangeDirectoryRow.Device(
                    title = device.device?.deviceName ?: device.deviceId,
                    product = device.device?.product,
                    online = device.online,
                    paused = device.paused,
                )
            ) + device.resources.map { item -> resourceRow(item, chosen) }
        }
        // A listed attachment is already its own row above; only the ones the listing lacks need one here.
        val elsewhere = view.attachments
            .filter { view.listing.resource(it.binding.sourceKey, it.binding.resourceId) == null }
            .map { ExchangeDirectoryRow.Attached(it.name, it.state) }
        val rows = if (elsewhere.isEmpty()) listed else listed + ExchangeDirectoryRow.AttachedHeader + elsewhere
        _state.value = ExchangeDirectoryState.Loaded(
            rows = rows,
            unreachable = view.listing.unreachableSources.size,
            pending = view.listing.pending,
            anyAttachable = attachable().isNotEmpty(),
        )
    }

    private fun resourceRow(item: ExchangeListedResource, chosen: Set<ExchangeBinding>): ExchangeDirectoryRow.Resource {
        val binding = ExchangeBinding(item.sourceKey, item.record.resourceId)
        val attached = view.isAttached(item)
        return ExchangeDirectoryRow.Resource(
            item = item,
            attached = attached,
            checked = binding in chosen,
            pinNeeded = attached && view.attachment(binding)?.pin == AttachmentPin.NEEDED,
            signInNeeded = item.access == ExchangeResourceAccess.PIN_NO_ACCESS &&
                item.sourceKey in view.signInSources,
        )
    }
}
