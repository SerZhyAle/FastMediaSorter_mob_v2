package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * S1905: per-cell configuration storage for launcher desktop gadgets (e.g. weather city location).
 */
@Entity(
    tableName = "launcher_cell_config",
    primaryKeys = ["cellId", "key"],
    foreignKeys = [
        ForeignKey(
            entity = LauncherCellEntity::class,
            parentColumns = ["id"],
            childColumns = ["cellId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LauncherCellConfigEntity(
    val cellId: Long,
    val key: String,
    val value: String,
)

@Dao
interface LauncherCellConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LauncherCellConfigEntity)

    @Query("SELECT value FROM launcher_cell_config WHERE cellId = :cellId AND key = :key")
    suspend fun getConfigValue(cellId: Long, key: String): String?

    @Query("SELECT * FROM launcher_cell_config WHERE cellId = :cellId")
    suspend fun getAllConfigForCell(cellId: Long): List<LauncherCellConfigEntity>

    @Query("SELECT * FROM launcher_cell_config")
    fun observeAllConfigs(): kotlinx.coroutines.flow.Flow<List<LauncherCellConfigEntity>>

    @Query("DELETE FROM launcher_cell_config WHERE cellId = :cellId")
    suspend fun deleteForCell(cellId: Long)
}
