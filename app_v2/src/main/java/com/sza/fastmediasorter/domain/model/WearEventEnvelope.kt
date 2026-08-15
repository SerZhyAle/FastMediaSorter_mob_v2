package com.sza.fastmediasorter.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Versioned wrapper for all new Wear Data Layer event types.
 * Existing /fms/network_sources/ paths (push, request, ack) do not use this envelope for backward compatibility.
 *
 * S1631: every key is pinned. The watch keeps its copy of this contract unobfuscated, so a minified
 * phone that wrote {"a":..} would hand the watch a payload whose fields all read as null - including
 * [schemaVersion], which is what would otherwise have reported the incompatibility.
 */
data class WearEventEnvelope(
    @SerializedName("eventType") val eventType: String,
    @SerializedName("schemaVersion") val schemaVersion: Int = 1,
    @SerializedName("sentAt") val sentAt: Long,
    @SerializedName("data") val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WearEventEnvelope) return false
        return eventType == other.eventType &&
            schemaVersion == other.schemaVersion &&
            sentAt == other.sentAt &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = eventType.hashCode()
        result = 31 * result + schemaVersion
        result = 31 * result + sentAt.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}
