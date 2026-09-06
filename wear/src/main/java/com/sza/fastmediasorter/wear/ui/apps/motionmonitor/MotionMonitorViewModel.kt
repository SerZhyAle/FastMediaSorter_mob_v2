package com.sza.fastmediasorter.wear.ui.apps.motionmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamId
import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamState
import com.sza.fastmediasorter.wear.domain.motion.deliveryAgeMillis
import com.sza.fastmediasorter.wear.domain.repository.WearMotionDiagnosticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Publishes the watch's motion and activity readings for as long as the screen observes them and no
 * longer.
 *
 * `WhileSubscribed` with no grace period is the whole cost policy, the same one the Network Monitor
 * uses: the repository's flow is cold, so the moment the last collector goes away every registered
 * `SensorEventListener` is unregistered. A grace period here would keep the sensors running past the
 * screen for no benefit a diagnostic session can name.
 */
@HiltViewModel
class MotionMonitorViewModel @Inject constructor(
    repository: WearMotionDiagnosticsRepository
) : ViewModel() {

    val uiState: StateFlow<MotionMonitorUiState> = repository.streams()
        .map(::toUiState)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = MotionMonitorUiState()
        )

    private fun toUiState(streams: List<WearSensorStreamState>): MotionMonitorUiState {
        // Read once per emission rather than per row, so every age on one screen refers to one instant.
        val nowMillis = System.currentTimeMillis()
        val rows = streams.map { stream -> toRow(stream, nowMillis) }
        return MotionMonitorUiState(
            motion = rows.filterNot { it.id in ACTIVITY_STREAMS },
            activity = rows.filter { it.id in ACTIVITY_STREAMS }
        )
    }

    private fun toRow(stream: WearSensorStreamState, nowMillis: Long): MotionStreamRow = MotionStreamRow(
        id = stream.id,
        availability = stream.availability,
        values = stream.values,
        eventCount = stream.delivery.eventCount,
        hertz = stream.delivery.hertz,
        ageMillis = deliveryAgeMillis(stream.delivery, nowMillis)
    )

    private companion object {
        val ACTIVITY_STREAMS = setOf(WearSensorStreamId.STEP_COUNTER, WearSensorStreamId.STEP_DETECTOR)
    }
}
