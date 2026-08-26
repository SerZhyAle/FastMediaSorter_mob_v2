package com.sza.fastmediasorter.wear.ui.apps.systeminfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.usecase.GatherWearSystemInfoUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Reads the watch's system information once, when the screen is opened.
 *
 * Once rather than continuously: this is a report to read, not a monitor. The facts it shows change on
 * the scale of a reboot or a pairing, and a watch that re-polled them while the screen sat open would
 * spend battery to redraw the same lines.
 */
@HiltViewModel
class SystemInfoViewModel @Inject constructor(
    gatherWearSystemInfo: GatherWearSystemInfoUseCase
) : ViewModel() {

    private val state = MutableStateFlow(SystemInfoUiState())
    val uiState: StateFlow<SystemInfoUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val sections = gatherWearSystemInfo()
            Timber.d("S2008: system info opened from Apps, ${sections.size} section(s)")
            state.value = SystemInfoUiState(loading = false, sections = sections)
        }
    }
}
