package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.domain.model.devicestatus.MetricValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S1178: the estimate is the only arithmetic in this ticket that no screenshot can falsify, and a wrong
 * remaining-time figure is the worst outcome the gadget set can produce. The clock is driven through the
 * parameter rather than read from the device, so every case here is deterministic.
 */
class DischargeRateBatteryRuntimeEstimatorTest {

    private val estimator = DischargeRateBatteryRuntimeEstimator()

    private val oneMinute = 60_000L
    private val twoMinutes = 120_000L

    @Test
    fun `single observation reports unknown`() {
        val result = estimator.estimateRemainingMillis(
            percent = 100,
            isCharging = false,
            elapsedRealtimeMillis = 0L
        )

        assertTrue(result is MetricValue.Unknown)
    }

    @Test
    fun `two falling observations report a value proportional to the observed rate`() {
        estimator.estimateRemainingMillis(percent = 100, isCharging = false, elapsedRealtimeMillis = 0L)

        // Ten percent per minute leaves nine minutes of the ninety percent still in the battery.
        val result = estimator.estimateRemainingMillis(
            percent = 90,
            isCharging = false,
            elapsedRealtimeMillis = oneMinute
        )

        assertEquals(MetricValue.Known(9 * oneMinute), result)
    }

    @Test
    fun `a rising level reports unknown`() {
        estimator.estimateRemainingMillis(percent = 50, isCharging = false, elapsedRealtimeMillis = 0L)

        val result = estimator.estimateRemainingMillis(
            percent = 60,
            isCharging = false,
            elapsedRealtimeMillis = oneMinute
        )

        assertTrue(result is MetricValue.Unknown)
    }

    @Test
    fun `unchanged readings between two drops do not shorten the measured rate`() {
        estimator.estimateRemainingMillis(percent = 100, isCharging = false, elapsedRealtimeMillis = 0L)
        // The gadget refreshes far more often than the level moves; these reads must not re-anchor the
        // sample, or the drop below would be measured against one refresh instead of the full minute.
        estimator.estimateRemainingMillis(percent = 100, isCharging = false, elapsedRealtimeMillis = 20_000L)
        estimator.estimateRemainingMillis(percent = 100, isCharging = false, elapsedRealtimeMillis = 40_000L)

        val result = estimator.estimateRemainingMillis(
            percent = 99,
            isCharging = false,
            elapsedRealtimeMillis = oneMinute
        )

        assertEquals(MetricValue.Known(99 * oneMinute), result)
    }

    @Test
    fun `charging reports unknown even after a rate has been observed`() {
        estimator.estimateRemainingMillis(percent = 100, isCharging = false, elapsedRealtimeMillis = 0L)
        estimator.estimateRemainingMillis(percent = 90, isCharging = false, elapsedRealtimeMillis = oneMinute)

        val result = estimator.estimateRemainingMillis(
            percent = 91,
            isCharging = true,
            elapsedRealtimeMillis = twoMinutes
        )

        assertTrue(result is MetricValue.Unknown)
    }
}
