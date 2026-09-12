package com.sza.fastmediasorter.wear.ui.apps.systeminfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.usecase.GatherWearSystemInfoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Reads the watch's system information when the screen opens, and again whenever the user asks.
 *
 * The read used to be a one-shot on the reasoning that these facts change on the scale of a reboot or
 * a pairing. That stopped being true once the report gained the health section: thermal state, battery
 * voltage, uptime and the background-restriction flag all move while the screen sits open, and the
 * owner chose a manual refresh over continuous polling (S2165 §6 question 6) - a watch that re-polled
 * on a timer would spend battery on a screen opened precisely because the battery is behaving oddly.
 */
@HiltViewModel
class SystemInfoViewModel @Inject constructor(
    private val gatherWearSystemInfo: GatherWearSystemInfoUseCase
) : ViewModel() {

    private val state = MutableStateFlow(SystemInfoUiState())
    val uiState: StateFlow<SystemInfoUiState> = state.asStateFlow()

    init {
        read()
    }

    /** Ignored while a read is already in flight - the report cannot be more current than the read. */
    fun refresh() {
        Timber.d("S2165: manual refresh requested")
        if (state.value.refreshing) {
            return
        }
        state.update { current -> current.copy(refreshing = true) }
        read()
    }

    private fun read() {
        viewModelScope.launch {
            val sections = gatherWearSystemInfo()
            state.value = SystemInfoUiState(loading = false, refreshing = false, sections = sections)
        }
    }
}
