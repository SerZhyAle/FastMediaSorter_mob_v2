package com.sza.fastmediasorter.wear.ui.apps.bodysensor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorReading
import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorUnavailableReason
import com.sza.fastmediasorter.wear.domain.bodysensor.WearBodySensorDataSource
import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.HeartRateZone
import com.sza.fastmediasorter.wear.domain.repository.HeartRateHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * S3013: Holds one foreground heart-rate session for exactly as long as the screen is open.
 *
 * Every collection runs in `viewModelScope`, so leaving the diagnostic cancels it and the cold flow's
 * own teardown unregisters the sensor. Exposes live physiological heart rate zones and last reading history.
 */
@HiltViewModel
class BodySensorViewModel @Inject constructor(
    private val bodySensorDataSource: WearBodySensorDataSource,
    private val historyRepository: HeartRateHistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(BodySensorUiState())
    val state: StateFlow<BodySensorUiState> = _state.asStateFlow()

    private var measurement: Job? = null
    private var lastHeartRate: BodySensorReading.HeartRate? = null
    private var cachedLastReading: HeartRateHistoryEntry? = null

    init {
        viewModelScope.launch {
            historyRepository.observeAll().collect { entries ->
                cachedLastReading = entries.firstOrNull()
                _state.value = _state.value.copy(lastReading = cachedLastReading)
            }
        }
        refreshAvailability()
    }

    /**
     * Asks whether a measurement could start, without starting one. Called again after the permission
     * dialog closes, because the answer that mattered was taken before the user decided.
     */
    fun refreshAvailability() {
        measurement?.cancel()
        measurement = null
        savePendingReading()
        viewModelScope.launch {
            publish(bodySensorDataSource.availability())
        }
    }

    fun startMeasurement() {
        // A second press must not leave the first session registered: the previous flow is cancelled
        // before the new one is collected, and cancellation is what runs its awaitClose.
        measurement?.cancel()
        savePendingReading()
        lastHeartRate = null
        measurement = viewModelScope.launch {
            bodySensorDataSource.measure().collect(::publish)
        }
        measurement?.invokeOnCompletion { savePendingReading() }
    }

    /**
     * S3013: saves the last heart-rate reading of the session that just ended, if one was received.
     * Called from [invokeOnCompletion] on the measurement job, which fires on both cancellation
     * (leaving the screen, starting a new measurement) and normal completion - so one history entry
     * is written per session, carrying the last BPM the sensor delivered.
     */
    private fun savePendingReading() {
        val reading = lastHeartRate
        if (reading != null) {
            lastHeartRate = null
            viewModelScope.launch { historyRepository.save(reading.beatsPerMinute) }
        }
    }

    private fun publish(reading: BodySensorReading) {
        val zone = if (reading is BodySensorReading.HeartRate) {
            lastHeartRate = reading
            HeartRateZone.classify(reading.beatsPerMinute)
        } else {
            null
        }
        _state.value = _state.value.copy(
            reading = reading,
            currentZone = zone,
            lastReading = cachedLastReading,
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
