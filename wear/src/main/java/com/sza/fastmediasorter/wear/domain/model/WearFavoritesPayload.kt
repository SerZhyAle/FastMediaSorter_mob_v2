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

/**
 * S2435 §3: how many entries the pending delta keeps.
 *
 * With [appendFavoriteDelta] collapsing per identity the queue is already bounded by the number of
 * DISTINCT favourites the owner touched, so this is a backstop against pathological path churn rather
 * than a routine trim - the same role [WEAR_STREAM_USAGE_LIMIT] plays for the play counters.
 */
const val WEAR_FAVORITES_DELTA_LIMIT = 500

/**
 * S2435: the whole of "queue one favourite change for the phone", kept pure so it can be tested
 * without an Android runtime - the watch module has no Robolectric harness, and the same split already
 * carries [mergeFavorites] and [recordWearStreamPlay].
 *
 * Collapsing per identity loses nothing: `ApplyWatchFavoritesDeltaUseCase` on the phone applies each
 * entry independently and idempotently, so the final state of a path is decided by the LAST entry
 * naming it and the intermediate flips are not observable. Without the collapse, marking and unmarking
 * one file all evening grows the stored queue with entries that cancel each other out.
 *
 * Identity is [favoriteIdentityKey], not the raw pair, so two spellings of one stream address collapse
 * into one entry the way they already count as one favourite (S2039).
 */
fun appendFavoriteDelta(
    current: List<WearFavoriteDeltaItem>,
    item: WearFavoriteDeltaItem,
    limit: Int = WEAR_FAVORITES_DELTA_LIMIT
): List<WearFavoriteDeltaItem> {
    val key = favoriteIdentityKey(item.sourceId, item.filePath)
    val kept = current.filterNot { favoriteIdentityKey(it.sourceId, it.filePath) == key }
    val appended = kept + item
    // Oldest first in the queue, so overflow drops the changes that have waited longest for a phone.
    return if (appended.size <= limit) appended else appended.takeLast(limit)
}
