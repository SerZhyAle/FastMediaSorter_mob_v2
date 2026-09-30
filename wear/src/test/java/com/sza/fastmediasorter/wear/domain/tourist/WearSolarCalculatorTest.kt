package com.sza.fastmediasorter.wear.domain.tourist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Sydney's sunrise and Los Angeles' sunset fall on a different UTC date than the local one;
 * Kyiv's do not, so it pins that the fix leaves the ordinary case alone.
 */
class WearSolarCalculatorTest {

    private val date = LocalDate.of(2026, 6, 21)

    private data class City(val zone: String, val latitude: Double, val longitude: Double)

    private val cities = listOf(
        City("Europe/Kyiv", 50.45, 30.52),
        City("Australia/Sydney", -33.87, 151.21),
        City("America/Los_Angeles", 34.05, -118.24),
    )

    private fun at(city: City, hour: Int): WearSolarCalculator.SolarTimes {
        val zoneId = ZoneId.of(city.zone)
        val millis = LocalDateTime.of(date, LocalTime.of(hour, 0))
            .atZone(zoneId).toInstant().toEpochMilli()
        return WearSolarCalculator.calculateSunriseSunset(city.latitude, city.longitude, millis, zoneId)
    }

    private fun localDateOf(millis: Long?, zone: String): LocalDate {
        assertNotNull(millis)
        return Instant.ofEpochMilli(millis!!).atZone(ZoneId.of(zone)).toLocalDate()
    }

    @Test
    fun `sunrise and sunset fall on the local date`() {
        cities.forEach { city ->
            val times = at(city, hour = 12)
            assertEquals(city.zone, date, localDateOf(times.sunriseMillis, city.zone))
            assertEquals(city.zone, date, localDateOf(times.sunsetMillis, city.zone))
            assertTrue(city.zone, times.sunriseMillis!! < times.sunsetMillis!!)
        }
    }

    @Test
    fun `daylight only between sunrise and sunset`() {
        cities.forEach { city ->
            assertFalse(city.zone, at(city, hour = 3).isDaylight)
            assertTrue(city.zone, at(city, hour = 12).isDaylight)
            assertFalse(city.zone, at(city, hour = 23).isDaylight)
        }
    }
}
