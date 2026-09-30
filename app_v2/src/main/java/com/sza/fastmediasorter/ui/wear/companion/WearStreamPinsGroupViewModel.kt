package com.sza.fastmediasorter.ui.wear.companion

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.PinnedStreamChannel
import com.sza.fastmediasorter.domain.usecase.PushWearStreamPinsUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObservePinnedStreamChannelsUseCase
import com.sza.fastmediasorter.domain.usecase.streams.ObserveStreamsEnabledUseCase
import com.sza.fastmediasorter.domain.usecase.streams.PinStreamSourceByIdentityUseCase
import com.sza.fastmediasorter.domain.usecase.streams.PinnedStreamMove
import com.sza.fastmediasorter.domain.usecase.streams.ReorderPinnedStreamUseCase
import com.sza.fastmediasorter.domain.usecase.streams.UnpinStreamSourceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S4016: the companion window's watch stream pins group.
 *
 * The list it edits is the phone's own pinned set (strategic ADR-1): every edit here is republished
 * to the watch by [PushWearStreamPinsUseCase.observeAndPush], so [pushNow] exists only for the owner
 * who wants the watch to have the list this moment rather than on the next change. Its own view model,
 * like [WearFaceSlotsViewModel], because WearSyncViewModel is at detekt's constructor ceiling.
 */
@HiltViewModel
class WearStreamPinsGroupViewModel @Inject constructor(
    observeStreamsEnabled: ObserveStreamsEnabledUseCase,
    observePinnedChannels: ObservePinnedStreamChannelsUseCase,
    private val pinByIdentity: PinStreamSourceByIdentityUseCase,
    private val unpinSource: UnpinStreamSourceUseCase,
    private val reorderPinned: ReorderPinnedStreamUseCase,
    private val pushWearStreamPins: PushWearStreamPinsUseCase,
) : ViewModel() {

    val streamsEnabled: StateFlow<Boolean> = observeStreamsEnabled()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    val pinned: StateFlow<List<PinnedStreamChannel>> = observePinnedChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _pushInFlight = MutableStateFlow(false)
    val pushInFlight: StateFlow<Boolean> = _pushInFlight.asStateFlow()

    private val _pushOutcome = MutableStateFlow<Int?>(null)

    /** The caption under the push-now button, or null before the first push of this window. */
    val pushOutcome: StateFlow<Int?> = _pushOutcome.asStateFlow()

    fun pin(identityKey: String) {
        viewModelScope.launch { pinByIdentity(identityKey) }
    }

    fun unpin(id: String) {
        viewModelScope.launch { unpinSource(id) }
    }

    fun move(id: String, move: PinnedStreamMove) {
        viewModelScope.launch { reorderPinned(id, move) }
    }

    fun pushNow() {
        if (_pushInFlight.value) return
        _pushInFlight.value = true
        _pushOutcome.value = null
        viewModelScope.launch {
            val result = pushWearStreamPins()
            result.exceptionOrNull()?.let { Timber.w(it, "Wear stream pins: push-now failed") }
            _pushOutcome.value = pushWording(result)
            _pushInFlight.value = false
        }
    }

    // The use case reports a missing watch through check(), the only IllegalStateException it raises;
    // a Data Layer failure arrives as an ApiException or an IOException instead.
    @StringRes
    private fun pushWording(result: Result<Unit>): Int = when (result.exceptionOrNull()) {
        null -> R.string.wear_stream_pins_push_sent
        is IllegalStateException -> R.string.wear_stream_pins_push_no_watch
        else -> R.string.wear_stream_pins_push_failed
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
