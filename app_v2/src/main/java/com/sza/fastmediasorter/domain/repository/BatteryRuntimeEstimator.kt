package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue

/**
 * S1178: how long the current charge is expected to last.
 *
 * This is a seam, not an implementation detail. Strategic ADR-3 admits a self-computed estimate only as
 * a fallback that stays replaceable - a better estimator swaps the Hilt binding and touches nothing else.
 *
 * S4529: the estimate is a rough "at the present draw" figure and must be available the moment the
 * battery is read, so an implementation answers from the remaining charge and the battery current
 * whenever the device reports them. [MetricValue.Unknown] is left for a device on external power and
 * for hardware that reports neither those readings nor a level change yet.
 */
interface BatteryRuntimeEstimator {

    /**
     * @param chargeCounterMicroampHours `BATTERY_PROPERTY_CHARGE_COUNTER` as read, null when unsupported.
     * @param currentMicroamps the battery current as read, null when unsupported; the sign is ignored
     *   because vendors disagree on it.
     */
    fun estimateRemainingMillis(
        percent: Int,
        isOnExternalPower: Boolean,
        elapsedRealtimeMillis: Long,
        chargeCounterMicroampHours: Int? = null,
        currentMicroamps: Int? = null
    ): MetricValue<Long>
}
