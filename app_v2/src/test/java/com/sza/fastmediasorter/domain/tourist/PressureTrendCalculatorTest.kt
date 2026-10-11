package com.sza.fastmediasorter.domain.tourist

import com.sza.fastmediasorter.domain.model.tourist.PressureSample
import com.sza.fastmediasorter.domain.model.tourist.PressureTrend
import com.sza.fastmediasorter.domain.model.tourist.PressureTrendResult
import org.junit.Assert.assertEquals
import org.junit.Test

class PressureTrendCalculatorTest {

    private val calculator = PressureTrendCalculator()

    @Test
    fun `under an hour of samples is accumulating`() {
        val samples = listOf(PressureSample(T0, 1000f), PressureSample(T0 + 30 * MINUTE, 1003f))

        assertEquals(PressureTrendResult.Accumulating, calculator.trend(samples, T0 + 30 * MINUTE))
    }

    @Test
    fun `no samples is accumulating`() {
        assertEquals(PressureTrendResult.Accumulating, calculator.trend(emptyList(), T0))
    }

    @Test
    fun `rise above one hectopascal is rising`() {
        val result = trendOver(1000f, 1002f)

        assertEquals(PressureTrend.RISING, (result as PressureTrendResult.Ready).trend)
    }

    @Test
    fun `fall below one hectopascal is falling`() {
        val result = trendOver(1002f, 1000f)

        assertEquals(PressureTrend.FALLING, (result as PressureTrendResult.Ready).trend)
    }

    @Test
    fun `small change is steady`() {
        val result = trendOver(1000f, 1000.5f)

        assertEquals(PressureTrend.STEADY, (result as PressureTrendResult.Ready).trend)
    }

    @Test
    fun `append keeps one sample a minute`() {
        val one = calculator.append(emptyList(), PressureSample(T0, 1000f))
        val two = calculator.append(one, PressureSample(T0 + 20_000L, 1001f))

        assertEquals(1, two.size)
    }

    @Test
    fun `append drops samples older than six hours`() {
        val old = listOf(PressureSample(T0, 1000f))
        val appended = calculator.append(old, PressureSample(T0 + 7 * HOUR, 1001f))

        assertEquals(listOf(PressureSample(T0 + 7 * HOUR, 1001f)), appended)
    }

    private fun trendOver(from: Float, to: Float): PressureTrendResult {
        val samples = listOf(PressureSample(T0, from), PressureSample(T0 + 2 * HOUR, to))
        return calculator.trend(samples, T0 + 2 * HOUR)
    }

    private companion object {
        const val T0 = 10_000_000L
        const val MINUTE = 60_000L
        const val HOUR = 3_600_000L
    }
}
