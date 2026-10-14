package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.tourist.TouristCounterState
import kotlinx.coroutines.flow.Flow

/**
 * S4184: the persisted Tourist tally counter. Every mutation is applied to the latest stored state, so
 * rapid taps from the screen are never lost to a stale read.
 */
interface TouristCounterRepository {

    fun observe(): Flow<TouristCounterState>

    suspend fun update(transform: (TouristCounterState) -> TouristCounterState)
}
