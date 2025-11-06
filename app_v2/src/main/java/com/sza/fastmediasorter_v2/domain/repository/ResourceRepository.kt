package com.sza.fastmediasorter_v2.domain.repository

import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Resource operations
 * Implementation will be in data layer
 */
interface ResourceRepository {
    
    fun getAllResources(): Flow<List<MediaResource>>
    
    suspend fun getAllResourcesSync(): List<MediaResource>
    
    suspend fun getResourceById(id: Long): MediaResource?
    
    fun getResourcesByType(type: ResourceType): Flow<List<MediaResource>>
    
    fun getDestinations(): Flow<List<MediaResource>>
    
    suspend fun addResource(resource: MediaResource): Long
    
    suspend fun updateResource(resource: MediaResource)
    
    suspend fun deleteResource(resourceId: Long)
    
    suspend fun testConnection(resource: MediaResource): Result<String>
}
