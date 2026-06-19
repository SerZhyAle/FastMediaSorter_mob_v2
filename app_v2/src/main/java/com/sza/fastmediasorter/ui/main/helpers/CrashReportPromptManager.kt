package com.sza.fastmediasorter.ui.main.helpers

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
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

    fun maybeShowPrompt() {
        val crashFile = LoggingHelper.getLatestCrashFile() ?: return
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_HANDLED, null) == crashFile.name) return
        // Mark handled before showing so a dismiss or a backgrounded prompt never re-offers this crash.
        prefs.edit().putString(KEY_LAST_HANDLED, crashFile.name).apply()

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
            val crashText = try {
                crashFile.readText()
            } catch (e: Exception) {
                Timber.w(e, "CrashReportPromptManager: could not read crash file")
                ""
            }
            val body = buildString {
                append(activity.getString(R.string.crash_report_email_body_intro))
                if (crashText.isNotBlank()) {
                    append("\n\n")
                    append(crashText)
                }
            }
            val zipUri = LogExportHelper.buildLogsZipUri(activity)
            withContext(Dispatchers.Main) {
                val intent = SupportIntentFactory.buildCrashReportEmail(subject, body, zipUri)
                try {
                    activity.startActivity(Intent.createChooser(intent, subject))
                } catch (e: ActivityNotFoundException) {
                    Timber.w(e, "CrashReportPromptManager: no email app to send crash report")
                    Toast.makeText(activity, R.string.export_logs_no_share_target, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "crash_report_prompt"
        const val KEY_LAST_HANDLED = "last_handled_crash"
    }
}
