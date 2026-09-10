package com.sza.fastmediasorter.wear.ui.apps.bloodpressure.history

import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry

/**
 * S2809: the history screen's state.
 *
 * `isEmpty` is derived from the list size to avoid a `when` over it in the composable,
 * matching the HeartRateHistoryUiState pattern.
 */
data class BloodPressureHistoryUiState(
    val entries: List<BloodPressureHistoryEntry> = emptyList(),
    val isEmpty: Boolean = entries.isEmpty()
)
