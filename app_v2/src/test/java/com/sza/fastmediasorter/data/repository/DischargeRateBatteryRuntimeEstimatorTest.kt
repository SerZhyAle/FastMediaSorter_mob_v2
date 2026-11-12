package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1178 / S4529: the estimate is the only arithmetic in the battery readout that no screenshot can
 * falsify. The clock and the battery readings are driven through the parameters rather than read from
 * the device, so every case here is deterministic.
 */
class DischargeRateBatteryRuntimeEstimatorTest {

    private val estimator = DischargeRateBatteryRuntimeEstimator()

    private val oneHour = 3_600_000L
    private val oneMinute = 60_000L
    private val twoMinutes = 120_000L

    @Test
    fun `the first read answers from the remaining charge and the current`() {
        // 2000 mAh left at a 500 mA draw lasts four hours.
        val result = estimator.estimateRemainingMillis(
            percent = 50,
            isOnExternalPower = false,
            elapsedRealtimeMillis = 0L,
            chargeCounterMicroampHours = 2_000_000,
            currentMicroamps = -500_000
        )

        assertEquals(MetricValue.Known(4 * oneHour), result)
    }

    @Test
    fun `readings reported in milliamps are scaled to microamps`() {
        val result = estimator.estimateRemainingMillis(
            percent = 50,
            isOnExternalPower = false,
            elapsedRealtimeMillis = 0L,
            chargeCounterMicroampHours = 2_000,
            currentMicroamps = 500
        )

        assertEquals(MetricValue.Known(4 * oneHour), result)
    }

    @Test
    fun `a current spike moves the estimate only part of the way`() {
        estimator.estimateRemainingMillis(50, false, 0L, 2_000_000, -500_000)

        val result = estimator.estimateRemainingMillis(50, false, 10_000L, 2_000_000, -1_500_000)

        // The smoothed draw is 800 mA, not the 1500 mA of the spike: 2000 / 800 = 2.5 hours.
        assertEquals(MetricValue.Known(oneHour * 5 / 2), result)
    }

    @Test
    fun `external power reports unknown`() {
        val result = estimator.estimateRemainingMillis(50, true, 0L, 2_000_000, 500_000)

        assertTrue(result is MetricValue.Unknown)
    }

    @Test
    fun `without battery readings a single observation reports unknown`() {
        val result = estimator.estimateRemainingMillis(
            percent = 100,
            isOnExternalPower = false,
            elapsedRealtimeMillis = 0L
        )

        assertTrue(result is MetricValue.Unknown)
    }

    @Test
    fun `without battery readings two falling observations report the observed rate`() {
        estimator.estimateRemainingMillis(percent = 100, isOnExternalPower = false, elapsedRealtimeMillis = 0L)

        // Ten percent per minute leaves nine minutes of the ninety percent still in the battery.
        val result = estimator.estimateRemainingMillis(
            percent = 90,
            isOnExternalPower = false,
            elapsedRealtimeMillis = oneMinute
        )

        assertEquals(MetricValue.Known(9 * oneMinute), result)
    }

    @Test
    fun `a zero current falls back to the observed rate`() {
        estimator.estimateRemainingMillis(100, false, 0L, 2_000_000, 0)

        val result = estimator.estimateRemainingMillis(90, false, oneMinute, 2_000_000, 0)

        assertEquals(MetricValue.Known(9 * oneMinute), result)
    }

    @Test
    fun `a rising level reports unknown without battery readings`() {
        estimator.estimateRemainingMillis(percent = 50, isOnExternalPower = false, elapsedRealtimeMillis = 0L)

        val result = estimator.estimateRemainingMillis(
            percent = 60,
            isOnExternalPower = false,
            elapsedRealtimeMillis = oneMinute
        )

        assertTrue(result is MetricValue.Unknown)
    }

    @Test
    fun `unchanged readings between two drops do not shorten the measured rate`() {
        estimator.estimateRemainingMillis(percent = 100, isOnExternalPower = false, elapsedRealtimeMillis = 0L)
        // Reads come far more often than the level moves; these must not re-anchor the sample, or the
        // drop below would be measured against one refresh instead of the full minute.
        estimator.estimateRemainingMillis(percent = 100, isOnExternalPower = false, elapsedRealtimeMillis = 20_000L)
        estimator.estimateRemainingMillis(percent = 100, isOnExternalPower = false, elapsedRealtimeMillis = 40_000L)

        val result = estimator.estimateRemainingMillis(
            percent = 99,
            isOnExternalPower = false,
            elapsedRealtimeMillis = oneMinute
        )

        assertEquals(MetricValue.Known(99 * oneMinute), result)
    }

    @Test
    fun `external power reports unknown even after a rate has been observed`() {
        estimator.estimateRemainingMillis(percent = 100, isOnExternalPower = false, elapsedRealtimeMillis = 0L)
        estimator.estimateRemainingMillis(percent = 90, isOnExternalPower = false, elapsedRealtimeMillis = oneMinute)

        val result = estimator.estimateRemainingMillis(
            percent = 91,
            isOnExternalPower = true,
            elapsedRealtimeMillis = twoMinutes
        )

        assertTrue(result is MetricValue.Unknown)
    }
}
