package com.sza.fastmediasorter.wear.ui.apps.bodysensor

import com.sza.fastmediasorter.wear.domain.bodysensor.BodySensorReading

/**
 * Everything the body-sensor diagnostic shows at one moment.
 *
 * @param reading what the sensor last said - a value, a refusal, or that nothing has been asked yet.
 * @param canMeasure whether the action is offered. Deliberately not derived in the composable: whether a
 *   refusal is worth retrying is a decision about this screen's affordances, and putting it here keeps
 *   the screen free of a second `when` over the same reasons that could disagree with the first.
 */
data class BodySensorUiState(
    val reading: BodySensorReading = BodySensorReading.Idle,
    val canMeasure: Boolean = false
)
