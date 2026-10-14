package com.sza.fastmediasorter.wear.domain.tourist

import org.junit.Assert.assertEquals
import org.junit.Test

class WearPressureTrendCalculatorTest {

    private val calculator = WearPressureTrendCalculator()

    @Test
    fun `under an hour of samples is accumulating`() {
        val samples = listOf(WearPressureSample(T0, 1000f), WearPressureSample(T0 + 30 * MINUTE, 1003f))

        assertEquals(WearPressureTrendResult.Accumulating, calculator.trend(samples, T0 + 30 * MINUTE))
    }

    @Test
    fun `no samples is accumulating`() {
        assertEquals(WearPressureTrendResult.Accumulating, calculator.trend(emptyList(), T0))
    }

    @Test
    fun `rise above one hectopascal is rising`() {
        val result = trendOver(1000f, 1002f)

        assertEquals(WearPressureTrend.RISING, (result as WearPressureTrendResult.Ready).trend)
    }

    @Test
    fun `fall below one hectopascal is falling`() {
        val result = trendOver(1002f, 1000f)

        assertEquals(WearPressureTrend.FALLING, (result as WearPressureTrendResult.Ready).trend)
    }

    @Test
    fun `small change is steady`() {
        val result = trendOver(1000f, 1000.5f)

        assertEquals(WearPressureTrend.STEADY, (result as WearPressureTrendResult.Ready).trend)
    }

    @Test
    fun `append keeps one sample a minute`() {
        val one = calculator.append(emptyList(), WearPressureSample(T0, 1000f))
        val two = calculator.append(one, WearPressureSample(T0 + 20_000L, 1001f))

        assertEquals(1, two.size)
    }

    @Test
    fun `append drops samples older than six hours`() {
        val old = listOf(WearPressureSample(T0, 1000f))
        val appended = calculator.append(old, WearPressureSample(T0 + 7 * HOUR, 1001f))

        assertEquals(listOf(WearPressureSample(T0 + 7 * HOUR, 1001f)), appended)
    }

    private fun trendOver(from: Float, to: Float): WearPressureTrendResult {
        val samples = listOf(WearPressureSample(T0, from), WearPressureSample(T0 + 2 * HOUR, to))
        return calculator.trend(samples, T0 + 2 * HOUR)
    }

    private companion object {
        const val T0 = 10_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
    }
}
