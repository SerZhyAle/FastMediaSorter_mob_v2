package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

// S1631: keys pinned - this one travels watch to phone, and the phone is the minified side here.

data class WearSourcesExportPayload(
    @SerializedName("sources") val sources: List<WearNetworkSourcePayload>,
    @SerializedName("watchName") val watchName: String
)
