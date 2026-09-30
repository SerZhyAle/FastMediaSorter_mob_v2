package com.sza.fastmediasorter.data.input

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InputBindingDao {

    @Query("SELECT * FROM input_bindings")
    fun observeAll(): Flow<List<InputBindingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: InputBindingEntity)

    @Query("DELETE FROM input_bindings WHERE command_id = :commandId")
    suspend fun deleteByCommand(commandId: String)

    @Query("DELETE FROM input_bindings WHERE command_id = :commandId AND device = :device")
    suspend fun deleteByCommandAndDevice(commandId: String, device: String)

    @Query("SELECT DISTINCT command_id FROM input_bindings")
    suspend fun distinctCommandIds(): List<String>

    @Query("DELETE FROM input_bindings WHERE command_id IN (:commandIds)")
    suspend fun deleteByCommands(commandIds: List<String>)

    @Query("DELETE FROM input_bindings")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM input_bindings)")
    suspend fun hasAny(): Boolean
}
