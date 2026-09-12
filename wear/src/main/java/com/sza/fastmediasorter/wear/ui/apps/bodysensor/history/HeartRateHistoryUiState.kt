package com.sza.fastmediasorter.wear.ui.apps.bodysensor.history

import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.HeartRateSummary

/**
 * S2808/S3013: everything the heart-rate history screen shows at one moment.
 *
 * [isEmpty] is derived here rather than in the composable so the screen holds no second
 * `when` over the list size, matching the [BodySensorUiState] pattern.
 * [summary] holds aggregated heart rate analytics (avg, min, max, zones).
 */
data class HeartRateHistoryUiState(
    val entries: List<HeartRateHistoryEntry> = emptyList(),
    val isEmpty: Boolean = true,
    val summary: HeartRateSummary? = null
)
