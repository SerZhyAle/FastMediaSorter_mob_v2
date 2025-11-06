package com.sza.fastmediasorter_v2.data.repository

import com.sza.fastmediasorter_v2.data.local.db.ResourceDao
import com.sza.fastmediasorter_v2.data.local.db.ResourceEntity
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceRepositoryImpl @Inject constructor(
    private val resourceDao: ResourceDao
) : ResourceRepository {
    
    override fun getAllResources(): Flow<List<MediaResource>> {
        return resourceDao.getAllResources().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun getAllResourcesSync(): List<MediaResource> {
        return resourceDao.getAllResourcesSync().map { it.toDomain() }
    }
    
    override suspend fun getResourceById(id: Long): MediaResource? {
        return resourceDao.getResourceByIdSync(id)?.toDomain()
    }
    
    override fun getResourcesByType(type: ResourceType): Flow<List<MediaResource>> {
        return resourceDao.getResourcesByType(type).map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override fun getDestinations(): Flow<List<MediaResource>> {
        return resourceDao.getDestinations().map { entities ->
            entities.map { it.toDomain() }
        }
    }
    
    override suspend fun addResource(resource: MediaResource): Long {
        return resourceDao.insert(resource.toEntity())
    }
    
    override suspend fun updateResource(resource: MediaResource) {
        resourceDao.update(resource.toEntity())
    }
    
    override suspend fun deleteResource(resourceId: Long) {
        resourceDao.deleteById(resourceId)
    }
    
    override suspend fun testConnection(resource: MediaResource): Result<String> {
        // TODO: Implement connection testing based on resource type
        return Result.success("Connection test not yet implemented")
    }
    
    private fun ResourceEntity.toDomain(): MediaResource {
        val mediaTypes = mutableSetOf<MediaType>()
        if (supportedMediaTypesFlags and 0b0001 != 0) mediaTypes.add(MediaType.IMAGE)
        if (supportedMediaTypesFlags and 0b0010 != 0) mediaTypes.add(MediaType.VIDEO)
        if (supportedMediaTypesFlags and 0b0100 != 0) mediaTypes.add(MediaType.AUDIO)
        if (supportedMediaTypesFlags and 0b1000 != 0) mediaTypes.add(MediaType.GIF)
        
        return MediaResource(
            id = id,
            name = name,
            path = path,
            type = type,
            credentialsId = credentialsId,
            supportedMediaTypes = mediaTypes,
            sortMode = sortMode,
            displayMode = displayMode,
            lastViewedFile = lastViewedFile,
            fileCount = fileCount,
            lastAccessedDate = lastAccessedDate,
            slideshowInterval = slideshowInterval,
            isDestination = isDestination,
            destinationOrder = destinationOrder,
            destinationColor = destinationColor,
            isWritable = isWritable,
            createdDate = createdDate
        )
    }
    
    private fun MediaResource.toEntity(): ResourceEntity {
        var flags = 0
        if (MediaType.IMAGE in supportedMediaTypes) flags = flags or 0b0001
        if (MediaType.VIDEO in supportedMediaTypes) flags = flags or 0b0010
        if (MediaType.AUDIO in supportedMediaTypes) flags = flags or 0b0100
        if (MediaType.GIF in supportedMediaTypes) flags = flags or 0b1000
        
        return ResourceEntity(
            id = id,
            name = name,
            path = path,
            type = type,
            credentialsId = credentialsId,
            supportedMediaTypesFlags = flags,
            sortMode = sortMode,
            displayMode = displayMode,
            lastViewedFile = lastViewedFile,
            fileCount = fileCount,
            lastAccessedDate = lastAccessedDate,
            slideshowInterval = slideshowInterval,
            isDestination = isDestination,
            destinationOrder = destinationOrder ?: -1,
            destinationColor = destinationColor,
            isWritable = isWritable,
            createdDate = createdDate
        )
    }
}
