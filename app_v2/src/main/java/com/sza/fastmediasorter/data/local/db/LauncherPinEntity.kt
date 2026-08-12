package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * S0404: an icon pinned to the launcher taskbar. [position] is the stable slot in the strip;
 * [target] holds an encoded command.
 */
@Entity(tableName = "launcher_pins")
data class LauncherPinEntity(
    @PrimaryKey
    val position: Int,
    val target: String
)

@Dao
interface LauncherPinDao {

    @Query("SELECT * FROM launcher_pins ORDER BY position ASC")
    fun observeAll(): Flow<List<LauncherPinEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LauncherPinEntity)

    @Query("DELETE FROM launcher_pins WHERE position = :position")
    suspend fun deleteByPosition(position: Int)

    @Query("DELETE FROM launcher_pins")
    suspend fun deleteAll()
}
