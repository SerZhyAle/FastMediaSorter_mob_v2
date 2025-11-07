package com.sza.fastmediasorter_v2.ui.common

import android.content.Context
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.sza.fastmediasorter_v2.R

/**
 * Utility object for displaying error dialogs
 * Supports both simple and detailed error messages
 */
object ErrorDialogHelper {

    /**
     * Shows a simple error message with Toast-like appearance
     */
    fun showSimpleError(context: Context, message: String) {
        AlertDialog.Builder(context)
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    /**
     * Shows a detailed error dialog with small selectable text
     * Use for debugging and detailed error information
     */
    fun showDetailedError(
        context: Context,
        title: String = "Error Details",
        message: String,
        errorDetails: String? = null
    ) {
        // Inflate custom view with small text
        val dialogView = android.view.LayoutInflater.from(context).inflate(
            R.layout.dialog_log_view,
            null
        )
        val textView = dialogView.findViewById<TextView>(R.id.tvLogText)
        
        // Compose full error text
        val fullText = buildString {
            append(message)
            if (errorDetails != null) {
                append("\n\n--- Details ---\n")
                append(errorDetails)
            }
        }
        
        textView.text = fullText
        
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Copy to Clipboard") { _, _ ->
                copyToClipboard(context, fullText)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Shows detailed error from Exception
     */
    fun showDetailedError(
        context: Context,
        title: String = "Error Details",
        exception: Throwable
    ) {
        val message = exception.message ?: "Unknown error"
        val stackTrace = exception.stackTraceToString()
        showDetailedError(context, title, message, stackTrace)
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Error details", text)
        clipboard.setPrimaryClip(clip)
        android.widget.Toast.makeText(context, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
    }
}
