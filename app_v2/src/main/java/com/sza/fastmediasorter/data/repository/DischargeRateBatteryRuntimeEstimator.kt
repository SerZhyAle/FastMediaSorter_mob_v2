package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.repository.BatteryRuntimeEstimator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S1178: estimates remaining runtime from the rate at which the charge has actually been falling.
 *
 * The estimator is a singleton because the rate only exists across observations - a per-gadget instance
 * would start from nothing every time a cell is attached and could never produce a figure at all.
 *
 * The remembered sample is mutable state shared between whichever coroutine happens to refresh the
 * gadget, so every entry point is synchronized on the instance.
 */
@Singleton
class DischargeRateBatteryRuntimeEstimator @Inject constructor() : BatteryRuntimeEstimator {

    private var lastPercent: Int? = null
    private var lastTimestampMillis: Long = 0L
    private var percentPerMillis: Double = NO_RATE_OBSERVED

    @Synchronized
    override fun estimateRemainingMillis(
        percent: Int,
        isCharging: Boolean,
        elapsedRealtimeMillis: Long
    ): MetricValue<Long> {
        if (isCharging) {
            // A rising level says nothing about how fast the device drains, and the rate measured before
            // the cable went in is stale the moment it comes out again.
            forgetSample()
            return MetricValue.Unknown
        }
        observe(percent, elapsedRealtimeMillis)
        val rate = percentPerMillis
        return if (rate > NO_RATE_OBSERVED) {
            MetricValue.Known((percent / rate).toLong())
        } else {
            MetricValue.Unknown
        }
    }

    private fun observe(percent: Int, elapsedRealtimeMillis: Long) {
        val previousPercent = lastPercent
        // The sample is anchored to the last level CHANGE, not to the last read. The gadget reads every
        // few seconds while the level moves every few minutes, so re-anchoring on an unchanged level
        // would measure one percent against one refresh interval and report minutes where hours remain.
        if (previousPercent == percent) return
        when {
            previousPercent == null -> Unit
            percent > previousPercent -> percentPerMillis = NO_RATE_OBSERVED
            else -> observeDrop(previousPercent - percent, elapsedRealtimeMillis - lastTimestampMillis)
        }
        lastPercent = percent
        lastTimestampMillis = elapsedRealtimeMillis
    }

    private fun observeDrop(droppedPercent: Int, elapsedMillis: Long) {
        if (elapsedMillis > 0L) {
            percentPerMillis = droppedPercent.toDouble() / elapsedMillis
        }
    }

    private fun forgetSample() {
        lastPercent = null
        lastTimestampMillis = 0L
        percentPerMillis = NO_RATE_OBSERVED
    }

    private companion object {
        /** No two observations have differed yet, so there is no rate to divide by. */
        const val NO_RATE_OBSERVED = 0.0
    }
}
