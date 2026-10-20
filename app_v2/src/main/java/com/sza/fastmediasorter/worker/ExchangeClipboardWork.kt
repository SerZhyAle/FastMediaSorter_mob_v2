package com.sza.fastmediasorter.worker

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import com.sza.fastmediasorter.domain.model.ExchangeClipboardKind
import java.io.File

/**
 * The one place the clipboard transfers are enqueued, so their unique names and policies cannot drift. Each call
 * returns once WorkManager stored the request, so an observer of the returned unique name never sees the
 * previous run in its place.
 */
object ExchangeClipboardWork {

    const val UNIQUE_SEND = "exchange_clipboard_send"
    const val UNIQUE_GET = "exchange_clipboard_get"

    /** What a send reads: text already staged as a UTF-8 file with LF line ends, or a clip's `content:` URI. */
    sealed interface Source {
        data class StagedText(val file: File) : Source
        data class Content(val uri: Uri, val kind: ExchangeClipboardKind, val mimeType: String) : Source
    }

    /**
     * REPLACE: the newest clipboard wins, and a send cut short changes nothing on the server, which keeps its
     * previous value until a send completes (contract DEVICE-EXCHANGE 22 item BX).
     */
    suspend fun enqueueSend(context: Context, source: Source): String {
        val input = when (source) {
            is Source.StagedText -> workDataOf(
                ExchangeClipboardTransferWorker.KEY_DIRECTION to ExchangeClipboardTransferWorker.DIRECTION_SEND,
                ExchangeClipboardTransferWorker.KEY_KIND to ExchangeClipboardKind.TEXT.wire,
                ExchangeClipboardTransferWorker.KEY_STAGED_PATH to source.file.absolutePath,
            )
            is Source.Content -> workDataOf(
                ExchangeClipboardTransferWorker.KEY_DIRECTION to ExchangeClipboardTransferWorker.DIRECTION_SEND,
                ExchangeClipboardTransferWorker.KEY_KIND to source.kind.wire,
                ExchangeClipboardTransferWorker.KEY_URI to source.uri.toString(),
                ExchangeClipboardTransferWorker.KEY_MIME to source.mimeType,
            )
        }
        val request = OneTimeWorkRequestBuilder<ExchangeClipboardTransferWorker>()
            .setInputData(input)
            .setConstraints(connected())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_SEND, ExistingWorkPolicy.REPLACE, request).await()
        return UNIQUE_SEND
    }

    /** KEEP: a second tap while a get runs follows the running one instead of starting over. */
    suspend fun enqueueGet(context: Context): String {
        val request = OneTimeWorkRequestBuilder<ExchangeClipboardTransferWorker>()
            .setInputData(
                workDataOf(
                    ExchangeClipboardTransferWorker.KEY_DIRECTION to ExchangeClipboardTransferWorker.DIRECTION_GET,
                )
            )
            .setConstraints(connected())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_GET, ExistingWorkPolicy.KEEP, request).await()
        return UNIQUE_GET
    }

    private fun connected(): Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}
