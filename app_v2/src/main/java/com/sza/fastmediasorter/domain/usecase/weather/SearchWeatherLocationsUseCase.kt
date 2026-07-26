package com.sza.fastmediasorter.domain.usecase.weather

import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.repository.WeatherRepository
import javax.inject.Inject

/** S0426: city name to coordinates, keyless. A one-letter query would match half the planet. */
class SearchWeatherLocationsUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {

    suspend operator fun invoke(query: String): List<WeatherLocation> {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return emptyList()
        return repository.search(trimmed)
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}
