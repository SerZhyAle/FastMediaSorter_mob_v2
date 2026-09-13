package com.sza.fastmediasorter.wear.data.broadcast

import com.google.gson.annotations.SerializedName

/**
 * S2509 ADR-3: the watch's copy of the S2508 subscription contract, write side only.
 *
 * Duplicated rather than shared through a Gradle module (strategic §6 question 7): the watch needs the
 * four fields and the encoder, never the parser, and a module added for that would widen the graph
 * around less code than the module's own build file.
 *
 * S2813 revisits the rule that used to stand here - that no field may name the watch as the source.
 * Its premise was that a discriminator forces `schemaVersion` past 1, which the released phone parser
 * refuses outright, stranding every shipped listener. That holds for a version bump and only for one:
 * the parser rejects a version ABOVE the one it supports and ignores JSON members it does not know, so
 * an OPTIONAL field at version 1 is read by new builds and silently skipped by old ones. [sourceId] is
 * added on exactly that basis; [SCHEMA_VERSION] stays at 1 and raising it still strands every listener.
 */
data class BroadcastDescriptorDto(
    @SerializedName("schemaVersion") val schemaVersion: Int = SCHEMA_VERSION,
    @SerializedName("url") val url: String,
    @SerializedName("title") val title: String? = null,
    @SerializedName("mode") val mode: String = MODE_AUDIO_ONLY,
    /**
     * S2813: this watch's own id, stable across sessions. The address is not - the port and the LAN
     * address belong to the session - so without it a phone cannot tell a returning watch from a new
     * one and files a second, duplicate entry beside the one the owner already uses.
     */
    @SerializedName("sourceId") val sourceId: String? = null,
    /** S3051: list of available stream endpoints (HTTP, RTSP, P2P, etc.) */
    @SerializedName("endpoints") val endpoints: List<BroadcastEndpointDto>? = null,
    /** S3051: explicit marker indicating this stream is live */
    @SerializedName("isLive") val isLive: Boolean? = null,
    /** S3051: recommended consumer target latency in milliseconds */
    @SerializedName("targetLatencyMs") val targetLatencyMs: Long? = null
) {
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

    companion object {

        /** The one version the released phone parser accepts; raising it strands every listener. */
        const val SCHEMA_VERSION = 1

        /** The only mode a watch can offer - the target watches carry no camera (ADR-1). */
        const val MODE_AUDIO_ONLY = "AUDIO_ONLY"
    }
}
