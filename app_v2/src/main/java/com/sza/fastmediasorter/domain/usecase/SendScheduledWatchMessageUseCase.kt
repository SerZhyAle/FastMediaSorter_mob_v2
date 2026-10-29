package com.sza.fastmediasorter.domain.usecase

import android.util.Base64
import com.google.gson.Gson
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.data.wear.ScheduledWatchImageEncoder
import com.sza.fastmediasorter.data.wear.ScheduledWatchReceiptRegistry
import com.sza.fastmediasorter.domain.model.ScheduledOperation
import com.sza.fastmediasorter.domain.model.ScheduledRunReason
import com.sza.fastmediasorter.domain.model.WearNode
import com.sza.fastmediasorter.domain.model.WearScheduledMessageCodec
import com.sza.fastmediasorter.domain.model.WearScheduledMessagePayload
import com.sza.fastmediasorter.domain.model.WearScheduledMessageReceipt
import com.sza.fastmediasorter.domain.model.WearScheduledMessageReceiptReasons
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.service.WearDataLayerPaths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** How long a run waits for the watch to say whether it showed the message. */
private const val RECEIPT_TIMEOUT_MS = 20_000L

/**
 * S4248: delivers one scheduled watch message and reports the run's outcome.
 *
 * Every way the watch can be out of reach - no bridge in this build, the Wear Companion switch off, no
 * paired watch nearby, a watch that never answers - is a skipped run with its reason, never an error:
 * the next slot is the retry, and nothing is queued for a watch that comes back later (ADR-3).
 */
@Singleton
class SendScheduledWatchMessageUseCase @Inject constructor(
    private val mediaCapabilities: MediaCapabilities,
    private val dataLayer: WearableDataLayerRepository,
    private val imageEncoder: ScheduledWatchImageEncoder,
    private val receipts: ScheduledWatchReceiptRegistry,
    private val gson: Gson,
) {

    suspend operator fun invoke(operation: ScheduledOperation): ScheduledExecutionResult {
        Timber.d("S4248: scheduled watch message run, op=%d", operation.id)
        val unreachable = unreachableReason()
        val nodes = if (unreachable == null) dataLayer.getConnectedNodes() else emptyList()
        return when {
            unreachable != null -> skipped(operation, unreachable, ScheduledRunReason.WATCH_UNAVAILABLE)
            nodes.isEmpty() -> skipped(operation, "Watch not reachable", ScheduledRunReason.WATCH_UNREACHABLE)
            else -> deliver(operation, nodes)
        }
    }

    private suspend fun deliver(operation: ScheduledOperation, nodes: List<WearNode>): ScheduledExecutionResult {
        val imageBytes = operation.watchImageUri?.let { imageEncoder.encode(it) }
        val messageId = "${operation.id}-${System.currentTimeMillis()}"
        val payload = WearScheduledMessagePayload(
            messageId = messageId,
            title = operation.watchTitle.orEmpty(),
            text = operation.watchText?.takeIf { it.isNotBlank() },
            imageJpegBase64 = imageBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) },
        )
        val bytes = WearScheduledMessageCodec.encode(payload, gson)
            ?: WearScheduledMessageCodec.encode(payload.copy(imageJpegBase64 = null), gson)
            ?: return skipped(operation, "Message is too long for the watch", ScheduledRunReason.MESSAGE_TOO_LONG)

        val waiting = receipts.expect(messageId)
        return try {
            nodes.forEach { dataLayer.sendMessage(it.id, WearDataLayerPaths.SCHEDULED_MESSAGE, bytes) }
            val receipt = withTimeoutOrNull(RECEIPT_TIMEOUT_MS) { waiting.await() }
            Timber.d("S4458: watch receipt on phone shown=%s reason=%s", receipt?.shown, receipt?.reason)
            outcomeOf(operation, receipt, pictureLost = operation.watchImageUri != null && imageBytes == null)
        } catch (e: CancellationException) {
            throw e
        } catch (
            // The bridge surfaces GMS's ApiException, which this flavor-neutral layer cannot name;
            // every failure here means the same thing - the watch did not get the message this slot.
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            Timber.w(e, "Scheduled watch message %s could not be sent", messageId)
            skipped(operation, "Watch not reachable", ScheduledRunReason.WATCH_UNREACHABLE)
        } finally {
            receipts.forget(messageId)
        }
    }

    private suspend fun unreachableReason(): String? = when {
        !mediaCapabilities.supportsWearCompanion -> "Watch is not supported in this build"
        !dataLayer.isCompanionEnabled() -> "Wear Companion is off"
        else -> null
    }

    private fun outcomeOf(
        operation: ScheduledOperation,
        receipt: WearScheduledMessageReceipt?,
        pictureLost: Boolean,
    ): ScheduledExecutionResult = when {
        receipt == null -> skipped(operation, "Watch did not confirm", ScheduledRunReason.WATCH_UNREACHABLE)
        !receipt.shown ->
            skipped(operation, refusalSentence(receipt.reason), ScheduledRunReason.WATCH_REFUSED)
        pictureLost -> ScheduledExecutionResult(operation.id, 0, emptyList()).also {
            Timber.i("Scheduled watch message %d shown without its picture", operation.id)
        }
        else -> ScheduledExecutionResult(operation.id, 0, emptyList())
    }

    private fun refusalSentence(reason: String?): String = when (reason) {
        WearScheduledMessageReceiptReasons.NO_NOTIFICATION_PERMISSION -> "Watch is not allowed to show notifications"
        WearScheduledMessageReceiptReasons.NOT_OFFERED -> "Watch app does not accept messages from the phone"
        WearScheduledMessageReceiptReasons.UNSUPPORTED_VERSION -> "Watch app is out of date"
        else -> "Watch could not read the message"
    }

    private fun skipped(operation: ScheduledOperation, text: String, reason: ScheduledRunReason) =
        ScheduledExecutionResult(operation.id, 0, emptyList(), skippedReason = text, reason = reason)
}
