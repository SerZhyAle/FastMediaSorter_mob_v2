package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLaunchPanelTileDao {

    @Query("SELECT * FROM app_launch_panel_tiles ORDER BY slotIndex ASC")
    fun observeAll(): Flow<List<AppLaunchPanelTileEntity>>

    @Query("SELECT * FROM app_launch_panel_tiles ORDER BY slotIndex ASC")
    suspend fun getAll(): List<AppLaunchPanelTileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AppLaunchPanelTileEntity)

    @Query("DELETE FROM app_launch_panel_tiles WHERE slotIndex = :slotIndex")
    suspend fun deleteBySlot(slotIndex: Int)

    @Query("DELETE FROM app_launch_panel_tiles")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM app_launch_panel_tiles")
    suspend fun count(): Int
}
