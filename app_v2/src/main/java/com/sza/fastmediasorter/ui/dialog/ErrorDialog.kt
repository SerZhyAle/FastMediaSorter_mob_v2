package com.sza.fastmediasorter.ui.dialog

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.sza.fastmediasorter.R

/**
 * Dialog for displaying detailed error messages
 * According to V2 Specification: Show detailed error with selectable/copyable text
 */
object ErrorDialog {

    /**
     * Show error dialog with detailed message
     * @param context Context
     * @param title Dialog title
     * @param message Error message
     * @param details Detailed error information (stack trace, etc.)
     */
    fun show(
        context: Context,
        title: String = context.getString(R.string.error),
        message: String,
        details: String? = null
    ) {
        val fullMessage = if (details != null) {
            "$message\n\nDetails:\n$details"
        } else {
            message
        }

        // Inflate custom view with scrollable text
        val dialogView = LayoutInflater.from(context).inflate(
            R.layout.dialog_log_view,
            null
        )
        val textView = dialogView.findViewById<TextView>(R.id.tvLogText)
        textView.text = fullMessage

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogView)
            .setNeutralButton(R.string.copy_to_clipboard) { _, _ ->
                copyToClipboard(context, fullMessage)
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    /**
     * Show error from Throwable
     */
    fun show(
        context: Context,
        title: String = context.getString(R.string.error),
        throwable: Throwable
    ) {
        val message = throwable.message ?: "Unknown error"
        val details = throwable.stackTraceToString()
        show(context, title, message, details)
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Error Details", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }
}
