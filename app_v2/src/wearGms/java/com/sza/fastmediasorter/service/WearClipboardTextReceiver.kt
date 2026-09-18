package com.sza.fastmediasorter.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.copyTextToClipboard
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.domain.model.WearClipboardTextAck
import com.sza.fastmediasorter.domain.model.WearClipboardTextCodec
import com.sza.fastmediasorter.domain.model.WearClipboardTextParseResult
import com.sza.fastmediasorter.domain.model.WearClipboardTextPayload
import com.sza.fastmediasorter.domain.model.WearClipboardTextRefusalReasons
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * S3109: takes one text clipboard from the watch, puts it on this phone's clipboard and answers.
 *
 * Unlike the S3108 report, a suppressed notification does not turn the answer into a refusal: the
 * clipboard write IS the delivery here, so a watch told "refused" would have the owner send again
 * over a text that already arrived. The notice is the announcement of a delivery, not the delivery.
 */
class WearClipboardTextReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val dataLayerRepository: WearableDataLayerRepository
) {

    /**
     * S3109: the watch's verdict on a clipboard this phone pushed, published to whoever is waiting.
     *
     * Parsed here rather than in the listener service, for the reason S2550 recorded on the listen
     * ack: that service holds no reference to the screen that asked and cannot acquire one, so the
     * answer travels the process-wide bus. An unparseable answer is dropped - it names no request and
     * the sender times out, which is the same thing a lost message does.
     */
    suspend fun publishAck(data: ByteArray) {
        val ack = runCatching {
            gson.fromJson(data.decodeToString(), WearClipboardTextAck::class.java)
        }.onFailure { Timber.w(it, "Wear clipboard: could not parse the watch's answer") }.getOrNull()
            ?: return
        WearSyncEvents.emitClipboardTextAck(ack)
    }

    suspend fun handle(nodeId: String, data: ByteArray) {
        when (val parsed = WearClipboardTextCodec.parse(data, gson)) {
            is WearClipboardTextParseResult.Malformed ->
                refuse(nodeId, requestId = null, reason = WearClipboardTextRefusalReasons.MALFORMED)

            is WearClipboardTextParseResult.UnsupportedVersion -> {
                Timber.w("Wear clipboard: unknown format version ${parsed.version}")
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

        val written = writeToClipboard(payload.text)
        if (!written) {
            refuse(nodeId, payload.requestId, WearClipboardTextRefusalReasons.CLIPBOARD_UNAVAILABLE)
            return
        }

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            postNotification()
        }
        answer(nodeId, WearClipboardTextAck(requestId = payload.requestId, accepted = true))
        Timber.i("Wear clipboard: ${payload.text.length} characters taken from ${payload.sourceDeviceModel}")
    }

    /**
     * On the main dispatcher because the shared helper shows a toast below Android 13, and a toast
     * needs a looper the Data Layer callback thread does not have.
     */
    private suspend fun writeToClipboard(text: String): Boolean = withContext(Dispatchers.Main) {
        runCatching {
            context.copyTextToClipboard(context.getString(R.string.wear_clipboard_notification_title), text)
        }.onFailure { Timber.w(it, "Wear clipboard: the system clipboard refused the text") }.isSuccess
    }

    /**
     * Announces the arrival without quoting it: the clipboard may hold an address or a password the
     * owner never chose to show on a lock screen.
     */
    // The caller checks areNotificationsEnabled() before invoking this method, and notify() is
    // wrapped in runCatching below to catch SecurityException if POST_NOTIFICATIONS is revoked
    // between the check and the post. Lint does not recognize runCatching as a permission guard.
    @SuppressLint("MissingPermission")
    private fun postNotification() {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.wear_clipboard_notification_title))
            .setContentText(context.getString(R.string.wear_clipboard_notification_text))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onFailure {
                // POST_NOTIFICATIONS revoked between the check and the post: the text is already on
                // the clipboard, so a missing notice is degraded but safe.
                Timber.i(it, "Wear clipboard: notification suppressed - POST_NOTIFICATIONS not granted")
            }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wear_clipboard_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private suspend fun refuse(nodeId: String, requestId: String?, reason: String) {
        Timber.w("Wear clipboard: refused - $reason")
        answer(
            nodeId,
            WearClipboardTextAck(requestId = requestId.orEmpty(), accepted = false, reason = reason)
        )
    }

    private suspend fun answer(nodeId: String, ack: WearClipboardTextAck) {
        runCatching {
            dataLayerRepository.sendMessage(
                nodeId,
                WearDataLayerPaths.CLIPBOARD_TEXT_FROM_WATCH_ACK,
                WearClipboardTextCodec.serializeAck(ack, gson)
            )
        }.onFailure { Timber.w(it, "Wear clipboard: acknowledgement could not be sent") }
    }

    private companion object {
        const val CHANNEL_ID = "wear_clipboard_text"

        /** Alias, never the literal - CLAUDE.md keeps every id in NotificationIds. */
        const val NOTIFICATION_ID = NotificationIds.WEAR_CLIPBOARD_TEXT
    }
}
