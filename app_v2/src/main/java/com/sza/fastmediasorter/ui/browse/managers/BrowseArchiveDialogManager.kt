package com.sza.fastmediasorter.ui.browse.managers

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.usecase.FileOperationProgress
import com.sza.fastmediasorter.ui.browse.BrowseEvent
import com.sza.fastmediasorter.ui.dialog.FileOperationProgressDialog
import com.sza.fastmediasorter.util.showBoundToHost
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages archive/extraction dialog presentation for BrowseActivity.
 *
 * Owns [archiveProgressDialog] and [extractProgressDialog] lifecycle.
 *
 * Extracted from BrowseActivity (Wave 1.5 decomposition - IV.1).
 */
class BrowseArchiveDialogManager(
    private val context: Context,
    private val onArchiveRequested: (archiveName: String, destinationPath: String) -> Unit,
    private val onCancelArchive: () -> Unit,
    private val onExtractArchive: (MediaFile) -> Unit,
    private val onExtractArchiveWithPassword: (MediaFile, CharArray) -> Unit,
    private val onCancelExtraction: () -> Unit,
    private val onNavigateToFolder: (String) -> Unit
) {
    private var archiveProgressDialog: androidx.appcompat.app.AlertDialog? = null
    private var extractProgressDialog: FileOperationProgressDialog? = null

    fun showArchiveConfigurationDialog(
        currentDir: String,
        selectedFiles: Set<String>,
        mediaFiles: List<MediaFile>
    ) {
        if (selectedFiles.isEmpty()) return

        val firstName = mediaFiles
            .firstOrNull { it.path in selectedFiles }?.name
            ?: SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        val defaultName = firstName.substringBeforeLast('.')

        val dp16 = (16 * context.resources.displayMetrics.density).toInt()
        val dp64 = dp16 * 4

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp64, dp16, dp64, dp16)
        }

        val etName = EditText(context).apply {
            setText(defaultName)
            hint = context.getString(R.string.archive_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT
            selectAll()
        }
        root.addView(etName)

        val tvDest = TextView(context).apply {
            text = context.getString(R.string.archive_destination_label, currentDir)
            setPadding(0, dp16 / 2, 0, 0)
            textSize = 13f
        }
        root.addView(tvDest)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.archive_dialog_title)
            .setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.archive_action_btn, null)
            .showBoundToHost(context)

        dialog?.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val name = etName.text.toString().trim()

            if (name.isEmpty()) {
                etName.error = context.getString(R.string.archive_name_empty_error)
                return@setOnClickListener
            }
            val invalidChars = Regex("""[/\\:*?"<>|]""")
            if (invalidChars.containsMatchIn(name)) {
                etName.error = context.getString(R.string.archive_name_invalid_chars_error)
                return@setOnClickListener
            }

            dialog?.dismiss()
            showArchiveProgressDialog()
            onArchiveRequested(name, currentDir)
        }
    }

    fun showArchiveProgressDialog() {
        dismissArchiveProgressDialog()
        archiveProgressDialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.archive_progress_title)
            .setMessage(context.getString(R.string.archive_progress_message, 0, 0, ""))
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                onCancelArchive()
            }
            .setCancelable(false)
            .showBoundToHost(context)
    }

    fun dismissArchiveProgressDialog() {
        archiveProgressDialog?.dismiss()
        archiveProgressDialog = null
    }

    fun updateArchiveProgress(current: Int, total: Int, fileName: String) {
        archiveProgressDialog?.setMessage(
            context.getString(R.string.archive_progress_message, current, total, fileName)
        )
    }

    fun showUnarchiveConfirmDialog(mediaFile: MediaFile, targetDirName: String) {
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.unarchive_dialog_title)
            .setMessage(context.getString(R.string.unarchive_dialog_message, mediaFile.name, targetDirName))
            .setPositiveButton(R.string.unarchive_action_extract) { _, _ ->
                showExtractProgressDialog()
                onExtractArchive(mediaFile)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundToHost(context)
    }

    fun showArchivePasswordDialog(mediaFile: MediaFile, targetDirName: String) {
        val dp16 = (16 * context.resources.displayMetrics.density).toInt()
        val dp64 = dp16 * 4

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp64, dp16, dp64, dp16)
        }

        root.addView(TextView(context).apply {
            text = context.getString(R.string.protected_archive_password_message, mediaFile.name)
            textSize = 14f
        })

        val passwordInput = EditText(context).apply {
            hint = context.getString(R.string.protected_archive_password_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        root.addView(passwordInput)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.protected_archive_password_title)
            .setView(root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundToHost(context)

        dialog?.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            val passwordText = passwordInput.text?.toString().orEmpty()
            if (passwordText.isEmpty()) {
                passwordInput.error = context.getString(R.string.protected_archive_password_empty)
                return@setOnClickListener
            }

            dialog?.dismiss()
            showExtractProgressDialog()
            onExtractArchiveWithPassword(mediaFile, passwordText.toCharArray())
        }
    }

    fun showExtractProgressDialog() {
        dismissExtractProgressDialog()
        extractProgressDialog = FileOperationProgressDialog.show(
            context = context,
            operationType = context.getString(R.string.unarchive_progress_title),
            onCancel = { onCancelExtraction() }
        )
    }

    fun updateExtractProgress(event: BrowseEvent.ExtractionProgress) {
        val total = event.total.coerceAtLeast(1)
        val currentIndex = (event.done - 1).coerceIn(0, total - 1)
        extractProgressDialog?.updateProgress(
            FileOperationProgress.Processing(
                currentFile = context.getString(R.string.unarchive_progress_entry, event.entryName) + " (${event.percent}%)",
                currentIndex = currentIndex,
                totalFiles = total,
                bytesTransferred = 0L,
                totalBytes = 0L,
                speedBytesPerSecond = 0L
            )
        )
    }

    fun dismissExtractProgressDialog() {
        extractProgressDialog?.dismiss()
        extractProgressDialog = null
    }

    fun onArchiveSuccess(archivePath: String, archivedCount: Int) {
        dismissArchiveProgressDialog()
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.archive_success, archivePath, archivedCount),
            android.widget.Toast.LENGTH_LONG
        ).show()
    }

    fun onArchiveError(message: String) {
        dismissArchiveProgressDialog()
    }

    fun onExtractionSuccess(targetPath: String) {
        dismissExtractProgressDialog()
        com.google.android.material.snackbar.Snackbar
            .make(
                (context as android.app.Activity).findViewById(android.R.id.content),
                context.getString(R.string.unarchive_success),
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            )
            .setAction(context.getString(R.string.action_open)) {
                onNavigateToFolder(targetPath)
            }
            .show()
    }

    fun onExtractionFailed(message: String) {
        dismissExtractProgressDialog()
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
    }
}
