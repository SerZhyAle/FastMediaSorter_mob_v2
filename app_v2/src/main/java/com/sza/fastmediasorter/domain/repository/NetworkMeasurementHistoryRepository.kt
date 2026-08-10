package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.networkmonitor.NetworkMeasurement
import kotlinx.coroutines.flow.Flow

/**
 * S1433: the Network Monitor's history of manual measurements.
 *
 * The retention cap lives behind this interface rather than at the call sites. A diagnostic screen is used
 * in bursts, and a caller that had to remember to trim would eventually be a caller that forgot.
 */
interface NetworkMeasurementHistoryRepository {

    /** Stores [measurement] and drops whatever the retention cap pushes out, as one atomic step. */
    suspend fun record(measurement: NetworkMeasurement)

    fun observeHistory(): Flow<List<NetworkMeasurement>>

    suspend fun clear()

    /**
     * One-shot snapshot for the export file.
     *
     * Separate from [observeHistory] because an export is a single moment on purpose: serialising from a
     * live stream would let a measurement finishing mid-write land halfway down the file.
     */
    suspend fun exportPayload(): List<NetworkMeasurement>
}
