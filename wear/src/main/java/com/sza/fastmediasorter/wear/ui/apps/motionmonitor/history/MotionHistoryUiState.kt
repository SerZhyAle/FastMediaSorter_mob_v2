package com.sza.fastmediasorter.wear.ui.apps.motionmonitor.history

import com.sza.fastmediasorter.wear.domain.model.MotionHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.MotionSummary

/**
 * S3014: UI state for the physical activity and motion history screen on Wear OS.
 */
data class MotionHistoryUiState(
    val entries: List<MotionHistoryEntry> = emptyList(),
    val isEmpty: Boolean = true,
    val summary: MotionSummary? = null
)
