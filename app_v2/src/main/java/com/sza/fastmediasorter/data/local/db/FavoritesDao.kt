package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favoritesEntity: FavoritesEntity)

    @Query("DELETE FROM favorites WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("SELECT * FROM favorites ORDER BY addedTimestamp DESC")
    fun getAllFavorites(): Flow<List<FavoritesEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE uri = :uri LIMIT 1)")
    fun isFavorite(uri: String): Flow<Boolean>
    
    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE uri = :uri LIMIT 1)")
    suspend fun isFavoriteSync(uri: String): Boolean

    @Query("SELECT uri FROM favorites WHERE uri IN (:paths)")
    suspend fun getFavoriteUrisForPaths(paths: List<String>): List<String>
    
    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM favorites ORDER BY addedTimestamp DESC")
    suspend fun getAllFavoritesSync(): List<FavoritesEntity>

    // S0783: file-only slice for consumers that cannot handle live-channel rows (widget, export,
    // backup, Wear). The favorites screen keeps using [getAllFavorites] so channels still show there.
    @Query("SELECT * FROM favorites WHERE kind = 'FILE' ORDER BY addedTimestamp DESC")
    fun getFileFavorites(): Flow<List<FavoritesEntity>>

    @Query("SELECT * FROM favorites WHERE kind = 'FILE' ORDER BY addedTimestamp DESC")
    suspend fun getFileFavoritesSync(): List<FavoritesEntity>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun getFavoritesCount(): Int

    // S2370: a resource's favorites carry per-file absolute addresses; when the resource's own
    // address changes scheme (reconnect to a system folder tree) these rows are the ones whose
    // addresses must be rewritten. Stream rows carry a channel URL, not a file address, and stay
    // out of that rewrite.
    @Query("SELECT * FROM favorites WHERE resourceId = :resourceId AND kind = 'FILE'")
    suspend fun getFavoritesForResource(resourceId: Long): List<FavoritesEntity>

    @Update
    suspend fun updateFavorites(entities: List<FavoritesEntity>)
}
