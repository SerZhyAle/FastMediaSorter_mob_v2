package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue

/**
 * S1178: how long the current charge is expected to last.
 *
 * This is a seam, not an implementation detail. Strategic ADR-3 admits a self-computed estimate only as
 * a fallback that stays replaceable - a better estimator swaps the Hilt binding and touches nothing else.
 *
 * An implementation that cannot answer yet returns [MetricValue.Unknown]. Producing a number before one
 * has been observed is the failure mode this contract exists to prevent.
 */
interface BatteryRuntimeEstimator {

    fun estimateRemainingMillis(
        percent: Int,
        isCharging: Boolean,
        elapsedRealtimeMillis: Long
    ): MetricValue<Long>
}
