package com.sza.fastmediasorter.wear.ui.apps.tourist.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.tourist.WearTouristCounterState
import com.sza.fastmediasorter.wear.domain.usecase.ObserveWearTouristCounterUseCase
import com.sza.fastmediasorter.wear.domain.usecase.UpdateWearTouristCounterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

/** S4185: the watch Tourist counter screen - every tap goes straight to the persisted counter. */
@HiltViewModel
class TouristCounterViewModel @Inject constructor(
    observeCounter: ObserveWearTouristCounterUseCase,
    private val updateCounter: UpdateWearTouristCounterUseCase,
) : ViewModel() {

    val state: StateFlow<WearTouristCounterState> = observeCounter().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = WearTouristCounterState(),
    )

    fun add(delta: Long) {
        viewModelScope.launch { updateCounter.add(delta) }
    }

    fun reset() {
        viewModelScope.launch { updateCounter.reset() }
    }

    fun setExtraStepsVisible(visible: Boolean) {
        viewModelScope.launch { updateCounter.setExtraStepsVisible(visible) }
    }

    fun clearHistory() {
        viewModelScope.launch { updateCounter.clearHistory() }
    }

    /**
     * Applies a typed starting number and answers whether it was one. The watch keyboard is a text keyboard,
     * so anything but a non-negative whole number within the counter's ceiling is refused unchanged.
     */
    fun setStartValue(text: String): Boolean {
        val start = text.trim().toLongOrNull()?.takeIf { it in 0L..WearTouristCounterState.MAX_VALUE }
        if (start != null) {
            viewModelScope.launch { updateCounter.setStartValue(start) }
        }
        return start != null
    }
}
