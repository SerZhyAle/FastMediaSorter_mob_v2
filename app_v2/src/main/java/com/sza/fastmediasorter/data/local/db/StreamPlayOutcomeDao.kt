package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * S1502: side-channel access to the per-channel play outcome. The streams list observes
 * `stream_sources`; this table is observed separately, so recording one outcome repaints one row
 * instead of re-emitting the catalog.
 */
@Dao
interface StreamPlayOutcomeDao {

    @Query("SELECT * FROM stream_play_outcome")
    fun observeAll(): Flow<List<StreamPlayOutcomeEntity>>

    /**
     * S1502: writes only when the stored outcome actually differs, mirroring the guard the old
     * `StreamSourceDao.markPlayOutcome` carried - an unchanged outcome matches zero rows, so
     * SQLite's update hook stays silent and [observeAll] does not re-emit.
     *
     * INSERT .. SELECT .. WHERE NOT EXISTS rather than upsert syntax: `ON CONFLICT DO UPDATE` needs
     * SQLite 3.24, which the framework only ships from API 30, and `legacy` runs on minSdk 23.
     */
    @Query(
        "INSERT OR REPLACE INTO stream_play_outcome (streamId, outcome, recordedAt) " +
            "SELECT :id, :outcome, :atMillis WHERE NOT EXISTS (" +
            "SELECT 1 FROM stream_play_outcome WHERE streamId = :id AND outcome = :outcome)"
    )
    suspend fun markPlayOutcome(id: String, outcome: String, atMillis: Long)

    /** One-shot read for the "about this channel" window, which renders once and observes nothing. */
    @Query("SELECT outcome FROM stream_play_outcome WHERE streamId = :id LIMIT 1")
    suspend fun outcomeFor(id: String): String?

    /** S0659 behaviour, moved here: clear every channel's OK/FAIL bullet without touching channels. */
    @Query("DELETE FROM stream_play_outcome")
    suspend fun clearAllPlayOutcomes()

    /** Removing a channel must not leave its outcome behind as an orphan keyed by a dead id. */
    @Query("DELETE FROM stream_play_outcome WHERE streamId = :id")
    suspend fun deleteByStreamId(id: String)

    /**
     * S1826: the bulk counterpart of [deleteByStreamId], for the delete paths that work by url or by
     * origin and so never learn the ids they removed - the catalog prune and the downloaded-streams
     * wipe. Every write to this table resolves a live `stream_sources` row first, so a streamId with
     * no owning row can only be an orphan and this can never take a reachable outcome.
     *
     * Returns how many orphans went, so the caller can log a number instead of guessing.
     */
    @Query("DELETE FROM stream_play_outcome WHERE streamId NOT IN (SELECT id FROM stream_sources)")
    suspend fun deleteOrphanedPlayOutcomes(): Int
}
