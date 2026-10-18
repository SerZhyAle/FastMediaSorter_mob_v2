package com.sza.fastmediasorter.wear.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.WearAccountBroadcast
import com.sza.fastmediasorter.wear.domain.model.WearAccountBroadcastSection
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastOpening
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastPlayback
import com.sza.fastmediasorter.wear.domain.model.WearStreamPlaybackTarget
import com.sza.fastmediasorter.wear.domain.usecase.ObserveAccountBroadcastsUseCase
import com.sza.fastmediasorter.wear.domain.usecase.PlayAccountBroadcastUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** What the broadcasts screen draws; a section not yet loaded reads as loading until [isGraceOver]. */
data class WearAccountBroadcastsUiState(
    val sections: List<WearAccountBroadcastSection> = emptyList(),
    val hasAnswered: Boolean = false,
    val isGraceOver: Boolean = false,
)

/** One-shot results of a tap, consumed once by the screen. */
sealed interface WearAccountBroadcastEvent {

    data class OpenPlayer(val target: WearStreamPlaybackTarget) : WearAccountBroadcastEvent

    data class Unavailable(val reason: WearBroadcastPlayback.Reason) : WearAccountBroadcastEvent
}

/**
 * S4229: the account's broadcasts of every exchange entry (contract DEVICE-EXCHANGE item CI). The list is read
 * only while this ViewModel's flow is collected (S4339: after [SUBSCRIPTION_GRACE_MS] without a subscriber the
 * upstream is cancelled, which is what drops the directory's credentials), the person picks, and nothing here
 * starts a broadcast by itself.
 */
@HiltViewModel
class WearAccountBroadcastsViewModel @Inject constructor(
    observeBroadcasts: ObserveAccountBroadcastsUseCase,
    private val playBroadcast: PlayAccountBroadcastUseCase,
) : ViewModel() {

    private val graceOver = MutableStateFlow(false)

    val uiState: StateFlow<WearAccountBroadcastsUiState> = combine(
        observeBroadcasts()
            .catch { Timber.w(it, "WearAccountBroadcastsViewModel: the list flow failed") },
        graceOver,
    ) { sections, isGraceOver ->
        WearAccountBroadcastsUiState(sections = sections, hasAnswered = true, isGraceOver = isGraceOver)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS),
        initialValue = WearAccountBroadcastsUiState(),
    )

    private val channel = Channel<WearAccountBroadcastEvent>(Channel.BUFFERED)
    val events: Flow<WearAccountBroadcastEvent> = channel.receiveAsFlow()

    private var playing: Job? = null

    init {
        viewModelScope.launch {
            delay(LOADING_GRACE_MS)
            graceOver.value = true
        }
    }

    /** A second tap while the first resolves is a no-op, so one pick never opens two players. */
    fun play(broadcast: WearAccountBroadcast, fallbackTitle: String) {
        if (playing?.isActive == true) return
        Timber.d("S4229: a broadcast was picked from the account list")
        playing = viewModelScope.launch {
            // The resolver moves its own blocking work (the LAN probe, the bridge) onto the IO dispatcher.
            val opening = playBroadcast(broadcast, fallbackTitle)
            channel.send(
                when (opening) {
                    is WearBroadcastOpening.Open -> WearAccountBroadcastEvent.OpenPlayer(opening.target)
                    is WearBroadcastOpening.Unavailable -> WearAccountBroadcastEvent.Unavailable(opening.reason)
                },
            )
        }
    }

    private companion object {
        // A server that has not answered by now is shown as failed instead of loading for ever.
        const val LOADING_GRACE_MS = 15_000L

        // S4339: how long the last subscriber's absence leaves the directory - and its credentials - collected.
        const val SUBSCRIPTION_GRACE_MS = 5_000L
    }
}
