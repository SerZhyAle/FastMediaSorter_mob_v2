package com.sza.fastmediasorter.ui.common.widget.dimclock

/**
 * S3366: opens the target behind a dim-screen tap - the application that owns a notification chip,
 * or the system battery-usage screen behind the battery box.
 *
 * The caller dismisses the dim overlay before invoking either verb (the overlay's exit hook), so the
 * started screen is never hidden under a lit dim window at minimum brightness.
 */
interface DimChipActionRouter {
    fun openChip(chip: DimStatusChip)
    fun openBatteryUsage()
}
