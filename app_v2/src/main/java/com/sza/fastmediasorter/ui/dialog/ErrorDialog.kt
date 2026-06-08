package com.sza.fastmediasorter.ui.dialog

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.button.MaterialButton
import com.sza.fastmediasorter.R
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
 * Dialog for displaying detailed text content with optional collapsible technical details,
 * Save-to-file, Share, and Copy actions.
 *
 * Layout: dialog_error_detail (portrait + landscape).
 * Public API is backwards-compatible with previous signature.
 */
object ErrorDialog {

    /**
     * Show error dialog with detailed message.
     *
     * @param context   Context (Activity preferred - required for window token safety)
     * @param title     Dialog title
     * @param message   User-readable error message shown in the main scrollable text area
     * @param details   Technical details / stack trace; null hides the collapsible section
     * @param actionButtonText Optional positive button label - when provided, replaces the Share button
     * @param onActionClick    Callback paired with [actionButtonText]
     * @param inlineActionButtonText Optional inline button label shown inside the dialog body.
     *        When omitted, the inline button keeps the default "Save to file" behaviour.
     * @param onInlineActionClick Callback paired with [inlineActionButtonText]
     */
    fun show(
        context: Context,
        title: String = context.getString(R.string.error),
        message: String,
        details: String? = null,
        actionButtonText: String? = null,
        onActionClick: (() -> Unit)? = null,
        inlineActionButtonText: String? = null,
        onInlineActionClick: (() -> Unit)? = null
    ): AlertDialog? {
        if (context is Activity && (context.isFinishing || context.isDestroyed)) {
            Timber.w("ErrorDialog: skipping show - Activity is finishing/destroyed")
            return null
        }

        val fullText = if (details != null) "$message\n\n$details" else message

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_error_detail, null)
        val tvErrorMessage = dialogView.findViewById<TextView>(R.id.tvErrorMessage)
        val tvDetailsToggle = dialogView.findViewById<TextView>(R.id.tvDetailsToggle)
        val scrollDetails = dialogView.findViewById<ScrollView>(R.id.scrollDetails)
        val tvErrorDetails = dialogView.findViewById<TextView>(R.id.tvErrorDetails)
        val btnPrimaryCta = dialogView.findViewById<MaterialButton>(R.id.btnPrimaryCta)
        val btnPrimary = dialogView.findViewById<MaterialButton>(R.id.btnPrimary)
        val btnInlineAction = dialogView.findViewById<MaterialButton>(R.id.btnInlineAction)
        val btnCopy = dialogView.findViewById<MaterialButton>(R.id.btnCopy)
        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnClose)

        tvErrorMessage.text = message

        if (details != null) {
            tvErrorDetails.text = details
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

        // All actions live in a single compact icon row (no AlertDialog button panel); the
        // dialog reference is needed by the close/CTA handlers, so create it before wiring.
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogView)
            .create()

        // Close - the only standard action that dismisses the dialog.
        btnClose.contentDescription = context.getString(R.string.close)
        TooltipCompat.setTooltipText(btnClose, context.getString(R.string.close))
        btnClose.setOnClickListener { dialog.dismiss() }

        // Copy to clipboard - keep the dialog open so the user can chain actions.
        btnCopy.contentDescription = context.getString(R.string.copy_to_clipboard)
        TooltipCompat.setTooltipText(btnCopy, context.getString(R.string.copy_to_clipboard))
        btnCopy.setOnClickListener { copyToClipboard(context, fullText) }

        // Primary slot: a caller-supplied CTA keeps its label (its meaning cannot be conveyed by a
        // bare icon); otherwise the default Share action renders icon-only.
        if (actionButtonText != null && onActionClick != null) {
            btnPrimary.visibility = View.GONE
            btnPrimaryCta.visibility = View.VISIBLE
            btnPrimaryCta.text = actionButtonText
            btnPrimaryCta.contentDescription = actionButtonText
            btnPrimaryCta.setOnClickListener {
                dialog.dismiss()
                onActionClick()
            }
        } else {
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
        }

        // Inline slot: a caller-supplied action (e.g. copy full report) uses a dedicated icon;
        // otherwise the default save-to-file action keeps its layout icon.
        if (inlineActionButtonText != null && onInlineActionClick != null) {
            btnInlineAction.setIconResource(R.drawable.ic_copy_full_report)
            btnInlineAction.contentDescription = inlineActionButtonText
            TooltipCompat.setTooltipText(btnInlineAction, inlineActionButtonText)
            btnInlineAction.setOnClickListener { onInlineActionClick() }
        } else {
            btnInlineAction.contentDescription = context.getString(R.string.error_dialog_save_to_file)
            TooltipCompat.setTooltipText(btnInlineAction, context.getString(R.string.error_dialog_save_to_file))
            btnInlineAction.setOnClickListener { saveErrorToFile(context, fullText) }
        }

        return try {
            dialog.show()
            dialog
        } catch (e: WindowManager.BadTokenException) {
            Timber.e(e, "ErrorDialog: show failed - bad window token")
            null
        }
    }

    /**
     * Convenience overload: shows dialog from a [Throwable] without exposing raw stack traces.
     */
    fun show(
        context: Context,
        title: String = context.getString(R.string.error),
        throwable: Throwable
    ) {
        val message = throwable.message ?: context.getString(R.string.error_reason_unknown)
        show(context, title, message)
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Error Details", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun saveErrorToFile(context: Context, text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "fms_error_$timestamp.txt"

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // API 29+: insert via MediaStore - no WRITE_EXTERNAL_STORAGE needed
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
                    // API 26–28: direct file write; WRITE_EXTERNAL_STORAGE declared with maxSdkVersion="28"
                    @Suppress("DEPRECATION")
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    downloadsDir.mkdirs()
                    File(downloadsDir, fileName).writeText(text, Charsets.UTF_8)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_saved_to_downloads, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "ErrorDialog: saveErrorToFile failed")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, R.string.error_log_save_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
