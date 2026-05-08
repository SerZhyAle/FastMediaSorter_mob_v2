package com.sza.fastmediasorter.wear.domain.model

/**
 * Versioned wrapper for all new Wear Data Layer event types.
 * Existing /fms/network_sources/ paths (push, request, ack) do not use this envelope for backward compatibility.
 */
data class WearEventEnvelope(
    val eventType: String,
    val schemaVersion: Int = 1,
    val sentAt: Long,
    val data: ByteArray
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
