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
