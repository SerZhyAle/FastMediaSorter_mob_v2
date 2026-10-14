package com.sza.fastmediasorter.ui.tourist

import android.os.SystemClock
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.domain.model.tourist.TouristDashboardState
import com.sza.fastmediasorter.domain.model.tourist.TouristTileType
import com.sza.fastmediasorter.domain.model.tourist.isOffered
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveTouristCounterUseCase
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveTouristDashboardUseCase
import com.sza.fastmediasorter.domain.usecase.tourist.ObserveWatchTouristHealthUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
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
    observeTouristCounterUseCase: ObserveTouristCounterUseCase,
) : ViewModel() {

    private val focus = MutableStateFlow(
        savedStateHandle.get<String>(KEY_FOCUSED_TILE)
            ?.let { runCatching { TouristTileType.valueOf(it) }.getOrNull() }
            ?: TouristTileType.SPEED,
    )

    /** Bumped when a grant changed: the flow reads grants and binds listeners only when subscribed. */
    private val restartToken = MutableStateFlow(0)

    private val sensorState = MutableStateFlow(TouristDashboardState(focusedTile = focus.value))

    /**
     * S4202: the primitive single-player stopwatch of the STOPWATCH tile. [stopwatchAccumulatedMillis] is
     * the frozen value at the last stop or start; while running the reading adds the monotonic distance
     * from [stopwatchStartMark], so a rotation or a process death recomputes the same elapsed span from
     * `SavedStateHandle` instead of restarting from zero.
     */
    private var stopwatchAccumulatedMillis = savedStateHandle.get<Long>(KEY_STOPWATCH_ELAPSED) ?: 0L
    private var stopwatchStartMark = savedStateHandle.get<Long>(KEY_STOPWATCH_MARK) ?: 0L
    private val stopwatchRunning = MutableStateFlow(savedStateHandle.get<Boolean>(KEY_STOPWATCH_RUNNING) ?: false)
    private val stopwatchElapsed = MutableStateFlow(
        if (stopwatchRunning.value) currentElapsed() else stopwatchAccumulatedMillis,
    )
    private var stopwatchTicker: Job? = null

    init {
        if (stopwatchRunning.value) startStopwatchTicker()
    }

    val state: StateFlow<TouristDashboardState> = restartToken
        .flatMapLatest { observeTouristDashboardUseCase(focus.value) }
        // Merged before sensorState: selectTile judges a tap against it, and a watch tile is offered only here.
        .combine(observeWatchTouristHealthUseCase()) { sensor, watch -> sensor.copy(watch = watch) }
        .combine(observeTouristCounterUseCase().map { it.value }.distinctUntilChanged()) { sensor, count ->
            sensor.copy(counterValue = count)
        }
        .combine(stopwatchElapsed) { sensor, elapsed -> sensor.copy(stopwatchElapsedMillis = elapsed) }
        .combine(stopwatchRunning) { sensor, running -> sensor.copy(stopwatchRunning = running) }
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

    /** S4202: start when stopped, freeze when running - one toggle button on the STOPWATCH tile. */
    fun toggleStopwatch() {
        if (stopwatchRunning.value) {
            stopwatchAccumulatedMillis = currentElapsed()
            stopwatchRunning.value = false
            stopwatchTicker?.cancel()
            stopwatchTicker = null
            stopwatchElapsed.value = stopwatchAccumulatedMillis
        } else {
            stopwatchStartMark = SystemClock.elapsedRealtime()
            stopwatchRunning.value = true
            startStopwatchTicker()
        }
        persistStopwatch()
    }

    /** S4202: zeroes the reading and leaves the stopwatch stopped. */
    fun resetStopwatch() {
        stopwatchAccumulatedMillis = 0L
        stopwatchStartMark = 0L
        stopwatchRunning.value = false
        stopwatchTicker?.cancel()
        stopwatchTicker = null
        stopwatchElapsed.value = 0L
        persistStopwatch()
    }

    private fun currentElapsed(): Long {
        if (!stopwatchRunning.value) return stopwatchAccumulatedMillis
        val tick = SystemClock.elapsedRealtime() - stopwatchStartMark
        // A mark from before a reboot reads larger than the clock; nothing ran during the gap.
        return stopwatchAccumulatedMillis + tick.coerceAtLeast(0L)
    }

    /**
     * Half a second keeps the M:SS reading from lagging a visible second behind the flip, at one
     * emission every two telemetry ticks. The coroutine is cancelled the moment the stopwatch stops.
     */
    private fun startStopwatchTicker() {
        stopwatchTicker?.cancel()
        stopwatchTicker = viewModelScope.launch {
            while (isActive) {
                stopwatchElapsed.value = currentElapsed()
                persistElapsed()
                delay(STOPWATCH_TICK_MS)
            }
        }
    }

    private fun persistStopwatch() {
        persistElapsed()
        savedStateHandle[KEY_STOPWATCH_RUNNING] = stopwatchRunning.value
        savedStateHandle[KEY_STOPWATCH_MARK] = stopwatchStartMark
    }

    private fun persistElapsed() {
        savedStateHandle[KEY_STOPWATCH_ELAPSED] = stopwatchElapsed.value
    }

    private fun effectiveFocus(sensor: TouristDashboardState, focused: TouristTileType): TouristTileType =
        if (focused.isOffered(sensor)) focused else TouristTileType.SPEED

    companion object {
        private const val KEY_FOCUSED_TILE = "tourist_focused_tile"
        private const val KEY_STOPWATCH_ELAPSED = "tourist_stopwatch_elapsed"
        private const val KEY_STOPWATCH_RUNNING = "tourist_stopwatch_running"
        private const val KEY_STOPWATCH_MARK = "tourist_stopwatch_mark"
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val STOPWATCH_TICK_MS = 500L
    }
}
