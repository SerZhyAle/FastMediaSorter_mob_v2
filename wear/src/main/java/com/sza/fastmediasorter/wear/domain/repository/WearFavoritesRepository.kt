package com.sza.fastmediasorter.wear.domain.repository

import com.sza.fastmediasorter.wear.domain.model.WearFavoriteDeltaItem
import com.sza.fastmediasorter.wear.domain.model.WearFavoriteRecord

interface WearFavoritesRepository {
    suspend fun addFavorite(sourceId: String, filePath: String)

    /**
     * S1846: marks a file and remembers enough about it to draw and reopen it later.
     *
     * The two-string [addFavorite] above stays: a caller that knows only the pair - the delta replay from
     * the phone, for one - would otherwise have to invent a display name.
     */
    suspend fun addFavorite(record: WearFavoriteRecord)

    /** S1846: every favourite on this watch, records and pre-record entries alike, newest write last. */
    suspend fun getFavorites(): List<WearFavoriteRecord>
    suspend fun removeFavorite(sourceId: String, filePath: String)
    suspend fun isFavorite(sourceId: String, filePath: String): Boolean
    suspend fun getPendingDelta(): List<WearFavoriteDeltaItem>
    suspend fun clearPendingDelta()
}
