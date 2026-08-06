package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * S0404: desktop-wide launcher state, single row (id = 1). The seeded flags make the profile
 * starter set a one-time event per orientation: once the user owns a desktop, changing the device
 * profile never overwrites it (strategic ADR-4).
 */
@Entity(tableName = "launcher_state")
data class LauncherStateEntity(
    @PrimaryKey
    val id: Int = 1,
    val seededPortrait: Boolean,
    val seededLandscape: Boolean,
    val columnsPortrait: Int,
    val columnsLandscape: Int
)

@Dao
interface LauncherStateDao {

    @Query("SELECT * FROM launcher_state WHERE id = 1")
    suspend fun get(): LauncherStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LauncherStateEntity)

    @Query("DELETE FROM launcher_state")
    suspend fun deleteAll()
}
