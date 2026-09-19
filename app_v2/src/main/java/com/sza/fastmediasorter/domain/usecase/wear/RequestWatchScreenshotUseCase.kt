package com.sza.fastmediasorter.domain.usecase.wear

import com.google.gson.Gson
import com.sza.fastmediasorter.domain.model.WatchScreenshotOutcome
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearScreenshotRefusalReasons
import com.sza.fastmediasorter.domain.model.WearScreenshotRequestAck
import com.sza.fastmediasorter.domain.model.WearScreenshotRequestCodec
import com.sza.fastmediasorter.domain.model.WearScreenshotRequestPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import com.sza.fastmediasorter.service.WearSyncEvents
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * S3110: asks the paired watch for a picture of its own screen and waits for the verdict.
 *
 * Only the verdict comes back here. The picture itself travels the file route the watch already uses
 * for everything binary (ADR-2), so it arrives with the same notification and lands in the same place
 * as any other file sent from the watch.
 */
class RequestWatchScreenshotUseCase @Inject constructor(
    private val dataLayerRepository: WearableDataLayerRepository,
    private val gson: Gson
) {

    suspend operator fun invoke(): WatchScreenshotOutcome =
        roundTrip(dataLayerRepository.getConnectedNodes())

    private suspend fun roundTrip(nodes: List<WearNode>): WatchScreenshotOutcome {
        if (nodes.isEmpty()) {
            return WatchScreenshotOutcome.NoConnectedWatch
        }

        val requestId = UUID.randomUUID().toString()
        val bytes = WearScreenshotRequestCodec.serialize(payload(requestId), gson)
        var sent = true

        // onSubscription for the reason S3109 recorded: the ack flow keeps no replay, so a watch
        // answering faster than this collector is registered would have its answer dropped and this
        // phone would wait out the whole timeout for a picture that in fact arrived.
        val ack = withTimeoutOrNull(ACK_TIMEOUT_MS) {
            WearSyncEvents.screenshotAckFlow
                .onSubscription { sent = sendToAll(nodes, bytes) }
                .first { answer -> answer.requestId == requestId || answer.requestId.isEmpty() }
        }
        return if (!sent) WatchScreenshotOutcome.NoConnectedWatch else answered(ack)
    }

    /** True when at least one node accepted the request. */
    private suspend fun sendToAll(nodes: List<WearNode>, bytes: ByteArray): Boolean =
        nodes.map { node ->
            // map before any: any() short-circuits, and the request is meant to reach every connected
            // watch, not merely the first one that accepts it.
            runCatching {
                dataLayerRepository.sendMessage(node.id, WearDataLayerPaths.SCREENSHOT_REQUEST, bytes)
            }
                .onFailure { Timber.w(it, "Watch screenshot: send to ${node.id} failed") }
                .isSuccess
        }.any { it }

    private fun answered(ack: WearScreenshotRequestAck?) = when {
        ack == null -> WatchScreenshotOutcome.WatchDidNotAnswer
        ack.captured -> WatchScreenshotOutcome.Captured(ack.fileName.orEmpty())
        ack.reason == WearScreenshotRefusalReasons.NO_FOREGROUND_SCREEN ->
            WatchScreenshotOutcome.WatchAppNotOpen
        else -> WatchScreenshotOutcome.WatchRefused(ack.reason.orEmpty())
    }

    private fun payload(requestId: String) = WearScreenshotRequestPayload(
        requestId = requestId,
        requestedAtEpochMillis = System.currentTimeMillis()
    )

    private companion object {
        /**
         * Longer than the clipboard's wait: the watch has to photograph its screen, compress a PNG and
         * push it through a channel whose own open and ack windows are thirty and ten seconds, so a
         * fifteen-second wait here would report "no answer" over a transfer still in flight.
         */
        const val ACK_TIMEOUT_MS = 60_000L
    }
}
