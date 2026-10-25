package com.sza.fastmediasorter.data.broadcast

import com.google.gson.annotations.SerializedName
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * LIVE-BROADCAST item S: the marker of a descriptor whose broadcast has not started yet. [until] is the RFC 3339
 * UTC instant after which the link it rides in is no longer valid; nullable because Gson may leave it unset.
 */
data class BroadcastPendingDto(
    @SerializedName("until") val until: String?,
) {

    /** Epoch millis of [until], or null when the value is not an RFC 3339 instant. */
    fun untilMs(): Long? = if (until.isNullOrBlank()) null else try {
        Instant.parse(until.trim()).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
