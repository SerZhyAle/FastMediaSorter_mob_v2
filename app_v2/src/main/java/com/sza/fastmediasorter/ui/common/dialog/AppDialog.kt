package com.sza.fastmediasorter.ui.common.dialog

import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.DialogAccessibilityHelper
import com.sza.fastmediasorter.databinding.DialogMaterialProgressHorizontalBinding
import com.sza.fastmediasorter.databinding.DialogMaterialProgressSpinnerBinding
import com.sza.fastmediasorter.ui.dialog.DialogKeyboardDelegate
import com.sza.fastmediasorter.util.showBoundTo

/**
 * Unified dialog factory (S3242): every floating dialog in the app goes through one of these
 * methods instead of hand-wiring `MaterialAlertDialogBuilder`, the keyboard contract, TalkBack
 * initial focus, insets and window sizing per call site.
 *
 * Every returned [AlertDialog] is already shown, bound to [owner]'s lifecycle via [showBoundTo],
 * and wired with [DialogKeyboardDelegate], [DialogAccessibilityHelper.applyInitialFocus],
 * [applyDialogInsets] and [DialogWindowSizer.applyTo].
 */
object AppDialog {

    /** Input dialog field padding, matching the S0189 `TextNoteSaveDialog` convention. */
    private const val INPUT_PADDING_HORIZONTAL_DP = 24
    private const val INPUT_PADDING_VERTICAL_DP = 16

    /** Confirm/cancel dialog using the standard (non-destructive) button pair. */
    fun confirm(
        owner: LifecycleOwner,
        context: Context,
        title: CharSequence,
        message: CharSequence,
        confirmLabel: CharSequence,
        onConfirm: () -> Unit,
    ): AlertDialog {
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(confirmLabel) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .create()
        return wireAndShow(dialog, owner, onConfirm)
    }

    /** Confirm/cancel dialog using the destructive (red confirm) button pair. */
    fun destructive(
        owner: LifecycleOwner,
        context: Context,
        title: CharSequence,
        message: CharSequence,
        confirmLabel: CharSequence,
        onConfirm: () -> Unit,
    ): AlertDialog {
        val themedContext = androidx.appcompat.view.ContextThemeWrapper(
            context,
            R.style.ThemeOverlay_FastMediaSorter_MaterialAlertDialog_Destructive,
        )
        val dialog = MaterialAlertDialogBuilder(themedContext)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(confirmLabel) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.cancel, null)
            .create()
        return wireAndShow(dialog, owner, onConfirm)
    }

    /** Single-line text input dialog; confirm stays disabled while [validate] rejects the text. */
    fun input(
        owner: LifecycleOwner,
        context: Context,
        title: CharSequence,
        hint: CharSequence,
        initial: String,
        validate: (String) -> Boolean,
        onAccept: (String) -> Unit,
    ): AlertDialog {
        val density = context.resources.displayMetrics.density
        val paddingHorizontal = (INPUT_PADDING_HORIZONTAL_DP * density).toInt()
        val paddingVertical = (INPUT_PADDING_VERTICAL_DP * density).toInt()
        val inputLayout = TextInputLayout(
            context,
            null,
            com.google.android.material.R.attr.textInputOutlinedStyle,
        ).apply {
            this.hint = hint
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
        }
        val editText = TextInputEditText(inputLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(initial)
            setSelection(initial.length)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
        inputLayout.addView(editText)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(inputLayout)
            .setPositiveButton(R.string.ok) { _, _ -> onAccept(editText.text?.toString().orEmpty()) }
            .setNegativeButton(R.string.cancel, null)
            .create()
        val onConfirmFromKeyboard = { onAccept(editText.text?.toString().orEmpty()) }
        wireAndShow(dialog, owner, onConfirmFromKeyboard)

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        fun refreshValidity() {
            positiveButton.isEnabled = validate(editText.text?.toString().orEmpty())
        }
        refreshValidity()
        editText.doOnTextChanged { _, _, _, _ -> refreshValidity() }
        return dialog
    }

    /**
     * Single-choice list dialog. [searchable] is reserved for a future filterable variant
     * (strategic spec §2.1); [AppDialog] has no filter-field layout to switch to yet, so a
     * `true` value is accepted but behaves the same as `false` until that layout exists.
     */
    fun singleChoice(
        owner: LifecycleOwner,
        context: Context,
        title: CharSequence,
        items: List<CharSequence>,
        selectedIndex: Int,
        @Suppress("UNUSED_PARAMETER") searchable: Boolean,
        onPick: (Int) -> Unit,
    ): AlertDialog {
        val labels = items.toTypedArray()
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setSingleChoiceItems(labels, selectedIndex.coerceAtLeast(0)) { d, which ->
                onPick(which)
                d.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
        return wireAndShow(dialog, owner, onConfirm = {})
    }

    /** Non-cancelable progress dialog; [determinate] picks the horizontal-bar vs spinner layout. */
    fun progress(
        owner: LifecycleOwner,
        context: Context,
        title: CharSequence,
        determinate: Boolean,
    ): AlertDialog {
        val inflater = LayoutInflater.from(context)
        val content = if (determinate) {
            DialogMaterialProgressHorizontalBinding.inflate(inflater).apply {
                tvProgressTitle.text = title
                tvProgressTitle.visibility = View.VISIBLE
            }.root
        } else {
            DialogMaterialProgressSpinnerBinding.inflate(inflater).apply {
                tvProgressTitle.text = title
                tvProgressTitle.visibility = View.VISIBLE
            }.root
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(content)
            .setCancelable(false)
            .create()
        return wireAndShow(dialog, owner, onConfirm = {})
    }

    /**
     * Custom-content dialog: inflates [layoutRes] and hands it to [bind] before showing.
     * [keyboardContract] can be disabled for a dialog that manages its own key handling
     * (e.g. a color picker with arrow-key sliders). [onConfirm] wires the keyboard contract's
     * Enter action; a caller with its own confirm button but no keyboard-triggered confirm can
     * leave it at the default no-op.
     */
    fun custom(
        owner: LifecycleOwner,
        context: Context,
        @LayoutRes layoutRes: Int,
        keyboardContract: Boolean = true,
        onConfirm: () -> Unit = {},
        bind: (View, AlertDialog) -> Unit,
    ): AlertDialog {
        val content = LayoutInflater.from(context).inflate(layoutRes, null, false)
        val dialog = MaterialAlertDialogBuilder(context)
            .setView(content)
            .create()
        bind(content, dialog)
        return wireAndShow(dialog, owner, onConfirm = onConfirm, keyboardContract = keyboardContract)
    }

    private fun wireAndShow(
        dialog: AlertDialog,
        owner: LifecycleOwner,
        onConfirm: () -> Unit,
        keyboardContract: Boolean = true,
    ): AlertDialog {
        // S3242: window/decor APIs need the dialog's window attached, which happens inside
        // show() (Dialog.dispatchOnCreate -> onCreate installs the content view) - so every
        // window-touching helper below runs after showBoundTo, not before it.
        dialog.showBoundTo(owner)
        DialogWindowSizer.applyTo(dialog)
        dialog.applyDialogInsets()
        if (keyboardContract) {
            DialogKeyboardDelegate.applyTo(dialog, onConfirm)
        }
        DialogAccessibilityHelper.applyInitialFocus(dialog)
        return dialog
    }
}
