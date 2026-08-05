package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * S1401: how often and how recently one launcher command was run. [target] is the encoded
 * [com.sza.fastmediasorter.domain.model.launcher.LauncherCellCommand], the same key the pins and the
 * journal use.
 *
 * Separate from `launcher_journal` on purpose: the journal is trimmed to its newest rows so the
 * taskbar's recent strip stays cheap, which would make a count derived from it reset itself as the
 * user keeps launching things. This table is never trimmed, so the "most used" order stays true.
 */
@Entity(tableName = "launcher_launch_stats")
data class LauncherLaunchStatsEntity(
    @PrimaryKey
    val target: String,
    val launchCount: Int,
    val lastLaunchedAt: Long
)

@Dao
interface LauncherLaunchStatsDao {

    @Query("SELECT * FROM launcher_launch_stats")
    fun observeAll(): Flow<List<LauncherLaunchStatsEntity>>

    /**
     * SQLite on the oldest supported API level predates the UPSERT clause, so the increment is an
     * UPDATE that reports how many rows it touched, with the INSERT as its zero-row branch. The
     * transaction is what keeps two concurrent launches from both taking that branch.
     */
    @Transaction
    suspend fun recordLaunch(target: String, launchedAt: Long) {
        if (increment(target, launchedAt) == 0) {
            insertIfAbsent(LauncherLaunchStatsEntity(target, launchCount = 1, lastLaunchedAt = launchedAt))
        }
    }

    @Query(
        "UPDATE launcher_launch_stats SET launchCount = launchCount + 1, lastLaunchedAt = :launchedAt " +
            "WHERE target = :target"
    )
    suspend fun increment(target: String, launchedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entity: LauncherLaunchStatsEntity)

    @Query("DELETE FROM launcher_launch_stats WHERE target = :target")
    suspend fun deleteByTarget(target: String)
}
