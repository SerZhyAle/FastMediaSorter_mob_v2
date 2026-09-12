package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.wear.WEAR_MESSAGE_ACK_TIMEOUT_MS
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WearCastAck
import com.sza.fastmediasorter.wear.domain.model.WearCastOutcome
import com.sza.fastmediasorter.wear.domain.model.WearCastRequest
import com.sza.fastmediasorter.wear.domain.model.WearCastState
import com.sza.fastmediasorter.wear.domain.model.WearCastStopRequest
import com.sza.fastmediasorter.wear.domain.repository.WearCastRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * S2531: one message out, one acknowledgement back - the watch never holds the link open for this.
 *
 * Unlike the open-on-phone round trip, the replies arrive through [WatchWearListenerService] rather
 * than through a listener this class registers: the session state is pushed by the phone whenever it
 * changes, so the watch needs a receiver that exists between requests, and having two receivers for
 * the same two paths would race over which one saw the ack.
 */
@Singleton
class WearCastRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearCastRepository {

    private val state = MutableStateFlow(WearCastState(isCasting = false, deviceName = null, displayName = null))

    private val pending = AtomicReference<PendingRequest?>(null)

    override val castState: StateFlow<WearCastState> = state.asStateFlow()

    override suspend fun requestCast(request: WearCastRequest): WearCastOutcome =
        exchange(request.requestId, WearDataLayerPaths.CAST_REQUEST, gson.toJson(request))

    override suspend fun requestStop(): WearCastOutcome {
        val requestId = UUID.randomUUID().toString()
        val payload = gson.toJson(WearCastStopRequest(requestId))
        return exchange(requestId, WearDataLayerPaths.CAST_STOP, payload)
    }

    override fun onAckReceived(payload: ByteArray) {
        val ack = runCatching {
            gson.fromJson(payload.decodeToString(), WearCastAck::class.java)
        }.getOrNull() ?: return
        val waiting = pending.get() ?: return
        if (waiting.requestId == ack.requestId) {
            waiting.answer.complete(ack.outcome)
        }
    }

    override fun onStateReceived(payload: ByteArray) {
        val received = runCatching {
            gson.fromJson(payload.decodeToString(), WearCastState::class.java)
        }.getOrNull() ?: return
        state.value = received
    }

    private suspend fun exchange(requestId: String, path: String, json: String): WearCastOutcome =
        withContext(Dispatchers.IO) {
            val nodes = connectedNodes()
            if (nodes.isEmpty()) {
                return@withContext WearCastOutcome.PHONE_BUSY
            }
            val waiting = PendingRequest(requestId, CompletableDeferred())
            // Registered before the message goes out: an ack is a message, nothing replays one, and a
            // phone that answers faster than this assignment would leave the watch waiting out the
            // whole timeout for an answer that had already arrived.
            pending.set(waiting)
            try {
                if (!sendToAny(nodes, path, json.toByteArray(Charsets.UTF_8))) {
                    return@withContext WearCastOutcome.PHONE_BUSY
                }
                withTimeoutOrNull(WEAR_MESSAGE_ACK_TIMEOUT_MS) { waiting.answer.await() }
                    ?: WearCastOutcome.PHONE_BUSY
            } finally {
                pending.compareAndSet(waiting, null)
            }
        }

    private suspend fun connectedNodes(): List<Node> = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await()
    }.onFailure { Timber.w(it, "Cast on phone: connected node lookup failed") }
        .getOrDefault(emptyList())

    /** True when at least one node accepted the message. */
    private suspend fun sendToAny(nodes: List<Node>, path: String, bytes: ByteArray): Boolean {
        val messageClient = Wearable.getMessageClient(context)
        // map before any(): any() short-circuits, and a watch paired with more than one phone should
        // reach every one of them rather than only the first that accepts.
        return nodes.map { node ->
            runCatching {
                messageClient.sendMessage(node.id, path, bytes).await()
            }.onFailure { Timber.w(it, "Cast on phone: send to %s failed", node.id) }.isSuccess
        }.any { it }
    }

    private data class PendingRequest(
        val requestId: String,
        val answer: CompletableDeferred<WearCastOutcome>
    )
}
