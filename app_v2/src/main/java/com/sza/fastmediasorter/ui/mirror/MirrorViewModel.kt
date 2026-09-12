package com.sza.fastmediasorter.ui.mirror

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.MirrorSettings
import com.sza.fastmediasorter.domain.usecase.mirror.ObserveMirrorSettingsUseCase
import com.sza.fastmediasorter.domain.usecase.mirror.UpdateMirrorSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the three values the mirror keeps between runs (strategic S1924 2.6): the zoom preset, the
 * horizontal flip and whether the backlight is on.
 *
 * Window brightness is deliberately absent, for the same reason it is absent from
 * [com.sza.fastmediasorter.ui.flashlight.FrontFlashlightViewModel]: it is an attribute of the running
 * window, and ADR-2 forbids the system setting outright.
 *
 * Reaches the settings through use cases rather than the repository, so the ViewModel does not skip the
 * use-case layer (S2103).
 */
@HiltViewModel
class MirrorViewModel @Inject constructor(
    observeMirrorSettings: ObserveMirrorSettingsUseCase,
    private val updateMirrorSettings: UpdateMirrorSettingsUseCase,
) : ViewModel() {

    private val settings: StateFlow<MirrorSettings> = observeMirrorSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            // The model's own defaults, never a second copy of them: a hardcoded literal here would
            // drift silently the day a default changes in AppSettings.
            initialValue = MirrorSettings(),
        )

    val zoomRatio: StateFlow<Float> = settings.mapToState { it.zoomRatio }

    val horizontallyFlipped: StateFlow<Boolean> = settings.mapToState { it.horizontallyFlipped }

    val backlightOn: StateFlow<Boolean> = settings.mapToState { it.backlightOn }

    fun setZoomRatio(ratio: Float) {
        viewModelScope.launch { updateMirrorSettings.setZoomRatio(ratio) }
    }

    fun setHorizontallyFlipped(flipped: Boolean) {
        viewModelScope.launch { updateMirrorSettings.setHorizontallyFlipped(flipped) }
    }

    fun setBacklightOn(on: Boolean) {
        viewModelScope.launch { updateMirrorSettings.setBacklightOn(on) }
    }

    private fun <T> StateFlow<MirrorSettings>.mapToState(select: (MirrorSettings) -> T): StateFlow<T> =
        map(select).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = select(value),
        )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
