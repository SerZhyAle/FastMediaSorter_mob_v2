package com.sza.fastmediasorter.wear.data.exchange

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.sza.fastmediasorter.wear.data.broadcast.BroadcastDescriptorDto
import com.sza.fastmediasorter.wear.data.broadcast.BroadcastEndpointDto
import timber.log.Timber
import java.time.Instant

/** What one announcement of the watch's broadcast carries, apart from the time it is sent. */
data class WearExchangeBroadcastPublication(
    val broadcastId: String,
    val deviceId: String,
    val lan: BroadcastDescriptorDto,
    val publicEndpoint: String,
    val certFingerprint: String?,
    val startedAtMs: Long,
)

/**
 * The broadcast record of contract DEVICE-EXCHANGE 6.3 and the exchange endpoint of 7.7, built from the
 * descriptor the watch already publishes on the LAN. Endpoints keep the order a consumer tries them in: the LAN
 * ones first, then `RELAY` - only while an upload is accepted, so a listener is never sent to an empty relay.
 */
object WearExchangeBroadcastRecords {

    const val TRANSPORT_RELAY = "RELAY"
    private const val RELAY_TARGET_LATENCY_MS = 2_000L
    private const val AUDIO_CODEC_AAC = "aac"

    private const val KEY_BROADCAST_ID = "broadcastId"
    private const val KEY_DEVICE_ID = "deviceId"
    private const val KEY_TITLE = "title"
    private const val KEY_MODE = "mode"
    private const val KEY_STARTED_AT = "startedAt"
    private const val KEY_UPDATED_AT = "updatedAt"
    private const val KEY_DESCRIPTOR = "descriptor"

    private val gson = Gson()

    /** The LAN descriptor the broadcast session published, or null when it does not parse. */
    fun parseLan(descriptorJson: String): BroadcastDescriptorDto? = try {
        gson.fromJson(descriptorJson, BroadcastDescriptorDto::class.java)
    } catch (e: JsonParseException) {
        Timber.w(e, "WearExchangeBroadcastRecords: the LAN descriptor does not parse, nothing announced")
        null
    }

    /** A new id per broadcast in the share-id form of contract section 2; it is the secret-link capability. */
    fun newBroadcastId(): String = WearExchangeEntry.newDeviceId()

    fun relayUrl(publicEndpoint: String, broadcastId: String): String =
        "https://$publicEndpoint/v2/b/$broadcastId/stream"

    fun descriptor(publication: WearExchangeBroadcastPublication, relayUploading: Boolean): BroadcastDescriptorDto {
        val lanEndpoints = publication.lan.getEffectiveEndpoints().filterNot { it.transport == TRANSPORT_RELAY }
        val relay = BroadcastEndpointDto(
            url = relayUrl(publication.publicEndpoint, publication.broadcastId),
            transport = TRANSPORT_RELAY,
            mode = BroadcastDescriptorDto.MODE_AUDIO_ONLY,
            audioCodec = AUDIO_CODEC_AAC,
            isLive = true,
            targetLatencyMs = RELAY_TARGET_LATENCY_MS,
            certFingerprint = publication.certFingerprint,
        )
        return publication.lan.copy(endpoints = if (relayUploading) lanEndpoints + relay else lanEndpoints)
    }

    fun record(publication: WearExchangeBroadcastPublication, relayUploading: Boolean, updatedAtMs: Long): JsonObject =
        JsonObject().apply {
            addProperty(KEY_BROADCAST_ID, publication.broadcastId)
            addProperty(KEY_DEVICE_ID, publication.deviceId)
            publication.lan.title?.let { addProperty(KEY_TITLE, it) }
            addProperty(KEY_MODE, BroadcastDescriptorDto.MODE_AUDIO_ONLY)
            addProperty(KEY_STARTED_AT, Instant.ofEpochMilli(publication.startedAtMs).toString())
            addProperty(KEY_UPDATED_AT, Instant.ofEpochMilli(updatedAtMs).toString())
            add(KEY_DESCRIPTOR, gson.toJsonTree(descriptor(publication, relayUploading)))
        }
}
