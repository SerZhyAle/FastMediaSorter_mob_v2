package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchEngine
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * How often the reading is repainted while something runs.
 *
 * Hundredths are shown, so anything slower than this drops digits the display promises; the ticker is
 * cancelled outright when nothing runs, so this rate costs nothing on a stopped screen.
 */
private const val TICK_INTERVAL_MILLIS = 50L

/**
 * Holds the measurement and writes the program's durable parts to the watch's own settings store.
 *
 * The participant count and the last result are persisted the moment they change rather than on leaving
 * the screen: a watch program is dismissed with a gesture that gives no callback worth relying on.
 *
 * This is the only place that reads the clock. The domain takes every instant as a parameter, which is
 * what lets the engine be tested without waiting for time to pass.
 */
@HiltViewModel
class WearStopwatchViewModel @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository,
    private val ongoingNotificationManager: WearStopwatchOngoingNotificationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearStopwatchUiState())
    val uiState: StateFlow<WearStopwatchUiState> = _uiState.asStateFlow()

    private var ticker: Job? = null

    init {
        // A collection rather than a first(): the count is changed from this screen's own menu, so the
        // regions follow the store instead of the store following a local copy.
        viewModelScope.launch {
            preferencesRepository.stopwatchParticipantCount.collect { count ->
                _uiState.update { current ->
                    current.copy(state = WearStopwatchEngine.resize(current.state, count))
                }
            }
        }
        viewModelScope.launch {
            preferencesRepository.stopwatchLastResult.collect { result ->
                _uiState.update { current -> current.copy(lastResult = result) }
            }
        }
    }

    fun onStartOrLap(index: Int) = mutate { state, now ->
        WearStopwatchEngine.startOrLap(state, index, now)
    }

    fun onStopOrReset(index: Int) = mutate { state, now ->
        WearStopwatchEngine.stopOrReset(state, index, now)
    }

    fun onStartAll() = mutate { state, now -> WearStopwatchEngine.startAll(state, now) }

    fun onStopAll() = mutate { state, now -> WearStopwatchEngine.stopAll(state, now) }

    fun onResetAll() = mutate { state, _ -> WearStopwatchEngine.resetAll(state) }

    fun onParticipantCountSelected(count: Int) {
        viewModelScope.launch { preferencesRepository.setStopwatchParticipantCount(count) }
    }

    /**
     * The screen renders the result text, because only it can resolve the words; this keeps it.
     *
     * A measurement that holds nothing is not remembered - it would replace a real earlier result with
     * an empty one, which is the one thing the store is here to prevent.
     */
    fun onResultRendered(text: String) {
        if (_uiState.value.state.isPristine) return
        viewModelScope.launch { preferencesRepository.setStopwatchLastResult(text) }
    }

    private fun mutate(transform: (WearStopwatchState, Long) -> WearStopwatchState) {
        val now = SystemClock.elapsedRealtime()
        _uiState.update { current ->
            current.copy(state = transform(current.state, now), nowMillis = now)
        }
        refreshTicker()
    }

    private fun refreshTicker() {
        val state = _uiState.value
        if (!state.anyRunning) {
            ticker?.cancel()
            ticker = null
            ongoingNotificationManager.hideOngoing()
            return
        }
        val firstRunning = state.state.participants.firstOrNull { it.isRunning }
        val elapsed = firstRunning?.elapsedAt(state.nowMillis) ?: 0L
        val baseTimeUtc = System.currentTimeMillis() - elapsed
        ongoingNotificationManager.showOngoing(baseTimeUtc)

        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                _uiState.update { current -> current.copy(nowMillis = SystemClock.elapsedRealtime()) }
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        if (!_uiState.value.anyRunning) {
            ongoingNotificationManager.hideOngoing()
        }
        super.onCleared()
    }
}
