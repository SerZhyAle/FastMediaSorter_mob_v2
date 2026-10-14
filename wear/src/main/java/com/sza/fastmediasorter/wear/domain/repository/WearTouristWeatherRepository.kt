package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearTouristWeatherPayload
import com.sza.fastmediasorter.wear.domain.tourist.WearWeatherPosition
import kotlinx.coroutines.flow.Flow

/**
 * S4152: where Tourist gets its weather details. Today the paired phone answers; a source of the watch's own
 * would stand behind this same interface, so the screen never learns which one spoke.
 */
interface WearTouristWeatherRepository {

    /** The latest answer, whatever its age; null until one exists. Freshness is the caller's decision. */
    fun observeWeather(): Flow<WearTouristWeatherPayload?>

    /** Asks for the weather at an already rounded [position]. */
    suspend fun requestWeather(position: WearWeatherPosition)
}
