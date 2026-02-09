package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDao: FavoritesDao
) : FavoritesRepository {
    override fun getAllFavorites(): Flow<List<FavoritesEntity>> {
        return favoritesDao.getAllFavorites()
    }

    override fun isFavorite(uri: String): Flow<Boolean> {
        return favoritesDao.isFavorite(uri)
    }

    override suspend fun isFavoriteSync(uri: String): Boolean {
        return favoritesDao.isFavoriteSync(uri)
    }

    override suspend fun addFavorite(entity: FavoritesEntity) {
        favoritesDao.insert(entity)
    }

    override suspend fun removeFavorite(uri: String) {
        favoritesDao.deleteByUri(uri)
    }
    
    override suspend fun removeFavoriteById(id: Long) {
        favoritesDao.deleteById(id)
    }
}
