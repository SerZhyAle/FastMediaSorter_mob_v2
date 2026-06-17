package com.sza.fastmediasorter.ui.dialog

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.logging.LogExportHelper
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.ui.common.support.SupportIntentFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single component for all scrollable read-only text dialogs (errors, logs, reports, system info).
 * Shows a title, a selectable text block on a distinct surface, an optional collapsible details
 * section, and one compact icon action row. Built on `dialog_error_detail` (portrait + landscape)
 * via [MaterialAlertDialogBuilder].
 *
 * Dismiss semantics (S0384 §6.1): only Close and the primary CTA dismiss the dialog; copy, share,
 * save and the extra action keep it open.
 *
 * Replaces the former `ErrorDialog`, `DialogUtils.showScrollableDialog` and `ErrorDialogHelper`.
 */
object ScrollableTextDialog {

    /** Optional caller-supplied icon action (e.g. "clear log"), rendered as an extra icon slot. */
    data class ExtraAction(
        @DrawableRes val icon: Int,
        val contentDescription: String,
        val dismissOnClick: Boolean,
        val onClick: () -> Unit,
    )

    /**
     * @param title    Dialog title.
     * @param message  Main selectable text.
     * @param details  Optional technical details behind a collapsible toggle.
     * @param monospace Render the message in a monospace typeface (logs).
     * @param actionButtonText Optional primary CTA label - when set, the primary slot is a labeled
     *        button (its arbitrary meaning cannot be conveyed by a bare icon) and dismisses on click.
     * @param onActionClick Callback paired with [actionButtonText].
     * @param inlineActionButtonText Optional inline action label (e.g. "copy full report") - uses a
     *        dedicated icon; when omitted the inline slot is the default Save-to-file action.
     * @param onInlineActionClick Callback paired with [inlineActionButtonText].
     * @param showShare Show the default Share icon in the primary slot (ignored when a CTA is set).
     * @param showCopy  Show the Copy-to-clipboard icon.
     * @param showSave  Show the Save-to-file icon (ignored when an inline action is set).
     * @param onSaveClick Optional override for the default Save-to-file action.
     * @param extraAction Optional extra icon action.
     * @param reportableThrowable When non-null, shows a crash-report action that emails the error
     *        text plus the app log ZIP to the author; pass the originating exception only for real
     *        failures, not informational "unavailable" messages.
     */
    fun show(
        context: Context,
        title: String = context.getString(R.string.error),
        message: String,
        details: String? = null,
        monospace: Boolean = false,
        actionButtonText: String? = null,
        onActionClick: (() -> Unit)? = null,
        inlineActionButtonText: String? = null,
        onInlineActionClick: (() -> Unit)? = null,
        showShare: Boolean = true,
        showCopy: Boolean = true,
        showSave: Boolean = true,
        onSaveClick: (() -> Unit)? = null,
        extraAction: ExtraAction? = null,
        reportableThrowable: Throwable? = null,
        cancelable: Boolean = true,
    ): AlertDialog? {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            Timber.w("ScrollableTextDialog: skipping show - Activity is finishing/destroyed")
            return null
        }
        val fullText = if (details != null) "$message\n\n$details" else message

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_error_detail, null)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvErrorMessage)
        val tvDetailsToggle = dialogView.findViewById<TextView>(R.id.tvDetailsToggle)
        val scrollDetails = dialogView.findViewById<ScrollView>(R.id.scrollDetails)
        val tvDetails = dialogView.findViewById<TextView>(R.id.tvErrorDetails)
        val btnPrimaryCta = dialogView.findViewById<MaterialButton>(R.id.btnPrimaryCta)
        val btnPrimary = dialogView.findViewById<MaterialButton>(R.id.btnPrimary)
        val btnInlineAction = dialogView.findViewById<MaterialButton>(R.id.btnInlineAction)
        val btnExtra = dialogView.findViewById<MaterialButton>(R.id.btnExtra)
        val btnCopy = dialogView.findViewById<MaterialButton>(R.id.btnCopy)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnClose)
        val btnReport = dialogView.findViewById<MaterialButton>(R.id.btnReport)

        tvMessage.text = message
        if (monospace) tvMessage.typeface = Typeface.MONOSPACE

        if (details != null) {
            tvDetails.text = details
            tvDetailsToggle.visibility = View.VISIBLE
            tvDetailsToggle.setOnClickListener {
                if (scrollDetails.visibility == View.VISIBLE) {
                    scrollDetails.visibility = View.GONE
                    tvDetailsToggle.text = context.getString(R.string.error_dialog_show_details)
                } else {
                    scrollDetails.visibility = View.VISIBLE
                    tvDetailsToggle.text = context.getString(R.string.error_dialog_hide_details)
                }
            }
        } else {
            tvDetailsToggle.visibility = View.GONE
            scrollDetails.visibility = View.GONE
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(dialogView)
            .setCancelable(cancelable)
            .create()

        // Close - the only standard action that dismisses the dialog.
        btnClose.contentDescription = context.getString(R.string.close)
        TooltipCompat.setTooltipText(btnClose, context.getString(R.string.close))
        btnClose.setOnClickListener { dialog.dismiss() }

        // Copy to clipboard - keeps the dialog open so the user can chain actions.
        if (showCopy) {
            btnCopy.visibility = View.VISIBLE
            btnCopy.contentDescription = context.getString(R.string.copy_to_clipboard)
            TooltipCompat.setTooltipText(btnCopy, context.getString(R.string.copy_to_clipboard))
            btnCopy.setOnClickListener { copyToClipboard(context, fullText) }
        } else {
            btnCopy.visibility = View.GONE
        }

        // Primary slot: a caller-supplied CTA keeps its label (its meaning cannot be conveyed by a
        // bare icon and it dismisses); otherwise the default Share action renders icon-only.
        if (actionButtonText != null && onActionClick != null) {
            btnPrimary.visibility = View.GONE
            btnPrimaryCta.visibility = View.VISIBLE
            btnPrimaryCta.text = actionButtonText
            btnPrimaryCta.contentDescription = actionButtonText
            btnPrimaryCta.setOnClickListener {
                dialog.dismiss()
                onActionClick()
            }
        } else if (showShare) {
            btnPrimaryCta.visibility = View.GONE
            btnPrimary.visibility = View.VISIBLE
            btnPrimary.contentDescription = context.getString(R.string.error_dialog_share)
            TooltipCompat.setTooltipText(btnPrimary, context.getString(R.string.error_dialog_share))
            btnPrimary.setOnClickListener {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, fullText)
                }
                context.startActivity(Intent.createChooser(shareIntent, title))
            }
        } else {
            btnPrimaryCta.visibility = View.GONE
            btnPrimary.visibility = View.GONE
        }

        // Inline slot: a caller-supplied action (e.g. copy full report) uses a dedicated icon;
        // otherwise the default save-to-file action keeps its layout icon.
        if (inlineActionButtonText != null && onInlineActionClick != null) {
            btnInlineAction.visibility = View.VISIBLE
            btnInlineAction.setIconResource(R.drawable.ic_copy_full_report)
            btnInlineAction.contentDescription = inlineActionButtonText
            TooltipCompat.setTooltipText(btnInlineAction, inlineActionButtonText)
            btnInlineAction.setOnClickListener { onInlineActionClick() }
        } else if (showSave) {
            btnInlineAction.visibility = View.VISIBLE
            btnInlineAction.contentDescription = context.getString(R.string.error_dialog_save_to_file)
            TooltipCompat.setTooltipText(btnInlineAction, context.getString(R.string.error_dialog_save_to_file))
            btnInlineAction.setOnClickListener {
                if (onSaveClick != null) {
                    onSaveClick()
                } else {
                    saveErrorToFile(context, fullText)
                }
            }
        } else {
            btnInlineAction.visibility = View.GONE
        }

        // Optional extra action (e.g. clear log).
        if (extraAction != null) {
            btnExtra.visibility = View.VISIBLE
            btnExtra.setIconResource(extraAction.icon)
            btnExtra.contentDescription = extraAction.contentDescription
            TooltipCompat.setTooltipText(btnExtra, extraAction.contentDescription)
            btnExtra.setOnClickListener {
                if (extraAction.dismissOnClick) dialog.dismiss()
                extraAction.onClick()
            }
        } else {
            btnExtra.visibility = View.GONE
        }

        // Crash report (S0483): only when the caller supplies the originating exception (a real
        // failure, not an informational message). Keeps the dialog open like the other icon actions.
        if (reportableThrowable != null) {
            btnReport.visibility = View.VISIBLE
            val reportLabel = context.getString(R.string.error_dialog_report_to_author)
            btnReport.contentDescription = reportLabel
            TooltipCompat.setTooltipText(btnReport, reportLabel)
            btnReport.setOnClickListener {
                Timber.d("S0483: crash-report button tapped; building email + log ZIP")
                val body = buildString {
                    appendLine(context.getString(R.string.crash_report_email_body_intro))
                    appendLine()
                    appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("${reportableThrowable.javaClass.name}: ${reportableThrowable.message}")
                    appendLine()
                    append(fullText)
                }
                val subject = context.getString(R.string.crash_report_email_subject)
                CoroutineScope(Dispatchers.IO).launch {
                    val zipUri = LogExportHelper.buildLogsZipUri(context)
                    withContext(Dispatchers.Main) {
                        val emailIntent = SupportIntentFactory.buildCrashReportEmail(subject, body, zipUri)
                        val chooser = Intent.createChooser(emailIntent, subject)
                        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(chooser)
                        } catch (e: ActivityNotFoundException) {
                            Timber.w(e, "ScrollableTextDialog: no email app to send crash report")
                            Toast.makeText(context, R.string.export_logs_no_share_target, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } else {
            btnReport.visibility = View.GONE
        }

        return try {
            dialog.show()
            // Resize to 90% of screen width (parity with the former DialogUtils path).
            val width = (context.resources.displayMetrics.widthPixels * 0.9).toInt()
            dialog.window?.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
            DialogAccessibilityHelper.applyInitialFocus(dialog)
            dialog
        } catch (e: WindowManager.BadTokenException) {
            Timber.e(e, "ScrollableTextDialog: show failed - bad window token")
            null
        }
    }

    /** Convenience overload: shows a dialog from a [Throwable] without exposing raw stack traces. */
    fun show(
        context: Context,
        title: String = context.getString(R.string.error),
        throwable: Throwable,
    ): AlertDialog? {
        val message = throwable.message ?: context.getString(R.string.error_reason_unknown)
        return show(context = context, title = title, message = message)
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Dialog text", text))
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun saveErrorToFile(context: Context, text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "fms_text_$timestamp.txt"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { stream ->
                            stream.write(text.toByteArray(Charsets.UTF_8))
                        }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloadsDir.mkdirs()
                    File(downloadsDir, fileName).writeText(text, Charsets.UTF_8)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_saved_to_downloads, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "ScrollableTextDialog: saveToFile failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_log_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
