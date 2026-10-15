package com.sza.fastmediasorter.wear.data.broadcast

import com.google.gson.annotations.SerializedName

/**
 * Endpoint descriptor representing one transport/track option for a live broadcast stream on Wear OS.
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
    @SerializedName("targetLatencyMs") val targetLatencyMs: Long? = null,
    /**
     * S4160/S4177: the LAN URL a `TUNNEL` endpoint reaches;
     * the consumer swaps its host and port (`LIVE-BROADCAST` J).
     */
    @SerializedName("inner") val inner: String? = null,
    /**
     * S4160/S4177: the exchange server certificate pin, written only for a self-signed deployment (7.7).
     */
    @SerializedName("certFingerprint") val certFingerprint: String? = null,
    /** S4240: DEVICE-EXCHANGE 27 item DK - the endpoint is sealed; a record carries `v` only, the keys ride its box. */
    @SerializedName("seal") val seal: BroadcastSealDto? = null,
)

/** `seal` of a sealed `RELAY` endpoint as the watch writes it into a record: the version alone. */
data class BroadcastSealDto(@SerializedName("v") val v: Int = VERSION) {
    companion object {
        const val VERSION = 1
    }
}
