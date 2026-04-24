package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.view.KeyEvent
import android.view.View
import android.widget.CompoundButton
import android.widget.EditText
import androidx.fragment.app.FragmentActivity
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.util.KeyboardShortcutHandler

/**
 * Wires standard keyboard contract onto any [Dialog]:
 *  - Enter  → [onConfirm]
 *  - Escape → [Dialog.dismiss]
 *
 * Call [applyTo] once after the dialog's view is created (e.g. inside [Dialog.onCreate]).
 */
object DialogKeyboardDelegate {

    private val shortcutHandler = KeyboardShortcutHandler(
        surface = InputSurface.DIALOG,
        dispatcher = KeyboardShortcutHandler.ActionDispatcher { false },
    )

    fun applyTo(dialog: Dialog, onConfirm: () -> Unit) {
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false

            when (val action = shortcutHandler.mapToAction(keyCode, event.metaState)) {
                InputAction.DialogPrimary -> {
                    onConfirm()
                    true
                }
                InputAction.DialogDismiss -> {
                    dialog.dismiss()
                    true
                }
                InputAction.ShowHelp -> {
                    showHelp(dialog)
                    true
                }
                InputAction.ToggleSelection -> toggleFocusedControl(dialog)
                is InputAction.MoveFocus -> moveFocus(dialog, action)
                else -> false
            }
        }
    }

    /**
     * Variant for [androidx.fragment.app.DialogFragment]: wires the contract onto the
     * underlying [Dialog] via [DialogInterface.OnKeyListener] after the dialog is shown.
     * Call from [androidx.fragment.app.DialogFragment.onStart].
     */
    fun applyToDialogFragment(dialog: Dialog?, onConfirm: () -> Unit) {
        dialog ?: return
        applyTo(dialog, onConfirm)
    }

    private fun showHelp(dialog: Dialog) {
        val activity = dialog.context as? FragmentActivity ?: return
        InputHelpDialogFragment.show(activity.supportFragmentManager, InputSurface.DIALOG)
    }

    private fun toggleFocusedControl(dialog: Dialog): Boolean {
        val focused = dialog.currentFocus ?: return false
        // Text fields need Space for text entry; dialog toggling only applies to real toggle rows.
        if (focused is EditText) return false
        return when (focused) {
            is CompoundButton,
            is Chip -> {
                focused.performClick()
                true
            }
            else -> false
        }
    }

    private fun moveFocus(dialog: Dialog, action: InputAction.MoveFocus): Boolean {
        val focused = dialog.currentFocus ?: return false
        val direction = when (action.direction) {
            FocusDirection.UP -> View.FOCUS_UP
            FocusDirection.DOWN -> View.FOCUS_DOWN
            FocusDirection.LEFT -> View.FOCUS_LEFT
            FocusDirection.RIGHT -> View.FOCUS_RIGHT
            FocusDirection.FIRST -> View.FOCUS_UP
            FocusDirection.LAST -> View.FOCUS_DOWN
            FocusDirection.NEXT -> View.FOCUS_FORWARD
            FocusDirection.PREVIOUS -> View.FOCUS_BACKWARD
        }
        val next = focused.focusSearch(direction) ?: return false
        return next.requestFocus()
    }
}
