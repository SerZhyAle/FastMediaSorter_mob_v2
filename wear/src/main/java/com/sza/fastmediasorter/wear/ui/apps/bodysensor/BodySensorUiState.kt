package com.sza.fastmediasorter.wear.ui.apps.bodysensor

import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorReading
import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.HeartRateZone

/**
 * S3013: Everything the body-sensor diagnostic shows at one moment.
 *
 * @param reading what the sensor last said - a value, a refusal, or that nothing has been asked yet.
 * @param currentZone physiological heart rate zone for the current live reading.
 * @param lastReading most recent saved heart rate measurement from history.
 * @param history S3112: the saved measurements the trend chart under the current value is drawn from.
 * @param canMeasure whether the action is offered.
 */
data class BodySensorUiState(
    val reading: BodySensorReading = BodySensorReading.Idle,
    val currentZone: HeartRateZone? = null,
    val lastReading: HeartRateHistoryEntry? = null,
    val history: List<HeartRateHistoryEntry> = emptyList(),
    val canMeasure: Boolean = false
)
