package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.MotionHistoryEntry
import kotlinx.coroutines.flow.Flow

/**
 * S3014: Persistent store for physical activity and step measurements on the watch.
 */
interface MotionHistoryRepository {

    /**
     * Emits the complete list of history entries newest-first, and re-emits on every change.
     */
    fun observeAll(): Flow<List<MotionHistoryEntry>>

    /**
     * Inserts one history entry and returns its generated row id.
     */
    suspend fun insert(entry: MotionHistoryEntry): Long

    /**
     * Deletes all history entries from the store.
     */
    suspend fun deleteAll()
}
