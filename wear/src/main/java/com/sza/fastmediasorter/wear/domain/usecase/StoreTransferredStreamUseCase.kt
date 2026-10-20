package com.sza.fastmediasorter.wear.domain.usecase

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastEndpoints
import com.sza.fastmediasorter.wear.data.exchange.WearBroadcastPassStore
import com.sza.fastmediasorter.wear.data.exchange.WearStoredBroadcast
import com.sza.fastmediasorter.wear.domain.model.WearBroadcastHandoffPayload
import com.sza.fastmediasorter.wear.domain.model.WearStreamChannel
import com.sza.fastmediasorter.wear.domain.model.WearStreamStoreResult
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferAck
import com.sza.fastmediasorter.wear.domain.model.WearStreamTransferPayload
import com.sza.fastmediasorter.wear.domain.model.WearTileKind
import com.sza.fastmediasorter.wear.domain.repository.WearStreamChannelRepository
import com.sza.fastmediasorter.wear.util.rethrowIfCancellation
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * S1799: stores one stream channel transferred from the phone and answers with the outcome the
 * phone will show the user - stored, updated, or the error that prevented either.
 *
 * S1944: also hands back the channel it stored, because the phone can now ask for it to be opened and
 * the caller needs the same record the list would have used - not the raw payload.
 */
class StoreTransferredStreamUseCase @Inject constructor(
    private val repository: WearStreamChannelRepository,
    private val classifier: ClassifyWearStreamMediaKindUseCase,
    private val requestWearTileRefreshUseCase: RequestWearTileRefreshUseCase,
    private val passStore: WearBroadcastPassStore,
) {

    suspend operator fun invoke(payload: WearStreamTransferPayload): WearStreamStoreResult {
        if (payload.url.isBlank()) {
            return error(payload.requestId, "blank url")
        }
        val result = upsert(payload.requestId, channel(payload.name, payload.url, payload.mediaKind, null))
        // S4261: the plain form replaced a handed-off broadcast, so its descriptor and pass answer for nothing.
        if (result.channel != null) {
            passStore.forget(payload.url)
        }
        return result
    }

    /**
     * S4261: a broadcast handed off with its descriptor (LIVE-BROADCAST item P). Nothing is stored for one the watch
     * cannot play, and the secret store is written before the channel, so a channel marked as a broadcast always has
     * its descriptor.
     */
    suspend fun storeBroadcast(payload: WearBroadcastHandoffPayload): WearStreamStoreResult {
        val descriptor = payload.descriptor
        val broadcastId = payload.broadcastId.orEmpty()
        return when {
            payload.url.isBlank() -> error(payload.requestId, "blank url")
            descriptor == null || !isPlayable(broadcastId, descriptor, payload) ->
                WearStreamStoreResult(ack(payload.requestId, WearStreamTransferAck.OUTCOME_UNPLAYABLE), channel = null)
            !passStore.save(payload.url, WearStoredBroadcast(broadcastId, descriptor, payload.pass)) ->
                error(payload.requestId, "secret store refused")
            else -> upsert(payload.requestId, channel(payload.name, payload.url, payload.mediaKind, true))
        }
    }

    // Item M: a protected descriptor without its pass cannot answer the challenge, so it is not playable here.
    private fun isPlayable(broadcastId: String, descriptor: JsonObject, payload: WearBroadcastHandoffPayload): Boolean {
        val mode = descriptor.get(KEY_MODE)?.takeIf(JsonElement::isJsonPrimitive)?.asString
        val playable = WearBroadcastEndpoints.parse(broadcastId, descriptor, mode)
            .filter { WearBroadcastEndpoints.isPlayableMode(it.mode ?: mode) }
        return playable.isNotEmpty() && (payload.pass != null || !WearBroadcastEndpoints.isProtected(descriptor))
    }

    private fun channel(name: String, url: String, mediaKind: String, hasBroadcast: Boolean?) = WearStreamChannel(
        id = UUID.randomUUID().toString(),
        name = name.ifBlank { url },
        url = url,
        // S1944: classify whenever the value is not one this watch acts on - blank or unrecognised
        // alike. Before, only a blank was reclassified, so a non-blank typo was stored raw and then
        // routed to the audio player without the URL ever being consulted.
        mediaKind = classifier.resolve(mediaKind, url),
        origin = WearStreamChannel.ORIGIN_PHONE,
        hasBroadcast = hasBroadcast,
    )

    // The ack is the phone's only view of what happened here; any persistence failure must become
    // an OUTCOME_ERROR answer rather than a crash that leaves the phone waiting out its timeout.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun upsert(requestId: String, channel: WearStreamChannel): WearStreamStoreResult = try {
        val added = repository.upsertChannel(channel)
        val outcome = if (added) {
            WearStreamTransferAck.OUTCOME_STORED
        } else {
            WearStreamTransferAck.OUTCOME_UPDATED
        }
        requestWearTileRefreshUseCase(WearTileKind.STREAM)
        WearStreamStoreResult(ack(requestId, outcome), channel)
    } catch (e: Exception) {
        e.rethrowIfCancellation()
        Timber.w(e, "Failed to store transferred stream")
        error(requestId, e.message)
    }

    private fun ack(requestId: String, outcome: String, message: String? = null) =
        WearStreamTransferAck(requestId = requestId, outcome = outcome, message = message)

    private fun error(requestId: String, message: String?) =
        WearStreamStoreResult(ack(requestId, WearStreamTransferAck.OUTCOME_ERROR, message), channel = null)

    private companion object {
        const val KEY_MODE = "mode"
    }
}
