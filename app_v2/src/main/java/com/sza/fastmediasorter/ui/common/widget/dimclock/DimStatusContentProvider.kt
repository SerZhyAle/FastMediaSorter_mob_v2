package com.sza.fastmediasorter.ui.common.widget.dimclock

import kotlinx.coroutines.flow.Flow

/**
 * Status chip item representing a notification, network, or connectivity indicator.
 *
 * S3475: the extra fields mirror the launcher tray's icon model one for one, so a flavor that owns a
 * tray can hand the dim row exactly what the tray draws. [iconLevel] drives a level-list glyph (SIM
 * signal), [badgeText] wins over [count] for the bottom-end badge, [cornerMarkResId] names the
 * top-end mark drawable (roaming) because that drawable lives in the flavor's own resources, and a
 * non-null [text] turns the chip into a text cell (transfer speed) with no icon at all.
 */
data class DimStatusChip(
    val id: String,
    val iconResId: Int = 0,
    val packageName: String? = null,
    val count: Int = 0,
    val isNotification: Boolean = false,
    val contentDescription: String? = null,
    val iconLevel: Int? = null,
    val badgeText: String? = null,
    val cornerMarkResId: Int = 0,
    val highlighted: Boolean = false,
    val text: String? = null,
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
