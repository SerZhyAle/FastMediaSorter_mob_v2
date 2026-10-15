package com.sza.fastmediasorter.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardFileNames
import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardFiles
import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardPlacer
import com.sza.fastmediasorter.core.clipboard.ExchangeClipboardReader
import com.sza.fastmediasorter.core.notification.NotificationIcons
import com.sza.fastmediasorter.core.notification.NotificationIds
import com.sza.fastmediasorter.domain.model.ExchangeClipboardEntryFailure
import com.sza.fastmediasorter.domain.model.ExchangeClipboardEntryFailure.Cause
import com.sza.fastmediasorter.domain.model.ExchangeClipboardGetOutcome
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import com.sza.fastmediasorter.domain.model.ExchangeClipboardMeta
import com.sza.fastmediasorter.domain.model.ExchangeClipboardSendOutcome
import com.sza.fastmediasorter.domain.model.ExchangeMessage
import com.sza.fastmediasorter.domain.usecase.exchange.GetClipboardFromServerUseCase
import com.sza.fastmediasorter.domain.usecase.exchange.SendClipboardToServerUseCase
import com.sza.fastmediasorter.ui.exchange.helpers.ExchangeMessageText
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Carries one clipboard transfer to or from the exchange server in the foreground (strategic S4226 §6 item 4):
 * a value of up to 5 GiB must outlive the window that started it, and only the person's Cancel - the
 * notification's action or the window's button - stops it. The window only observes this work.
 *
 * A send reads the staged text file or the clip's URI under the read grant the clipboard gave the app, which
 * holds until the clipboard changes; a get places the value only after it arrived whole (22 item BY).
 */
