package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.WearFavoritesDeltaPayload
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import timber.log.Timber
import javax.inject.Inject

class ApplyWatchFavoritesDeltaUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {

    suspend operator fun invoke(payload: WearFavoritesDeltaPayload) {
        for (item in payload.items) {
            if (item.isFavorite) {
                val entity = FavoritesEntity(
                    uri = item.filePath,
                    resourceId = 0L,
                    displayName = item.filePath.substringAfterLast('/').ifEmpty { item.filePath },
                    mediaType = 0,
                    size = 0L,
                    lastKnownPath = item.filePath,
                    dateModified = item.changedAt,
                    addedTimestamp = item.changedAt
                )
                favoritesRepository.addFavorite(entity)
            } else {
                favoritesRepository.removeFavorite(item.filePath)
            }
        }
    }
}
