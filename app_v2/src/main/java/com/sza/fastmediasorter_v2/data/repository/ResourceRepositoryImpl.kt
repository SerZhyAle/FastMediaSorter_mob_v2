package com.sza.fastmediasorter_v2.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.sza.fastmediasorter_v2.data.local.db.ResourceDao
import com.sza.fastmediasorter_v2.data.local.db.ResourceEntity
import com.sza.fastmediasorter_v2.domain.model.MediaResource
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.model.SortMode
import com.sza.fastmediasorter_v2.domain.repository.ResourceRepository
import com.sza.fastmediasorter_v2.domain.usecase.SmbOperationsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourceRepositoryImpl @Inject constructor(
    private val resourceDao: ResourceDao,
    private val smbOperationsUseCase: SmbOperationsUseCase
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
    
    override suspend fun getFilteredResources(
        filterByType: Set<ResourceType>?,
        filterByMediaType: Set<MediaType>?,
        filterByName: String?,
        sortMode: SortMode
    ): List<MediaResource> {
        val whereConditions = mutableListOf<String>()
        val args = mutableListOf<Any>()
        
        // Filter by resource type
        if (filterByType != null && filterByType.isNotEmpty()) {
            val placeholders = filterByType.joinToString(",") { "?" }
            whereConditions.add("type IN ($placeholders)")
            args.addAll(filterByType.map { it.name })
        }
        
        // Filter by media types (using bitwise AND on supportedMediaTypesFlags)
        if (filterByMediaType != null && filterByMediaType.isNotEmpty()) {
            // Calculate required flags
            var requiredFlags = 0
            if (MediaType.IMAGE in filterByMediaType) requiredFlags = requiredFlags or 0b0001
            if (MediaType.VIDEO in filterByMediaType) requiredFlags = requiredFlags or 0b0010
            if (MediaType.AUDIO in filterByMediaType) requiredFlags = requiredFlags or 0b0100
            if (MediaType.GIF in filterByMediaType) requiredFlags = requiredFlags or 0b1000
            
            // Resource matches if ANY of the selected media types are supported
            whereConditions.add("(supportedMediaTypesFlags & ?) > 0")
            args.add(requiredFlags)
        }
        
        // Filter by name substring
        if (filterByName != null && filterByName.isNotBlank()) {
            whereConditions.add("(name LIKE ? OR path LIKE ?)")
            val pattern = "%$filterByName%"
            args.add(pattern)
            args.add(pattern)
        }
        
        // Build WHERE clause
        val whereClause = if (whereConditions.isNotEmpty()) {
            "WHERE " + whereConditions.joinToString(" AND ")
        } else {
            ""
        }
        
        // Build ORDER BY clause
        val orderBy = when (sortMode) {
            SortMode.MANUAL -> "ORDER BY displayOrder ASC"
            SortMode.NAME_ASC -> "ORDER BY name COLLATE NOCASE ASC"
            SortMode.NAME_DESC -> "ORDER BY name COLLATE NOCASE DESC"
            SortMode.DATE_ASC -> "ORDER BY createdDate ASC"
            SortMode.DATE_DESC -> "ORDER BY createdDate DESC"
            SortMode.SIZE_ASC -> "ORDER BY fileCount ASC"
            SortMode.SIZE_DESC -> "ORDER BY fileCount DESC"
            SortMode.TYPE_ASC -> "ORDER BY type ASC"
            SortMode.TYPE_DESC -> "ORDER BY type DESC"
        }
        
        // Build full query
        val sql = "SELECT * FROM resources $whereClause $orderBy"
        
        Timber.d("getFilteredResources: SQL=$sql, args=$args")
        
        val query = SimpleSQLiteQuery(sql, args.toTypedArray())
        val entities = resourceDao.getResourcesRaw(query)
        
        return entities.map { it.toDomain() }
    }
    
    override suspend fun addResource(resource: MediaResource): Long {
        return resourceDao.insert(resource.toEntity())
    }
    
    override suspend fun updateResource(resource: MediaResource) {
        resourceDao.update(resource.toEntity())
    }
    
    override suspend fun swapResourceDisplayOrders(resource1: MediaResource, resource2: MediaResource) {
        resourceDao.swapDisplayOrders(
            id1 = resource1.id,
            order1 = resource1.displayOrder,
            id2 = resource2.id,
            order2 = resource2.displayOrder
        )
    }
    
    override suspend fun deleteResource(resourceId: Long) {
        resourceDao.deleteById(resourceId)
    }
    
    override suspend fun testConnection(resource: MediaResource): Result<String> {
        return when (resource.type) {
            ResourceType.LOCAL -> {
                // Local resources don't need connection testing
                Result.success("Local resource - no connection test needed")
            }
            ResourceType.SMB -> {
                testSmbConnection(resource)
            }
            ResourceType.CLOUD, ResourceType.SFTP -> {
                // Not yet implemented
                Result.success("Connection test not yet implemented for ${resource.type}")
            }
        }
    }
    
    private suspend fun testSmbConnection(resource: MediaResource): Result<String> {
        val credentialsId = resource.credentialsId
        if (credentialsId == null) {
            Timber.w("SMB resource has no credentials ID")
            return Result.failure(Exception("No credentials configured for this SMB resource"))
        }
        
        // Get connection info from credentials
        val connectionInfoResult = smbOperationsUseCase.getConnectionInfo(credentialsId)
        if (connectionInfoResult.isFailure) {
            val error = connectionInfoResult.exceptionOrNull()
            Timber.e(error, "Failed to get SMB connection info")
            return Result.failure(error ?: Exception("Failed to get connection info"))
        }
        
        val connectionInfo = connectionInfoResult.getOrNull()!!
        
        // Test connection
        return smbOperationsUseCase.testConnection(
            server = connectionInfo.server,
            shareName = connectionInfo.shareName,
            username = connectionInfo.username,
            password = connectionInfo.password,
            domain = connectionInfo.domain,
            port = connectionInfo.port
        )
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
            createdDate = createdDate,
            displayOrder = displayOrder
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
            createdDate = createdDate,
            displayOrder = displayOrder
        )
    }
}
