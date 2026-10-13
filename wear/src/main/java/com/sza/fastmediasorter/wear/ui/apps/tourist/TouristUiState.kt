package com.sza.fastmediasorter.wear.ui.apps.tourist

import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState

/**
 * S3007 / S3015: UI state for the Wear OS Tourist sub-program including athlete mode and lock state.
 */
data class TouristUiState(
    val telemetry: WearTouristState = WearTouristState(),
    val isMetricSystem: Boolean = true,
    val isAthleteMode: Boolean = false,
    val isScreenLocked: Boolean = false,
    /** S4185: the counter's current count, shown on its launch tile. */
    val counterValue: Long = 0L,
)
