package com.sza.fastmediasorter.ui.settings

import android.view.KeyEvent
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.util.KeyboardShortcutHandler

/**
 * Keyboard handler for [SettingsActivity].
 * Translates semantic [InputAction]s from [KeyboardShortcutHandler] into settings-surface
 * callbacks; legacy tab-switching via Page Up/Down and focus traversal via arrows are
 * handled through the same action path.
 */
class SettingsKeyboardNavigationManager(
    private val callback: Callback,
) {

    interface Callback {
        fun switchTab(delta: Int)
        fun tabCount(): Int
        fun currentTab(): Int
        fun openSearchOverlay()
        fun closeSearchOverlay()
        fun isSearchVisible(): Boolean
        fun isTextEditorFocused(): Boolean
        fun clearFocusedTextEditor(): Boolean
        fun navigateBack()
        fun showHelp()
        fun activateFocused(): Boolean
        fun moveFocus(direction: FocusDirection)
    }

    private val shortcutHandler = KeyboardShortcutHandler(
        surface = InputSurface.SETTINGS,
        dispatcher = KeyboardShortcutHandler.ActionDispatcher { action -> dispatchAction(action) },
    )

    /** Returns true if the event was consumed. */
    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event == null) return false
        if (callback.isTextEditorFocused()) {
            if (keyCode == KeyEvent.KEYCODE_ESCAPE) return callback.clearFocusedTextEditor()
            return false
        }
        if (shortcutHandler.handleKeyEvent(keyCode, event)) return true
        return false
    }

    private fun dispatchAction(action: InputAction): Boolean = when (action) {
        InputAction.ShowHelp -> { callback.showHelp(); true }
        InputAction.ExitSurface -> {
            if (callback.isSearchVisible()) callback.closeSearchOverlay()
            else callback.navigateBack()
            true
        }
        InputAction.SearchRequested -> { callback.openSearchOverlay(); true }
        InputAction.OpenCurrent -> callback.activateFocused()
        is InputAction.MoveFocus -> {
            when (action.direction) {
                FocusDirection.LEFT -> {
                    val cur = callback.currentTab()
                    if (cur > 0) callback.switchTab(-1)
                    true
                }
                FocusDirection.RIGHT -> {
                    val cur = callback.currentTab()
                    if (cur < callback.tabCount() - 1) callback.switchTab(+1)
                    true
                }
                else -> { callback.moveFocus(action.direction); true }
            }
        }
        is InputAction.PageJump -> {
            if (action.deltaPages < 0) {
                val cur = callback.currentTab()
                if (cur > 0) callback.switchTab(-1)
            } else {
                val cur = callback.currentTab()
                if (cur < callback.tabCount() - 1) callback.switchTab(+1)
            }
            true
        }
        else -> false
    }
}
