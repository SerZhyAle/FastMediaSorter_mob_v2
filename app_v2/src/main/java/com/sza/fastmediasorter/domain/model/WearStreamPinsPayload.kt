package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S2149: the whole set of stream channels pinned on the phone, as the identities the watch compares
 * against - not as channels, so the watch never has to reconcile two catalogues.
 *
 * The identities are folded by `StreamChannelIdentity`, which is the rule the watch's own
 * `foldWearStreamIdentity` mirrors; a raw address would not match a catalogue row spelled with the
 * other web scheme.
 *
 * The key is pinned for the same reason [WearEventEnvelope]'s are: the phone ships minified while the
 * watch keeps its copy of this contract unobfuscated, so a phone that wrote {"a":..} would hand the
 * watch a payload reading as null (S1631). The watch declaration must stay a mirror of this one.
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
