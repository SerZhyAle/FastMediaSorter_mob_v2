package com.sza.fastmediasorter.wear.ui.apps.motionmonitor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.model.MotionHistoryEntry
import com.sza.fastmediasorter.wear.domain.motion.WearSensorAvailability
import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamId
import com.sza.fastmediasorter.wear.domain.motion.WearSensorStreamState
import com.sza.fastmediasorter.wear.domain.motion.deliveryAgeMillis
import com.sza.fastmediasorter.wear.domain.repository.MotionHistoryRepository
import com.sza.fastmediasorter.wear.domain.repository.WearMotionDiagnosticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S2458/S3014: Publishes the watch's activity and motion readings for as long as the screen observes them.
 *
 * Tracks step baseline offsets for the reset action, allows saving snapshot history entries,
 * and prioritizes activity metrics.
 */
@HiltViewModel
class MotionMonitorViewModel @Inject constructor(
    repository: WearMotionDiagnosticsRepository,
    private val historyRepository: MotionHistoryRepository
) : ViewModel() {

    private val stepBaseline = MutableStateFlow<Long?>(null)
    private val snapshotSaved = MutableStateFlow(false)
    private var lastKnownRawSteps: Long = 0L

    val uiState: StateFlow<MotionMonitorUiState> = combine(
        repository.streams(),
        stepBaseline,
        snapshotSaved
    ) { streams, baseline, saved ->
        toUiState(streams, baseline, saved)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = MotionMonitorUiState()
    )

    fun resetSteps() {
        stepBaseline.value = lastKnownRawSteps
    }

    fun saveCurrentSnapshot() {
        val currentBaseline = stepBaseline.value ?: 0L
        val displayed = (lastKnownRawSteps - currentBaseline).coerceAtLeast(0L)
        viewModelScope.launch {
            historyRepository.insert(
                MotionHistoryEntry(
                    timestampMillis = System.currentTimeMillis(),
                    steps = displayed
                )
            )
            snapshotSaved.value = true
        }
    }

    private fun toUiState(
        streams: List<WearSensorStreamState>,
        baseline: Long?,
        saved: Boolean
    ): MotionMonitorUiState {
        val nowMillis = System.currentTimeMillis()
        val stepStream = streams.firstOrNull { it.id == WearSensorStreamId.STEP_COUNTER }
        val rawSteps = stepStream?.values?.firstOrNull()?.toLong() ?: 0L
        lastKnownRawSteps = rawSteps

        val effectiveBaseline = baseline ?: 0L
        val displayed = (rawSteps - effectiveBaseline).coerceAtLeast(0L)
        val hasStep = stepStream?.availability == WearSensorAvailability.Available

        val rows = streams.map { stream ->
            toRow(stream, nowMillis, displayed)
        }

        return MotionMonitorUiState(
            activity = rows.filter { it.id in ACTIVITY_STREAMS },
            motion = rows.filterNot { it.id in ACTIVITY_STREAMS },
            displayedSteps = displayed,
            hasStepData = hasStep,
            snapshotSaved = saved
        )
    }

    private fun toRow(
        stream: WearSensorStreamState,
        nowMillis: Long,
        displayedSteps: Long
    ): MotionStreamRow = MotionStreamRow(
        id = stream.id,
        availability = stream.availability,
        values = stream.values,
        eventCount = stream.delivery.eventCount,
        hertz = stream.delivery.hertz,
        ageMillis = deliveryAgeMillis(stream.delivery, nowMillis),
        displayedSteps = if (stream.id == WearSensorStreamId.STEP_COUNTER) displayedSteps else null
    )

    private companion object {
        val ACTIVITY_STREAMS = setOf(WearSensorStreamId.STEP_COUNTER, WearSensorStreamId.STEP_DETECTOR)
    }
}
