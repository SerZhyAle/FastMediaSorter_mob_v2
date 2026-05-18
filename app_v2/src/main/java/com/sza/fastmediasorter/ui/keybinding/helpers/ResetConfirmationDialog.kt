package com.sza.fastmediasorter.ui.keybinding.helpers

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.input.CommandGroup

/**
 * Thin [AlertDialog.Builder] wrapper for destructive reset confirmations.
 * Caller supplies the [onConfirm] action - only invoked on the positive button.
 */
object ResetConfirmationDialog {

    fun showForGroup(context: Context, group: CommandGroup, groupName: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.reset_group_title))
            .setMessage(context.getString(R.string.reset_group_body_fmt, groupName))
            .setPositiveButton(R.string.reset_confirm_button) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.reset_cancel_button, null)
            .show()
    }

    fun showForAll(context: Context, onConfirm: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.reset_all_title))
            .setMessage(context.getString(R.string.reset_all_body))
            .setPositiveButton(R.string.reset_confirm_button) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.reset_cancel_button, null)
            .show()
    }
}
