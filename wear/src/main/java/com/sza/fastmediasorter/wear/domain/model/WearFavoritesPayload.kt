package com.sza.fastmediasorter.wear.domain.model

data class WearFavoriteDeltaItem(
    val sourceId: String,
    val filePath: String,
    val isFavorite: Boolean,
    val changedAt: Long
)

data class WearFavoritesDeltaPayload(
    val items: List<WearFavoriteDeltaItem>
)
