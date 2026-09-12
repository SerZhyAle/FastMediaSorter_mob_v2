package com.sza.fastmediasorter.wear.ui.apps.bloodpressure.history

import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.BloodPressureSummary

/**
 * S3012: The blood pressure history and analytics screen state.
 *
 * @param entries all recorded blood pressure measurements, newest first.
 * @param summary computed summary statistics over [entries], or null when empty.
 * @param isEmpty whether no measurements exist yet.
 */
data class BloodPressureHistoryUiState(
    val entries: List<BloodPressureHistoryEntry> = emptyList(),
    val summary: BloodPressureSummary? = null,
    val isEmpty: Boolean = entries.isEmpty()
)
