package com.sza.fastmediasorter.ui.addresource

import android.app.Activity
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.util.showBoundToHost

/** Matches the name column the resource list draws; longer names are truncated on every screen. */
private const val MAX_WATCH_NAME_LENGTH = 80

/**
 * S1861: asks for the one thing a watch resource needs - its name - and hands it to the ViewModel.
 *
 * A watch has no address, no port and no credentials, so this is the whole wizard; the fields the
 * other resource types fill are not hidden here, they do not exist.
 */
internal class AddResourceWatchPromptManager(
    private val activity: Activity,
    private val viewModel: AddResourceViewModel
) {

    fun promptForName() {
        val editText = EditText(activity).apply {
            setText(R.string.resource_type_wear_watch)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
            contentDescription = activity.getString(R.string.paired_watch_name_dialog_title)
            val padPx = (DIALOG_INPUT_PADDING_DP * activity.resources.displayMetrics.density).toInt()
            setPadding(padPx, padPx, padPx, padPx)
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.paired_watch_name_dialog_title)
            // Wired below rather than here so an empty name keeps the dialog open instead of
            // silently creating a resource the list cannot label.
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setView(editText)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val input = editText.text?.toString()?.trim().orEmpty()
                if (input.isBlank() || input.length > MAX_WATCH_NAME_LENGTH) {
                    editText.error = activity.getString(R.string.paired_watch_name_dialog_title)
                } else {
                    viewModel.addPairedWatchResource(input)
                    dialog.dismiss()
                }
            }
        }
        dialog.showBoundToHost(activity)
    }

    private companion object {
        const val DIALOG_INPUT_PADDING_DP = 12
    }
}
