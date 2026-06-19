package com.sza.fastmediasorter.ui.cloudfolders

import android.view.KeyEvent
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.util.KeyboardShortcutHandler

/**
 * Keyboard handler shared by all three cloud folder picker activities
 * (Dropbox, Google Drive, OneDrive).
 *
 * Supported shortcuts:
 *  - Enter / DPAD_CENTER → confirm/select current folder
 *  - Backspace / BACK    → navigate up one level
 *  - Escape / Alt+F4    → cancel and close picker
 *  - F1                  → show help dialog
 *  - Arrow keys          → delegated to RecyclerView focus system
 */
class CloudFolderPickerKeyboardDelegate(
    private val callback: Callback,
) {

    interface Callback {
        fun activateFocused(): Boolean
        fun navigateUp()
        fun refresh()
        fun cancel()
        fun showHelp()
    }

    private val shortcutHandler = KeyboardShortcutHandler(
        surface = UiSurface.CLOUD_PICKER,
        dispatcher = KeyboardShortcutHandler.ActionDispatcher { action -> dispatchAction(action) },
    )

    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && shortcutHandler.handleKeyEvent(keyCode, event)) return true
        return false
    }

    private fun dispatchAction(action: InputAction): Boolean = when (action) {
        InputAction.OpenCurrent -> callback.activateFocused()
        InputAction.BackOneLevel -> { callback.navigateUp(); true }
        InputAction.RefreshCurrent -> { callback.refresh(); true }
        InputAction.ExitSurface -> { callback.cancel(); true }
        InputAction.ShowHelp -> { callback.showHelp(); true }
        else -> false
    }
}
