package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import android.os.Build
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.sza.fastmediasorter.wear.BuildConfig
import com.sza.fastmediasorter.wear.core.logging.WearLogBuffer
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
 * S1802: the watch half of the log-report round trip - one message out, one acknowledgement back.
 *
 * The outcome is a sealed type rather than a boolean because the About row has to tell the user why
 * nothing was sent; strategic 7 names a silently non-working button as the highest risk here, and a
 * boolean collapses exactly the distinctions the wording needs.
 */
class WearLogReportClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    /**
     * Sends the current log snapshot to the paired phone and waits for its answer.
     *
     * Runs wholly off the main thread: the snapshot copy, the JSON encoding and the Data Layer calls
     * are all on the IO dispatcher, because the caller is a settings row tap on the main thread. The
     * dispatcher is named here rather than injected - the wear module binds none, and adding a
     * binding for one call site is a Hilt graph change this step does not carry.
     */
    suspend fun send(): WearLogReportOutcome = withContext(Dispatchers.IO) {
        val requestId = UUID.randomUUID().toString()
        val nodes = connectedNodes()
        if (nodes.isEmpty()) {
            return@withContext WearLogReportOutcome.NoConnectedPhone
        }

        val bytes = WearLogReportCodec.serialize(buildPayload(requestId), gson)
        val messageClient = Wearable.getMessageClient(context)
        val ack = CompletableDeferred<WearLogReportAck>()
        val listener = ackListener(requestId, ack)

        try {
            // Registered, and the registration awaited, BEFORE the report goes out. The ack is a
            // message, not a data item - nothing replays it - so a phone that answers faster than
            // this listener is installed would leave the watch waiting out the whole timeout and
            // telling the user nobody answered, for a report that in fact arrived.
            messageClient.addListener(listener).await()

            if (!sendToAll(messageClient, nodes, bytes)) {
                return@withContext WearLogReportOutcome.NoConnectedPhone
            }

            // A timeout, never an unbounded await: step 02.2 measured a send that neither succeeded
            // nor failed, so waiting on an answer that may never come is a real state here.
            val answer = withTimeoutOrNull(WEAR_MESSAGE_ACK_TIMEOUT_MS) { ack.await() }
            when {
                answer == null -> WearLogReportOutcome.PhoneDidNotAnswer
                answer.accepted -> WearLogReportOutcome.Delivered
                else -> WearLogReportOutcome.PhoneRefused(answer.reason.orEmpty())
            }
        } finally {
            // One removal site, reached by every exit - answered, timed out, failed or cancelled.
            // A leaked Data Layer listener keeps delivering into a screen the user has left.
            messageClient.removeListener(listener)
        }
    }

    private fun buildPayload(requestId: String) = WearLogReportPayload(
        requestId = requestId,
        appVersionName = BuildConfig.VERSION_NAME,
        appVersionCode = BuildConfig.VERSION_CODE.toLong(),
        deviceModel = Build.MODEL,
        androidRelease = Build.VERSION.RELEASE,
        capturedAtEpochMillis = System.currentTimeMillis(),
        logText = WearLogBuffer.snapshot()
    )

    private suspend fun connectedNodes(): List<Node> = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await()
    }.onFailure { Timber.w(it, "Log report: connected node lookup failed") }
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
            messageClient.sendMessage(node.id, WearDataLayerPaths.LOG_REPORT_REQUEST, bytes).await()
        }.onFailure { Timber.w(it, "Log report: send to ${node.id} failed") }.isSuccess
    }.any { it }

    private fun ackListener(
        requestId: String,
        ack: CompletableDeferred<WearLogReportAck>
    ) = MessageClient.OnMessageReceivedListener { event ->
        val received = if (event.path == WearDataLayerPaths.LOG_REPORT_ACK) {
            runCatching {
                gson.fromJson(String(event.data, Charsets.UTF_8), WearLogReportAck::class.java)
            }.getOrNull()
        } else {
            null
        }
        if (received != null && received.requestId == requestId) {
            ack.complete(received)
        }
    }
}

/**
 * S1802: the phone's answer to one report.
 *
 * Field names are pinned because this crosses a process and a version boundary; R8 renaming them
 * would make an older phone's answer unreadable rather than merely unknown.
 */
object WearLogReportRefusalReasons {

    /**
     * The phone stored the report but cannot show a notification for it.
     *
     * Matched as a literal because the phone writes it from its own copy of this constant - the two
     * modules share no code, so the string itself is the contract.
     */
    const val NOTIFICATIONS_DISABLED = "notifications_disabled"
}

data class WearLogReportAck(
    @SerializedName("requestId") val requestId: String,
    @SerializedName("accepted") val accepted: Boolean,
    @SerializedName("reason") val reason: String? = null
)

/** What one log-report round trip produced. */
sealed interface WearLogReportOutcome {

    /** The phone received the report and said so. */
    data object Delivered : WearLogReportOutcome

    /** No node is connected - the phone is absent, asleep or out of range. */
    data object NoConnectedPhone : WearLogReportOutcome

    /** The message left the watch, but nothing answered within the timeout. */
    data object PhoneDidNotAnswer : WearLogReportOutcome

    /** The phone answered and declined, carrying a reason the UI can phrase. */
    data class PhoneRefused(val reason: String) : WearLogReportOutcome
}
