package com.sza.fastmediasorter.ui.flashlight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the one piece of state the flashlight keeps between runs: the glow colour (strategic S1796
 * §5.1.3). Brightness is deliberately absent - it is a window attribute of the running screen and
 * must not survive the program, or a device left dim would come back dim with no visible cause.
 */
@HiltViewModel
class FrontFlashlightViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val glowColor: StateFlow<Int> = settingsRepository.getSettings()
        .map { it.frontFlashlightColor }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppSettings.FRONT_FLASHLIGHT_DEFAULT_COLOR,
        )

    fun setGlowColor(color: Int) {
        viewModelScope.launch {
            settingsRepository.updateSettings { it.copy(frontFlashlightColor = color) }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
