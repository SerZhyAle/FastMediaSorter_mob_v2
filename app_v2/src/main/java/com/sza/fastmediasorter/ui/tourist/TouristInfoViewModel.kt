package com.sza.fastmediasorter.ui.tourist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.domain.model.tourist.isOffered
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveTouristDashboardUseCase
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveWatchTouristHealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S2922/S2995/S3011: ViewModel for the Tourist dashboard, managing live sensor telemetry and the hero focus tile.
 *
 * S4145: the dashboard flow is subscribed only while the screen collects [state]. Launching it in `init`
 * kept every sensor, the position listener and the weather loop alive for as long as the ViewModel - that
 * is, while the screen sat stopped in the back stack - and each new sensor multiplied that cost.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TouristInfoViewModel @Inject constructor(
    private val observeTouristDashboardUseCase: ObserveTouristDashboardUseCase,
    private val savedStateHandle: SavedStateHandle,
    observeWatchTouristHealthUseCase: ObserveWatchTouristHealthUseCase,
) : ViewModel() {

    private val focus = MutableStateFlow(
        savedStateHandle.get<String>(KEY_FOCUSED_TILE)
            ?.let { runCatching { TouristTileType.valueOf(it) }.getOrNull() }
            ?: TouristTileType.SPEED,
    )

    /** Bumped when a grant changed: the flow reads grants and binds listeners only when subscribed. */
    private val restartToken = MutableStateFlow(0)

    private val sensorState = MutableStateFlow(TouristDashboardState(focusedTile = focus.value))

    val state: StateFlow<TouristDashboardState> = restartToken
        .flatMapLatest { observeTouristDashboardUseCase(focus.value) }
        // Merged before sensorState: selectTile judges a tap against it, and a watch tile is offered only here.
        .combine(observeWatchTouristHealthUseCase()) { sensor, watch -> sensor.copy(watch = watch) }
        .onEach { sensorState.value = it }
        .combine(focus) { sensor, focused -> sensor.copy(focusedTile = effectiveFocus(sensor, focused)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), sensorState.value)

    fun refreshPermissions() {
        val current = sensorState.value
        val locationChanged =
            observeTouristDashboardUseCase.hasLocationPermission() != current.hasLocationPermission
        val activityChanged =
            observeTouristDashboardUseCase.hasActivityRecognitionPermission() !=
                current.hasActivityRecognitionPermission
        if (locationChanged || activityChanged) {
            restartToken.update { it + 1 }
        }
    }

    fun selectTile(tileType: TouristTileType) {
        if (!tileType.isOffered(sensorState.value)) return
        savedStateHandle[KEY_FOCUSED_TILE] = tileType.name
        focus.value = tileType
    }

    fun resetTrip() {
        viewModelScope.launch { observeTouristDashboardUseCase.resetTrip() }
    }

    fun resetSteps() {
        observeTouristDashboardUseCase.resetSteps()
    }

    /** Steps keep their own counter; every other tile belongs to the trip, which resets as a whole. */
    fun resetFor(tile: TouristTileType) {
        if (tile == TouristTileType.STEPS) resetSteps() else resetTrip()
    }

    private fun effectiveFocus(sensor: TouristDashboardState, focused: TouristTileType): TouristTileType =
        if (focused.isOffered(sensor)) focused else TouristTileType.SPEED

    companion object {
        private const val KEY_FOCUSED_TILE = "tourist_focused_tile"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
