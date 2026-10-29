package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import kotlinx.coroutines.flow.Flow

/** S4247: the broadcasts prepared before their start, kept in protected storage outside every backup. */
interface PreparedBroadcastLinkStore {

    val links: Flow<List<PreparedBroadcastLink>>

    /** The stored records; empty when none is stored and also when the stored list cannot be read right now. */
    suspend fun all(): List<PreparedBroadcastLink>

    /**
     * The stored records, empty when none is stored, and null when a list is stored but cannot be read right now.
     * A caller that writes or sweeps from the result must use this, because an unreadable list is not an empty one.
     */
    suspend fun allOrNull(): List<PreparedBroadcastLink>?

    /**
     * Adds [link] or replaces the record with its `broadcastId`; false when it could not be encrypted, or when the
     * stored list cannot be read, which leaves the stored records untouched.
     */
    suspend fun put(link: PreparedBroadcastLink): Boolean

    /** Deletes a stored list that cannot be read; false when nothing is stored or the list reads fine. */
    suspend fun discardUnreadable(): Boolean

    suspend fun remove(broadcastId: String)

    /** Keeps the record of a started broadcast with its secrets cleared. */
    suspend fun markConsumed(broadcastId: String)
}
