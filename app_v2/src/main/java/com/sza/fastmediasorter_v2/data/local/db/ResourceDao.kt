package com.sza.fastmediasorter_v2.data.local.db

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
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
    
    /**
     * Raw query for flexible filtering and sorting.
     * Used by repository to build dynamic queries based on filter parameters.
     */
    @RawQuery(observedEntities = [ResourceEntity::class])
    fun getResourcesRaw(query: SupportSQLiteQuery): List<ResourceEntity>
    
    /**
     * Atomically swap display orders of two resources in a single transaction.
     * Used for manual reordering (moveUp/moveDown) to avoid race conditions.
     */
    @Transaction
    suspend fun swapDisplayOrders(id1: Long, order1: Int, id2: Long, order2: Int) {
        // Update first resource
        updateDisplayOrder(id1, order2)
        // Update second resource
        updateDisplayOrder(id2, order1)
    }
    
    @Query("UPDATE resources SET displayOrder = :newOrder WHERE id = :resourceId")
    suspend fun updateDisplayOrder(resourceId: Long, newOrder: Int)
}
