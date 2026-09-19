package com.sza.fastmediasorter.wear.ui.apps.bodysensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.data.bodysensor.HeartRateSessionManager
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorReading
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.model.HeartRateZone
import com.sza.fastmediasorter.wear.domain.repository.HeartRateHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * S3013/S3112: a view of the heart-rate session, which it no longer owns.
 *
 * The session runs for as long as the app is in the foreground and is held by
 * [HeartRateSessionManager]; this ViewModel only renders its readings and the history it writes. Owning
 * a measurement here would register a second sensor callback and write a competing history row.
 */
@HiltViewModel
class BodySensorViewModel @Inject constructor(
    private val sessionManager: HeartRateSessionManager,
    historyRepository: HeartRateHistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BodySensorUiState())
    val state: StateFlow<BodySensorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            historyRepository.observeAll().collect { entries ->
                _state.value = _state.value.copy(
                    lastReading = entries.firstOrNull(),
                    history = entries
                )
            }
        }
        viewModelScope.launch {
            sessionManager.state.collect { reading -> publish(reading) }
        }
    }

    /**
     * Restarts the session after the permission dialog closes: the refusal that ended the previous one
     * was recorded before the user decided, so it says nothing about the answer that is now in force.
     */
    fun refreshAvailability() {
        sessionManager.restart()
    }

    fun startMeasurement() {
        sessionManager.restart()
    }

    private fun publish(reading: BodySensorReading) {
        val zone = if (reading is BodySensorReading.HeartRate) {
            HeartRateZone.classify(reading.beatsPerMinute)
        } else {
            null
        }
        _state.value = _state.value.copy(
            reading = reading,
            currentZone = zone,
            canMeasure = isRetryable(reading)
        )
    }

    /**
     * Whether to offer the action for the state now on screen.
     *
     * A live session does not offer it - neither while waiting nor while samples are arriving - because
     * pressing again would only restart what is already running. A refusal offers it exactly when the
     * user can change the answer: granting the permission, putting the watch on, or simply trying again
     * after a silent or failed attempt. The three permanent refusals never do, so the button does not
     * invite a retry that cannot succeed.
     */
    private fun isRetryable(reading: BodySensorReading): Boolean = when (reading) {
        is BodySensorReading.Idle -> true
        is BodySensorReading.Measuring -> false
        is BodySensorReading.HeartRate -> false
        is BodySensorReading.Unavailable -> when (reading.reason) {
            BodySensorUnavailableReason.NOT_OFFERED_IN_THIS_BUILD -> false
            BodySensorUnavailableReason.PLATFORM_TOO_OLD -> false
            BodySensorUnavailableReason.NO_HARDWARE -> false
            BodySensorUnavailableReason.PERMISSION_DENIED -> true
            BodySensorUnavailableReason.SENSOR_OFF_BODY -> true
            BodySensorUnavailableReason.MEASUREMENT_TIMED_OUT -> true
            BodySensorUnavailableReason.MEASUREMENT_FAILED -> true
        }
    }
}
