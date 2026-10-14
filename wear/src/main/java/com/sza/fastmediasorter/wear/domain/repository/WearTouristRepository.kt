package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.tourist.WearTouristState
import kotlinx.coroutines.flow.Flow

/**
 * S3007: repository providing real-time telemetry from GPS and hardware sensors on Wear OS.
 */
interface WearTouristRepository {

    /**
     * Emits continuous telemetry updates for as long as the flow is collected.
     */
    fun observeTelemetry(): Flow<WearTouristState>

    /**
     * Resets the whole trip - distance, time, moving time, ascent, descent and max speed - and its stored copy.
     */
    suspend fun resetTrip()

    /**
     * Resets the base step count for the current session.
     */
    fun resetSteps()
}
