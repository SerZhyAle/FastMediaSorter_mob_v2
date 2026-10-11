package com.sza.fastmediasorter.domain.usecase.tourist

import com.sza.fastmediasorter.domain.model.weather.WeatherCondition
import com.sza.fastmediasorter.domain.model.weather.WeatherDetails
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.model.weather.WeatherSnapshot
import com.sza.fastmediasorter.domain.model.weather.WeatherUnit
import com.sza.fastmediasorter.domain.repository.WeatherRepository
import com.sza.fastmediasorter.domain.repository.WeatherResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetWatchTouristWeatherUseCaseTest {

    private class FakeWeatherRepository(private val result: WeatherResult) : WeatherRepository {
        var requested: WeatherLocation? = null

        override suspend fun current(location: WeatherLocation, forceRefresh: Boolean): WeatherResult {
            requested = location
            return result
        }

        override suspend fun search(query: String): List<WeatherLocation> = emptyList()
    }

    private fun snapshot(unit: WeatherUnit, apparent: Double?) = WeatherSnapshot(
        location = WeatherLocation(LAT, LON, "x"),
        temperature = 10.0,
        unit = unit,
        condition = WeatherCondition.CLEAR,
        isDay = true,
        observedAtMs = OBSERVED_AT,
        details = WeatherDetails(
            apparentTemperature = apparent,
            relativeHumidityPercent = 64,
            windSpeedKmh = 12.5,
            windGustsKmh = 30.0,
            windDirectionDegrees = 230,
            uvIndex = 3.2,
            precipitationProbabilityPercent = 40,
            cloudCoverPercent = 75,
            visibilityMetres = 24_000.0,
        ),
    )

    @Test
    fun `fresh reading maps every detail and asks for the given place`() = runTest {
        val repo = FakeWeatherRepository(WeatherResult.Fresh(snapshot(WeatherUnit.CELSIUS, 8.0)))

        val payload = GetWatchTouristWeatherUseCase(repo)(LAT, LON)!!

        assertEquals(LAT, repo.requested!!.latitude, 0.0)
        assertEquals(LON, repo.requested!!.longitude, 0.0)
        assertEquals(OBSERVED_AT, payload.observedAtMs)
        assertEquals(8.0, payload.apparentTemperatureCelsius!!, DELTA)
        assertEquals(64, payload.humidityPercent)
        assertEquals(12.5, payload.windSpeedKmh!!, DELTA)
        assertEquals(30.0, payload.windGustsKmh!!, DELTA)
        assertEquals(230, payload.windDirectionDegrees)
        assertEquals(3.2, payload.uvIndex!!, DELTA)
        assertEquals(40, payload.precipitationProbabilityPercent)
        assertEquals(75, payload.cloudCoverPercent)
        assertEquals(24_000.0, payload.visibilityMetres!!, DELTA)
    }

    @Test
    fun `fahrenheit feels-like is sent in celsius`() = runTest {
        val repo = FakeWeatherRepository(WeatherResult.Fresh(snapshot(WeatherUnit.FAHRENHEIT, 50.0)))

        val payload = GetWatchTouristWeatherUseCase(repo)(LAT, LON)!!

        assertEquals(10.0, payload.apparentTemperatureCelsius!!, DELTA)
    }

    @Test
    fun `stale reading is still sent with its own observation time`() = runTest {
        val repo = FakeWeatherRepository(WeatherResult.Stale(snapshot(WeatherUnit.CELSIUS, null), ageMs = 1L))

        val payload = GetWatchTouristWeatherUseCase(repo)(LAT, LON)!!

        assertEquals(OBSERVED_AT, payload.observedAtMs)
        assertNull(payload.apparentTemperatureCelsius)
    }

    @Test
    fun `unavailable weather sends nothing`() = runTest {
        val repo = FakeWeatherRepository(WeatherResult.Unavailable)

        assertNull(GetWatchTouristWeatherUseCase(repo)(LAT, LON))
    }

    private companion object {
        const val LAT = 50.45
        const val LON = 30.52
        const val OBSERVED_AT = 1_700_000_000_000L
        const val DELTA = 1e-6
    }
}
