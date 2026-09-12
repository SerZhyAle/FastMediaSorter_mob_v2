package com.sza.fastmediasorter.ui.tourist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveTouristDashboardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S2922/S2995/S3011: ViewModel for the Tourist dashboard, managing live sensor telemetry and the hero focus tile.
 */
@HiltViewModel
class TouristInfoViewModel @Inject constructor(
    private val observeTouristDashboardUseCase: ObserveTouristDashboardUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val initialFocus: TouristTileType =
        savedStateHandle.get<String>(KEY_FOCUSED_TILE)
            ?.let { runCatching { TouristTileType.valueOf(it) }.getOrNull() }
            ?: TouristTileType.SPEED

    private val _state = MutableStateFlow(TouristDashboardState(focusedTile = initialFocus))
    val state: StateFlow<TouristDashboardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeTouristDashboardUseCase(initialFocus).collectLatest { sensorState ->
                _state.update { current ->
                    val focus = if (current.focusedTile == TouristTileType.STEPS && !sensorState.stepsAvailable) {
                        TouristTileType.SPEED
                    } else {
                        current.focusedTile
                    }
                    sensorState.copy(focusedTile = focus)
                }
            }
        }
    }

    fun selectTile(tileType: TouristTileType) {
        if (tileType == TouristTileType.STEPS && !_state.value.stepsAvailable) return
        savedStateHandle[KEY_FOCUSED_TILE] = tileType.name
        _state.update { it.copy(focusedTile = tileType) }
    }

    fun resetTrip() {
        observeTouristDashboardUseCase.resetTripDistance()
        _state.update { it.copy(tripDistanceMeters = 0.0) }
    }

    fun resetSpeedAndTrip() {
        observeTouristDashboardUseCase.resetMaxSpeedAndTrip()
        _state.update { it.copy(maxSpeedKmh = 0f, tripDistanceMeters = 0.0) }
    }

    fun resetSteps() {
        observeTouristDashboardUseCase.resetSteps()
        _state.update { it.copy(stepsCount = 0L) }
    }

    companion object {
        private const val KEY_FOCUSED_TILE = "tourist_focused_tile"
    }
}
