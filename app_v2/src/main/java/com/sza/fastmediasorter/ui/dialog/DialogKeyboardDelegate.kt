package com.sza.fastmediasorter.ui.dialog

import android.app.Dialog
import android.view.KeyEvent
import android.view.View
import android.widget.AdapterView
import android.widget.CompoundButton
import android.widget.EditText
import com.google.android.material.chip.Chip
import com.sza.fastmediasorter.ui.common.help.DialogHelpEntryManager
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.util.KeyboardShortcutHandler

/**
 * Wires standard keyboard contract onto any [Dialog]:
 *  - Enter  → [onConfirm], unless the focused view owns Enter itself (see [focusedViewOwnsEnter])
 *  - Escape → [Dialog.dismiss]
 *  - F1     → the dialog's help page when the help map has one, else the shortcut sheet
 *
 * Call [applyTo] once after the dialog's view is created (e.g. inside [Dialog.onCreate]).
 */
object DialogKeyboardDelegate {

    private val shortcutHandler = KeyboardShortcutHandler(
        surface = UiSurface.DIALOG,
        dispatcher = KeyboardShortcutHandler.ActionDispatcher { false },
    )

    fun applyTo(dialog: Dialog, onConfirm: () -> Unit) = applyTo(dialog, onHelp = null, onConfirm = onConfirm)

    /**
     * [onHelp] lets a dialog that belongs to a surface with a help page open that page on F1; it returns
     * true when it handled the key. Otherwise F1 opens the help the map gives the dialog's surface id -
     * [helpSurfaceId], else the owning DialogFragment's class name - and the generic dialog shortcut
     * sheet is shown only when there is none. A dialog already showing also gets its "?" button here.
     */
    fun applyTo(
        dialog: Dialog,
        onHelp: (() -> Boolean)?,
        helpSurfaceId: String? = null,
        onConfirm: () -> Unit,
    ) {
        if (dialog.isShowing) {
            DialogHelpEntryManager.installButton(dialog, DialogHelpEntryManager.surfaceIdOf(dialog, helpSurfaceId))
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_UP) return@setOnKeyListener false

            when (val action = shortcutHandler.mapToAction(keyCode, event.metaState)) {
                InputAction.DialogPrimary -> {
                    if (focusedViewOwnsEnter(dialog)) {
                        false
                    } else {
                        onConfirm()
                        true
                    }
                }
                InputAction.DialogDismiss -> {
                    dialog.dismiss()
                    true
                }
                InputAction.ShowHelp -> {
                    if (onHelp?.invoke() != true && !openMappedHelp(dialog, helpSurfaceId)) showHelp(dialog)
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

    /** Variant of [applyToDialogFragment] whose F1 can open a help page, see [applyTo]. */
    fun applyToDialogFragment(
        dialog: Dialog?,
        onHelp: (() -> Boolean)?,
        helpSurfaceId: String? = null,
        onConfirm: () -> Unit,
    ) {
        dialog ?: return
        applyTo(dialog, onHelp, helpSurfaceId, onConfirm)
    }

    /**
     * Dialog.dispatchKeyEvent runs this listener before the window, so consuming Enter here would
     * steal the ACTION_UP that View.onKeyUp turns into a click: Enter on a focused Cancel button
     * would run the confirm action, Enter on a list row would do nothing. A focused text field is
     * the exception - Enter there is the keyboard "accept" and still confirms the dialog.
     */
    private fun focusedViewOwnsEnter(dialog: Dialog): Boolean {
        val focused = dialog.currentFocus ?: return false
        return focused !is EditText && (focused.isClickable || focused is AdapterView<*>)
    }

    private fun openMappedHelp(dialog: Dialog, helpSurfaceId: String?): Boolean =
        DialogHelpEntryManager.openHelp(dialog, DialogHelpEntryManager.surfaceIdOf(dialog, helpSurfaceId))

    private fun showHelp(dialog: Dialog) {
        val activity = DialogHelpEntryManager.activityOf(dialog.context) ?: return
        InputHelpDialogFragment.show(activity.supportFragmentManager, UiSurface.DIALOG)
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
