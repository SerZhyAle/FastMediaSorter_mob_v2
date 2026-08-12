package com.sza.fastmediasorter.domain.model.networkmonitor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1433: the chart window is the one place where a Monitor section holds data over time, so the cases that
 * matter are the ones a live screen reaches after minutes of sampling: the window must stay bounded, the
 * derived numbers must describe what is still inside it rather than everything ever sampled, and a source
 * that emits faster than the cap must not be able to grow it.
 */
class SignalSeriesTest {

    @Test
    fun `empty series derives nothing`() {
        val series = SignalSeries()

        assertNull(series.last)
        assertNull(series.min)
        assertNull(series.max)
        assertNull(series.trend)
        assertNull(series.summary())
    }

    @Test
    fun `single sample has no trend`() {
        val series = SignalSeries().append(sample(1, -55.0))

        assertEquals(-55.0, series.last!!, 0.0)
        assertNull(series.trend)
    }

    @Test
    fun `overflowing the window drops the oldest sample`() {
        var series = SignalSeries()
        repeat(SignalSeries.SAMPLE_CAPACITY + 10) { index ->
            series = series.append(sample(index, index.toDouble()))
        }

        assertEquals(SignalSeries.SAMPLE_CAPACITY, series.samples.size)
        assertEquals(instant(10), series.samples.first().takenAtMillis)
        assertEquals(instant(SignalSeries.SAMPLE_CAPACITY + 9), series.samples.last().takenAtMillis)
    }

    @Test
    fun `extremes describe the window and not the dropped history`() {
        var series = SignalSeries().append(sample(0, -100.0))
        repeat(SignalSeries.SAMPLE_CAPACITY) { index ->
            series = series.append(sample(index + 1, -60.0 + index))
        }

        assertEquals(-60.0, series.min!!, 0.0)
        assertFalse(series.samples.any { it.value == -100.0 })
    }

    @Test
    fun `min and max span the whole window`() {
        val series = SignalSeries()
            .append(sample(1, -70.0))
            .append(sample(2, -40.0))
            .append(sample(3, -55.0))

        assertEquals(-70.0, series.min!!, 0.0)
        assertEquals(-40.0, series.max!!, 0.0)
        assertEquals(-55.0, series.last!!, 0.0)
    }

    @Test
    fun `trend reads the last step and not the whole window`() {
        val climbThenDip = SignalSeries()
            .append(sample(1, -90.0))
            .append(sample(2, -50.0))
            .append(sample(3, -52.0))

        assertEquals(SignalTrend.FALLING, climbThenDip.trend)
    }

    @Test
    fun `trend rises when the newest sample is higher`() {
        val series = SignalSeries()
            .append(sample(1, -80.0))
            .append(sample(2, -60.0))

        assertEquals(SignalTrend.RISING, series.trend)
    }

    @Test
    fun `trend is flat when the newest sample repeats the previous one`() {
        val series = SignalSeries()
            .append(sample(1, -65.0))
            .append(sample(2, -65.0))

        assertEquals(SignalTrend.FLAT, series.trend)
    }

    @Test
    fun `appending returns a new window and leaves the original untouched`() {
        val original = SignalSeries().append(sample(1, -70.0))

        val extended = original.append(sample(2, -60.0))

        assertEquals(1, original.samples.size)
        assertEquals(2, extended.samples.size)
    }

    @Test
    fun `a sample arriving inside the rate limit is ignored`() {
        val series = SignalSeries()
            .append(SignalSample(takenAtMillis = 0L, value = -70.0))
            .append(SignalSample(takenAtMillis = SignalSeries.MIN_SAMPLE_INTERVAL_MS - 1, value = -20.0))

        assertEquals(1, series.samples.size)
        assertEquals(-70.0, series.last!!, 0.0)
    }

    @Test
    fun `a sample arriving exactly at the rate limit is kept`() {
        val series = SignalSeries()
            .append(SignalSample(takenAtMillis = 0L, value = -70.0))
            .append(SignalSample(takenAtMillis = SignalSeries.MIN_SAMPLE_INTERVAL_MS, value = -20.0))

        assertEquals(2, series.samples.size)
    }

    @Test
    fun `chart points carry the timestamp and the value with no second line`() {
        val series = SignalSeries().append(sample(1, -70.0)).append(sample(2, -60.0))

        val points = series.toChartPoints()

        assertEquals(2, points.size)
        assertEquals(instant(1), points.first().takenAtMillis)
        assertEquals(-60.0, points.last().primaryValue, 0.0)
        assertNull(points.last().secondaryValue)
    }

    @Test
    fun `summary reports the numbers of the window`() {
        val series = SignalSeries()
            .append(sample(1, -70.0))
            .append(sample(2, -40.0))
            .append(sample(3, -55.0))

        val summary = series.summary()!!

        assertEquals(-55.0, summary.last, 0.0)
        assertEquals(-70.0, summary.min, 0.0)
        assertEquals(-40.0, summary.max, 0.0)
        assertEquals(SignalTrend.FALLING, summary.trend)
    }

    @Test
    fun `summary of one reading has no trend`() {
        val summary = SignalSeries().append(sample(1, -65.0)).summary()!!

        assertEquals(-65.0, summary.last, 0.0)
        assertEquals(-65.0, summary.min, 0.0)
        assertNull(summary.trend)
    }

    private fun sample(step: Int, value: Double) = SignalSample(instant(step), value)

    private fun instant(step: Int) = step * SignalSeries.MIN_SAMPLE_INTERVAL_MS
}
