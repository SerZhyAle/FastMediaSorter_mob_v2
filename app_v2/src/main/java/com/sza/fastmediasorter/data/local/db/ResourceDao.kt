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

    // --- S0200: Drive resource "needs sign-in" state ---

    /**
     * S0200 Phase 05: mark every Drive resource as requiring a fresh primary sign-in (or unmark).
     * Used ONLY by [S0200AuthStateWipe] on first launch after upgrade (ADR-6: full legacy
     * auth-state purge). Sign-out from the primary account must NOT use this - it has to spare
     * secondary multi-account Drive resources (strategic §11.5 / §5.2). Use
     * [markDriveNeedsSignInForCredentials] there instead.
     */
    @Query("UPDATE resources SET needs_sign_in = :flag WHERE type = 'CLOUD' AND cloudProvider = 'GOOGLE_DRIVE'")
    abstract suspend fun markAllDriveNeedsSignIn(flag: Boolean)

    /**
     * S0200 §11.5 fix: mark only the Drive resources whose [credentialsId] (= the bound Google
     * email per [GoogleDriveCredentialsManager]) matches the primary account being signed out.
     * Symmetric to [clearNeedsSignInForCredentials]; preserves access to secondary multi-account
     * Drive resources per strategic ADR-3 / §5.2.
     */
    @Query("UPDATE resources SET needs_sign_in = :flag WHERE type = 'CLOUD' AND cloudProvider = 'GOOGLE_DRIVE' AND credentialsId = :credentialsId")
    abstract suspend fun markDriveNeedsSignInForCredentials(credentialsId: String, flag: Boolean)

    /**
     * S0200 Phase 05: clear the needs-sign-in flag for every resource matching [credentialsId].
     * Called by [GoogleAccountSettingsViewModel.signInPrimary] on success.
     */
    @Query("UPDATE resources SET needs_sign_in = 0 WHERE credentialsId = :credentialsId")
    abstract suspend fun clearNeedsSignInForCredentials(credentialsId: String)

    // --- Public Write Methods ---

    // S1159: `resources_fts` is an external-content FTS4 table, so Room emits its own
    // BEFORE UPDATE/DELETE + AFTER INSERT/UPDATE content-sync triggers on `resources` (they are
    // re-created in onPostMigrate, so upgraded databases have them too). The index is therefore
    // maintained by the database itself - these writers must touch the content table only.
    // Hand-rolled FTS statements here used to run *after* the content row had already been
    // deleted/updated, which SQLite documents as undefined for external-content tables.

    open suspend fun insert(resource: ResourceEntity): Long = insertResource(resource)

    open suspend fun update(resource: ResourceEntity) = updateResource(resource)

    open suspend fun delete(resource: ResourceEntity) = deleteResource(resource)

    open suspend fun deleteAll() = deleteAllResources()

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

    @Query("SELECT * FROM resources WHERE lastBrowseDate IS NOT NULL ORDER BY lastBrowseDate DESC LIMIT :limit")
    abstract suspend fun getRecentResourcesSync(limit: Int): List<ResourceEntity>

    /**
     * Fetch the first local resource whose path equals [path]. Used by the Open-in-FMS resolver
     * to reuse a previously auto-created folder resource instead of duplicating it. Read-only;
     * no schema change.
     */
    @Query("SELECT * FROM resources WHERE type = 'LOCAL' AND path = :path ORDER BY displayOrder ASC LIMIT 1")
    abstract suspend fun getLocalResourceByPathSync(path: String): ResourceEntity?
    
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
        updateDisplayOrder(id2, order1)
    }
    
    @Query("UPDATE resources SET displayOrder = :newOrder WHERE id = :resourceId")
    abstract suspend fun updateDisplayOrder(resourceId: Long, newOrder: Int)

    @Query("UPDATE resources SET icon_id = :iconId WHERE id = :resourceId")
    abstract suspend fun updateIcon(resourceId: Long, iconId: String?)

    // S1001: single-field writers must not rewrite the whole row - a full-entity @Update built
    // from a stale in-memory copy clobbers concurrently written stats (fileCount/lastBrowseDate).
    @Query("UPDATE resources SET lastViewedFile = :path WHERE id = :resourceId")
    abstract suspend fun updateLastViewedFile(resourceId: Long, path: String?)

    @Query("UPDATE resources SET lastScrollPosition = :position WHERE id = :resourceId")
    abstract suspend fun updateLastScrollPosition(resourceId: Long, position: Int)

    @Query("SELECT * FROM resources WHERE icon_id IS NULL")
    abstract suspend fun findResourcesWithoutIcon(): List<ResourceEntity>

    /**
     * Batch-update displayOrder for a reordered list in a single transaction.
     * Each pair is (resourceId, newDisplayOrder). Used by drag-to-reorder.
     */
    @Transaction
    open suspend fun updateAllDisplayOrders(orders: List<Pair<Long, Int>>) {
        for ((id, order) in orders) {
            updateDisplayOrder(id, order)
        }
    }
}
