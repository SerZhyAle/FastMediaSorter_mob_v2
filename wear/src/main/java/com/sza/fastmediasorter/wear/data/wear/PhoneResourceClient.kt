package com.sza.fastmediasorter.wear.data.wear

import android.content.Context
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.domain.model.WearEventEnvelopeCodec
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourcePage
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceRequest
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceRequestKind
import com.sza.fastmediasorter.wear.domain.model.WearPhoneResourceResponseStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * S1697: the watch half of the paired-phone contract. Every call is one correlated round trip - a
 * message out, one matching response in - so a stale answer from an abandoned request can never be
 * mistaken for the current one, and nothing here survives the call that made it.
 */
class PhoneResourceClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    private val envelopeCodec = WearEventEnvelopeCodec()

    /**
     * Asks the phone for one page. [parentToken] null requests the root list.
     *
     * S1846: [mediaType] narrows the answer to one kind of file and travels on every request of a browse
     * session, not only the first - stepping into a folder with the type dropped would widen the list back
     * to everything halfway through.
     */
    suspend fun browse(
        parentToken: String?,
        pageToken: String? = null,
        mediaType: String? = null,
        isFlat: Boolean? = null
    ): PhoneResourceOutcome {
        val request = WearPhoneResourceRequest(
            requestId = UUID.randomUUID().toString(),
            kind = if (parentToken == null) {
                WearPhoneResourceRequestKind.ROOT
            } else {
                WearPhoneResourceRequestKind.CHILDREN
            },
            parentToken = parentToken,
            pageToken = pageToken,
            mediaType = mediaType,
            isFlat = isFlat
        )

        return request(request, WearDataLayerPaths.PHONE_RESOURCE_BROWSE_REQUEST)
    }

    /**
     * Asks the phone to deliver one item into [destination]. The file is written only after the
     * phone accepted the request, and it is deleted again if the transfer does not complete - a
     * half-written media file would fail later, further from the cause.
     */
    suspend fun open(itemToken: String, destination: File): PhoneResourceOutcome = coroutineScope {
        val request = WearPhoneResourceRequest(
            requestId = UUID.randomUUID().toString(),
            kind = WearPhoneResourceRequestKind.OPEN,
            itemToken = itemToken
        )

        // S1950: the phone opens the transfer channel the moment it has answered, and a channel
        // callback registered after that answer is never told about a channel that is already open.
        // Waiting for the channel is therefore armed before the request leaves, so the open cannot
        // fall into the gap and leave the watch waiting out the whole transfer timeout.
        val channelClient = Wearable.getChannelClient(context)
        Timber.d("S1950: channel wait armed before open request")
        val transfer = async { awaitChannel(channelClient) }
        val approval = request(request, WearDataLayerPaths.PHONE_RESOURCE_OPEN_REQUEST)
        if (approval is PhoneResourceOutcome.Page) {
            receiveTransfer(channelClient, transfer, destination)
        } else {
            transfer.cancel()
            approval
        }
    }

    private suspend fun request(request: WearPhoneResourceRequest, path: String): PhoneResourceOutcome {
        val nodeId = connectedPhoneId()
        val sent = nodeId?.let { node ->
            runCatching {
                Wearable.getMessageClient(context)
                    .sendMessage(node, path, gson.toJson(request).toByteArray())
                    .await()
            }.onFailure { Timber.w(it, "Phone resource request could not be sent") }
        }

        val page = if (sent?.isSuccess == true) {
            withTimeoutOrNull(RESPONSE_TIMEOUT_MS) { awaitPage(request.requestId) }
        } else {
            null
        }

        return when {
            page == null -> PhoneResourceOutcome.PhoneUnavailable
            page.status in ANSWERED_STATUSES -> PhoneResourceOutcome.Page(page)
            else -> PhoneResourceOutcome.Rejected(page.status)
        }
    }

    private suspend fun connectedPhoneId(): String? = runCatching {
        Wearable.getNodeClient(context).connectedNodes.await().firstOrNull()?.id
    }.onFailure { Timber.w(it, "Connected phone lookup failed") }.getOrNull()

    /**
     * Waits for the one page carrying [requestId]. The listener is removed on every exit - normal,
     * timed out or cancelled - because a leaked Data Layer listener keeps delivering into a screen
     * the user has already left.
     */
    private suspend fun awaitPage(requestId: String): WearPhoneResourcePage {
        val dataClient = Wearable.getDataClient(context)
        var registered: DataClient.OnDataChangedListener? = null

        // One removal site, reached by every exit - answered, timed out or cancelled. Removing in
        // both the callback and a cancellation handler would double-count as an unpaired remove.
        return try {
            suspendCancellableCoroutine { continuation ->
                val listener = DataClient.OnDataChangedListener { events ->
                    val page = events.firstMatchingPage(requestId)
                    events.release()
                    if (page != null && continuation.isActive) {
                        continuation.resume(page)
                    }
                }
                registered = listener
                dataClient.addListener(listener)
            }
        } finally {
            registered?.let { dataClient.removeListener(it) }
        }
    }

    private fun DataEventBuffer.firstMatchingPage(requestId: String): WearPhoneResourcePage? = this
        .asSequence()
        .filter { it.type == DataEvent.TYPE_CHANGED }
        .filter { it.dataItem.uri.path == WearDataLayerPaths.PHONE_RESOURCE_PAGE }
        .mapNotNull { event -> DataMapItem.fromDataItem(event.dataItem).dataMap.getByteArray("payload") }
        .mapNotNull { payload -> decodePage(payload) }
        .firstOrNull { it.requestId == requestId }

    private fun decodePage(payload: ByteArray): WearPhoneResourcePage? = runCatching {
        val envelope = envelopeCodec.decode(payload)
        Timber.d("S1893: page envelope decoded wireBytes=%d payloadBytes=%d", payload.size, envelope.data.size)
        gson.fromJson(envelope.data.decodeToString(), WearPhoneResourcePage::class.java)
    }.onFailure { Timber.w(it, "Unreadable phone resource page") }.getOrNull()

    /**
     * Accepts the channel the phone opens after an approved open request and writes it to disk.
     */
    private suspend fun receiveTransfer(
        channelClient: ChannelClient,
        transfer: Deferred<ChannelClient.Channel>,
        destination: File
    ): PhoneResourceOutcome {
        Timber.d("S1860: receiveTransfer for ${destination.name}")
        // The budget is spent from the approval on, not from the request: the wait was armed
        // earlier only so an early channel is not missed, never to shorten what the phone gets.
        val channel = withTimeoutOrNull(TRANSFER_TIMEOUT_MS) { transfer.await() }
        if (channel == null) {
            transfer.cancel()
            return PhoneResourceOutcome.PhoneUnavailable
        }

        val copied = runCatching {
            channelClient.getInputStream(channel).await().use { input -> input.writeTo(destination) }
        }.onFailure { Timber.w(it, "Phone resource transfer failed") }

        runCatching { channelClient.close(channel).await() }
            .onFailure { Timber.w(it, "Failed to close phone resource channel") }

        return if (copied.isSuccess) {
            PhoneResourceOutcome.Transferred(destination)
        } else {
            destination.delete()
            PhoneResourceOutcome.PhoneUnavailable
        }
    }

    private suspend fun awaitChannel(channelClient: ChannelClient): ChannelClient.Channel {
        var registered: ChannelClient.ChannelCallback? = null

        return try {
            suspendCancellableCoroutine { continuation ->
                val callback = object : ChannelClient.ChannelCallback() {
                    override fun onChannelOpened(channel: ChannelClient.Channel) {
                        val expected = channel.path == WearDataLayerPaths.PHONE_RESOURCE_TRANSFER
                        if (expected && continuation.isActive) {
                            continuation.resume(channel)
                        }
                    }
                }
                registered = callback
                channelClient.registerChannelCallback(callback)
            }
        } finally {
            registered?.let { channelClient.unregisterChannelCallback(it) }
        }
    }

    private fun InputStream.writeTo(destination: File) {
        destination.parentFile?.mkdirs()
        destination.outputStream().use { output -> copyTo(output) }
    }

    companion object {
        /** How long the watch waits for one correlated page before calling the phone unreachable. */
        private const val RESPONSE_TIMEOUT_MS = 10_000L

        /** How long the watch waits for the phone to open the approved transfer channel. */
        private const val TRANSFER_TIMEOUT_MS = 30_000L

        /** Statuses that carry a page the watch can render; everything else is a refusal. */
        private val ANSWERED_STATUSES = setOf(
            WearPhoneResourceResponseStatus.OK,
            WearPhoneResourceResponseStatus.EMPTY
        )
    }
}

/** What one paired-phone round trip produced. */
sealed interface PhoneResourceOutcome {

    /** A page of metadata, possibly empty, that the phone was willing to show. */
    data class Page(val page: WearPhoneResourcePage) : PhoneResourceOutcome

    /** A completed transfer, already written to [file]. */
    data class Transferred(val file: File) : PhoneResourceOutcome

    /** No phone answered - the paired device is absent, asleep or out of range. */
    data object PhoneUnavailable : PhoneResourceOutcome

    /** The phone answered and said no, with a reason the UI can phrase for the user. */
    data class Rejected(val status: WearPhoneResourceResponseStatus) : PhoneResourceOutcome
}
