package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.content.Context
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.core.logging.LoggingHelper
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * S0490: on the first launch after an uncaught crash, offers to email the crash report (with the app
 * log attached) to the author. Reuses the S0483 send path. Shown once per crash, gated by a
 * SharedPreferences watermark so a dismissed or backgrounded prompt never reappears for the same crash.
 */
class CrashReportPromptManager(private val activity: Activity) {

    /**
     * IO-safe: list the log dir for the newest not-yet-handled crash file and claim it by writing the
     * "handled" watermark up front, so a dismissed or backgrounded prompt never re-offers this crash.
     * Returns the file to prompt for, or null. Split from [showPrompt] (S1153) so the directory
     * listing runs off the main thread; render the result on Main via [showPrompt].
     */
    fun findPendingCrash(): File? {
        val crashFile = LoggingHelper.getLatestCrashFile() ?: return null
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_HANDLED, null) == crashFile.name) return null
        prefs.edit().putString(KEY_LAST_HANDLED, crashFile.name).apply()
        return crashFile
    }

    /** Main-thread: show the crash-report prompt for the file claimed by [findPendingCrash]. */
    fun showPrompt(crashFile: File) {
        if (activity.isFinishing) return
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.crash_prompt_title)
            .setMessage(R.string.crash_prompt_message)
            .setPositiveButton(R.string.crash_prompt_send) { _, _ -> sendReport(crashFile) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendReport(crashFile: File) {
        val subject = activity.getString(R.string.crash_report_email_subject)
        CoroutineScope(Dispatchers.IO).launch {
            val zipUri = LogExportHelper.buildLogsZipUri(activity)
            val body = buildString {
                append(activity.getString(R.string.crash_report_email_body_intro))
                // The crash text already travels inside the attached log ZIP; only inline it when
                // packaging produced no attachment, so the email body stays small.
                if (zipUri == null) {
                    val crashText = try {
                        crashFile.readText()
                    } catch (e: Exception) {
                        Timber.w(e, "CrashReportPromptManager: could not read crash file")
                        ""
                    }
                    if (crashText.isNotBlank()) {
                        append("\n\n")
                        append(crashText)
                    }
                }
            }
            withContext(Dispatchers.Main) {
                // Email-first with share-sheet fallback so a missing mail app does not drop the report.
                val delivered = SupportIntentFactory.launchCrashReport(activity, subject, body, zipUri)
                if (!delivered) {
                    Toast.makeText(activity, R.string.crash_report_no_share_target, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "crash_report_prompt"
        const val KEY_LAST_HANDLED = "last_handled_crash"
    }
}
