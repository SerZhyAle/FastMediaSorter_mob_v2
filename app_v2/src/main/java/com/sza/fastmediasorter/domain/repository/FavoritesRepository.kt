package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.FavoritesRemapOutcome
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun getAllFavorites(): Flow<List<FavoritesEntity>>

    /**
     * S1170: the file-only slice, i.e. favourites that have something to open. A live-channel favourite
     * has no file behind it, so surfaces that render a playable list - the home-screen widget and the
     * launcher desktop gadget - read this rather than filtering [getAllFavorites] themselves.
     */
    fun getFileFavorites(): Flow<List<FavoritesEntity>>
    fun isFavorite(uri: String): Flow<Boolean>
    suspend fun isFavoriteSync(uri: String): Boolean
    suspend fun getFavoritesForPaths(paths: List<String>): Map<String, Boolean>
    suspend fun addFavorite(entity: FavoritesEntity)
    suspend fun removeFavorite(uri: String)
    suspend fun removeFavoriteById(id: Long)

    /**
     * S2370: rewrites the file addresses of one resource's favorites from an old direct-path
     * prefix onto a system folder tree. Rows whose target file no longer exists under the tree
     * keep their old address (preserved in their [FavoritesEntity.lastKnownPath] fallback) and
     * are counted, never deleted.
     */
    suspend fun remapResourceFavoritesToTree(
        resourceId: Long,
        oldPathPrefix: String,
        treeUriString: String,
    ): FavoritesRemapOutcome
}
