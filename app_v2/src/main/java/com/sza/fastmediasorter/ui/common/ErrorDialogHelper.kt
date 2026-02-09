package com.sza.fastmediasorter.ui.common

import android.content.Context
import com.sza.fastmediasorter.R

/**
 * Utility object for displaying error dialogs
 * Supports both simple and detailed error messages
 */
object ErrorDialogHelper {

    /**
     * Shows a simple error message with Toast-like appearance
     */
    fun showSimpleError(context: Context, message: String) {
        DialogUtils.showScrollableDialog(
            context,
            "Error",
            message,
            "OK"
        )
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
        // Compose full error text
        val fullText = buildString {
            append(message)
            if (errorDetails != null) {
                append("\n\n--- Details ---\n")
                append(errorDetails)
            }
        }
        
        DialogUtils.showScrollableDialog(
            context,
            title,
            fullText,
            "Close"
        )
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
}
