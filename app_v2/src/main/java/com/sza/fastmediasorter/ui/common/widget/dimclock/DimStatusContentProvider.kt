package com.sza.fastmediasorter.ui.common.widget.dimclock

import kotlinx.coroutines.flow.Flow

/**
 * Status chip item representing a notification, network, or connectivity indicator.
 */
data class DimStatusChip(
    val id: String,
    val iconResId: Int = 0,
    val packageName: String? = null,
    val count: Int = 0,
    val isNotification: Boolean = false,
    val contentDescription: String? = null
)

/**
 * Snapshot of device battery and status chips for the dim screen overlay.
 */
data class DimStatusSnapshot(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val chips: List<DimStatusChip> = emptyList()
)

/**
 * S3256: Provider of battery and status indicator chips for the dim overlay.
 */
interface DimStatusContentProvider {
    fun observeStatus(): Flow<DimStatusSnapshot>
}
