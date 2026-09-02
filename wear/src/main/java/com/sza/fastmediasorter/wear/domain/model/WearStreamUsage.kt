package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2146: how often a stream was started on this watch, and when it was last started.
 *
 * [identity] is what [foldWearStreamIdentity] returns, the same folded address the streams list
 * compares pins by - so a catalogue re-import that renumbers every row leaves the count attached to
 * the station rather than to a row id, and http and https spellings of one station share a count.
 *
 * The wire names are pinned because this JSON outlives the process: the writer and the reader can be
 * two different builds, and one R8 mapping does not survive between them.
 */
data class WearStreamUsage(
    @SerializedName("identity") val identity: String,
    @SerializedName("play_count") val playCount: Int,
    @SerializedName("last_played_at") val lastPlayedAt: Long
)

/**
 * S2146 §7: how many entries the store keeps. The record grows with what the owner actually started,
 * not with the nineteen thousand rows of the catalogue, so the bound is a backstop rather than a
 * routine trim.
 */
const val WEAR_STREAM_USAGE_LIMIT = 500

/**
 * S2146: the whole of "record one play", kept pure so it can be tested without an Android runtime.
 *
 * The watch module has no Robolectric harness, and the same split already carries [mergeFavorites] -
 * the store supplies the two shapes it reads, the decision lives here.
 */
fun recordWearStreamPlay(
    current: Map<String, WearStreamUsage>,
    identity: String,
    atEpochMillis: Long,
    limit: Int = WEAR_STREAM_USAGE_LIMIT
): Map<String, WearStreamUsage> {
    val merged = current + (
        identity to WearStreamUsage(
            identity = identity,
            playCount = (current[identity]?.playCount ?: 0) + 1,
            lastPlayedAt = atEpochMillis
        )
        )
    if (merged.size <= limit) {
        return merged
    }
    // Newest first, so the entries dropped are the ones the owner has not returned to in longest.
    return merged.values
        .sortedByDescending { it.lastPlayedAt }
        .take(limit)
        .associateBy { it.identity }
}
