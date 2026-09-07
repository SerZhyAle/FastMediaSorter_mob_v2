package com.sza.fastmediasorter.data.broadcast

import com.google.gson.annotations.SerializedName

/**
 * Data transfer object describing a live broadcast stream.
 */
data class BroadcastDescriptorDto(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("url") val url: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("mode") val mode: String
)
