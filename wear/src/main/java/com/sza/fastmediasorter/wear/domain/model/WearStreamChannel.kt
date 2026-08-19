package com.sza.fastmediasorter.wear.domain.model

import com.google.gson.annotations.SerializedName

/**
 * S1708: Domain model representing a streaming channel (radio or video) on Wear OS.
 */
data class WearStreamChannel(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("url") val url: String,
    @SerializedName("media_kind") val mediaKind: String, // "AUDIO" | "VIDEO" | "RTSP"
    @SerializedName("favicon_index") val faviconIndex: Int? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("topic") val topic: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("country") val country: String? = null,
    @SerializedName("access") val access: String? = null
)
