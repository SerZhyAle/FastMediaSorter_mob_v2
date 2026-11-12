package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import com.sza.fastmediasorter.domain.repository.BatteryRuntimeEstimator
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * S1178 / S4529: estimates remaining runtime at the present draw.
 *
 * The primary answer is the remaining charge divided by the battery current, both read from the
 * public battery properties, so a figure exists on the very first read. The rate at which the level
 * has been falling is the fallback for hardware that reports neither reading.
 *
 * The estimator is a singleton because the smoothed draw and the level-drop rate only exist across
 * observations - a per-caller instance would start from nothing on every read. That state is shared
 * between whichever coroutine refreshes the battery, so every entry point is synchronized.
 */
@Singleton
class DischargeRateBatteryRuntimeEstimator @Inject constructor() : BatteryRuntimeEstimator {

    private var lastPercent: Int? = null
    private var lastTimestampMillis: Long = 0L
    private var percentPerMillis: Double = NO_RATE_OBSERVED
    private var smoothedDrawMicroamps: Double? = null

    @Synchronized
    override fun estimateRemainingMillis(
        percent: Int,
        isOnExternalPower: Boolean,
        elapsedRealtimeMillis: Long,
        chargeCounterMicroampHours: Int?,
        currentMicroamps: Int?
    ): MetricValue<Long> {
        if (isOnExternalPower) {
            // Nothing drains while the device is powered, and the draw measured before the cable went in
            // is stale the moment it comes out again.
            forgetSample()
            return MetricValue.Unknown
        }
        observe(percent, elapsedRealtimeMillis)
        val millis = estimateFromCurrent(percent, chargeCounterMicroampHours, currentMicroamps)
            ?: estimateFromLevelDrop(percent)
        return millis?.let { MetricValue.Known(it) } ?: MetricValue.Unknown
    }

    private fun estimateFromCurrent(percent: Int, counter: Int?, current: Int?): Long? {
        val remainingMicroampHours = counter?.let { normalizeCharge(it, percent) }
        val draw = current?.let(::normalizeCurrent)?.let(::smoothDraw)
        val hours = if (remainingMicroampHours != null && draw != null) remainingMicroampHours / draw else null
        return hours?.let { (it * MILLIS_PER_HOUR).toLong() }?.takeIf { it in PLAUSIBLE_RUNTIME_MILLIS }
    }

    /** Some vendors report the counter in mAh: a full charge under 100 mAh can only mean that unit. */
    private fun normalizeCharge(raw: Int, percent: Int): Double? {
        if (raw <= 0) return null
        val fullCharge = raw.toDouble() * FULL_PERCENT / percent.coerceAtLeast(1)
        return if (fullCharge < MIN_FULL_CHARGE_MICROAMP_HOURS) raw * UNIT_SCALE else raw.toDouble()
    }

    /**
     * The sign is dropped because vendors disagree on it. Some report mA instead of uA; a device with the
     * screen on never draws under 10 mA, so a magnitude below that ceiling is read as mA.
     */
    private fun normalizeCurrent(raw: Int): Double? {
        val magnitude = abs(raw.toLong())
        return when {
            raw == Int.MIN_VALUE || magnitude == 0L -> null
            magnitude < MILLIAMP_REPORT_CEILING -> magnitude * UNIT_SCALE
            else -> magnitude.toDouble()
        }
    }

    /** The instantaneous current jumps with every burst of work; the estimate follows its trend instead. */
    private fun smoothDraw(sample: Double): Double {
        val next = smoothedDrawMicroamps?.let { it + SMOOTHING_WEIGHT * (sample - it) } ?: sample
        smoothedDrawMicroamps = next
        return next
    }

    private fun estimateFromLevelDrop(percent: Int): Long? {
        val rate = percentPerMillis
        return if (rate > NO_RATE_OBSERVED) (percent / rate).toLong() else null
    }

    private fun observe(percent: Int, elapsedRealtimeMillis: Long) {
        val previousPercent = lastPercent
        // The sample is anchored to the last level CHANGE, not to the last read. Reads come every few
        // seconds while the level moves every few minutes, so re-anchoring on an unchanged level would
        // measure one percent against one refresh interval and report minutes where hours remain.
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
        smoothedDrawMicroamps = null
    }

    private companion object {
        /** No two observations have differed yet, so there is no rate to divide by. */
        const val NO_RATE_OBSERVED = 0.0
        const val FULL_PERCENT = 100
        const val UNIT_SCALE = 1_000.0
        const val MILLIS_PER_HOUR = 3_600_000.0
        const val MIN_FULL_CHARGE_MICROAMP_HOURS = 100_000.0
        const val MILLIAMP_REPORT_CEILING = 10_000L
        const val SMOOTHING_WEIGHT = 0.3

        /** Beyond ten days the draw reading is idle noise, not a forecast. */
        val PLAUSIBLE_RUNTIME_MILLIS = 1L..864_000_000L
    }
}
