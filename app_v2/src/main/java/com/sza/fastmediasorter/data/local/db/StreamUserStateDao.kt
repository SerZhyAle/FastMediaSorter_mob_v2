package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * S1832: reads and writes the user-authored half of a stream channel, keyed by derived identity.
 *
 * Every read the catalog merge needs is a whole-table read. The bank is 17 628 rows and growing, so a
 * per-row lookup during import would turn one merge into that many queries; the table holding only what
 * the user actually made keeps it small enough to load at once.
 */
@Dao
interface StreamUserStateDao {

    @Query("SELECT * FROM stream_user_state")
    fun observeAll(): Flow<List<StreamUserStateEntity>>

    /** One-shot whole-table read for the merge, which joins in memory rather than per row. */
    @Query("SELECT * FROM stream_user_state")
    suspend fun snapshot(): List<StreamUserStateEntity>

    @Query("SELECT * FROM stream_user_state WHERE identityKey = :identityKey")
    suspend fun stateFor(identityKey: String): StreamUserStateEntity?

    /**
     * INSERT OR REPLACE rather than upsert syntax, mirroring [StreamQualityMemoryDao.rememberRung]:
     * SQLite's native upsert clause needs 3.24, which the framework ships only from API 30, and the
     * `legacy` flavor runs on minSdk 23.
     */
    @Query(
        "INSERT OR REPLACE INTO stream_user_state " +
            "(identityKey, pinned, sortIndex, playOutcome, outcomeAt, updatedAt) " +
            "SELECT :identityKey, :pinned, :sortIndex, " +
            "(SELECT playOutcome FROM stream_user_state WHERE identityKey = :identityKey), " +
            "(SELECT outcomeAt FROM stream_user_state WHERE identityKey = :identityKey), " +
            ":atMillis"
    )
    suspend fun setPin(identityKey: String, pinned: Boolean, sortIndex: Int, atMillis: Long)

    /**
     * Writes the outcome while preserving the pin, so recording a failed play cannot silently unpin a
     * channel. The pin columns are non-null, so an absent row seeds them as unpinned at index 0.
     */
    @Query(
        "INSERT OR REPLACE INTO stream_user_state " +
            "(identityKey, pinned, sortIndex, playOutcome, outcomeAt, updatedAt) " +
            "SELECT :identityKey, " +
            "COALESCE((SELECT pinned FROM stream_user_state WHERE identityKey = :identityKey), 0), " +
            "COALESCE((SELECT sortIndex FROM stream_user_state WHERE identityKey = :identityKey), 0), " +
            ":outcome, :recordedAt, :atMillis"
    )
    suspend fun setOutcome(identityKey: String, outcome: String, recordedAt: Long, atMillis: Long)

    /** S0756: the pin order the main-window streams panel renders, lowest [sortIndex] first. */
    @Query("SELECT * FROM stream_user_state WHERE pinned = 1 ORDER BY sortIndex ASC")
    suspend fun pinnedSnapshot(): List<StreamUserStateEntity>

    /** S0565: pin-to-top decrements below the current minimum; null when nothing is pinned yet. */
    @Query("SELECT MIN(sortIndex) FROM stream_user_state WHERE pinned = 1")
    suspend fun minSortIndex(): Int?

    @Query(
        "UPDATE stream_user_state SET sortIndex = :sortIndex, updatedAt = :atMillis " +
            "WHERE identityKey = :identityKey"
    )
    suspend fun setSortIndex(identityKey: String, sortIndex: Int, atMillis: Long)

    /** S0659: clear every OK/FAIL bullet without touching a single pin. */
    @Query("UPDATE stream_user_state SET playOutcome = NULL, outcomeAt = NULL WHERE playOutcome IS NOT NULL")
    suspend fun clearOutcomes()

    /**
     * Bounds the table. A pinned row is never pruned - it is the thing the user asked to keep - so only
     * unpinned rows, which carry at most a stale OK/FAIL bullet, age out.
     */
    @Query("DELETE FROM stream_user_state WHERE pinned = 0 AND updatedAt < :cutoffMillis")
    suspend fun pruneUnpinnedOlderThan(cutoffMillis: Long): Int

    @Query("DELETE FROM stream_user_state WHERE identityKey = :identityKey")
    suspend fun deleteByIdentity(identityKey: String)
}
