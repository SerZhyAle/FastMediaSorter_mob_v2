package com.sza.fastmediasorter.data.repository

import com.sza.fastmediasorter.data.local.db.FavoritesDao
import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val favoritesDao: FavoritesDao
) : FavoritesRepository {
    companion object {
        private const val SQLITE_IN_CLAUSE_LIMIT = 900
    }

    override fun getAllFavorites(): Flow<List<FavoritesEntity>> {
        // Room invalidates at table granularity; dedup so an unrelated favorites write doesn't re-render unchanged rows (S0733/S0717 P3).
        return favoritesDao.getAllFavorites().distinctUntilChanged()
    }

    override fun isFavorite(uri: String): Flow<Boolean> {
        return favoritesDao.isFavorite(uri).distinctUntilChanged()
    }

    override suspend fun isFavoriteSync(uri: String): Boolean {
        return favoritesDao.isFavoriteSync(uri)
    }

    override suspend fun getFavoritesForPaths(paths: List<String>): Map<String, Boolean> {
        if (paths.isEmpty()) return emptyMap()

        val favoriteUris = mutableSetOf<String>()
        paths.distinct().chunked(SQLITE_IN_CLAUSE_LIMIT).forEach { chunk ->
            favoriteUris += favoritesDao.getFavoriteUrisForPaths(chunk)
        }

        return paths.associateWith { it in favoriteUris }
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
