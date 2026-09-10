package com.sza.fastmediasorter.wear.ui.apps.bodysensor.history

import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry

/**
 * S2808: everything the heart-rate history screen shows at one moment.
 *
 * [isEmpty] is derived here rather than in the composable so the screen holds no second
 * `when` over the list size, matching the [BodySensorUiState] pattern.
 */
data class HeartRateHistoryUiState(
    val entries: List<HeartRateHistoryEntry> = emptyList(),
    val isEmpty: Boolean = true
)
