package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for pending OAuth token revocations (B5-T3).
 */
@Dao
interface PendingRevocationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PendingRevocationEntity): Long

    @Query("SELECT * FROM pending_revocations ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingRevocationEntity>

    @Query("DELETE FROM pending_revocations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_revocations SET attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun incrementAttemptCount(id: Long)

    /** Remove stale entries exceeding max retry threshold */
    @Query("DELETE FROM pending_revocations WHERE attemptCount >= :maxAttempts")
    suspend fun deleteExhausted(maxAttempts: Int)
}
