package com.sza.fastmediasorter.data.broadcast

import com.google.gson.annotations.SerializedName

/**
 * Data transfer object describing a live broadcast stream.
 */
data class BroadcastDescriptorDto(
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("url") val url: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("mode") val mode: String,
    /**
     * S2813: the device that is broadcasting, when it can name itself. Optional at schemaVersion 1
     * rather than a version bump: an unknown member is ignored by the parser while a higher version is
     * refused outright, so an old app keeps reading a new device's descriptor and a new app keeps
     * reading an old device's.
     */
    @SerializedName("sourceId") val sourceId: String? = null
)
