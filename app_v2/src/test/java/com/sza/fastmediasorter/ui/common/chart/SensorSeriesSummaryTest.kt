package com.sza.fastmediasorter.ui.common.chart

import com.sza.fastmediasorter.domain.model.sensors.SensorSeriesPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S1446: covers `summarizeSensorSeries`, the only derived value the shared chart exposes. The trend
 * compares the final step alone, so an off-by-one there would reach both consumers unnoticed.
 */
class SensorSeriesSummaryTest {

    @Test
    fun `empty series summarises to nothing`() {
        assertNull(summarizeSensorSeries(emptyList()))
    }

    @Test
    fun `single point summarises to nothing`() {
        assertNull(summarizeSensorSeries(listOf(point(0, 5.0))))
    }

    @Test
    fun `rising tail reports rising`() {
        val summary = summarizeSensorSeries(series(1.0, 2.0, 7.0))

        assertEquals(SensorSeriesTrend.RISING, summary?.trend)
        assertEquals(7.0, summary?.last)
    }

    @Test
    fun `falling tail reports falling`() {
        val summary = summarizeSensorSeries(series(9.0, 4.0, 1.0))

        assertEquals(SensorSeriesTrend.FALLING, summary?.trend)
        assertEquals(1.0, summary?.last)
    }

    @Test
    fun `unchanged tail reports flat`() {
        val summary = summarizeSensorSeries(series(3.0, 6.0, 6.0))

        assertEquals(SensorSeriesTrend.FLAT, summary?.trend)
    }

    @Test
    fun `min and max span the whole series rather than its tail`() {
        val summary = summarizeSensorSeries(series(2.0, 11.0, -4.0, 5.0, 6.0))

        assertEquals(-4.0, summary?.min)
        assertEquals(11.0, summary?.max)
        assertEquals(SensorSeriesTrend.RISING, summary?.trend)
    }

    @Test
    fun `series without any secondary value still summarises its primary`() {
        val points = listOf(point(0, 1.0), point(1, 4.0))

        val summary = summarizeSensorSeries(points)

        assertEquals(4.0, summary?.last)
        assertEquals(1.0, summary?.min)
        assertEquals(4.0, summary?.max)
    }

    private fun series(vararg values: Double): List<SensorSeriesPoint> =
        values.mapIndexed { index, value -> point(index.toLong(), value) }

    private fun point(index: Long, value: Double): SensorSeriesPoint = SensorSeriesPoint(
        takenAtMillis = index * STEP_MS,
        primaryValue = value,
        secondaryValue = null,
    )

    private companion object {
        const val STEP_MS = 1_000L
    }
}
