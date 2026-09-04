package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2149: the whole set of stream channels pinned on the phone, as the identities this watch compares
 * against - not as channels, so the watch never has to reconcile two catalogues.
 *
 * Mirrors the phone declaration `com.sza.fastmediasorter.domain.model.WearStreamPinsPayload` and must
 * not gain a field the phone does not send. The key is pinned because the phone ships minified while
 * this module keeps its copy of the contract unobfuscated: a phone that wrote {"a":..} would hand the
 * watch a payload reading as null (S1631).
 */
data class WearStreamPinsPayload(
    @SerializedName("identities") val identities: List<String>
)

/**
 * S2497: one stream pin change queued for explicit synchronization with the phone.
 */
data class WearStreamPinDeltaItem(
    @SerializedName("urlOrIdentity") val urlOrIdentity: String,
    @SerializedName("isPinned") val isPinned: Boolean,
    @SerializedName("changedAt") val changedAt: Long
)

/**
 * S2497: payload carrying pending stream pin changes from watch to phone.
 */
data class WearStreamPinsDeltaPayload(
    @SerializedName("items") val items: List<WearStreamPinDeltaItem>
)

const val WEAR_STREAM_PINS_DELTA_LIMIT = 500

/**
 * S2497: appends or collapses one stream pin change into the pending delta queue.
 */
fun appendStreamPinDelta(
    current: List<WearStreamPinDeltaItem>,
    item: WearStreamPinDeltaItem,
    limit: Int = WEAR_STREAM_PINS_DELTA_LIMIT
): List<WearStreamPinDeltaItem> {
    val key = foldWearStreamIdentity(item.urlOrIdentity)
    val kept = current.filterNot { foldWearStreamIdentity(it.urlOrIdentity) == key }
    val appended = kept + item
    return if (appended.size <= limit) appended else appended.takeLast(limit)
}
