package com.sza.fastmediasorter.util

import android.app.Dialog
import android.os.Build
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.sza.fastmediasorter.R

/**
 * Owner rule (S4051): a destructive confirmation opens with its default focus on the safe button, so
 * a stray Enter or D-pad centre cancels instead of deleting.
 *
 * A dialog counts as destructive when its themed context sets `?attr/dialogDestructive`, which
 * `ThemeOverlay.FastMediaSorter.MaterialAlertDialog.Destructive` does. Reading the marker off the
 * theme covers every builder that already picks the red pair, so no call site carries its own focus
 * code. Runs after `show()`: the buttons exist only once the dialog has been created.
 */
internal fun Dialog.focusSafeButtonIfDestructive() {
    if (!isDestructiveTheme()) return
    val safeButton = when (this) {
        is AlertDialog -> getButton(AlertDialog.BUTTON_NEGATIVE)
        is android.app.AlertDialog -> getButton(android.app.AlertDialog.BUTTON_NEGATIVE)
        else -> null
    }
    if (safeButton != null && safeButton.visibility == View.VISIBLE) {
        safeButton.makeDefaultFocus()
    }
}

private fun Dialog.isDestructiveTheme(): Boolean {
    val value = TypedValue()
    return context.theme.resolveAttribute(R.attr.dialogDestructive, value, true) &&
        value.type == TypedValue.TYPE_INT_BOOLEAN &&
        value.data != 0
}

/**
 * `focusedByDefault` is what the window restores when a key press leaves touch mode, which is the
 * moment a keyboard or D-pad user first acts on the dialog. A window already outside touch mode
 * (a TV, a session driven by keys) never passes that moment, so the button takes focus at once.
 * API 23-25 (`legacy`) has no default-focus attribute and relies on the direct request alone.
 */
private fun View.makeDefaultFocus() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        isFocusedByDefault = true
    }
    if (!isInTouchMode) {
        requestFocus()
    }
}
