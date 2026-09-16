package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import android.os.Build
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.domain.repository.WearClipboardTextOutcome
import com.sza.fastmediasorter.wear.domain.repository.WearClipboardTextSender
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * S3109: the watch half of the clipboard round trip - one message out, one answer back.
 *
 * Built on the shape S1802 settled for the log report and S3108 reused for the system report, step
 * for step, because the three actions fail in the same ways and must not explain themselves
 * differently: the ack listener is registered and its registration awaited before the text goes out,
 * every connected node is sent to, the answer is awaited under a shared timeout, and the listener is
 * removed on every exit.
 */
class AndroidWearClipboardTextSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearClipboardTextSender {

    /**
     * Runs wholly off the main thread: the JSON encoding and the Data Layer calls are both on IO,
     * because the caller is a chip tap on the main thread.
     */
    override suspend fun send(text: String): WearClipboardTextOutcome = withContext(Dispatchers.IO) {
        when {
            text.isBlank() -> WearClipboardTextOutcome.NothingToSend

            // Refused here rather than on the phone: an oversized text would spend a Data Layer
            // message and a whole timeout to be told what this build already knows.
            text.length > WearClipboardTextPayload.MAX_TEXT_LENGTH ->
                WearClipboardTextOutcome.PhoneRefused(WearClipboardTextRefusalReasons.TOO_LONG)

            else -> {
                val nodes = connectedNodes()
                if (nodes.isEmpty()) {
                    WearClipboardTextOutcome.NoConnectedPhone
                } else {
                    roundTrip(nodes, UUID.randomUUID().toString(), text)
                }
            }
        }
    }

    private suspend fun roundTrip(
        nodes: List<Node>,
        requestId: String,
        text: String
    ): WearClipboardTextOutcome {
        val bytes = WearClipboardTextCodec.serialize(payload(requestId, text), gson)
        val messageClient = Wearable.getMessageClient(context)
        val ack = CompletableDeferred<WearClipboardTextAck>()
        val listener = ackListener(requestId, ack)

        return try {
            // The registration is awaited BEFORE the text goes out: the ack is a message, not a data
            // item, so a phone answering faster than this listener is installed would leave the watch
            // waiting out the whole timeout for an answer that in fact arrived.
            messageClient.addListener(listener).await()
            if (sendToAll(messageClient, nodes, bytes)) {
                answered(withTimeoutOrNull(WEAR_MESSAGE_ACK_TIMEOUT_MS) { ack.await() })
            } else {
                WearClipboardTextOutcome.NoConnectedPhone
            }
        } finally {
            // One removal site reached by every exit - answered, timed out, failed or cancelled. A
            // leaked Data Layer listener keeps delivering into a screen the user has left.
            messageClient.removeListener(listener)
        }
    }

    private fun answered(ack: WearClipboardTextAck?): WearClipboardTextOutcome = when {
        ack == null -> WearClipboardTextOutcome.PhoneDidNotAnswer
        ack.accepted -> WearClipboardTextOutcome.Delivered
        else -> WearClipboardTextOutcome.PhoneRefused(ack.reason.orEmpty())
    }

    private fun payload(requestId: String, text: String) = WearClipboardTextPayload(
        requestId = requestId,
        sourceDeviceModel = Build.MODEL,
        capturedAtEpochMillis = System.currentTimeMillis(),
        text = text
    )

    private suspend fun connectedNodes(): List<Node> = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await()
    }.onFailure { Timber.w(it, "Watch clipboard: connected node lookup failed") }
        .getOrDefault(emptyList())

    /** True when at least one node accepted the text. */
    private suspend fun sendToAll(
        messageClient: MessageClient,
        nodes: List<Node>,
        bytes: ByteArray
    ): Boolean = nodes.map { node ->
        // map before any: any() short-circuits, and the text is meant to reach every connected node,
        // not merely the first one that accepts it.
        runCatching {
            messageClient.sendMessage(node.id, WearDataLayerPaths.CLIPBOARD_TEXT_FROM_WATCH, bytes).await()
        }.onFailure { Timber.w(it, "Watch clipboard: send to ${node.id} failed") }.isSuccess
    }.any { it }

    private fun ackListener(
        requestId: String,
        ack: CompletableDeferred<WearClipboardTextAck>
    ) = MessageClient.OnMessageReceivedListener { event ->
        val received = if (event.path == WearDataLayerPaths.CLIPBOARD_TEXT_FROM_WATCH_ACK) {
            runCatching {
                gson.fromJson(String(event.data, Charsets.UTF_8), WearClipboardTextAck::class.java)
            }.getOrNull()
        } else {
            null
        }
        if (received != null && received.requestId == requestId) {
            ack.complete(received)
        }
    }
}
