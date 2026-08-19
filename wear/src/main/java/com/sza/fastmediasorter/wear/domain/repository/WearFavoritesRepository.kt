package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearFavoriteDeltaItem

interface WearFavoritesRepository {
    suspend fun addFavorite(sourceId: String, filePath: String)
    suspend fun removeFavorite(sourceId: String, filePath: String)
    suspend fun isFavorite(sourceId: String, filePath: String): Boolean

    /** S1781: the home screen shows the Favourites section only when there is something in it. */
    suspend fun hasAnyFavorite(): Boolean
    suspend fun getPendingDelta(): List<WearFavoriteDeltaItem>
    suspend fun clearPendingDelta()
}
