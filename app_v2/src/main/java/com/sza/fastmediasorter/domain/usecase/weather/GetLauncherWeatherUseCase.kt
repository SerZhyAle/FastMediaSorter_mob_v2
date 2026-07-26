package com.sza.fastmediasorter.domain.usecase.weather

import com.sza.fastmediasorter.domain.model.weather.WeatherLocation
import com.sza.fastmediasorter.domain.repository.WeatherRepository
import com.sza.fastmediasorter.domain.repository.WeatherResult
import javax.inject.Inject

/** S0426: the desktop gadget's only door to the weather - it never touches the repository directly. */
class GetLauncherWeatherUseCase @Inject constructor(
    private val repository: WeatherRepository,
) {

    suspend operator fun invoke(location: WeatherLocation): WeatherResult = repository.current(location)
}
