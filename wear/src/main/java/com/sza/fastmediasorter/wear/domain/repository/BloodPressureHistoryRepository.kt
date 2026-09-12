package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * S2809: persists and reads blood pressure measurement history.
 *
 * [save] takes both the systolic and diastolic values and stamps the timestamp internally,
 * keeping the clock concern out of the caller. [observeAll] returns newest-first, matching
 * the screen order.
 */
interface BloodPressureHistoryRepository {

    suspend fun save(systolic: Int, diastolic: Int)

    fun observeAll(): Flow<List<BloodPressureHistoryEntry>>

    suspend fun deleteAll()
}
