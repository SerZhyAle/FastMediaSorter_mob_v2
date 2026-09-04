package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.NetworkSource
import com.sza.fastmediasorter.wear.domain.model.WearSourceTombstonePayload
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing network storage sources.
 */
interface NetworkSourceRepository {
    
    /**
     * Get all configured network sources.
     */
    suspend fun getAllSources(): List<NetworkSource>

    /**
     * Observe configured network sources reactively.
     */
    fun observeSources(): Flow<List<NetworkSource>>
    
    /**
     * Get specific network source by ID.
     */
    suspend fun getSourceById(id: String): NetworkSource?
    
    /**
     * Add new network source.
     */
    suspend fun addSource(source: NetworkSource)
    
    /**
     * Update existing network source.
     */
    suspend fun updateSource(source: NetworkSource)
    
    /**
     * Insert or update a source by merge key (type, server, port, shareName).
     * Used by sync import to avoid duplicates.
     */
    suspend fun upsertSource(source: NetworkSource)

    /**
     * Delete network source by ID.
     *
     * Import path only: it leaves no deletion evidence, because the event it applies was already
     * decided by the other side. A user delete goes through [deleteSourceWithTombstone].
     */
    suspend fun deleteSource(id: String)

    /**
     * Delete network source by ID as a user action, recording the deletion event first.
     *
     * S2507: without the tombstone the next exchange cannot tell this removal from a resource that
     * never existed on this watch, and the phone hands the resource back.
     */
    suspend fun deleteSourceWithTombstone(id: String, deletedAt: Long)

    /**
     * Read the deletion events this watch still carries.
     */
    suspend fun getTombstones(): List<WearSourceTombstonePayload>

    /**
     * Store a deletion event, replacing any earlier event for the same resource id.
     */
    suspend fun recordTombstone(tombstone: WearSourceTombstonePayload)

    /**
     * Drop the deletion event for [id], used when a later edit beats the recorded deletion.
     */
    suspend fun removeTombstone(id: String)

    /**
     * Test connection to network source.
     * 
     * @return Result with true if connection successful, false otherwise
     */
    suspend fun testConnection(source: NetworkSource): Result<Boolean>
}
