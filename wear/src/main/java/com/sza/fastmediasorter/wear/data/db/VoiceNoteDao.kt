package com.sza.fastmediasorter.wear.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * S1862: the pending state is passed in rather than written into the SQL as a literal, so the enum
 * stays the only place that spells its name. No DAO method carries a Kotlin default argument - Room
 * generates the override and the defaulted call would route through a synthetic bridge for nothing.
 */
@Dao
interface VoiceNoteDao {

    /** Newest first: the note that was just taken belongs at the top of the recorder's list. */
    @Query("SELECT * FROM voice_notes ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<VoiceNoteEntity>>

    @Query("SELECT * FROM voice_notes WHERE deliveryState = :pendingState ORDER BY createdAtMillis ASC")
    fun observePending(pendingState: String): Flow<List<VoiceNoteEntity>>

    /**
     * The one-shot twin of [observePending]. Phase 02 drains the queue from a listener callback with
     * no lifecycle to collect a Flow on, so it needs the list once rather than a subscription.
     * Oldest first, like the observed query: draining newest-first starves the oldest note.
     */
    @Query("SELECT * FROM voice_notes WHERE deliveryState = :pendingState ORDER BY createdAtMillis ASC")
    suspend fun getPendingOnce(pendingState: String): List<VoiceNoteEntity>

    @Insert
    suspend fun insert(note: VoiceNoteEntity): Long

    @Query("UPDATE voice_notes SET deliveryState = :state WHERE id = :id")
    suspend fun updateDeliveryState(id: Long, state: String)

    @Query("DELETE FROM voice_notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM voice_notes WHERE id = :id")
    suspend fun getById(id: Long): VoiceNoteEntity?
}
