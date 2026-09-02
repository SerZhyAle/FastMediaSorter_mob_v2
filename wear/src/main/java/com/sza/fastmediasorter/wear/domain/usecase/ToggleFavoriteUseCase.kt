package com.sza.fastmediasorter.wear.domain.usecase

import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.repository.WearFavoritesRepository
import javax.inject.Inject

/**
 * S1689: marking a file and telling the phone about it are one action, not two.
 *
 * Both halves used to sit in the player's ViewModel, which meant every screen that ever gains a
 * favourite button would have to remember the second half. Extracted while adding the album-art
 * use case beside it, so the screen keeps asking for one collaborator per job.
 */
class ToggleFavoriteUseCase @Inject constructor(
    private val favoritesRepository: WearFavoritesRepository,
    private val sendFavoritesDelta: SendFavoritesDeltaUseCase,
    private val requestWearTileRefreshUseCase: RequestWearTileRefreshUseCase
) {

    /** Flips the mark for one file and pushes the change to the phone. Returns the new state. */
    suspend fun toggle(sourceId: String, filePath: String, wasFavorite: Boolean): Boolean {
        if (wasFavorite) {
            favoritesRepository.removeFavorite(sourceId, filePath)
        } else {
            favoritesRepository.addFavorite(sourceId, filePath)
        }
        sendFavoritesDelta()
        requestWearTileRefreshUseCase(WearTileKind.FAVOURITES)
        return !wasFavorite
    }

    suspend fun add(record: WearFavoriteRecord): Boolean {
        favoritesRepository.addFavorite(record)
        sendFavoritesDelta()
        requestWearTileRefreshUseCase(WearTileKind.FAVOURITES)
        return true
    }

    suspend fun isFavorite(sourceId: String, filePath: String): Boolean =
        favoritesRepository.isFavorite(sourceId, filePath)
}
