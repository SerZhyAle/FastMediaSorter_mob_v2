package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import timber.log.Timber

/**
 * S0189: dialog that asks the user to confirm (or rename) a text note filename before saving.
 *
 * Pre-fills with [defaultName]. The user can change name and extension freely.
 * OK is disabled while the trimmed name is empty or contains a forbidden path character.
 */
object TextNoteSaveDialog {

    private val FORBIDDEN_CHARS = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')

    /**
     * Shows the save-name dialog.
     *
     * @param context     Activity context (must not be finishing or destroyed).
     * @param defaultName Pre-filled filename.
     * @param onConfirm   Called with the trimmed, validated name the user entered.
     * @param onCancel    Called when the dialog is dismissed without confirming (optional).
     */
    fun show(
        context: Context,
        defaultName: String,
        onConfirm: (chosenName: String) -> Unit,
        onCancel: () -> Unit = {}
    ) {
        Timber.d("S0189: TextNoteSaveDialog.show default=$defaultName")

        val dp16 = (16 * context.resources.displayMetrics.density).toInt()
        val dp24 = (24 * context.resources.displayMetrics.density).toInt()

        val tilWrapper = TextInputLayout(
            context, null,
            com.google.android.material.R.attr.textInputOutlinedStyle
        ).apply {
            hint = context.getString(R.string.text_note_save_dialog_hint)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(dp24, dp16, dp24, dp16)
        }

        val inputEdit = TextInputEditText(tilWrapper.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(defaultName)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        tilWrapper.addView(inputEdit)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.text_note_save_dialog_title)
            .setView(tilWrapper)
            .setPositiveButton(R.string.text_note_save_dialog_ok) { _, _ ->
                val chosen = inputEdit.text.toString().trim()
                if (chosen.isNotEmpty() && chosen.none { it in FORBIDDEN_CHARS }) {
                    onConfirm(chosen)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
            .setOnCancelListener { onCancel() }
            .create()

        dialog.show()

        // Disable OK button on invalid input; enable when valid
        val positiveButton = dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
        val validate = {
            val text = inputEdit.text?.toString()?.trim() ?: ""
            val valid = text.isNotEmpty() && text.none { it in FORBIDDEN_CHARS }
            positiveButton.isEnabled = valid
        }

        // Initial validation (pre-filled name may already be valid)
        validate()

        inputEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = validate()
        })

        // Place cursor at end of pre-filled text
        inputEdit.setSelection(inputEdit.text?.length ?: 0)
    }
}
