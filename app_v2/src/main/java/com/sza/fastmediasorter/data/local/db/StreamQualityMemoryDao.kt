package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Query

/**
 * S1511: the quality memory of one stream channel. Read once per session when the rendition ladder becomes
 * known, written when the ceiling moves or a probe upward fails.
 */
@Dao
interface StreamQualityMemoryDao {

    /**
     * The rung the channel most recently settled on. Newest wins because a source that re-encodes leaves its
     * previous bitrate behind as a row that no longer matches any rung on the current ladder.
     */
    @Query(
        "SELECT * FROM stream_quality_memory WHERE normalizedUrl = :url " +
            "ORDER BY recordedAt DESC LIMIT 1"
    )
    suspend fun learnedRungFor(url: String): StreamQualityMemoryEntity?

    /**
     * S1511: writes only when something other than the timestamp would change, mirroring the guard
     * `StreamPlayOutcomeDao.markPlayOutcome` carries - an unchanged rung matches zero rows and never touches
     * the table.
     *
     * INSERT .. SELECT .. WHERE NOT EXISTS rather than upsert syntax: SQLite's native upsert clause needs
     * 3.24, which the framework only ships from API 30, and `legacy` runs on minSdk 23.
     */
    @Query(
        "INSERT OR REPLACE INTO stream_quality_memory " +
            "(normalizedUrl, rungBitrateBps, ceilingWidthPx, ceilingHeightPx, probeFailureCount, recordedAt) " +
            "SELECT :url, :bitrateBps, :widthPx, :heightPx, :failures, :atMillis WHERE NOT EXISTS (" +
            "SELECT 1 FROM stream_quality_memory WHERE normalizedUrl = :url AND rungBitrateBps = :bitrateBps " +
            "AND ceilingWidthPx = :widthPx AND ceilingHeightPx = :heightPx AND probeFailureCount = :failures)"
    )
    suspend fun rememberRung(
        url: String,
        bitrateBps: Int,
        widthPx: Int,
        heightPx: Int,
        failures: Int,
        atMillis: Long
    )

    /** Forgetting one channel must take every rung it learned, not only the newest. */
    @Query("DELETE FROM stream_quality_memory WHERE normalizedUrl = :url")
    suspend fun deleteByUrl(url: String)

    /** A record older than the cutoff is a verdict about a network the channel may no longer be on. */
    @Query("DELETE FROM stream_quality_memory WHERE recordedAt < :cutoffMillis")
    suspend fun pruneOlderThan(cutoffMillis: Long)

    /**
     * Bound the table by channel rather than by row: a channel with several remembered rungs is one entry of
     * the budget, so re-encoding a source cannot evict other channels.
     */
    @Query(
        "DELETE FROM stream_quality_memory WHERE normalizedUrl NOT IN (" +
            "SELECT normalizedUrl FROM stream_quality_memory GROUP BY normalizedUrl " +
            "ORDER BY MAX(recordedAt) DESC LIMIT :keepNewest)"
    )
    suspend fun pruneToNewestChannels(keepNewest: Int)

    @Query("SELECT COUNT(DISTINCT normalizedUrl) FROM stream_quality_memory")
    suspend fun countChannels(): Int
}
