package com.sza.fastmediasorter.data.local.db

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.sza.fastmediasorter.domain.model.ResourceType
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ResourceDao {
    
    // --- Main Table Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertResource(resource: ResourceEntity): Long
    
    @Update
    protected abstract suspend fun updateResource(resource: ResourceEntity)
    
    @Delete
    protected abstract suspend fun deleteResource(resource: ResourceEntity)

    @Query("DELETE FROM resources WHERE id = :id")
    abstract suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM resources")
    abstract suspend fun deleteAllResources()

    // --- FTS Table Operations ---

    @Query("INSERT INTO resources_fts(docid, name, path) VALUES (:docid, :name, :path)")
    protected abstract suspend fun insertFts(docid: Long, name: String, path: String)

    @Query("UPDATE resources_fts SET name = :name, path = :path WHERE docid = :docid")
    protected abstract suspend fun updateFts(docid: Long, name: String, path: String)

    @Query("DELETE FROM resources_fts WHERE docid = :docid")
    protected abstract suspend fun deleteFts(docid: Long)

    @Query("DELETE FROM resources_fts")
    protected abstract suspend fun deleteAllFts()

    // --- Public Transactional Methods ---

    @Transaction
    open suspend fun insert(resource: ResourceEntity): Long {
        val id = insertResource(resource)
        insertFts(id, resource.name, resource.path)
        return id
    }

    @Transaction
    open suspend fun update(resource: ResourceEntity) {
        updateResource(resource)
        updateFts(resource.id, resource.name, resource.path)
    }

    @Transaction
    open suspend fun delete(resource: ResourceEntity) {
        deleteResource(resource)
        deleteFts(resource.id)
    }

    @Transaction
    open suspend fun deleteByIdWithFts(id: Long) {
        deleteById(id)
        deleteFts(id)
    }

    @Transaction
    open suspend fun deleteAll() {
        deleteAllResources()
        deleteAllFts()
    }

    // --- Queries ---
    
    @Query("SELECT * FROM resources WHERE id = :id")
    abstract fun getResourceById(id: Long): Flow<ResourceEntity?>
    
    @Query("SELECT * FROM resources WHERE id = :id")
    abstract suspend fun getResourceByIdSync(id: Long): ResourceEntity?
    
    @Query("SELECT * FROM resources WHERE type = :type ORDER BY displayOrder ASC, name ASC")
    abstract fun getResourcesByType(type: ResourceType): Flow<List<ResourceEntity>>
    
    @Query("SELECT * FROM resources ORDER BY displayOrder ASC, name ASC")
    abstract fun getAllResources(): Flow<List<ResourceEntity>>
    
    @Query("SELECT * FROM resources ORDER BY displayOrder ASC, name ASC")
    abstract suspend fun getAllResourcesSync(): List<ResourceEntity>
    
    @Query("SELECT * FROM resources WHERE isDestination = 1 ORDER BY destinationOrder ASC")
    abstract fun getDestinations(): Flow<List<ResourceEntity>>
    
    /**
     * Raw query for flexible filtering and sorting.
     * Used by repository to build dynamic queries based on filter parameters.
     */
    @RawQuery(observedEntities = [ResourceEntity::class])
    abstract fun getResourcesRaw(query: SupportSQLiteQuery): List<ResourceEntity>

    /**
     * FTS Search Query
     * Uses JOIN to return full ResourceEntity objects.
     */
    @Query("""
        SELECT r.* 
        FROM resources r
        JOIN resources_fts fts ON r.id = fts.docid
        WHERE fts.name MATCH :query
    """)
    abstract fun searchResourcesFts(query: String): List<ResourceEntity>
    
    /**
     * Atomically swap display orders of two resources in a single transaction.
     * Used for manual reordering (moveUp/moveDown) to avoid race conditions.
     */
    @Transaction
    open suspend fun swapDisplayOrders(id1: Long, order1: Int, id2: Long, order2: Int) {
        // Update first resource
        updateDisplayOrder(id1, order2)
        // Update second resource
        updateDisplayOrder(id2, order1)
    }
    
    @Query("UPDATE resources SET displayOrder = :newOrder WHERE id = :resourceId")
    abstract suspend fun updateDisplayOrder(resourceId: Long, newOrder: Int)
}
