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
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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
 * Dialog for displaying detailed error messages with collapsible technical details,
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
     */
    fun show(
        context: Context,
        title: String = context.getString(R.string.error),
        message: String,
        details: String? = null,
        actionButtonText: String? = null,
        onActionClick: (() -> Unit)? = null
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
        val btnSaveToFile = dialogView.findViewById<Button>(R.id.btnSaveToFile)

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

        btnSaveToFile.setOnClickListener {
            saveErrorToFile(context, fullText)
        }

        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogView)
            .setNegativeButton(R.string.close, null)
            .setNeutralButton(R.string.copy_to_clipboard) { _, _ ->
                copyToClipboard(context, fullText)
            }

        if (actionButtonText != null && onActionClick != null) {
            builder.setPositiveButton(actionButtonText) { dialog, _ ->
                dialog.dismiss()
                onActionClick()
            }
        } else {
            builder.setPositiveButton(R.string.error_dialog_share) { _, _ ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, fullText)
                }
                context.startActivity(Intent.createChooser(shareIntent, title))
            }
        }

        return try {
            builder.show()
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
