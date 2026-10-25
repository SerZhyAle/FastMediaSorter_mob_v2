package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.domain.model.PreparedBroadcastLink
import kotlinx.coroutines.flow.Flow

/** S4247: the broadcasts prepared before their start, kept in protected storage outside every backup. */
interface PreparedBroadcastLinkStore {

    val links: Flow<List<PreparedBroadcastLink>>

    suspend fun all(): List<PreparedBroadcastLink>

    /** Adds [link] or replaces the record with its `broadcastId`; false when it could not be encrypted. */
    suspend fun put(link: PreparedBroadcastLink): Boolean

    suspend fun remove(broadcastId: String)

    /** Keeps the record of a started broadcast with its secrets cleared. */
    suspend fun markConsumed(broadcastId: String)
}
