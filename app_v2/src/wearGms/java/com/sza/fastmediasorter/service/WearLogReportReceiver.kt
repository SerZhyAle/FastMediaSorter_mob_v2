package com.sza.fastmediasorter.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.gson.Gson
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.core.logging.LoggingHelper
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.domain.model.WearLogReportAck
import com.sza.fastmediasorter.domain.model.WearLogReportCodec
import com.sza.fastmediasorter.domain.model.WearLogReportParseResult
import com.sza.fastmediasorter.domain.model.WearLogReportPayload
import com.sza.fastmediasorter.domain.repository.WearableDataLayerRepository
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import com.sza.fastmediasorter.util.resolveActivityCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * S1802: takes one log report from the watch, stores it beside the phone's own logs and answers.
 *
 * The answer is sent AFTER the notification decision, never on receipt: strategic 7 names a report
 * that arrives while notifications are suppressed as the case where the watch would claim success
 * and the user would still have nothing to act on.
 */
class WearLogReportReceiver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val dataLayerRepository: WearableDataLayerRepository
) {

    suspend fun handle(nodeId: String, data: ByteArray) {
        Timber.d("S1802: phone received a watch log report from node $nodeId")
        when (val parsed = WearLogReportCodec.parse(data, gson)) {
            is WearLogReportParseResult.Malformed -> {
                Timber.w("Wear log report: payload could not be parsed")
                answer(nodeId, requestId = null, accepted = false, reason = REASON_MALFORMED)
            }

            is WearLogReportParseResult.UnsupportedVersion -> {
                Timber.w("Wear log report: unknown format version ${parsed.version}")
                answer(nodeId, requestId = null, accepted = false, reason = REASON_UNSUPPORTED)
            }

            is WearLogReportParseResult.Parsed -> store(nodeId, parsed.payload)
        }
    }

    private suspend fun store(nodeId: String, payload: WearLogReportPayload) {
        val written = writeWatchLog(payload)
        if (written == null) {
            answer(nodeId, payload.requestId, accepted = false, reason = REASON_NOT_STORED)
            return
        }

        // The permission is checked before the watch is told anything: a report the user will never
        // see is not a delivered report, and the watch has to be able to say so.
        val canNotify = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (canNotify) {
            postNotification(payload, written)
        }
        answer(
            nodeId = nodeId,
            requestId = payload.requestId,
            accepted = canNotify,
            reason = if (canNotify) null else REASON_NOTIFICATIONS_OFF
        )
    }

    /** Writes the watch log beside the phone's own set, named so the pair is obvious. Off the main thread. */
    private suspend fun writeWatchLog(payload: WearLogReportPayload): File? = withContext(Dispatchers.IO) {
        val logsDirectory = LoggingHelper.getLogsDirectory(context)
        val written = runCatching {
            val stamp = FILE_STAMP_FORMAT.format(Instant.ofEpochMilli(payload.capturedAtEpochMillis))
            val target = File(logsDirectory, "${LoggingHelper.WATCH_LOG_PREFIX}$stamp${LoggingHelper.WATCH_LOG_SUFFIX}")
            target.parentFile?.mkdirs()
            val header = buildString {
                appendLine("device: ${payload.deviceModel} (Android ${payload.androidRelease})")
                appendLine("watch app: ${payload.appVersionName} (${payload.appVersionCode})")
                appendLine("captured: $stamp")
                appendLine()
            }
            target.writeText(header + payload.logText)
            target
        }.onFailure { Timber.e(it, "Wear log report: could not write the watch log") }.getOrNull()

        // S1805: prune only once the report is safely on disk, and outside the block above - a
        // cleanup that fails must not turn a stored report into one the watch is told was lost.
        if (written != null) {
            LoggingHelper.pruneLogFiles(
                logsDirectory,
                LoggingHelper.WATCH_LOG_PREFIX,
                LoggingHelper.WATCH_LOG_SUFFIX
            )
        }
        written
    }

    /**
     * Announces the arrival; the tap opens the crash-report email with both devices' logs attached.
     *
     * A notification rather than a screen because reception happens in a background service, and a
     * background process has not been allowed to start an activity since Android 10 - strategic
     * ADR-4. The user is looking at the watch at that moment anyway.
     */
    private fun postNotification(payload: WearLogReportPayload, written: File) {
        ensureChannel()
        val zipUri = LogExportHelper.buildLogsZipUri(context, listOf(written))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(R.string.wear_log_report_notification_title))
            .setContentText(context.getString(R.string.wear_log_report_notification_text))
            .setContentIntent(emailPendingIntent(zipUri))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
            .onFailure {
                // POST_NOTIFICATIONS revoked between the check and the post: the log is already
                // stored, so a missing notice is degraded but safe.
                Timber.i(it, "Wear log report: notification suppressed - POST_NOTIFICATIONS not granted")
            }
        Timber.i("Wear log report: stored ${written.name} from ${payload.deviceModel}")
    }

    /**
     * The email intent when a mail app can resolve it, the selector-less share intent otherwise.
     *
     * SupportIntentFactory.launchCrashReport makes that same choice by catching
     * ActivityNotFoundException, which a PendingIntent cannot do - it only fires. Resolving up front
     * is what keeps the tap from dead-ending on a phone with no mail client.
     */
    private fun emailPendingIntent(zipUri: android.net.Uri?): PendingIntent {
        val email = SupportIntentFactory.buildCrashReportEmail(
            subject = context.getString(R.string.wear_log_report_email_subject),
            body = context.getString(R.string.wear_log_report_email_body),
            attachmentUri = zipUri
        )
        val resolved = context.packageManager.resolveActivityCompat(email) != null
        val intent = if (resolved) email else email.apply { selector = null }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.wear_log_report_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    private suspend fun answer(nodeId: String, requestId: String?, accepted: Boolean, reason: String?) {
        val ack = WearLogReportAck(requestId = requestId.orEmpty(), accepted = accepted, reason = reason)
        runCatching {
            dataLayerRepository.sendMessage(
                nodeId,
                WearDataLayerPaths.LOG_REPORT_ACK,
                WearLogReportCodec.serializeAck(ack, gson)
            )
        }.onFailure { Timber.w(it, "Wear log report: acknowledgement could not be sent") }
    }

    private companion object {
        const val CHANNEL_ID = "wear_log_report"

        /** Alias, never the literal - CLAUDE.md keeps every id in NotificationIds. */
        const val NOTIFICATION_ID = NotificationIds.WATCH_LOG_REPORT
        const val REASON_MALFORMED = "malformed"
        const val REASON_UNSUPPORTED = "unsupported_version"
        const val REASON_NOT_STORED = "not_stored"
        const val REASON_NOTIFICATIONS_OFF = "notifications_disabled"

        // DateTimeFormatter, not SimpleDateFormat: this receiver is injected once and handles
        // reports from the service scope, so a mutable formatter would be shared across threads.
        val FILE_STAMP_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US).withZone(ZoneId.systemDefault())
    }
}
