package com.sza.fastmediasorter.ui.sos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.core.di.ApplicationScope
import com.sza.fastmediasorter.domain.model.sos.SosMode
import com.sza.fastmediasorter.domain.usecase.sos.GetSosModeUseCase
import com.sza.fastmediasorter.domain.usecase.sos.SaveSosModeUseCase
import com.sza.fastmediasorter.domain.usecase.wear.SendSosCommandToWatchUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S3216: holds the mode the phone's distress signal runs in, and the strobe phase the screen flashes on.
 *
 * The mode is persisted rather than kept for the window's lifetime: the next emergency starts in the mode
 * the last one ended in, which is the only default the owner can have expressed without being asked a
 * question while something is wrong.
 *
 * The activity reads the strobe phase from here rather than from [SosTorchManager] directly, so the host
 * never reaches past the layer above it for state.
 */
@HiltViewModel
class SosViewModel @Inject constructor(
    private val getSosMode: GetSosModeUseCase,
    private val saveSosMode: SaveSosModeUseCase,
    private val sendSosCommandToWatch: SendSosCommandToWatchUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope,
    torchManager: SosTorchManager,
) : ViewModel() {

    private val selectedMode = MutableStateFlow<SosMode?>(null)

    /** The chosen mode, or null until the stored one has been read. */
    val mode: StateFlow<SosMode?> = selectedMode.asStateFlow()

    /** Whether the signal is in an engaged span right now, so the window can flash with it. */
    val isLit: StateFlow<Boolean> = torchManager.isLit

    /**
     * Adopts [requested] when a caller named one - a launch from the paired watch does - and otherwise
     * falls back to the stored mode.
     *
     * A mode the watch named is NOT echoed back to it: the watch is already signalling in that mode, and
     * answering its own command would be the start of a loop between the two devices (§06.4).
     */
    fun resolveInitialMode(requested: SosMode?) {
        if (selectedMode.value != null) return
        if (requested != null) {
            selectedMode.value = requested
            viewModelScope.launch { saveSosMode(requested) }
            return
        }
        viewModelScope.launch {
            val stored = getSosMode()
            selectedMode.value = stored
            sendSosCommandToWatch.start(stored)
        }
    }

    /** Records [mode] as the chosen one, persists it, and asks the paired watch for the same mode. */
    fun select(mode: SosMode) {
        if (selectedMode.value == mode) return
        selectedMode.value = mode
        viewModelScope.launch {
            saveSosMode(mode)
            sendSosCommandToWatch.start(mode)
        }
    }

    /**
     * Asks the paired watch to stop as well (§3.3: switching off either device ends the signal on both).
     *
     * On the application scope rather than [viewModelScope]: the window is closing as this is called,
     * and a command cancelled with the scope would leave the watch sounding alone.
     */
    fun requestStopEverywhere() {
        applicationScope.launch { sendSosCommandToWatch.stop() }
    }
}
