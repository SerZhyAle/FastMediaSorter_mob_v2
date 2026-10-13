package com.sza.fastmediasorter.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ExchangeAttachResult
import com.sza.fastmediasorter.domain.model.ExchangeAttachmentState
import com.sza.fastmediasorter.domain.model.ExchangeBinding
import com.sza.fastmediasorter.domain.model.ExchangeDirectoryView
import com.sza.fastmediasorter.domain.model.ExchangeListedResource
import com.sza.fastmediasorter.domain.model.ExchangeResourceAccess
import com.sza.fastmediasorter.domain.usecase.exchange.AttachExchangeResourcesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One line of the "From my devices" list. */
sealed interface ExchangeDirectoryRow {

    data class Device(val title: String, val product: String?, val online: Boolean) : ExchangeDirectoryRow

    data class Resource(
        val item: ExchangeListedResource,
        val attached: Boolean,
        val checked: Boolean,
    ) : ExchangeDirectoryRow {
        val binding: ExchangeBinding get() = ExchangeBinding(item.sourceKey, item.record.resourceId)
        val checkable: Boolean get() = !attached && item.access == ExchangeResourceAccess.ATTACHABLE
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

/**
 * The "From my devices" dialog of the add-resource wizard (S4116 phase 05, strategic 6.3): the account's resources
 * by device with presence, marks, and attach. The directory is watched only between [open] and [close].
 */
@HiltViewModel
class ExchangeDirectoryViewModel @Inject constructor(
    private val directory: AttachExchangeResourcesUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ExchangeDirectoryState>(ExchangeDirectoryState.Closed)
    val state: StateFlow<ExchangeDirectoryState> = _state.asStateFlow()

    private val _messages = Channel<MyDevicesMessage>(Channel.BUFFERED)
    val messages: Flow<MyDevicesMessage> = _messages.receiveAsFlow()

    private var view: ExchangeDirectoryView = ExchangeDirectoryView.EMPTY
    private val selection = MutableStateFlow<Set<ExchangeBinding>>(emptySet())
    private var watch: Job? = null

    fun open() {
        watch?.cancel()
        selection.value = emptySet()
        _state.value = ExchangeDirectoryState.Loading
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

    private fun attach(items: List<ExchangeListedResource>) {
        viewModelScope.launch {
            report(directory.attach(items))
            selection.value = emptySet()
            view = directory.sync(view.listing)
            render()
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
        .filter { it.access == ExchangeResourceAccess.ATTACHABLE && !view.isAttached(it) }

    private fun render() {
        if (watch == null) return
        val chosen = selection.value
        val listed = view.listing.devices.flatMap { device ->
            listOf<ExchangeDirectoryRow>(
                ExchangeDirectoryRow.Device(
                    title = device.device?.deviceName ?: device.deviceId,
                    product = device.device?.product,
                    online = device.online,
                )
            ) + device.resources.map { item ->
                val attached = view.isAttached(item)
                ExchangeDirectoryRow.Resource(
                    item,
                    attached,
                    ExchangeBinding(item.sourceKey, item.record.resourceId) in chosen
                )
            }
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
}
