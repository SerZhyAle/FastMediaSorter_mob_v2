package com.sza.fastmediasorter.domain.repository

/**
 * S4247, DEVICE-EXCHANGE item DW: the expiry of a stream row imported from a link to a broadcast that has not
 * started yet, kept beside the row so the stream table gains no column.
 */
interface BroadcastPendingStore {

    /** Epoch millis the row stops waiting at; null when the row is not waiting. */
    suspend fun untilMs(streamId: String): Long?

    suspend fun put(streamId: String, untilMs: Long)

    suspend fun clear(streamId: String)
}
