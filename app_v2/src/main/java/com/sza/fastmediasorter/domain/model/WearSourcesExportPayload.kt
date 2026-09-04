package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

// S1631: keys pinned - this one travels watch to phone, and the phone is the minified side here.

data class WearSourcesExportPayload(
    @SerializedName("sources") val sources: List<WearNetworkSourcePayload>,
    @SerializedName("watchName") val watchName: String,
    // S2502: when the watch sent this batch, in the watch's own time base. Together with the phone's
    // own reading of when it took delivery this measures the two clocks' offset, which is what lets an
    // edit time be compared at all. Carried here rather than taken from the envelope: the phone's event
    // bus hands the consumer this payload alone, and the envelope's `sentAt` never reaches it. Null
    // from a watch that predates this field, and the phone then measures no skew rather than guessing.
    @SerializedName("sentAt") val sentAt: Long? = null,
    @SerializedName("tombstones") val tombstones: List<WearSourceTombstonePayload> = emptyList()
)
