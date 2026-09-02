package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.wear.WEAR_MESSAGE_ACK_TIMEOUT_MS
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneAck
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneOutcome
import com.sza.fastmediasorter.wear.domain.model.WearOpenOnPhoneRequest
import com.sza.fastmediasorter.wear.domain.repository.WearOpenOnPhoneRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2004: sends one open request over the bridge and reports what the phone did with it.
 *
 * Modelled on the log-report round trip rather than on the file sender: nothing is transferred, so
 * there is no channel to open - one message out, one acknowledgement back.
 */
@Singleton
class WearOpenOnPhoneRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearOpenOnPhoneRepository {

    override suspend fun requestOpen(request: WearOpenOnPhoneRequest): WearOpenOnPhoneOutcome? =
        withContext(Dispatchers.IO) {
            val nodes = connectedNodes()
            if (nodes.isEmpty()) null else awaitAnswer(nodes, request)
        }

    private suspend fun awaitAnswer(
        nodes: List<Node>,
        request: WearOpenOnPhoneRequest
    ): WearOpenOnPhoneOutcome? {
        val messageClient = Wearable.getMessageClient(context)
        val ack = CompletableDeferred<WearOpenOnPhoneAck>()
        val listener = ackListener(request.token, ack)
        return try {
            // Registered, and the registration awaited, before the request goes out. The ack is a
            // message and nothing replays a message, so a phone that answers faster than this
            // listener is installed would leave the watch waiting out the whole timeout for an answer
            // that had already arrived.
            messageClient.addListener(listener).await()
            val bytes = gson.toJson(request).toByteArray(Charsets.UTF_8)
            if (sendToAny(messageClient, nodes, bytes)) {
                withTimeoutOrNull(WEAR_MESSAGE_ACK_TIMEOUT_MS) { ack.await() }?.outcome
            } else {
                null
            }
        } finally {
            // One removal site, reached by every exit - answered, timed out, failed or cancelled.
            messageClient.removeListener(listener)
        }
    }

    private suspend fun connectedNodes(): List<Node> = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await()
    }.onFailure { Timber.w(it, "Open on phone: connected node lookup failed") }
        .getOrDefault(emptyList())

    /** True when at least one node accepted the request. */
    private suspend fun sendToAny(
        messageClient: MessageClient,
        nodes: List<Node>,
        bytes: ByteArray
    ): Boolean = nodes.map { node ->
        // map before any(): any() short-circuits, and a watch paired with more than one phone should
        // reach every one of them rather than only the first that accepts.
        runCatching {
            messageClient.sendMessage(node.id, WearDataLayerPaths.OPEN_ON_PHONE_REQUEST, bytes).await()
        }.onFailure { Timber.w(it, "Open on phone: send to %s failed", node.id) }.isSuccess
    }.any { it }

    private fun ackListener(
        token: String,
        ack: CompletableDeferred<WearOpenOnPhoneAck>
    ) = MessageClient.OnMessageReceivedListener { event ->
        val received = if (event.path == WearDataLayerPaths.OPEN_ON_PHONE_ACK) {
            runCatching {
                gson.fromJson(String(event.data, Charsets.UTF_8), WearOpenOnPhoneAck::class.java)
            }.getOrNull()
        } else {
            null
        }
        // Correlated by the token rather than by a request id: the token addresses one file, and the
        // watch has at most one open request outstanding for a file at a time.
        if (received != null && received.token == token) {
            ack.complete(received)
        }
    }
}
