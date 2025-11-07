package com.sza.fastmediasorter_v2.data.local.db

import androidx.room.*
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import kotlinx.coroutines.flow.Flow

@Dao
interface ResourceDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(resource: ResourceEntity): Long
    
    @Update
    suspend fun update(resource: ResourceEntity)
    
    @Delete
    suspend fun delete(resource: ResourceEntity)
    
    @Query("DELETE FROM resources WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("SELECT * FROM resources WHERE id = :id")
    fun getResourceById(id: Long): Flow<ResourceEntity?>
    
    @Query("SELECT * FROM resources WHERE id = :id")
    suspend fun getResourceByIdSync(id: Long): ResourceEntity?
    
    @Query("SELECT * FROM resources WHERE type = :type ORDER BY displayOrder ASC, name ASC")
    fun getResourcesByType(type: ResourceType): Flow<List<ResourceEntity>>
    
    @Query("SELECT * FROM resources ORDER BY displayOrder ASC, name ASC")
    fun getAllResources(): Flow<List<ResourceEntity>>
    
    @Query("SELECT * FROM resources ORDER BY displayOrder ASC, name ASC")
    suspend fun getAllResourcesSync(): List<ResourceEntity>
    
    @Query("SELECT * FROM resources WHERE isDestination = 1 ORDER BY destinationOrder ASC")
    fun getDestinations(): Flow<List<ResourceEntity>>
}
