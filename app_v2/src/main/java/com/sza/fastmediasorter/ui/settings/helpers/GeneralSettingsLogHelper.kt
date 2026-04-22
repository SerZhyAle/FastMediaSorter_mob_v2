package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.debug.DebugToolsBridge
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class GeneralSettingsLogHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
    private val saveLogsLauncher: ActivityResultLauncher<String>,
) {
    fun setupVersionInfo() {
        val versionInfo = "${com.sza.fastmediasorter.BuildConfig.VERSION_NAME} | Build ${com.sza.fastmediasorter.BuildConfig.VERSION_CODE} | sza@ukr.net"
        binding.tvVersionInfo.text = versionInfo
        binding.tvVersionInfo.setOnClickListener { openEmailClient() }
        binding.tvVersionInfo.setOnLongClickListener {
            val intent = DebugToolsBridge.maybeCreateDebugMenuIntent(fragment.requireContext())
            if (intent != null) fragment.startActivity(intent) else shareLogs()
            true
        }
    }

    fun setupButtons() {
        binding.btnShowLog.setOnClickListener { showLogDialog(fullLog = true) }
        binding.btnShowSessionLog.setOnClickListener { showLogDialog(fullLog = false) }
        binding.btnShareLogs?.setOnClickListener { shareLogs() }
        binding.btnSaveLogs?.setOnClickListener { launchSaveLogs() }
    }

    fun shareLogs() {
        val result = LogExportHelper.exportLogs(fragment.requireActivity())
        when (result) {
            is LogExportHelper.ExportResult.NoLogs ->
                Toast.makeText(fragment.requireContext(), R.string.export_logs_no_files, Toast.LENGTH_SHORT).show()
            is LogExportHelper.ExportResult.Error ->
                Toast.makeText(fragment.requireContext(), result.message, Toast.LENGTH_LONG).show()
            else -> {}
        }
    }

    fun launchSaveLogs() {
        try {
            saveLogsLauncher.launch("fastmediasorter_logs.zip")
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.w(e, "LogExport: CREATE_DOCUMENT not supported — falling back to share")
            showSaveLogsNotSupportedDialog()
        }
    }

    fun showLogDialog(fullLog: Boolean) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val logText = withContext(Dispatchers.IO) {
                if (fullLog) getFullLog() else getSessionLog()
            }
            if (!fragment.isAdded || fragment.view == null) return@launch
            com.sza.fastmediasorter.ui.common.DialogUtils.showScrollableDialog(
                fragment.requireContext(),
                if (fullLog) "Application Log" else "Current Session Log",
                logText,
                "Close"
            )
        }
    }

    private fun showSaveLogsNotSupportedDialog() {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.save_debug_logs)
            .setMessage(R.string.save_logs_not_supported)
            .setPositiveButton(R.string.share) { _, _ -> shareLogs() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openEmailClient() {
        val version = com.sza.fastmediasorter.BuildConfig.VERSION_NAME
        val subject = "About FastImageSorter (mobile) $version"
        val mailtoUri = android.net.Uri.parse("mailto:sza@ukr.net")
            .buildUpon()
            .appendQueryParameter("subject", subject)
            .build()
        val emailIntent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
            data = mailtoUri
            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
        }
        try {
            fragment.startActivity(android.content.Intent.createChooser(emailIntent, fragment.getString(R.string.send_email)))
        } catch (e: Exception) {
            Timber.e(e, "Failed to open email client")
            Toast.makeText(fragment.requireContext(), R.string.no_email_client, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFullLog(): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val bufferedReader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val log = StringBuilder()
            var lineCount = 0
            val lines = bufferedReader.readLines()
            val startIndex = maxOf(0, lines.size - 512)
            for (i in startIndex until lines.size) {
                log.append(lines[i]).append("\n")
                lineCount++
            }
            bufferedReader.close()
            if (log.isEmpty()) "No log entries found" else "Last $lineCount lines of log:\n\n$log"
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }

    private fun getSessionLog(): String {
        return try {
            val packageName = fragment.requireContext().packageName
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val bufferedReader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val log = StringBuilder()
            var lineCount = 0
            bufferedReader.forEachLine { line ->
                if (line.contains(packageName, ignoreCase = true) || line.contains("FastMediaSorter", ignoreCase = true)) {
                    log.append(line).append("\n")
                    lineCount++
                }
            }
            bufferedReader.close()
            if (log.isEmpty()) "No log entries found for current session" else "Current session log ($lineCount lines):\n\n$log"
        } catch (e: Exception) {
            "Error reading log: ${e.message}"
        }
    }
}
