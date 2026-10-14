package com.sza.fastmediasorter.ui.tourist.counter

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.tourist.TouristCounterState
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveTouristCounterUseCase
import com.sza.fastmediasorter.domain.usecase.tourist.UpdateTouristCounterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** S4184: the Tourist tally counter screen. */
@HiltViewModel
class TouristCounterViewModel @Inject constructor(
    observeTouristCounterUseCase: ObserveTouristCounterUseCase,
    private val updateTouristCounterUseCase: UpdateTouristCounterUseCase,
) : ViewModel() {

    val state: StateFlow<TouristCounterState> = observeTouristCounterUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TouristCounterState())

    fun add(delta: Long) {
        viewModelScope.launch { updateTouristCounterUseCase.add(delta) }
    }

    fun reset() {
        timber.log.Timber.d("S4184: counter reset confirmed")
        viewModelScope.launch { updateTouristCounterUseCase.reset() }
    }

    fun setStartValue(start: Long) {
        viewModelScope.launch { updateTouristCounterUseCase.setStartValue(start) }
    }

    fun setExtraStepsVisible(visible: Boolean) {
        viewModelScope.launch { updateTouristCounterUseCase.setExtraStepsVisible(visible) }
    }

    fun clearHistory() {
        viewModelScope.launch { updateTouristCounterUseCase.clearHistory() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
