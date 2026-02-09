package com.sza.fastmediasorter.ui.dialog

import android.app.AlertDialog
import android.content.Context
import com.sza.fastmediasorter.R

/**
 * Simple utility for showing context-sensitive tooltip dialogs.
 * Used to explain complex settings and features to users.
 */
object TooltipDialog {
    
    /**
     * Show a tooltip dialog with title and message
     * @param context Android context
     * @param title Dialog title (typically the setting name)
     * @param message Explanation text
     */
    fun show(context: Context, title: String, message: String) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }
    
    /**
     * Show a tooltip dialog using string resource IDs
     * @param context Android context
     * @param titleResId Title string resource ID
     * @param messageResId Message string resource ID
     */
    fun show(context: Context, titleResId: Int, messageResId: Int) {
        show(
            context,
            context.getString(titleResId),
            context.getString(messageResId)
        )
    }
}
