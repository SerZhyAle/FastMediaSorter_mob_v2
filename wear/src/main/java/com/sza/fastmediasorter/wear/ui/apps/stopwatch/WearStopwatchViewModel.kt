package com.sza.fastmediasorter.wear.ui.apps.stopwatch

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.repository.WearPreferencesRepository
import com.sza.fastmediasorter.wear.domain.repository.WearStopwatchSessionRepository
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchEngine
import com.sza.fastmediasorter.wear.domain.stopwatch.WearStopwatchState
import com.sza.fastmediasorter.wear.domain.usecase.SyncWearStopwatchOngoingUseCase
import com.sza.fastmediasorter.wear.domain.usecase.UpdateWearStopwatchUseCase
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
 * How often the reading is repainted while something runs and the screen is visible.
 *
 * Hundredths are shown, so anything slower than this drops digits the display promises; the ticker is
 * cancelled outright when nothing runs or nobody looks, so this rate costs nothing in either case.
 */
private const val TICK_INTERVAL_MILLIS = 50L

/**
 * Draws the stopwatch session and turns taps into changes of it.
 *
 * S3555: the measurement belongs to the session, not to this view model - the ongoing-activity indicator
 * and the programs tile outlive the screen, so leaving it must not end or reset what they advertise. What
 * stays here is the clock at the moment of a tap, the repaint while the screen is visible, and the one
 * notification-permission request a screen session may raise when the indicator is blocked.
 *
 * The participant count and the last result stay in the watch's own settings store, written the moment
 * they change: a watch program is dismissed with a gesture that gives no callback worth relying on.
 */
@HiltViewModel
class WearStopwatchViewModel @Inject constructor(
    private val preferencesRepository: WearPreferencesRepository,
    private val session: WearStopwatchSessionRepository,
    private val updateStopwatch: UpdateWearStopwatchUseCase,
    private val syncOngoing: SyncWearStopwatchOngoingUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearStopwatchUiState())
    val uiState: StateFlow<WearStopwatchUiState> = _uiState.asStateFlow()

    private var ticker: Job? = null
    private var screenVisible = false
    private var permissionRequested = false

    init {
        viewModelScope.launch {
            session.session.collect { state ->
                _uiState.update { current -> current.copy(state = state, nowMillis = SystemClock.elapsedRealtime()) }
                refreshTicker()
            }
        }
        // A collection rather than a first(): the count is changed from this screen's own menu, so the
        // regions follow the store instead of the store following a local copy.
        viewModelScope.launch {
            preferencesRepository.stopwatchParticipantCount.collect { count ->
                if (session.current().participants.size != WearStopwatchState.snapCount(count)) {
                    change { state, _ -> WearStopwatchEngine.resize(state, count) }
                }
            }
        }
        viewModelScope.launch {
            preferencesRepository.stopwatchLastResult.collect { result ->
                _uiState.update { current -> current.copy(lastResult = result) }
            }
        }
        viewModelScope.launch { syncOngoing() }
    }

    fun onStartOrLap(index: Int) = change { state, now ->
        WearStopwatchEngine.startOrLap(state, index, now)
    }

    fun onStopOrReset(index: Int) = change { state, now ->
        WearStopwatchEngine.stopOrReset(state, index, now)
    }

    fun onStartAll() = change { state, now -> WearStopwatchEngine.startAll(state, now) }

    fun onStopAll() = change { state, now -> WearStopwatchEngine.stopAll(state, now) }

    fun onResetAll() = change { state, _ -> WearStopwatchEngine.resetAll(state) }

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

    fun onScreenVisible(visible: Boolean) {
        screenVisible = visible
        refreshTicker()
    }

    fun onNotificationPermissionAsked() {
        _uiState.update { current -> current.copy(askNotificationPermission = false) }
    }

    /** Whatever the answer, the indicator is brought in line: a grant is what lets it appear now. */
    fun onNotificationPermissionResult() {
        viewModelScope.launch { syncOngoing() }
    }

    private fun change(transform: (WearStopwatchState, Long) -> WearStopwatchState) {
        // Read at the tap, while the session may still be restoring: a start keeps the instant it was
        // pressed rather than the instant the store answered.
        val now = SystemClock.elapsedRealtime()
        viewModelScope.launch {
            val update = updateStopwatch(now, transform)
            if (update.indicatorBlocked && !permissionRequested) {
                permissionRequested = true
                _uiState.update { current -> current.copy(askNotificationPermission = true) }
            }
        }
    }

    private fun refreshTicker() {
        if (!screenVisible || !_uiState.value.anyRunning) {
            ticker?.cancel()
            ticker = null
            return
        }
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive) {
                _uiState.update { current -> current.copy(nowMillis = SystemClock.elapsedRealtime()) }
                delay(TICK_INTERVAL_MILLIS)
            }
        }
    }
}
