package com.sza.fastmediasorter.ui.addresource

import android.view.KeyEvent
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.util.KeyboardShortcutHandler

/**
 * Keyboard handler for [AddResourceActivity].
 * Provides Escape (exit) and F1 (help) for the add-resource form.
 */
class AddResourceKeyboardDelegate(
    private val callback: Callback,
) {

    interface Callback {
        fun navigateBack()
        fun showHelp()
        fun activateFocused(): Boolean
        fun moveFocus(direction: FocusDirection)
        fun isTextEditorFocused(): Boolean
    }

    private val shortcutHandler = KeyboardShortcutHandler(
        surface = UiSurface.ADD_RESOURCE,
        dispatcher = KeyboardShortcutHandler.ActionDispatcher { action -> dispatchAction(action) },
    )

    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return false
        if (callback.isTextEditorFocused()) {
            if (keyCode == KeyEvent.KEYCODE_ESCAPE) { callback.navigateBack(); return true }
            return false
        }
        if (shortcutHandler.handleKeyEvent(keyCode, event)) return true
        return false
    }

    private fun dispatchAction(action: InputAction): Boolean = when (action) {
        InputAction.ExitSurface -> { callback.navigateBack(); true }
        InputAction.ShowHelp -> { callback.showHelp(); true }
        InputAction.OpenCurrent -> callback.activateFocused()
        is InputAction.MoveFocus -> { callback.moveFocus(action.direction); true }
        else -> false
    }
}
