package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.BloodPressureHistoryEntry
import com.sza.fastmediasorter.wear.domain.model.BloodPressureSource
import kotlinx.coroutines.flow.Flow

/**
 * S2809: persists and reads blood pressure measurement history.
 *
 * [save] takes both the systolic and diastolic values and stamps the timestamp internally,
 * keeping the clock concern out of the caller. [observeAll] returns newest-first, matching
 * the screen order.
 */
interface BloodPressureHistoryRepository {

    /**
     * S3113: [source] says whether the values were typed, taken by a cuff, or estimated; [pulse] is the heart
     * rate measured with them, or null when none was.
     */
    suspend fun save(systolic: Int, diastolic: Int, source: BloodPressureSource, pulse: Int?)

    fun observeAll(): Flow<List<BloodPressureHistoryEntry>>

    suspend fun deleteAll()
}
