package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * S0404: what was launched through the launcher, newest first. This is the app's own record - the
 * system usage statistics need a special-access permission the mode deliberately avoids (ADR-7).
 * [target] holds an encoded command.
 */
@Entity(tableName = "launcher_journal")
data class LauncherJournalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val target: String,
    val launchedAt: Long
)

@Dao
interface LauncherJournalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LauncherJournalEntity)

    @Query("SELECT * FROM launcher_journal ORDER BY launchedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<LauncherJournalEntity>>

    @Query("DELETE FROM launcher_journal WHERE target = :target")
    suspend fun deleteByTarget(target: String)

    @Query(
        "DELETE FROM launcher_journal WHERE id NOT IN " +
            "(SELECT id FROM launcher_journal ORDER BY launchedAt DESC LIMIT :keep)"
    )
    suspend fun trim(keep: Int)

    @Query("DELETE FROM launcher_journal")
    suspend fun deleteAll()
}
