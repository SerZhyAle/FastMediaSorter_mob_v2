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
            context.getString(R.string.error),
            message,
            context.getString(R.string.close)
        )
    }

    /**
     * Shows a detailed error dialog with small selectable text
     * Use for debugging and detailed error information
     */
    fun showDetailedError(
        context: Context,
        title: String? = null,
        message: String,
        errorDetails: String? = null
    ) {
        val resolvedTitle = title ?: context.getString(R.string.error)

        // Compose full error text
        val fullText = buildString {
            append(message)
            if (errorDetails != null) {
                append("\n\n")
                append(context.getString(R.string.show_details))
                append("\n")
                append(errorDetails)
            }
        }
        
        DialogUtils.showScrollableDialog(
            context,
            resolvedTitle,
            fullText,
            context.getString(R.string.close)
        )
    }

    /**
     * Shows detailed error from Exception
     */
    fun showDetailedError(
        context: Context,
        title: String? = null,
        exception: Throwable
    ) {
        val message = exception.message ?: context.getString(R.string.error_reason_unknown)
        showDetailedError(context, title, message)
    }
}
