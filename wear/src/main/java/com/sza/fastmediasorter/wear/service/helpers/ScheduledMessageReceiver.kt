package com.sza.fastmediasorter.wear.service.helpers

import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.core.notification.WearScheduledMessageNotifier
import com.sza.fastmediasorter.wear.data.wear.WearDataLayerPaths
import com.sza.fastmediasorter.wear.data.wear.WearScheduledMessageCodec
import com.sza.fastmediasorter.wear.data.wear.WearScheduledMessageParseResult
import com.sza.fastmediasorter.wear.data.wear.WearScheduledMessagePayload
import com.sza.fastmediasorter.wear.data.wear.WearScheduledMessageReceipt
import com.sza.fastmediasorter.wear.data.wear.WearScheduledMessageReceiptReasons
import com.sza.fastmediasorter.wear.domain.capability.WearRestrictedCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

private const val PICTURE_DIR = "scheduled_message"
private const val PICTURE_FILE = "latest.jpg"

/**
 * S4248: turns one scheduled message from the phone into a notification and answers with a receipt.
 *
 * Every outcome is answered, so the phone can tell "not shown, and why" from a message that never
 * arrived. Only the latest picture is kept: the watch shows it, it does not collect it for sorting.
 */
@Singleton
class ScheduledMessageReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilities: WearRestrictedCapabilities,
    private val notifier: WearScheduledMessageNotifier,
    private val gson: Gson,
) {

    suspend fun onMessage(sourceNodeId: String, data: ByteArray) {
        Timber.d("S4248: scheduled message received on watch, bytes=%d", data.size)
        answer(sourceNodeId, receive(data))
    }

    internal suspend fun receive(data: ByteArray): WearScheduledMessageReceipt {
        // Parsed before the offer is judged: a refusal only reaches the waiting phone run by carrying the id.
        val parsed = WearScheduledMessageCodec.parse(data, gson)
        if (!capabilities.offersContentTransfer) {
            return refusal(messageIdOf(parsed), WearScheduledMessageReceiptReasons.NOT_OFFERED)
        }
        return when (parsed) {
            is WearScheduledMessageParseResult.Parsed -> show(parsed.payload)
            is WearScheduledMessageParseResult.UnsupportedVersion ->
                refusal(parsed.messageId, WearScheduledMessageReceiptReasons.UNSUPPORTED_VERSION)
            is WearScheduledMessageParseResult.Malformed,
            WearScheduledMessageParseResult.TooLarge ->
                refusal(messageIdOf(parsed), WearScheduledMessageReceiptReasons.MALFORMED)
        }
    }

    /** Empty when the message carried no readable id; the phone cannot match such a receipt and drops it. */
    private fun messageIdOf(parsed: WearScheduledMessageParseResult): String = when (parsed) {
        is WearScheduledMessageParseResult.Parsed -> parsed.payload.messageId
        is WearScheduledMessageParseResult.UnsupportedVersion -> parsed.messageId
        is WearScheduledMessageParseResult.Malformed -> parsed.messageId.orEmpty()
        WearScheduledMessageParseResult.TooLarge -> ""
    }

    private suspend fun show(payload: WearScheduledMessagePayload): WearScheduledMessageReceipt {
        val picture = payload.imageJpegBase64?.let { storePicture(it) }
        val shown = notifier.notify(payload.title, payload.text?.takeIf { it.isNotBlank() }, picture)
        return if (shown) {
            Timber.i("Scheduled message: shown, picture=%s", picture != null)
            WearScheduledMessageReceipt(payload.messageId, shown = true)
        } else {
            refusal(payload.messageId, WearScheduledMessageReceiptReasons.NO_NOTIFICATION_PERMISSION)
        }
    }

    /** Null when the picture cannot be decoded or written; the message is then shown as text only. */
    private suspend fun storePicture(base64: String): File? = withContext(Dispatchers.IO) {
        try {
            val bytes = Base64.getDecoder().decode(base64)
            val dir = File(context.cacheDir, PICTURE_DIR).apply { mkdirs() }
            File(dir, PICTURE_FILE).apply { writeBytes(bytes) }
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Scheduled message: the picture is not valid base64")
            null
        } catch (e: IOException) {
            Timber.w(e, "Scheduled message: the picture could not be cached")
            null
        }
    }

    private suspend fun answer(nodeId: String, receipt: WearScheduledMessageReceipt) {
        if (nodeId.isBlank()) return
        try {
            Wearable.getMessageClient(context)
                .sendMessage(
                    nodeId,
                    WearDataLayerPaths.SCHEDULED_MESSAGE_RECEIPT,
                    WearScheduledMessageCodec.encodeReceipt(receipt, gson),
                )
                .await()
        } catch (e: ApiException) {
            // The notification is already up or refused; the phone records an unanswered message as skipped.
            Timber.w(e, "Scheduled message: the receipt could not be sent")
        }
    }

    private fun refusal(messageId: String, reason: String): WearScheduledMessageReceipt {
        Timber.w("Scheduled message: not shown - %s", reason)
        Timber.d("S4458: refusal receipt answered with id=%s reason=%s", messageId, reason)
        return WearScheduledMessageReceipt(messageId, shown = false, reason = reason)
    }
}
