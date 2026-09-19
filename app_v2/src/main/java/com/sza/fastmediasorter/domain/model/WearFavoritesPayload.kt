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

/**
 * S3161: what [WearFavoriteDeltaItem.sourceId] may say, mirrored from the watch's own declaration in
 * `wear/domain/model/WearFavoriteRecord.kt`. The two modules share no artifact, so the pair is joined
 * by matching characters alone and is compared by `assert-wear-wire-vocabulary-parity.ps1`.
 *
 * The phone used to read no source id at all and applied every item by its path, which is how a watch
 * MediaStore address reached the phone's favorites table: the row resolved to nothing, or to an
 * unrelated phone file carrying the same id, and unmarking it on the watch deleted that phone file's
 * favourite.
 *
 * A file the watch itself holds: its address means nothing outside the watch.
 */
const val SOURCE_ID_LOCAL = "local"

/** A network file whose source id was not recorded - the pre-S1846 spelling, kept so old marks resolve. */
const val SOURCE_ID_NETWORK = "network"

/** A direct stream keyed by its normalized address instead of a volatile catalog row id. */
const val SOURCE_ID_STREAM = "stream"

/** A voice note recorded on the watch, so its address is watch storage as well. */
const val SOURCE_ID_VOICE_NOTE = "voice_note"

/**
 * The source ids that address watch storage. An item carrying one of them describes a file only the
 * watch can open, so the phone neither materializes it nor deletes anything for it.
 */
val WATCH_HELD_FAVORITE_SOURCE_IDS = setOf(SOURCE_ID_LOCAL, SOURCE_ID_VOICE_NOTE)
