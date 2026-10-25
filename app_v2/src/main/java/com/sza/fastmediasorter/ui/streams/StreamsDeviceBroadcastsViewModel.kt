package com.sza.fastmediasorter.ui.streams

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.ExchangeDeviceBroadcast
import com.sza.fastmediasorter.domain.model.ExchangeDeviceBroadcasts
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.usecase.exchange.ObserveDeviceBroadcastsUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ImportStreamBroadcastUseCase
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeMessageText
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
import timber.log.Timber
import javax.inject.Inject

/** The "Broadcasts of my devices" section: [listed] is null until the first answer of an open section. */
data class DeviceBroadcastsSectionState(
    val expanded: Boolean = false,
    val listed: ExchangeDeviceBroadcasts? = null,
)

/**
 * The streams list section of S4116 phase 07 (strategic 6.3): the account's live broadcasts, each imported as a
 * live channel on a tap (DEVICE-EXCHANGE 9.5). The directory is watched only while the section is expanded and
 * the screen is started, so a closed section holds no server stream.
 */
@HiltViewModel
class StreamsDeviceBroadcastsViewModel @Inject constructor(
    private val observeBroadcasts: ObserveDeviceBroadcastsUseCase,
    private val importBroadcast: ImportStreamBroadcastUseCase,
) : ViewModel() {

    val available: StateFlow<Boolean> = observeBroadcasts.hasAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    private val _state = MutableStateFlow(DeviceBroadcastsSectionState())
    val state: StateFlow<DeviceBroadcastsSectionState> = _state.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages: Flow<Int> = _messages.receiveAsFlow()

    private var watch: Job? = null
    private var started = false

    fun toggle() {
        _state.update { it.copy(expanded = !it.expanded) }
        syncWatch()
    }

    fun onScreenStarted() {
        started = true
        syncWatch()
    }

    fun onScreenStopped() {
        started = false
        syncWatch()
    }

    fun onBroadcastChosen(item: ExchangeDeviceBroadcast) {
        if (!item.playable) {
            _messages.trySend(R.string.streams_device_broadcast_unsupported)
            return
        }
        viewModelScope.launch { _messages.send(messageFor(importBroadcast(item.descriptorJson))) }
    }

    private fun syncWatch() {
        val wanted = started && _state.value.expanded
        if (!wanted) {
            watch?.cancel()
            watch = null
            return
        }
        if (watch != null) return
        _state.update { it.copy(listed = null) }
        watch = viewModelScope.launch {
            observeBroadcasts().collect { listed -> _state.update { it.copy(listed = listed) } }
        }
    }

    @StringRes
    private fun messageFor(result: ImportStreamBroadcastUseCase.ImportResult): Int = when (result) {
        ImportStreamBroadcastUseCase.ImportResult.Success,
        is ImportStreamBroadcastUseCase.ImportResult.Waiting,
        -> R.string.broadcast_import_success
        ImportStreamBroadcastUseCase.ImportResult.Updated -> R.string.broadcast_import_refreshed
        ImportStreamBroadcastUseCase.ImportResult.Duplicate -> R.string.streams_device_broadcast_already_added
        ImportStreamBroadcastUseCase.ImportResult.InvalidUrl,
        ImportStreamBroadcastUseCase.ImportResult.InvalidDescriptor,
        ImportStreamBroadcastUseCase.ImportResult.Expired,
        -> R.string.broadcast_import_malformed
        ImportStreamBroadcastUseCase.ImportResult.UnsupportedVersion -> {
            Timber.i("LIVE-BROADCAST: device broadcast schema above supported, format-unsupported")
            ExchangeMessageText.what(ExchangeMessage.FORMAT_UNSUPPORTED)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
