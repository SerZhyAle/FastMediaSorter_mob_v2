package com.sza.fastmediasorter.ui.settings.helpers

import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.data.debug.ReleasedTicketsDataSource
import com.sza.fastmediasorter.databinding.FragmentSettingsGeneralBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase
import com.sza.fastmediasorter.domain.usecase.SaveTextFileToResourceUseCase
import com.sza.fastmediasorter.ui.dialog.DestinationPickerDialog
import com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeneralSettingsLogHelper(
    private val binding: FragmentSettingsGeneralBinding,
    private val fragment: Fragment,
    private val saveLogsLauncher: ActivityResultLauncher<String>,
    private val getDestinationsUseCase: GetDestinationsUseCase,
    private val saveTextFileToResourceUseCase: SaveTextFileToResourceUseCase,
) {
    fun setupButtons() {
        binding.btnShowLog.setOnClickListener { showLogDialog(fullLog = true) }
        binding.btnShowSessionLog.setOnClickListener { showLogDialog(fullLog = false) }
        binding.btnShareLogs?.setOnClickListener { shareLogs() }
        binding.btnSaveLogs?.setOnClickListener { launchSaveLogs() }
        // S1783: the button lives in the debug-tools group, which GeneralSettingsViewSetupHelper
        // already hides outside a debug build - so this only wires the click.
        binding.btnReleasedTickets?.setOnClickListener { showReleasedTicketsDialog() }
    }

    private fun showReleasedTicketsDialog() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val tickets = ReleasedTicketsDataSource.read(fragment.requireContext())
            if (!fragment.isAdded || fragment.view == null) return@launch
            val message = if (tickets.isEmpty()) {
                fragment.getString(R.string.released_tickets_empty)
            } else {
                tickets.joinToString(separator = "\n") { "${it.id}  ${it.slug}  ${it.status}" }
            }
            ScrollableTextDialog.show(
                context = fragment.requireContext(),
                title = fragment.getString(R.string.released_tickets_title),
                message = message,
                monospace = true,
            )
        }
    }

    fun shareLogs() {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val result = LogExportHelper.exportLogs(fragment.requireActivity())
            if (!fragment.isAdded || fragment.view == null) return@launch
            when (result) {
                is LogExportHelper.ExportResult.NoLogs ->
                    Toast.makeText(fragment.requireContext(), R.string.export_logs_no_files, Toast.LENGTH_SHORT).show()
                is LogExportHelper.ExportResult.Error ->
                    Toast.makeText(fragment.requireContext(), result.message, Toast.LENGTH_LONG).show()
                else -> {}
            }
        }
    }

    fun launchSaveLogs() {
        // Guard: check upfront whether any activity can handle ACTION_CREATE_DOCUMENT on this device.
        // Some custom/minimal AOSP firmware (e.g. SPRD) ships without a document picker, which would
        // cause ActivityNotFoundException if we launch the launcher directly. Using resolveActivity
        // avoids the exception being used as flow-control and keeps the log free of spurious stacks.
        val testIntent = android.content.Intent(android.content.Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        val canSave = testIntent.resolveActivity(fragment.requireActivity().packageManager) != null
        if (canSave) {
            saveLogsLauncher.launch("fastmediasorter_logs.zip")
        } else {
            Timber.d("LogExport: CREATE_DOCUMENT not supported on this device - using share fallback")
            showSaveLogsNotSupportedDialog()
        }
    }

    fun showLogDialog(fullLog: Boolean) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val logText = withContext(Dispatchers.IO) {
                if (fullLog) getFullLog() else getSessionLog()
            }
            if (!fragment.isAdded || fragment.view == null) return@launch
            ScrollableTextDialog.show(
                context = fragment.requireContext(),
                title = if (fullLog) {
                    fragment.getString(
                        R.string.settings_application_log_title
                    )
                } else {
                    fragment.getString(R.string.show_current_session_log)
                },
                message = logText,
                monospace = true,
                onSaveClick = { showSaveLogToResourceDialog(fullLog = fullLog, logText = logText) }
            )
        }
    }

    private fun showSaveLogToResourceDialog(fullLog: Boolean, logText: String) {
        DestinationPickerDialog(
            context = fragment.requireContext(),
            lifecycleOwner = fragment.viewLifecycleOwner,
            getDestinationsUseCase = getDestinationsUseCase,
            currentSelection = null,
            title = fragment.getString(R.string.settings_log_save_destination_title),
            allowClear = false,
            onResourceSelected = { resource ->
                resource ?: return@DestinationPickerDialog
                saveLogToResource(resource, fullLog, logText)
            }
        ).show()
    }

    private fun saveLogToResource(resource: MediaResource, fullLog: Boolean, logText: String) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                saveTextFileToResourceUseCase(
                    resource = resource,
                    fileName = buildLogFileName(fullLog),
                    content = logText,
                )
            }
            if (!fragment.isAdded || fragment.view == null) return@launch
            result.fold(
                onSuccess = {
                    Toast.makeText(
                        fragment.requireContext(),
                        fragment.getString(R.string.settings_log_saved_to_resource, resource.name),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                onFailure = { error ->
                    Timber.e(error, "GeneralSettingsLogHelper: failed to save log to resource")
                    Toast.makeText(
                        fragment.requireContext(),
                        R.string.settings_log_save_failed,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun showSaveLogsNotSupportedDialog() {
        MaterialAlertDialogBuilder(fragment.requireContext())
            .setTitle(R.string.save_debug_logs)
            .setMessage(R.string.save_logs_not_supported)
            .setPositiveButton(R.string.share) { _, _ -> shareLogs() }
            .setNegativeButton(R.string.cancel, null)
            .showBoundTo(fragment)
    }

    private fun getFullLog(): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val lines = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream)).use {
                readTailLines(it, FULL_LOG_TAIL_LINES)
            }
            if (lines.isEmpty()) {
                fragment.getString(R.string.settings_log_empty)
            } else {
                val log = lines.joinToString(separator = "\n", postfix = "\n")
                fragment.getString(R.string.settings_log_last_lines, lines.size, log)
            }
        } catch (e: Exception) {
            fragment.getString(R.string.settings_log_read_failed)
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
            if (log.isEmpty()) {
                fragment.getString(R.string.settings_log_empty_session)
            } else {
                fragment.getString(R.string.settings_log_current_session_body, lineCount, log.toString())
            }
        } catch (e: Exception) {
            fragment.getString(R.string.settings_log_read_failed)
        }
    }

    private fun buildLogFileName(fullLog: Boolean): String {
        val stamp = SimpleDateFormat("yy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        val prefix = if (fullLog) "app_log" else "session_log"
        return "${prefix}_$stamp.txt"
    }
}

private const val FULL_LOG_TAIL_LINES = 512

/** Keeps only the last [maxLines] lines while streaming, so a large logcat dump is never held whole. */
internal fun readTailLines(reader: java.io.BufferedReader, maxLines: Int): List<String> {
    val tail = ArrayDeque<String>(maxLines)
    reader.forEachLine { line ->
        if (tail.size == maxLines) tail.removeFirst()
        tail.addLast(line)
    }
    return tail.toList()
}
