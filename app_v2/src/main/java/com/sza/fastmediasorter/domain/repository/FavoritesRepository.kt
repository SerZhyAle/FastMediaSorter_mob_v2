package com.sza.fastmediasorter.domain.repository

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import kotlinx.coroutines.flow.Flow

interface FavoritesRepository {
    fun getAllFavorites(): Flow<List<FavoritesEntity>>
    fun isFavorite(uri: String): Flow<Boolean>
    suspend fun isFavoriteSync(uri: String): Boolean
    suspend fun addFavorite(entity: FavoritesEntity)
    suspend fun removeFavorite(uri: String)
    suspend fun removeFavoriteById(id: Long)
}
