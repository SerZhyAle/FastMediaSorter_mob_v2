package com.sza.fastmediasorter.wear.data.repository

import android.content.Context
import android.webkit.MimeTypeMap
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.domain.model.WEAR_FILE_TRANSFER_MAX_BYTES
import com.sza.fastmediasorter.wear.domain.model.WearFileReceiveAck
import com.sza.fastmediasorter.wear.domain.model.WearFileSendOutcome
import com.sza.fastmediasorter.wear.domain.model.WearFileTransferMetadata
import com.sza.fastmediasorter.wear.domain.repository.WearFileSendResult
import com.sza.fastmediasorter.wear.domain.repository.WearFileSenderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The bridge answers a message within this window or the phone is treated as gone. */
private const val MESSAGE_TIMEOUT_MS = 10_000L

/** A channel open is given longer than a message: GMS may have to wake the phone app first. */
private const val CHANNEL_TIMEOUT_MS = 30_000L

/** Window to wait for the phone's received file ack after transfer closes. */
private const val WEAR_MESSAGE_ACK_TIMEOUT_MS = 10_000L

private const val SEND_BUFFER_BYTES = 64 * 1024

/**
 * S1861: sends one watch-side file to the paired phone (`ChannelClient`, same path as the inbound
 * direction).
 *
 * The size is refused here rather than on the phone so the user is told before the transfer starts,
 * not after it fails; the phone counts the arriving bytes anyway, because this declaration is only
 * a hint on the far end.
 */
@Singleton
class WearFileSenderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : WearFileSenderRepository {

    override suspend fun sendFile(file: File): WearFileSendResult = withContext(Dispatchers.IO) {
        val size = file.length()
        when {
            !file.isFile -> {
                Timber.w("Refusing to send %s to the phone: not a readable file", file.name)
                WearFileSendResult(WearFileSendOutcome.FAILED)
            }
            size > WEAR_FILE_TRANSFER_MAX_BYTES -> {
                Timber.i("Refusing to send %s to the phone: %d bytes over the ceiling", file.name, size)
                WearFileSendResult(WearFileSendOutcome.TOO_LARGE)
            }
            else -> sendToNode(file, size)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun sendToNode(file: File, size: Long): WearFileSendResult {
        val nodeId = firstConnectedNodeId()
        if (nodeId == null) {
            return WearFileSendResult(WearFileSendOutcome.PHONE_UNREACHABLE)
        }

        val messageClient = Wearable.getMessageClient(context)
        val ackDeferred = CompletableDeferred<WearFileReceiveAck>()
        val listener = MessageClient.OnMessageReceivedListener { event ->
            if (event.path == WearDataLayerPaths.FILE_RECEIVE_ACK) {
                try {
                    val ack = gson.fromJson(event.data.decodeToString(), WearFileReceiveAck::class.java)
                    if (ack.fileName == file.name) {
                        ackDeferred.complete(ack)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to parse WearFileReceiveAck")
                }
            }
        }

        return try {
            messageClient.addListener(listener).await()
            announce(nodeId, file, size)
            copyToPhone(nodeId, file)
            val ack = withTimeoutOrNull(WEAR_MESSAGE_ACK_TIMEOUT_MS) { ackDeferred.await() }
            if (ack == null) {
                WearFileSendResult(WearFileSendOutcome.UNCONFIRMED)
            } else {
                val outcome = when (ack.outcome) {
                    WearFileReceiveAck.OUTCOME_SAVED -> WearFileSendOutcome.SENT
                    WearFileReceiveAck.OUTCOME_QUEUED -> WearFileSendOutcome.QUEUED_ON_PHONE
                    WearFileReceiveAck.OUTCOME_NO_DESTINATION -> WearFileSendOutcome.NO_DESTINATION
                    WearFileReceiveAck.OUTCOME_TOO_LARGE -> WearFileSendOutcome.TOO_LARGE
                    else -> WearFileSendOutcome.FAILED
                }
                WearFileSendResult(outcome, ack.destination.ifEmpty { null })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The bridge drops out through ApiException, IOException and TimeoutCancellation
            // alike, and every one of them means the same thing to the caller: it did not arrive.
            Timber.w(e, "Failed to send %s to the phone", file.name)
            WearFileSendResult(WearFileSendOutcome.FAILED)
        } finally {
            runCatching { messageClient.removeListener(listener).await() }
        }
    }

    private suspend fun announce(nodeId: String, file: File, size: Long) {
        val metadata = WearFileTransferMetadata(name = file.name, size = size, mimeType = mimeTypeOf(file))
        withTimeout(MESSAGE_TIMEOUT_MS) {
            Wearable.getMessageClient(context)
                .sendMessage(
                    nodeId,
                    WearDataLayerPaths.FILE_TRANSFER_META,
                    gson.toJson(metadata).toByteArray(Charsets.UTF_8)
                )
                .await()
        }
    }

    private suspend fun copyToPhone(nodeId: String, file: File) {
        val channelClient = Wearable.getChannelClient(context)
        val path = "${WearDataLayerPaths.FILE_TRANSFER}/${file.name}"
        val channel = withTimeout(CHANNEL_TIMEOUT_MS) { channelClient.openChannel(nodeId, path).await() }
        try {
            channelClient.getOutputStream(channel).await().use { output ->
                file.inputStream().use { input -> input.copyTo(output, SEND_BUFFER_BYTES) }
                output.flush()
            }
            Timber.i("Sent %s to the phone", file.name)
        } finally {
            closeChannel(channelClient, channel)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun firstConnectedNodeId(): String? = try {
        withTimeout(MESSAGE_TIMEOUT_MS) {
            Wearable.getNodeClient(context).connectedNodes.await().firstOrNull()?.id
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Expected whenever the phone is out of range or the companion app is not installed.
        Timber.i(e, "No paired phone reachable for the file transfer")
        null
    }

    private suspend fun closeChannel(channelClient: ChannelClient, channel: ChannelClient.Channel) {
        withContext(NonCancellable) {
            runCatching { channelClient.close(channel).await() }
                .onFailure { Timber.w(it, "Failed to close the outgoing file channel") }
        }
    }

    private fun mimeTypeOf(file: File): String? {
        val extension = file.extension.lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}
