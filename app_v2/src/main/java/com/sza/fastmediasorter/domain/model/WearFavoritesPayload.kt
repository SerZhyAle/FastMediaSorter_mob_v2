package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

// S1631: keys pinned - the watch reads this contract by its real field names.

data class WearFavoriteDeltaItem(
    @SerializedName("sourceId") val sourceId: String,
    @SerializedName("filePath") val filePath: String,
    @SerializedName("isFavorite") val isFavorite: Boolean,
    @SerializedName("changedAt") val changedAt: Long
)

data class WearFavoritesDeltaPayload(
    @SerializedName("items") val items: List<WearFavoriteDeltaItem>
)
