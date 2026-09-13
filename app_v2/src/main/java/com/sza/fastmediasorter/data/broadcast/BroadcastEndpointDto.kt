package com.sza.fastmediasorter.data.broadcast

import com.google.gson.annotations.SerializedName

/**
 * Endpoint descriptor representing one transport/track option for a live broadcast stream.
 */
data class BroadcastEndpointDto(
    @SerializedName("url") val url: String,
    @SerializedName("transport") val transport: String = "HTTP",
    @SerializedName("mode") val mode: String = "AUDIO_ONLY",
    @SerializedName("videoCodec") val videoCodec: String? = null,
    @SerializedName("audioCodec") val audioCodec: String? = null,
    @SerializedName("sampleRate") val sampleRate: Int? = null,
    @SerializedName("bitrate") val bitrate: Int? = null,
    @SerializedName("isLive") val isLive: Boolean? = null,
    @SerializedName("targetLatencyMs") val targetLatencyMs: Long? = null
)