@HiltWorker
class ExchangeClipboardTransferWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val sendClipboard: SendClipboardToServerUseCase,
    private val getClipboard: GetClipboardFromServerUseCase,
    private val files: ExchangeClipboardFiles,
    private val placer: ExchangeClipboardPlacer,
) : CoroutineWorker(context, params) {

    private val sending: Boolean get() = inputData.getString(KEY_DIRECTION) == DIRECTION_SEND
    private val notifications: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(done = null, total = null)

    override suspend fun doWork(): Result {
        Timber.d("S4226: clipboard transfer worker started, sending=%s", sending)
        promoteToForeground()
        val owned = mutableListOf<File>()
        inputData.getString(KEY_STAGED_PATH)?.let { owned += File(it) }
        val report = try {
            if (sending) runSend(owned) else runGet()
        } catch (e: IOException) {
            Timber.w(e, "Exchange clipboard transfer failed before any entry was asked")
            Report(
                outcome = if (sending) OUTCOME_NOT_SENT else OUTCOME_NOT_RECEIVED,
                headline = context.getString(
                    if (sending) R.string.exchange_clipboard_not_sent else R.string.exchange_clipboard_not_received
                ),
                detail = context.getString(R.string.exchange_clipboard_broken),
            )
        } finally {
            // Only this run's own files: a newer send may already have staged its text beside them.
            owned.forEach(File::delete)
        }
        Timber.i("Exchange clipboard transfer finished: %s", report.outcome)
        notifyResult(report)
        return Result.success(report.toData())
    }

    private suspend fun runSend(owned: MutableList<File>): Report {
        val prepared = prepareSource(owned)
            ?: return report(OUTCOME_NOT_SENT, R.string.exchange_clipboard_unsupported)
        val outcome = withProgress(prepared.meta.size) { onProgress ->
            sendClipboard(prepared.meta, prepared.open, onProgress)
        }
        return when (outcome) {
            is ExchangeClipboardSendOutcome.Sent -> if (outcome.failures.isEmpty()) {
                report(OUTCOME_SENT, R.string.exchange_clipboard_sent)
            } else {
                val missed = outcome.failures.joinToString(", ") { it.entryLabel }
                Report(
                    OUTCOME_SENT_PARTLY,
                    context.getString(R.string.exchange_clipboard_sent_partly, missed),
                    detailOf(outcome.failures),
                )
            }
            is ExchangeClipboardSendOutcome.NotSent ->
                report(OUTCOME_NOT_SENT, R.string.exchange_clipboard_not_sent, outcome.failures)
            ExchangeClipboardSendOutcome.NoEntry -> report(OUTCOME_NO_ENTRY, R.string.exchange_clipboard_no_entry)
        }
    }

    private suspend fun runGet(): Report {
        val outcome = withProgress(total = null) { onProgress -> getClipboard(onProgress) }
        if (outcome is ExchangeClipboardGetOutcome.Received) placer.place(outcome.value, outcome.file)
        return when (outcome) {
            is ExchangeClipboardGetOutcome.Received -> Report(
                OUTCOME_RECEIVED,
                context.getString(
                    R.string.exchange_clipboard_received,
                    outcome.value.sourceDeviceName ?: outcome.value.sourceDeviceId.orEmpty(),
                    storedAtText(outcome.value.storedAtInstant(), outcome.value.storedAt),
                ),
                detailOf(outcome.failures),
            )
            is ExchangeClipboardGetOutcome.Empty ->
                report(OUTCOME_EMPTY, ExchangeMessageText.what(ExchangeMessage.CLIPBOARD_EMPTY), outcome.failures)
            is ExchangeClipboardGetOutcome.NoRoom -> {
                val needed = Formatter.formatShortFileSize(context, outcome.size)
                Report(OUTCOME_NO_ROOM, context.getString(R.string.exchange_clipboard_no_room, needed), null)
            }
            ExchangeClipboardGetOutcome.Damaged -> report(OUTCOME_DAMAGED, R.string.exchange_clipboard_damaged)
            is ExchangeClipboardGetOutcome.NotReceived ->
                report(OUTCOME_NOT_RECEIVED, R.string.exchange_clipboard_not_received, outcome.failures)
            ExchangeClipboardGetOutcome.NoEntry -> report(OUTCOME_NO_ENTRY, R.string.exchange_clipboard_no_entry)
        }
    }

    private fun report(
        outcome: String,
        @StringRes headline: Int,
        failures: List<ExchangeClipboardEntryFailure> = emptyList(),
    ) = Report(outcome, context.getString(headline), detailOf(failures))

    private fun prepareSource(owned: MutableList<File>): Prepared? {
        val kind = ExchangeClipboardKind.fromWire(inputData.getString(KEY_KIND))
        val staged = inputData.getString(KEY_STAGED_PATH)?.let(::File)?.takeIf { it.length() > 0 }
        val uri = inputData.getString(KEY_URI)?.let(Uri::parse)
        return when {
            kind == ExchangeClipboardKind.TEXT && staged != null ->
                Prepared(ExchangeClipboardMeta(kind, staged.length(), null, null)) { staged.inputStream() }
            kind != null && kind != ExchangeClipboardKind.TEXT && uri != null -> {
                val mimeType = inputData.getString(KEY_MIME) ?: ExchangeClipboardReader.MIME_UNKNOWN
                preparedContent(kind, uri, mimeType, owned)
            }
            else -> null
        }
    }

    // 22 item BW: the size goes out before any byte, so a source that cannot state it is copied first.
    private fun preparedContent(
        kind: ExchangeClipboardKind,
        uri: Uri,
        mimeType: String,
        owned: MutableList<File>,
    ): Prepared? {
        val (displayName, declaredSize) = describe(uri)
        // 22 item BW: a file carries a name, an image may go without one.
        val name = when {
            displayName != null -> ExchangeClipboardFileNames.safeName(displayName, kind, mimeType, "")
            kind == ExchangeClipboardKind.FILE -> ExchangeClipboardFileNames.safeName(null, kind, mimeType, "")
            else -> null
        }
        val size = declaredSize?.takeIf { it > 0 }
        val copy = if (size == null) stageCopy(uri).also(owned::add) else null
        val length = size ?: copy?.length() ?: 0L
        if (length <= 0L) return null
        val meta = ExchangeClipboardMeta(kind, length, mimeType, name)
        return Prepared(meta) { copy?.inputStream() ?: openUri(uri) }
    }

    private fun describe(uri: Uri): Pair<String?, Long?> = try {
        context.contentResolver.query(uri, DESCRIBE_COLUMNS, null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null to null
                val nameAt = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeAt = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameAt >= 0 && !cursor.isNull(nameAt)) cursor.getString(nameAt) else null
                val size = if (sizeAt >= 0 && !cursor.isNull(sizeAt)) cursor.getLong(sizeAt) else null
                name to size
            } ?: (null to null)
    } catch (e: SecurityException) {
        Timber.w(e, "Exchange clipboard: the clip's URI cannot be described")
        null to null
    }

    private fun stageCopy(uri: Uri): File {
        val copy = files.newOutgoingFile(STAGED_EXTENSION)
        openUri(uri).use { input -> copy.outputStream().use { input.copyTo(it) } }
        return copy
    }

    // A revoked grant - the person copied something else meanwhile - is a broken source, not a crash.
    private fun openUri(uri: Uri): InputStream = try {
        context.contentResolver.openInputStream(uri) ?: throw IOException("the clip's URI opened nothing")
    } catch (e: SecurityException) {
        throw IOException("the clipboard no longer grants this URI", e)
    }

    private suspend fun <T> withProgress(total: Long?, block: suspend ((Long) -> Unit) -> T): T = coroutineScope {
        val done = AtomicLong()
        val ticker = launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                val now = done.get()
                setProgress(workDataOf(KEY_DONE to now, KEY_TOTAL to (total ?: UNKNOWN_TOTAL)))
                notifications.notify(PROGRESS_ID, foregroundInfo(now, total).notification)
            }
        }
        try {
            block { done.set(it) }
        } finally {
            ticker.cancel()
        }
    }

    private suspend fun promoteToForeground() {
        try {
            setForeground(foregroundInfo(done = null, total = null))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            // Android 12+ refuses a foreground start the system judges to be from the background; the
            // transfer still runs, only without its ongoing notification.
            Timber.w(e, "Exchange clipboard: the transfer runs without a foreground notification")
        }
    }

    private fun foregroundInfo(done: Long?, total: Long?): ForegroundInfo {
        ensureChannel()
        val title = if (sending) R.string.exchange_clipboard_sending else R.string.exchange_clipboard_getting
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                R.drawable.ic_cancel,
                context.getString(R.string.cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(id),
            )
        if (total != null && total > 0 && done != null) {
            builder.setProgress(PROGRESS_MAX, (done * PROGRESS_MAX / total).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        done?.let { builder.setContentText(Formatter.formatShortFileSize(context, it)) }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(PROGRESS_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(PROGRESS_ID, builder.build())
        }
    }

    private fun notifyResult(report: Report) {
        ensureChannel()
        val title = if (sending) R.string.exchange_clipboard_send_label else R.string.exchange_clipboard_get_label
        val text = listOfNotNull(report.headline, report.detail).joinToString("\n")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(NotificationIcons.STATUS_BAR)
            .setContentTitle(context.getString(title))
            .setContentText(report.headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        notifications.notify(NotificationIds.EXCHANGE_CLIPBOARD_RESULT, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notifications.getNotificationChannel(CHANNEL_ID) != null) return
        notifications.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.exchange_clipboard_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun detailOf(failures: List<ExchangeClipboardEntryFailure>): String? =
        failures.takeIf { it.isNotEmpty() }
            ?.joinToString("\n") {
                context.getString(R.string.exchange_failure_named, it.entryLabel, causeText(it.cause))
            }
            ?.take(MAX_DETAIL_CHARS)

    // 25 item CZ gives `limit-reached` on a clipboard send its own row, which ofWire picks by the flag.
    private fun causeText(cause: Cause): String = when (cause) {
        is Cause.Refused ->
            ExchangeMessageText.twoParts(context, ExchangeMessage.ofWire(cause.reason, clipboardSend = sending))
        Cause.NotOffered -> context.getString(R.string.exchange_clipboard_not_offered)
        Cause.Guest -> ExchangeMessageText.twoParts(context, ExchangeMessage.NOT_PERMITTED)
        Cause.Unreachable -> ExchangeMessageText.twoParts(context, ExchangeMessage.UNREACHABLE_SERVER)
        Cause.Broken -> context.getString(R.string.exchange_clipboard_broken)
        is Cause.Sealing -> ExchangeMessageText.twoParts(context, cause.message)
    }

    private fun storedAtText(instant: Instant?, raw: String): String =
        instant?.let {
            DateUtils.formatDateTime(
                context,
                it.toEpochMilli(),
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME,
            )
        } ?: raw

    private class Prepared(val meta: ExchangeClipboardMeta, val open: () -> InputStream)

    private data class Report(val outcome: String, val headline: String, val detail: String?) {
        fun toData(): Data = workDataOf(KEY_OUTCOME to outcome, KEY_HEADLINE to headline, KEY_DETAIL to detail)
    }

    companion object {
        const val KEY_DIRECTION = "exchange_clipboard_direction"
        const val KEY_KIND = "exchange_clipboard_kind"
        const val KEY_URI = "exchange_clipboard_uri"
        const val KEY_STAGED_PATH = "exchange_clipboard_staged_path"
        const val KEY_MIME = "exchange_clipboard_mime"
        const val KEY_DONE = "exchange_clipboard_done"
        const val KEY_TOTAL = "exchange_clipboard_total"
        const val KEY_OUTCOME = "exchange_clipboard_outcome"
        const val KEY_HEADLINE = "exchange_clipboard_headline"
        const val KEY_DETAIL = "exchange_clipboard_detail"
        const val DIRECTION_SEND = "send"
        const val DIRECTION_GET = "get"
        const val UNKNOWN_TOTAL = -1L

        const val OUTCOME_SENT = "sent"
        const val OUTCOME_SENT_PARTLY = "sent-partly"
        const val OUTCOME_NOT_SENT = "not-sent"
        const val OUTCOME_RECEIVED = "received"
        const val OUTCOME_EMPTY = "empty"
        const val OUTCOME_NO_ROOM = "no-room"
        const val OUTCOME_DAMAGED = "damaged"
        const val OUTCOME_NOT_RECEIVED = "not-received"

        /** No entry takes part: the window offers "My devices" for this one. */
        const val OUTCOME_NO_ENTRY = "no-entry"

        private const val CHANNEL_ID = "exchange_clipboard"
        private const val PROGRESS_ID = NotificationIds.EXCHANGE_CLIPBOARD_PROGRESS
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val PROGRESS_MAX = 1000
        private const val MAX_DETAIL_CHARS = 4000
        private const val STAGED_EXTENSION = "part"
        private val DESCRIBE_COLUMNS = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    }
}
