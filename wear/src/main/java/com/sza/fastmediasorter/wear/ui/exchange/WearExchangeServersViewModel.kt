package com.sza.fastmediasorter.wear.ui.exchange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeAccountRepository
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeEntry
import com.sza.fastmediasorter.wear.data.exchange.WearExchangeRemoval
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** What the screen draws over the list, one at a time; [None] shows the list itself. */
sealed interface WearExchangeServersOverlay {
    data object None : WearExchangeServersOverlay

    /** The actions of one entry: make it outgoing, or remove it. */
    data class Actions(val entry: WearExchangeEntry) : WearExchangeServersOverlay

    data class ConfirmRemoval(val entry: WearExchangeEntry) : WearExchangeServersOverlay

    data object Removing : WearExchangeServersOverlay

    data class Removed(val removal: WearExchangeRemoval) : WearExchangeServersOverlay
}

/**
 * S4158: the watch's exchange servers - view, choose the outgoing one, remove. Adding exists only on the phone
 * (strategic goal 8), so nothing here creates an entry.
 */
@HiltViewModel
class WearExchangeServersViewModel @Inject constructor(
    private val accounts: WearExchangeAccountRepository,
) : ViewModel() {

    /** Null until the store was read once, so the screen never flashes the empty state over a full list. */
    val entries: StateFlow<List<WearExchangeEntry>?> = accounts.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    private val mutableOverlay = MutableStateFlow<WearExchangeServersOverlay>(WearExchangeServersOverlay.None)
    val overlay: StateFlow<WearExchangeServersOverlay> = mutableOverlay.asStateFlow()

    fun open(entry: WearExchangeEntry) {
        mutableOverlay.value = WearExchangeServersOverlay.Actions(entry)
    }

    fun makeOutgoing(entry: WearExchangeEntry) {
        mutableOverlay.value = WearExchangeServersOverlay.None
        viewModelScope.launch { accounts.setOutgoing(entry) }
    }

    fun askRemoval(entry: WearExchangeEntry) {
        mutableOverlay.value = WearExchangeServersOverlay.ConfirmRemoval(entry)
    }

    fun remove(entry: WearExchangeEntry) {
        mutableOverlay.value = WearExchangeServersOverlay.Removing
        viewModelScope.launch {
            val removal = accounts.remove(entry)
            mutableOverlay.update { WearExchangeServersOverlay.Removed(removal) }
        }
    }

    fun dismiss() {
        mutableOverlay.value = WearExchangeServersOverlay.None
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
