package com.sza.fastmediasorter.domain.usecase.tourist

import com.sza.fastmediasorter.domain.model.UnitScale
import com.sza.fastmediasorter.domain.model.WearTouristWeatherPayload
import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.model.weather.WeatherSnapshot
import com.sza.fastmediasorter.domain.model.weather.WeatherUnit
import com.sza.fastmediasorter.domain.repository.WeatherRepository
import com.sza.fastmediasorter.domain.repository.WeatherResult
import javax.inject.Inject

/**
 * S4152: the weather details the watch Tourist asked for, read through the phone's own weather cache.
 *
 * The watch has no network request of its own, so the phone answers with what its Tourist would show at
 * that place. A stale reading is still sent: the watch judges its age by [WeatherSnapshot.observedAtMs]
 * and drops it itself, which keeps one freshness rule on one side.
 */
class GetWatchTouristWeatherUseCase @Inject constructor(
    private val weatherRepository: WeatherRepository,
) {

    suspend operator fun invoke(latitude: Double, longitude: Double): WearTouristWeatherPayload? {
        val location = WeatherLocation(latitude, longitude, LOCATION_LABEL)
        val snapshot = when (val result = weatherRepository.current(location)) {
            is WeatherResult.Fresh -> result.snapshot
            is WeatherResult.Stale -> result.snapshot
            WeatherResult.Unavailable -> null
        }
        return snapshot?.toPayload()
    }

    private fun WeatherSnapshot.toPayload(): WearTouristWeatherPayload = WearTouristWeatherPayload(
        observedAtMs = observedAtMs,
        apparentTemperatureCelsius = details.apparentTemperature?.let { toCelsius(it, unit) },
        humidityPercent = details.relativeHumidityPercent,
        windSpeedKmh = details.windSpeedKmh,
        windGustsKmh = details.windGustsKmh,
        windDirectionDegrees = details.windDirectionDegrees,
        uvIndex = details.uvIndex,
        precipitationProbabilityPercent = details.precipitationProbabilityPercent,
        cloudCoverPercent = details.cloudCoverPercent,
        visibilityMetres = details.visibilityMetres,
    )

    // The repository answers in the phone user's scale; the wire is Celsius so the watch can follow its own.
    private fun toCelsius(value: Double, unit: WeatherUnit): Double = when (unit) {
        WeatherUnit.CELSIUS -> value
        WeatherUnit.FAHRENHEIT -> UnitScale.fahrenheitToCelsius(value)
    }

    private companion object {
        // Shares the phone Tourist's cache entry for the same place; the label never reaches the wire.
        const val LOCATION_LABEL = "tourist"
    }
}
