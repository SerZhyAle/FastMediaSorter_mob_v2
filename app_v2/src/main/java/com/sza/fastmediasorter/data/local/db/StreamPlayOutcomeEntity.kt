package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * S1502: last local playback outcome of one stream channel, held in its own table instead of in
 * `stream_sources`. Room tracks invalidation per table, so while this value lived on the catalog row
 * every probe result re-emitted the whole catalog to the streams list; a separate table is the only
 * split that breaks that coupling.
 *
 * [outcome] is "OK" (last play reached playing) or "FAIL" (last attempt errored); a channel with no
 * row here has never been tried. [recordedAt] is the epoch-millis timestamp of that outcome.
 */
@Entity(tableName = "stream_play_outcome")
data class StreamPlayOutcomeEntity(
    @PrimaryKey
    val streamId: String,

    val outcome: String,
    val recordedAt: Long
)
