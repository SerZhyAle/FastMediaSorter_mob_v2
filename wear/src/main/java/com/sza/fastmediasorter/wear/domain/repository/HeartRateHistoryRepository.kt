package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.HeartRateHistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * S2808: persists and reads heart-rate measurement history.
 *
 * [save] takes only the BPM value and stamps the timestamp internally, keeping the clock
 * concern out of the caller. [observeAll] returns newest-first, matching the screen order.
 */
interface HeartRateHistoryRepository {

    suspend fun save(bpm: Int)

    fun observeAll(): Flow<List<HeartRateHistoryEntry>>

    suspend fun deleteAll()
}
