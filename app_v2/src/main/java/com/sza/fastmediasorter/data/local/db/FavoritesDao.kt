package com.sza.fastmediasorter.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
    
    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: Long)
}
