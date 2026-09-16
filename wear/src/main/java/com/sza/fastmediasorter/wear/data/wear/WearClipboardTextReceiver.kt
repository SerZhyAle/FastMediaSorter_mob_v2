package com.sza.fastmediasorter.wear.data.wear

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.google.android.gms.wearable.Wearable
import com.google.gson.Gson
import com.sza.fastmediasorter.wear.R
import com.sza.fastmediasorter.wear.util.errorUnlessCancellation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S3109: takes one text clipboard from the phone, puts it on this watch's clipboard and answers.
 *
 * The answer is sent for every outcome, refusals included (ADR-3): the phone that sent the text must
 * never have to tell a refusal from a lost connection, because those two need different actions from
 * the owner.
 */
class WearClipboardTextReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    suspend fun handle(nodeId: String, data: ByteArray) {
        Timber.d("S3109: phone clipboard text received")
        when (val parsed = WearClipboardTextCodec.parse(data, gson)) {
            is WearClipboardTextParseResult.Malformed ->
                refuse(nodeId, requestId = null, reason = WearClipboardTextRefusalReasons.MALFORMED)

            is WearClipboardTextParseResult.UnsupportedVersion -> {
                Timber.w("Phone clipboard: unknown format version ${parsed.version}")
                refuse(
                    nodeId,
                    requestId = null,
                    reason = WearClipboardTextRefusalReasons.UNSUPPORTED_VERSION
                )
            }

            is WearClipboardTextParseResult.TooLong ->
                refuse(nodeId, requestId = null, reason = WearClipboardTextRefusalReasons.TOO_LONG)

            is WearClipboardTextParseResult.Parsed -> take(nodeId, parsed.payload)
        }
    }

    private suspend fun take(nodeId: String, payload: WearClipboardTextPayload) {
        if (payload.text.isBlank()) {
            refuse(nodeId, payload.requestId, WearClipboardTextRefusalReasons.EMPTY_TEXT)
            return
        }

        if (!writeToClipboard(payload.text)) {
            refuse(nodeId, payload.requestId, WearClipboardTextRefusalReasons.CLIPBOARD_UNAVAILABLE)
            return
        }

        answer(nodeId, WearClipboardTextAck(requestId = payload.requestId, accepted = true))
        Timber.i("Phone clipboard: ${payload.text.length} characters taken")
    }

    /**
     * On the main dispatcher because the toast needs a looper the Data Layer callback thread does not
     * have. The toast announces the arrival without quoting it - the text may be a password the owner
     * never chose to show on a watch face.
     */
    private suspend fun writeToClipboard(text: String): Boolean = withContext(Dispatchers.Main) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        if (clipboard == null) {
            Timber.w("Phone clipboard: this watch exposes no clipboard service")
            return@withContext false
        }
        try {
            clipboard.setPrimaryClip(
                ClipData.newPlainText(context.getString(R.string.wear_clipboard_title), text)
            )
            Toast.makeText(context, R.string.wear_clipboard_received_toast, Toast.LENGTH_SHORT).show()
            true
        } catch (e: SecurityException) {
            // Some OEM watch builds restrict clipboard access; the phone is told so by name rather
            // than left waiting out its timeout.
            Timber.w(e, "Phone clipboard: the watch refused the write")
            false
        }
    }

    private suspend fun refuse(nodeId: String, requestId: String?, reason: String) {
        Timber.w("Phone clipboard: refused - $reason")
        answer(
            nodeId,
            WearClipboardTextAck(requestId = requestId.orEmpty(), accepted = false, reason = reason)
        )
    }

    private suspend fun answer(nodeId: String, ack: WearClipboardTextAck) {
        if (nodeId.isBlank()) return
        runCatching {
            Wearable.getMessageClient(context)
                .sendMessage(
                    nodeId,
                    WearDataLayerPaths.CLIPBOARD_TEXT_FROM_PHONE_ACK,
                    WearClipboardTextCodec.serializeAck(ack, gson)
                )
                .await()
        }.onFailure { failure ->
            // Rethrows a cancellation rather than logging it: swallowing one leaves the coroutine
            // machinery believing this job is still live (S1363/S1889/S1910).
            failure.errorUnlessCancellation("Phone clipboard: acknowledgement could not be sent")
        }
    }
}
