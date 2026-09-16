package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import android.os.Build
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.domain.model.WearSystemInfoSection
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoReportOutcome
import com.sza.fastmediasorter.wear.domain.repository.WearSystemInfoReportSender
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
 * S3108: the watch half of the system-information round trip - one message out, one answer back.
 *
 * Built on the shape S1802 settled for the log report, step for step, because the two actions fail in
 * the same ways and must not explain themselves differently: the ack listener is registered and its
 * registration awaited before the report goes out, every connected node is sent to, the answer is
 * awaited under a shared timeout, and the listener is removed on every exit.
 */
class AndroidWearSystemInfoReportSender @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: WearSystemInfoReportRenderer,
    private val gson: Gson
) : WearSystemInfoReportSender {

    /**
     * Runs wholly off the main thread: the rendering, the JSON encoding and the Data Layer calls are
     * all on IO, because the caller is a chip tap on the main thread.
     */
    override suspend fun send(sections: List<WearSystemInfoSection>): WearSystemInfoReportOutcome =
        withContext(Dispatchers.IO) {
            val nodes = connectedNodes()
            if (nodes.isEmpty()) {
                return@withContext WearSystemInfoReportOutcome.NoConnectedPhone
            }
            roundTrip(nodes, UUID.randomUUID().toString(), sections)
        }

    private suspend fun roundTrip(
        nodes: List<Node>,
        requestId: String,
        sections: List<WearSystemInfoSection>
    ): WearSystemInfoReportOutcome {
        val bytes = WearSystemInfoReportCodec.serialize(payload(requestId, sections), gson)
        val messageClient = Wearable.getMessageClient(context)
        val ack = CompletableDeferred<WearSystemInfoReportAck>()
        val listener = ackListener(requestId, ack)

        return try {
            // The registration is awaited BEFORE the report goes out: the ack is a message, not a
            // data item, so a phone answering faster than this listener is installed would leave the
            // watch waiting out the whole timeout for a report that in fact arrived.
            messageClient.addListener(listener).await()
            if (sendToAll(messageClient, nodes, bytes)) {
                answered(withTimeoutOrNull(WEAR_MESSAGE_ACK_TIMEOUT_MS) { ack.await() })
            } else {
                WearSystemInfoReportOutcome.NoConnectedPhone
            }
        } finally {
            // One removal site reached by every exit - answered, timed out, failed or cancelled. A
            // leaked Data Layer listener keeps delivering into a screen the user has left.
            messageClient.removeListener(listener)
        }
    }

    private fun answered(ack: WearSystemInfoReportAck?): WearSystemInfoReportOutcome = when {
        ack == null -> WearSystemInfoReportOutcome.PhoneDidNotAnswer
        ack.accepted -> WearSystemInfoReportOutcome.Delivered
        else -> WearSystemInfoReportOutcome.PhoneRefused(ack.reason.orEmpty())
    }

    private fun payload(requestId: String, sections: List<WearSystemInfoSection>) =
        WearSystemInfoReportPayload(
            requestId = requestId,
            appVersionName = BuildConfig.VERSION_NAME,
            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
            deviceModel = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            capturedAtEpochMillis = System.currentTimeMillis(),
            reportText = renderer.render(sections)
        )

    private suspend fun connectedNodes(): List<Node> = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await()
    }.onFailure { Timber.w(it, "System info report: connected node lookup failed") }
        .getOrDefault(emptyList())

    /** True when at least one node accepted the report. */
    private suspend fun sendToAll(
        messageClient: MessageClient,
        nodes: List<Node>,
        bytes: ByteArray
    ): Boolean = nodes.map { node ->
        // map before any: any() short-circuits, and the report is meant to reach every connected
        // node, not merely the first one that accepts it.
        runCatching {
            messageClient.sendMessage(node.id, WearDataLayerPaths.SYSTEM_INFO_REPORT, bytes).await()
        }.onFailure { Timber.w(it, "System info report: send to ${node.id} failed") }.isSuccess
    }.any { it }

    private fun ackListener(
        requestId: String,
        ack: CompletableDeferred<WearSystemInfoReportAck>
    ) = MessageClient.OnMessageReceivedListener { event ->
        val received = if (event.path == WearDataLayerPaths.SYSTEM_INFO_REPORT_ACK) {
            runCatching {
                gson.fromJson(String(event.data, Charsets.UTF_8), WearSystemInfoReportAck::class.java)
            }.getOrNull()
        } else {
            null
        }
        if (received != null && received.requestId == requestId) {
            ack.complete(received)
        }
    }
}
