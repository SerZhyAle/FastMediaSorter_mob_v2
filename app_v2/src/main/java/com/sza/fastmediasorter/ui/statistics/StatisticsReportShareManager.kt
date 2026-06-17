package com.sza.fastmediasorter.ui.statistics

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.StrictModeHelper
import com.sza.fastmediasorter.core.share.SystemShareInvoker
import com.sza.fastmediasorter.util.queryIntentActivitiesCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a ready statistics report (built by
 * [com.sza.fastmediasorter.domain.usecase.BuildStatisticsReportUseCase]) into a shareable TXT and
 * launches the share/email surface (S0473 Phase 05).
 *
 * "Send to author" pre-fills the author address + subject and attaches the TXT; "Export" opens a
 * plain chooser so the user can keep the file. Both go through [SystemShareInvoker] (ACTION_SEND
 * with a FileProvider URI + read grant) and the user always confirms the send in their own client -
 * there is no background egress (ADR-1). When no app can handle the intent a clear message is
 * surfaced instead of a silent no-op (strategic §7 risk row).
 */
@Singleton
class StatisticsReportShareManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Compose an email to the author with the report attached; user sends it from their client. */
    fun sendToAuthor(reportText: String) {
        val uri = writeReportToCache(reportText) ?: run {
            toast(R.string.statistics_no_email_app)
            return
        }
        val email = context.getString(R.string.statistics_author_email)
        val subject = context.getString(R.string.statistics_email_subject)

        // Pre-check resolvability so an empty device (no mail/handler app) gets a clear message
        // rather than a chooser that immediately dead-ends (Rule 21: *Compat, no raw overload).
        if (!canResolveSend(uri, EMAIL_MIME, email, subject)) {
            Timber.i("StatisticsReportShareManager: no app resolves the report email intent")
            toast(R.string.statistics_no_email_app)
            return
        }
        val launched = SystemShareInvoker.invokeFiles(
            context = context,
            uris = listOf(uri),
            mime = EMAIL_MIME,
            chooserTitle = context.getString(R.string.statistics_send_to_author),
            emailAddresses = arrayOf(email),
            subject = subject,
        )
        if (!launched) toast(R.string.statistics_no_email_app)
    }

    /** Save/share the same TXT through a plain chooser so the user can keep it. */
    fun export(reportText: String) {
        val uri = writeReportToCache(reportText) ?: run {
            toast(R.string.statistics_no_email_app)
            return
        }
        val launched = SystemShareInvoker.invokeFiles(
            context = context,
            uris = listOf(uri),
            mime = EXPORT_MIME,
            chooserTitle = context.getString(R.string.statistics_export),
        )
        if (!launched) toast(R.string.statistics_no_email_app)
    }

    /**
     * Overwrite the single cache report file and hand back a content URI for it. Returns null on a
     * write failure, in which case the caller surfaces the no-handler message (the action degrades
     * to a clear toast, never a crash).
     */
    private fun writeReportToCache(reportText: String): Uri? = try {
        StrictModeHelper.allowDiskIO {
            val file = File(context.cacheDir, REPORT_FILE_NAME)
            file.writeText(reportText)
            FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        }
    } catch (e: Exception) {
        Timber.e(e, "StatisticsReportShareManager: failed to write report to cache")
        null
    }

    private fun canResolveSend(uri: Uri, mime: String, email: String, subject: String): Boolean {
        val probe = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
        }
        return context.packageManager.queryIntentActivitiesCompat(probe).isNotEmpty()
    }

    private fun toast(resId: Int) {
        Toast.makeText(context, context.getString(resId), Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val REPORT_FILE_NAME = "statistics_report.txt"
        const val EMAIL_MIME = "text/plain"
        const val EXPORT_MIME = "text/plain"
        val FILE_PROVIDER_AUTHORITY = "${BuildConfig.APPLICATION_ID}.fileprovider"
    }
}
