package com.sza.fastmediasorter.domain.model.devicestatus

/**
 * S1178: what the battery gadget states - the charge level, whether the device is charging, and how
 * long the charge is expected to last.
 *
 * [remainingMillis] stays [MetricValue.Unknown] until an estimate actually exists. A placeholder number
 * would be indistinguishable from a measured one, and the user believes whatever the gadget prints.
 *
 * [isEstimateApproximate] is the flag the view uses to mark a computed estimate as such. No public
 * Android API reports time to depletion, so in practice every estimate this app shows is computed and
 * carries the mark; the flag exists so a future system-supplied figure can arrive unmarked.
 */
data class BatteryStatus(
    val percent: MetricValue<Int>,
    val isCharging: Boolean,
    val remainingMillis: MetricValue<Long>,
    val isEstimateApproximate: Boolean
)
