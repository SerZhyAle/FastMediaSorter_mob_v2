package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.tourist.WearTouristCounterState
import kotlinx.coroutines.flow.Flow

/**
 * S4185: the persisted watch Tourist tally counter. Every mutation is applied to the latest stored state,
 * so rapid taps from the screen are never lost to a stale read.
 */
interface WearTouristCounterRepository {

    fun observe(): Flow<WearTouristCounterState>

    suspend fun update(transform: (WearTouristCounterState) -> WearTouristCounterState)
}
