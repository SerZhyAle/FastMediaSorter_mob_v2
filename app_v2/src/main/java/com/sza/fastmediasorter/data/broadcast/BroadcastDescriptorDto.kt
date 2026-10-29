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
    @SerializedName("sourceId") val sourceId: String? = null,
    /** S3051: list of available stream endpoints (HTTP, RTSP, P2P, etc.) */
    @SerializedName("endpoints") val endpoints: List<BroadcastEndpointDto>? = null,
    /** S3051: explicit marker indicating this stream is live */
    @SerializedName("isLive") val isLive: Boolean? = null,
    /** S3051: recommended consumer target latency in milliseconds */
    @SerializedName("targetLatencyMs") val targetLatencyMs: Long? = null,
    /** S4258, LIVE-BROADCAST item M: present when every endpoint demands a password; never carries the password. */
    @SerializedName("auth") val auth: BroadcastAuthDto? = null,
    /** S4258: the own pair from an opened directory record's box; never on a descriptor that is shared. */
    @SerializedName("credentials") val credentials: BroadcastCredentialsDto? = null,
    /**
     * LIVE-BROADCAST item S (S4247): present when the broadcast has not started yet; optional at schemaVersion 1,
     * so a reader that does not know it reads an ordinary RELAY and TUNNEL descriptor.
     */
    @SerializedName("pending") val pending: BroadcastPendingDto? = null,
) {

    /** True when a listener must answer a `password-v1` challenge to play this broadcast. */
    val isProtected: Boolean get() = auth?.isPasswordV1 == true

    /** True for a link handed out before its broadcast started (LIVE-BROADCAST item S). */
    val isPending: Boolean get() = pending != null

    /**
     * Returns explicit [endpoints] if present and non-empty, or synthesizes a single legacy endpoint
     * from root fields.
     */
    fun getEffectiveEndpoints(): List<BroadcastEndpointDto> {
        if (!endpoints.isNullOrEmpty()) return endpoints
        return listOf(
            BroadcastEndpointDto(
                url = url,
                transport = if (url.startsWith("rtsp://", ignoreCase = true)) "RTSP" else "HTTP",
                mode = mode,
                isLive = isLive,
                targetLatencyMs = targetLatencyMs
            )
        )
    }
}
