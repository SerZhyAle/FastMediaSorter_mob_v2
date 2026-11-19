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
    @SerializedName("targetLatencyMs") val targetLatencyMs: Long? = null,
    /** S4160: the LAN URL a `TUNNEL` endpoint reaches; the consumer swaps its host and port (`LIVE-BROADCAST` J). */
    @SerializedName("inner") val inner: String? = null,
    /** S4160: the exchange server certificate pin, written only for a self-signed deployment (7.7). */
    @SerializedName("certFingerprint") val certFingerprint: String? = null,
    /**
     * DEVICE-EXCHANGE 27 item DK: the endpoint is sealed. In a record it carries `v` only and the keys ride the
     * record's box; in a descriptor handed by hand, or once the box was opened here, it carries them as well.
     */
    @SerializedName("seal") val seal: BroadcastSealDto? = null,
)

/**
 * `seal` of a `RELAY` or `TUNNEL` endpoint: [key] is a relay group key, [pin] the tunnel's leaf pin.
 *
 * S4675: [box] exists only in a stored row, where it holds `key` and `pin` sealed by the device; the stored-list codec
 * opens it into those two members, so a decoded seal never carries one.
 */
data class BroadcastSealDto(
    @SerializedName("v") val v: Int = VERSION,
    @SerializedName("key") val key: String? = null,
    @SerializedName("pin") val pin: String? = null,
    @SerializedName("box") val box: String? = null,
) {
    // A group key opens the broadcast and a pin names the tunnel's certificate: neither is ever printed.
    override fun toString(): String =
        "BroadcastSealDto(v=$v, key=${key?.let { "***" }}, pin=${pin?.let { "***" }}, box=${box?.let { "***" }})"

    /** A version this build reads; any other endpoint is skipped, never played as clear bytes (item DK). */
    val isKnown: Boolean get() = v == VERSION

    companion object {
        const val VERSION = 1
    }
}
