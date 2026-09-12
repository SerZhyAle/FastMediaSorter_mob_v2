package com.sza.fastmediasorter.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SolarCalculatorTest {

    @Test
    fun calculateSunriseSunset_equator_approx12HourDay() {
        // Quito, Ecuador (near equator)
        val lat = -0.1807
        val lon = -78.4678
        val zone = ZoneId.of("America/Guayaquil")
        val date = LocalDate.of(2026, 3, 20) // Equinox
        val testTime = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val result = SolarCalculator.calculateSunriseSunset(lat, lon, testTime, zone)

        assertNotNull(result.sunriseMillis)
        assertNotNull(result.sunsetMillis)
        assertTrue(result.isDaylight)

        val sunriseZoned = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(result.sunriseMillis!!), zone)
        val sunsetZoned = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(result.sunsetMillis!!), zone)

        // Sunrise should be around 06:00 - 06:30 local time
        assertEquals(6, sunriseZoned.hour)
        // Sunset should be around 18:00 - 18:30 local time
        assertEquals(18, sunsetZoned.hour)
    }

    @Test
    fun calculateSunriseSunset_londonSummer() {
        // London, UK on Summer Solstice
        val lat = 51.5074
        val lon = -0.1278
        val zone = ZoneId.of("Europe/London")
        val date = LocalDate.of(2026, 6, 21)
        val noonTime = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

        val result = SolarCalculator.calculateSunriseSunset(lat, lon, noonTime, zone)

        assertNotNull(result.sunriseMillis)
        assertNotNull(result.sunsetMillis)
        assertTrue(result.isDaylight)

        val sunriseZoned = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(result.sunriseMillis!!), zone)
        val sunsetZoned = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(result.sunsetMillis!!), zone)

        // Sunrise around 04:43 BST
        assertEquals(4, sunriseZoned.hour)
        // Sunset around 21:21 BST
        assertEquals(21, sunsetZoned.hour)
    }
}
