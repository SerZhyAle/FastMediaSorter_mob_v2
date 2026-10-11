package com.sza.fastmediasorter.domain.model.tourist

import org.junit.Assert.assertEquals
import org.junit.Test

/** S4150: a watch reading is shown only while fresh and only in a build that offers it. */
class WatchHealthReadingTest {

    private val reading = WatchHealthReading(heartRateBpm = 72, bodyTemperatureCelsius = 36.6f, receivedAtMillis = NOW)

    @Test
    fun `fresh reading keeps both values`() {
        val stats = reading.toStats(offered = true, nowMillis = NOW + WATCH_HEALTH_FRESHNESS_MS)

        assertEquals(WatchHealthStats(offered = true, heartRateBpm = 72, bodyTemperatureCelsius = 36.6f), stats)
    }

    @Test
    fun `stale reading drops the values but stays offered`() {
        val stats = reading.toStats(offered = true, nowMillis = NOW + WATCH_HEALTH_FRESHNESS_MS + 1)

        assertEquals(WatchHealthStats(offered = true), stats)
    }

    @Test
    fun `build without watch health shows nothing`() {
        assertEquals(WatchHealthStats(offered = false), reading.toStats(offered = false, nowMillis = NOW))
    }

    @Test
    fun `no report yet gives empty values`() {
        val none: WatchHealthReading? = null

        assertEquals(WatchHealthStats(offered = true), none.toStats(offered = true, nowMillis = NOW))
    }

    @Test
    fun `reading stamped in the future after a clock change is treated as absent`() {
        assertEquals(WatchHealthStats(offered = true), reading.toStats(offered = true, nowMillis = NOW - 1))
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
