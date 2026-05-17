package com.sza.fastmediasorter.ui.browse.managers

import android.view.KeyEvent
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.util.KeyboardShortcutHandler
import timber.log.Timber

/**
 * Keyboard adapter for BrowseActivity. Converts raw [KeyEvent]s via the shared
 * [KeyboardShortcutHandler] to semantic [InputAction]s and forwards them to
 * [KeyboardNavigationCallbacks]. No raw key-code branching lives here.
 */
class KeyboardNavigationManager(
    private val recyclerView: RecyclerView,
    private val callbacks: KeyboardNavigationCallbacks,
) {

    interface KeyboardNavigationCallbacks {
        fun getCurrentFocusPosition(): Int
        fun getMediaFilesCount(): Int
        fun getSelectedFilesCount(): Int
        fun toggleCurrentItemSelection(position: Int)
        fun playCurrentOrSelected(position: Int)
        fun onBackPressed()
        fun showDeleteConfirmation()
        fun showCopyDialog()
        fun showMoveDialog()
        fun showRenameDialog()
        fun selectAllFiles()
        fun clearSelection()
        fun refreshFiles()
        fun navigateUp()
        fun showCreateFolderDialog()
        fun showCreateTextNoteDialog()
        fun showHelp()
        fun showContextMenu()
        fun extendSelectionUp()
        fun extendSelectionDown()
        fun undoLastOperation()
        /** Reshuffle and immediately start playback; no-op for non-media libraries. */
        fun playRandomFiles()
    }

    private val shortcutHandler = KeyboardShortcutHandler(
        surface = InputSurface.BROWSE,
        dispatcher = KeyboardShortcutHandler.ActionDispatcher { action -> dispatchAction(action) },
    )

    internal fun dispatchCommandId(commandId: String): Boolean = when (commandId) {
        "sorting.delete"            -> dispatchAction(InputAction.DeleteSelection)
        "sorting.copy"              -> dispatchAction(InputAction.CopySelection)
        "sorting.move"              -> dispatchAction(InputAction.MoveSelection)
        "sorting.rename"            -> dispatchAction(InputAction.RenameSelection)
        "sorting.create_text_note"  -> dispatchAction(InputAction.CreateTextNote)
        "navigation.next_file"      -> dispatchAction(InputAction.MoveFocus(FocusDirection.DOWN))
        "navigation.previous_file"  -> dispatchAction(InputAction.MoveFocus(FocusDirection.UP))
        else                        -> false
    }

    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && shortcutHandler.handleKeyEvent(keyCode, event)) return true
        return false
    }

    private fun dispatchAction(action: InputAction): Boolean {
        val pos = callbacks.getCurrentFocusPosition()
        val hasSelection = callbacks.getSelectedFilesCount() > 0

        return when (action) {
            InputAction.ShowHelp -> { callbacks.showHelp(); true }

            InputAction.ExitSurface -> { callbacks.onBackPressed(); true }
            InputAction.BackOneLevel -> { callbacks.navigateUp(); true }

            InputAction.OpenCurrent -> {
                callbacks.playCurrentOrSelected(pos); true
            }

            InputAction.CopySelection -> { if (hasSelection) callbacks.showCopyDialog(); true }
            InputAction.MoveSelection -> { if (hasSelection) callbacks.showMoveDialog(); true }
            InputAction.RenameSelection -> { if (hasSelection) callbacks.showRenameDialog(); true }
            InputAction.DeleteSelection -> { if (hasSelection) callbacks.showDeleteConfirmation(); true }
            InputAction.CreateFolder -> { callbacks.showCreateFolderDialog(); true }
            InputAction.CreateTextNote -> { callbacks.showCreateTextNoteDialog(); true }
            InputAction.RefreshCurrent -> { callbacks.refreshFiles(); true }
            InputAction.SelectAll -> { callbacks.selectAllFiles(); true }
            InputAction.ClearSelection -> { callbacks.clearSelection(); true }
            InputAction.ToggleSelection -> {
                callbacks.toggleCurrentItemSelection(pos); true
            }
            InputAction.ShowContextMenu -> { callbacks.showContextMenu(); true }
            InputAction.RangeExtendUp -> { callbacks.extendSelectionUp(); true }
            InputAction.RangeExtendDown -> { callbacks.extendSelectionDown(); true }
            InputAction.UndoRequested -> { callbacks.undoLastOperation(); true }
            InputAction.RedoRequested -> false

            InputAction.PlayRandomCurrent -> { callbacks.playRandomFiles(); true }

            is InputAction.MoveFocus -> { handleMoveFocus(action.direction); true }
            is InputAction.PageJump -> { scrollPage(action.deltaPages); true }

            else -> false
        }
    }

    // ── focus movement ────────────────────────────────────────────────────────

    private fun handleMoveFocus(direction: FocusDirection) {
        val pos = callbacks.getCurrentFocusPosition()
        val lm = recyclerView.layoutManager
        when (direction) {
            FocusDirection.UP -> movePosition(pos, lm, vertical = -1)
            FocusDirection.DOWN -> movePosition(pos, lm, vertical = +1)
            FocusDirection.LEFT -> movePosition(pos, lm, horizontal = -1)
            FocusDirection.RIGHT -> movePosition(pos, lm, horizontal = +1)
            FocusDirection.FIRST -> scrollToPosition(0)
            FocusDirection.LAST -> scrollToPosition(callbacks.getMediaFilesCount() - 1)
            FocusDirection.NEXT, FocusDirection.PREVIOUS -> { /* Tab handled by system */ }
        }
    }

    private fun movePosition(
        current: Int,
        lm: RecyclerView.LayoutManager?,
        vertical: Int = 0,
        horizontal: Int = 0,
    ) {
        val count = callbacks.getMediaFilesCount()
        val target = when {
            vertical != 0 && lm is GridLayoutManager -> {
                (current + vertical * lm.spanCount).coerceIn(0, count - 1)
            }
            vertical != 0 -> (current + vertical).coerceIn(0, count - 1)
            horizontal != 0 && lm is GridLayoutManager -> (current + horizontal).coerceIn(0, count - 1)
            else -> return
        }
        scrollToPosition(target)
    }

    private fun scrollPage(deltaPages: Int) {
        val lm = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val first = lm.findFirstVisibleItemPosition()
        val last = lm.findLastVisibleItemPosition()
        val page = (last - first).coerceAtLeast(1)
        val target = if (deltaPages > 0) {
            (last + page).coerceAtMost(callbacks.getMediaFilesCount() - 1)
        } else {
            (first - page).coerceAtLeast(0)
        }
        scrollToPosition(target)
        Timber.v("KeyboardNavigationManager: page=%+d target=%d", deltaPages, target)
    }

    private fun scrollToPosition(position: Int) {
        if (position < 0) return
        recyclerView.scrollToPosition(position)
        recyclerView.post {
            recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus()
        }
    }
}
