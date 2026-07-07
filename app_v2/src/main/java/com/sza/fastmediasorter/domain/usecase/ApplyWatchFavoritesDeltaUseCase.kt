package com.sza.fastmediasorter.domain.usecase

import com.sza.fastmediasorter.data.local.db.FavoritesEntity
import com.sza.fastmediasorter.domain.model.WearFavoritesDeltaPayload
import com.sza.fastmediasorter.domain.repository.FavoritesRepository
import javax.inject.Inject

class ApplyWatchFavoritesDeltaUseCase @Inject constructor(
    private val favoritesRepository: FavoritesRepository
) {

    suspend operator fun invoke(payload: WearFavoritesDeltaPayload) {
        for (item in payload.items) {
            if (item.isFavorite) {
                // S0932: the watch delta carries only a path (no kind/resourceId/streamMediaKind), so
                // rebuilding an entity here as a plain FILE row clobbers an existing STREAM favorite
                // (a channel URL like .../master.m3u8) into a mis-titled generic file favorite and drops
                // its stream open-routing. The favorites table is shared with live channels (S0783) and
                // Wear owns only the kind='FILE' slice, so never overwrite a row the phone already holds;
                // only materialize genuinely new file favorites the watch introduced.
                if (favoritesRepository.isFavoriteSync(item.filePath)) continue
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
