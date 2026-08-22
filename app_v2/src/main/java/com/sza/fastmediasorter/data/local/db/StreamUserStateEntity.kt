package com.sza.fastmediasorter.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * S1832: everything the user authored about one stream channel - the pin and its position, the last
 * local play outcome - filed under the channel's derived identity rather than under the catalog row's
 * id. The catalog row is imported data and is deleted and reissued whenever the published bank changes;
 * this row is the user's own and must outlive it.
 *
 * Deliberately not a child of `stream_sources` and deliberately carrying no foreign key: a cascade
 * would delete exactly the rows this table exists to keep, on the first import that drops the channel.
 * The price is that a row here can outlive every catalog row pointing at it, which is why the table is
 * bounded - [updatedAt] is the column the prune orders by, and a pinned row is never pruned.
 */
@Entity(tableName = "stream_user_state")
data class StreamUserStateEntity(
    @PrimaryKey
    val identityKey: String,

    val pinned: Boolean,
    /** Lower sorts higher, mirroring `stream_sources.sortIndex`; meaningless while [pinned] is false. */
    val sortIndex: Int,
    /** S1502 semantics preserved: "OK", "FAIL", or null for a channel that was never tried. */
    val playOutcome: String?,
    /** Epoch millis of [playOutcome]; null exactly when [playOutcome] is null. */
    val outcomeAt: Long?,
    val updatedAt: Long
)
