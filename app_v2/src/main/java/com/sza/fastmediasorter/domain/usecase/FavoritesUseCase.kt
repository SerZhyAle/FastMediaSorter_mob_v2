package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class FavoritesUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository,
    private val statsSink: StatsSink,
) {
    fun getAllFavorites(): Flow<List<FavoritesEntity>> {
        return favoritesRepository.getAllFavorites()
    }

    fun isFavorite(uri: String): Flow<Boolean> {
        return favoritesRepository.isFavorite(uri)
    }
    
    suspend fun isFavoriteSync(uri: String): Boolean {
        return favoritesRepository.isFavoriteSync(uri)
    }

    suspend fun getFavoritesForPaths(paths: List<String>): Map<String, Boolean> {
        return favoritesRepository.getFavoritesForPaths(paths)
    }

    suspend fun toggleFavorite(mediaFile: MediaFile, resourceId: Long) {
        timber.log.Timber.d("FavoritesUseCase.toggleFavorite: START - file='${mediaFile.name}', path='${mediaFile.path}', resourceId=$resourceId")
        val isFav = favoritesRepository.isFavoriteSync(mediaFile.path)
        timber.log.Timber.d("FavoritesUseCase.toggleFavorite: Current status - isFavorite=$isFav")
        
        if (isFav) {
            timber.log.Timber.d("FavoritesUseCase.toggleFavorite: Removing from favorites")
            favoritesRepository.removeFavorite(mediaFile.path)
            timber.log.Timber.d("FavoritesUseCase.toggleFavorite: REMOVED successfully")
            statsSink.record(StatsEvent.Favorite(added = false))
        } else {
            timber.log.Timber.d("FavoritesUseCase.toggleFavorite: Adding to favorites")
            val entity = FavoritesEntity(
                uri = mediaFile.path,
                resourceId = resourceId,
                displayName = mediaFile.name,
                mediaType = mediaFile.type.ordinal,
                size = mediaFile.size,
                lastKnownPath = mediaFile.path,
                dateModified = mediaFile.createdDate
            )
            favoritesRepository.addFavorite(entity)
            timber.log.Timber.d("FavoritesUseCase.toggleFavorite: ADDED successfully - entity.uri='${entity.uri}', entity.resourceId=${entity.resourceId}")
            statsSink.record(StatsEvent.Favorite(added = true))
        }
    }
}
