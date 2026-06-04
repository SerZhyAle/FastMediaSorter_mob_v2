package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledOperationDao {

    @Query("SELECT * FROM scheduled_operations ORDER BY id ASC")
    fun getAll(): Flow<List<ScheduledOperationEntity>>

    @Query("SELECT * FROM scheduled_operations WHERE is_enabled = 1 ORDER BY id ASC")
    suspend fun getAllEnabled(): List<ScheduledOperationEntity>

    @Query("SELECT * FROM scheduled_operations WHERE is_enabled = 1 ORDER BY next_run_at ASC")
    suspend fun getUpcomingEnabled(): List<ScheduledOperationEntity>

    @Query("SELECT * FROM scheduled_operations WHERE id = :id")
    suspend fun getById(id: Long): ScheduledOperationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduledOperationEntity): Long

    @Update
    suspend fun update(entity: ScheduledOperationEntity)

    @Query("DELETE FROM scheduled_operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scheduled_operations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM scheduled_operations")
    suspend fun getCount(): Int
}
