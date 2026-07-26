package com.sza.fastmediasorter.domain.weather

import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.model.weather.WeatherSnapshot
import com.sza.fastmediasorter.domain.model.weather.WeatherUnit

/**
 * S0426: the one place that speaks to a weather service. Swapping Open-Meteo for another keyless
 * source (api.met.no) must be a new implementation of this interface and nothing else.
 *
 * Neither method throws: a weather block that crashes the home screen is worse than one that shows
 * nothing, so failures come back as null / empty and the repository decides what the user sees.
 */
interface WeatherProvider {

    suspend fun currentWeather(location: WeatherLocation, unit: WeatherUnit): WeatherSnapshot?

    suspend fun searchLocations(query: String, languageTag: String): List<WeatherLocation>
}
