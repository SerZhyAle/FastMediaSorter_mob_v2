package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.ui.common.FocusManager
import com.sza.fastmediasorter.ui.common.input.FocusDirection
import com.sza.fastmediasorter.ui.common.input.InputAction
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.main.MainViewModel
import com.sza.fastmediasorter.ui.main.ResourceAdapter
import com.sza.fastmediasorter.util.KeyboardShortcutHandler

/**
 * Handles all keyboard navigation for MainActivity RecyclerView.
 * Supports arrow keys, page navigation, function keys, and grid/list layouts.
 * 
 * Dependencies:
 * - RecyclerView (for scrolling and item selection)
 * - MainViewModel (for state and actions)
 * - Action callbacks (for delete confirmation, button clicks)
 */
class KeyboardNavigationHandler(
    private val context: Context,
    private val recyclerView: RecyclerView,
    private val viewModel: MainViewModel,
    private val onDeleteConfirmation: (MediaResource) -> Unit,
    private val onAddResourceClick: () -> Unit,
    private val onFilterClick: () -> Unit,
    private val onExit: () -> Unit,
    private val onShowHelp: () -> Unit,
    private val onEditResourceClick: (MediaResource) -> Unit = {},
) {

    private val focusManager = FocusManager(
        recyclerView = recyclerView,
        callbacks = object : FocusManager.FocusCallbacks {
            override fun onItemFocused(position: Int) {
                selectResourceAt(position)
            }

            override fun onItemSelected(position: Int) {
                getCurrentResource(position)?.let { resource ->
                    viewModel.selectResource(resource)
                    viewModel.openBrowse(resource)
                }
            }

            override fun getItemCount(): Int = viewModel.state.value.resources.size
        }
    )

    private val shortcutHandler = KeyboardShortcutHandler(
        surface = UiSurface.MAIN,
        dispatcher = KeyboardShortcutHandler.ActionDispatcher { action -> dispatchSharedAction(action) }
    )
    
    /**
     * Main keyboard event handler. Returns true if event was consumed.
     * 
     * Supported keys:
     * - Shared semantic navigation via [KeyboardShortcutHandler]
     * - Insert / + remains a narrow local fallback for opening add-resource
     */
    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && shortcutHandler.handleKeyEvent(keyCode, event)) {
            return true
        }

        return when (keyCode) {
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_INSERT -> {
                onAddResourceClick()
                true
            }
            else -> false
        }
    }

    private fun dispatchSharedAction(action: InputAction): Boolean {
        val pos = getCurrentFocusPosition()
        return when (action) {
            InputAction.ShowHelp -> { onShowHelp(); true }
            InputAction.ExitSurface -> { onExit(); true }
            InputAction.SearchRequested -> { onFilterClick(); true }
            // color keys and Ctrl+ duplicates for resource list
            InputAction.CopySelection -> {
                getCurrentResource(pos)?.let { viewModel.copySelectedResource(it) }
                true
            }
            InputAction.RenameSelection -> {
                getCurrentResource(pos)?.let { onEditResourceClick(it) }
                true
            }
            InputAction.DeleteSelection -> {
                getCurrentResource(pos)?.let { onDeleteConfirmation(it) }
                true
            }
            InputAction.RefreshCurrent -> {
                viewModel.refreshResources()
                Toast.makeText(context, R.string.toast_resources_refreshed, Toast.LENGTH_SHORT).show()
                true
            }
            // Do not swallow unsupported resource operations on main: help and parser must
            // reflect only actions the surface can really execute.
            InputAction.MoveSelection,
            InputAction.CreateFolder -> false
            // Do not swallow unsupported undo/redo on main: a consumed no-op looks like success.
            InputAction.UndoRequested,
            InputAction.RedoRequested -> false
            is InputAction.MoveFocus -> {
                if (action.direction == FocusDirection.NEXT || action.direction == FocusDirection.PREVIOUS) {
                    false
                } else if (action.direction == FocusDirection.UP && getCurrentFocusPosition() == 0) {
                    // S0289: at the top of the list, propagate UP to the layout's nextFocusUp
                    // target (TabLayout / control bar) instead of swallowing the event.
                    false
                } else {
                    focusManager.applyAction(action)
                }
            }
            is InputAction.PageJump,
            InputAction.OpenCurrent -> focusManager.applyAction(action)
            else -> false
        }
    }

    internal fun dispatchCommandId(commandId: String): Boolean = when (commandId) {
        "sorting.delete"            -> dispatchSharedAction(InputAction.DeleteSelection)
        "sorting.copy"              -> dispatchSharedAction(InputAction.CopySelection)
        "sorting.rename"            -> dispatchSharedAction(InputAction.RenameSelection)
        "navigation.next_file"      -> dispatchSharedAction(InputAction.MoveFocus(FocusDirection.DOWN))
        "navigation.previous_file"  -> dispatchSharedAction(InputAction.MoveFocus(FocusDirection.UP))
        CONTEXT_MENU_COMMAND_ID     -> showFocusedResourceMenu()
        else                        -> false
    }

    /**
     * INPUT-PARITY `context` for gamepad Y and TV remote Menu. Long-click on a row opens the
     * resource editor, so the row's overflow button is pressed instead: one menu for touch and keys.
     */
    private fun showFocusedResourceMenu(): Boolean {
        val holder = recyclerView.focusedChild?.let(recyclerView::getChildViewHolder)
        val moreActions = when (holder) {
            is ResourceAdapter.ResourceViewHolder -> holder.moreActionsButton
            is ResourceAdapter.GridViewHolder -> holder.moreActionsButton
            else -> null
        }
        return moreActions?.isShown == true && moreActions.performClick()
    }

    fun ensureFocus() {
        focusManager.ensureFocus()
    }

    fun clearFocus() {
        focusManager.reset()
    }

    // ========== Navigation Helpers ==========
    
    /**
     * Get currently focused item position in RecyclerView.
     */
    private fun getCurrentFocusPosition(): Int {
        val sharedFocusPosition = focusManager.getCurrentPosition()
        if (sharedFocusPosition >= 0) return sharedFocusPosition

        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        return layoutManager?.findFirstVisibleItemPosition() ?: 0
    }
    
    /**
     * Get MediaResource at specified position.
     */
    private fun getCurrentResource(position: Int): MediaResource? {
        val resources = viewModel.state.value.resources
        return if (position in 0 until resources.size) resources[position] else null
    }
    
    /**
     * Select resource at position in ViewModel.
     */
    private fun selectResourceAt(position: Int) {
        val resource = getCurrentResource(position)
        if (resource != null) {
            viewModel.selectResource(resource)
        }
    }

    companion object {
        const val CONTEXT_MENU_COMMAND_ID = "browser.context_menu"
    }
}
