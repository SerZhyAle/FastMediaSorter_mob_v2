package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity

/**
 * S1511: what one stream channel learned about its video-quality rung, held in its own table for the same
 * reason `stream_play_outcome` is - Room invalidates per table, so a write landing on the catalog row would
 * re-emit the whole catalog to the streams list on every probe verdict.
 *
 * Keyed by [normalizedUrl] plus [rungBitrateBps] rather than by the catalog row id: the id is reissued when
 * the same channel is removed and re-imported, and the bitrate is part of the identity because a source can
 * move to a different encoding, which makes the old record describe a rung that no longer exists.
 *
 * [ceilingWidthPx] / [ceilingHeightPx] are the rung the channel settled on, [probeFailureCount] how many
 * probes back up to the rung above this one have failed, and [recordedAt] the epoch-millis of the last write
 * - the age this record is pruned by.
 */
@Entity(tableName = "stream_quality_memory", primaryKeys = ["normalizedUrl", "rungBitrateBps"])
data class StreamQualityMemoryEntity(
    val normalizedUrl: String,
    val rungBitrateBps: Int,

    val ceilingWidthPx: Int,
    val ceilingHeightPx: Int,
    val probeFailureCount: Int,
    val recordedAt: Long
)
