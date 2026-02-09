package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.NetworkSource

/**
 * Repository for managing network storage sources.
 */
interface NetworkSourceRepository {
    
    /**
     * Get all configured network sources.
     */
    suspend fun getAllSources(): List<NetworkSource>
    
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
     * Delete network source by ID.
     */
    suspend fun deleteSource(id: String)
    
    /**
     * Test connection to network source.
     * 
     * @return Result with true if connection successful, false otherwise
     */
    suspend fun testConnection(source: NetworkSource): Result<Boolean>
}
