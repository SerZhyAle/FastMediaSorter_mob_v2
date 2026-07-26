package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.model.weather.WeatherSnapshot

/**
 * S0426: what the UI is allowed to know about the weather.
 *
 * [WeatherResult.Stale] exists because "no network" must not blank the block - a two-hour-old reading
 * with a staleness marker is still information, while an empty cell reads as a broken gadget.
 */
sealed interface WeatherResult {

    data class Fresh(val snapshot: WeatherSnapshot) : WeatherResult

    data class Stale(val snapshot: WeatherSnapshot, val ageMs: Long) : WeatherResult

    data object Unavailable : WeatherResult
}

interface WeatherRepository {

    suspend fun current(location: WeatherLocation): WeatherResult

    suspend fun search(query: String): List<WeatherLocation>
}
