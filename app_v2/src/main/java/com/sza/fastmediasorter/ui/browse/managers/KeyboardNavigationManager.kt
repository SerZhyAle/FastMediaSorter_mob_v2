package com.sza.fastmediasorter.ui.browse.managers

import android.view.KeyEvent
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

/**
 * Manages keyboard navigation in BrowseActivity.
 * Handles arrow keys, page navigation, selection shortcuts, and function keys.
 * 
 * Task 8: Full keyboard and mouse support
 */
class KeyboardNavigationManager(
    private val recyclerView: RecyclerView,
    private val callbacks: KeyboardNavigationCallbacks
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
        fun performButtonClick(buttonId: Int)
        
        // Task 8: Additional keyboard shortcuts
        fun selectAllFiles() {}
        fun showRenameDialog() {}
        fun refreshFiles() {}
        fun clearSelection() {}
        fun navigateUp() {}
    }
    
    @Suppress("UNUSED_PARAMETER")
    fun handleKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val layoutManager = recyclerView.layoutManager
        val currentPosition = callbacks.getCurrentFocusPosition()
        
        // Check for modifier keys
        val isCtrlPressed = event?.isCtrlPressed == true
        
        // Task 8: Ctrl+key shortcuts
        if (isCtrlPressed) {
            return when (keyCode) {
                KeyEvent.KEYCODE_A -> {
                    Timber.d("Keyboard: Ctrl+A - Select All")
                    callbacks.selectAllFiles()
                    true
                }
                KeyEvent.KEYCODE_C -> {
                    if (callbacks.getSelectedFilesCount() > 0) {
                        Timber.d("Keyboard: Ctrl+C - Copy")
                        callbacks.showCopyDialog()
                    }
                    true
                }
                KeyEvent.KEYCODE_X -> {
                    if (callbacks.getSelectedFilesCount() > 0) {
                        Timber.d("Keyboard: Ctrl+X - Cut/Move")
                        callbacks.showMoveDialog()
                    }
                    true
                }
                else -> false
            }
        }
        
        return when (keyCode) {
            // Arrow navigation
            KeyEvent.KEYCODE_DPAD_UP -> {
                navigateUp(currentPosition, layoutManager)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                navigateDown(currentPosition, layoutManager)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                navigateLeft(currentPosition, layoutManager)
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                navigateRight(currentPosition, layoutManager)
                true
            }
            
            // Page navigation
            KeyEvent.KEYCODE_PAGE_UP -> {
                scrollPage(-1)
                true
            }
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                scrollPage(1)
                true
            }
            KeyEvent.KEYCODE_MOVE_HOME -> {
                scrollToPosition(0)
                true
            }
            KeyEvent.KEYCODE_MOVE_END -> {
                val lastPosition = callbacks.getMediaFilesCount() - 1
                scrollToPosition(lastPosition)
                true
            }
            
            // Selection actions
            KeyEvent.KEYCODE_SPACE -> {
                callbacks.toggleCurrentItemSelection(currentPosition)
                true
            }
            
            // Play action
            KeyEvent.KEYCODE_ENTER -> {
                callbacks.playCurrentOrSelected(currentPosition)
                true
            }
            
            // Back action
            KeyEvent.KEYCODE_ESCAPE -> {
                callbacks.onBackPressed()
                true
            }
            
            // Delete action
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (callbacks.getSelectedFilesCount() > 0) {
                    callbacks.showDeleteConfirmation()
                }
                true
            }
            
            // Task 8: F2 - Rename
            KeyEvent.KEYCODE_F2 -> {
                if (callbacks.getSelectedFilesCount() > 0) {
                    Timber.d("Keyboard: F2 - Rename")
                    callbacks.showRenameDialog()
                }
                true
            }
            
            // Task 8: F5 - Refresh
            KeyEvent.KEYCODE_F5 -> {
                Timber.d("Keyboard: F5 - Refresh")
                callbacks.refreshFiles()
                true
            }
            
            // Task 8: Backspace - Navigate up
            KeyEvent.KEYCODE_DEL -> {
                Timber.d("Keyboard: Backspace - Navigate up")
                callbacks.navigateUp()
                true
            }
            
            // Function keys handled via button clicks
            else -> false
        }
    }
    
    private fun navigateUp(currentPosition: Int, layoutManager: RecyclerView.LayoutManager?) {
        when (layoutManager) {
            is LinearLayoutManager -> {
                val targetPosition = (currentPosition - 1).coerceAtLeast(0)
                scrollToPosition(targetPosition)
            }
            is GridLayoutManager -> {
                val spanCount = layoutManager.spanCount
                val targetPosition = (currentPosition - spanCount).coerceAtLeast(0)
                scrollToPosition(targetPosition)
            }
        }
    }
    
    private fun navigateDown(currentPosition: Int, layoutManager: RecyclerView.LayoutManager?) {
        val itemCount = callbacks.getMediaFilesCount()
        when (layoutManager) {
            is LinearLayoutManager -> {
                val targetPosition = (currentPosition + 1).coerceAtMost(itemCount - 1)
                scrollToPosition(targetPosition)
            }
            is GridLayoutManager -> {
                val spanCount = layoutManager.spanCount
                val targetPosition = (currentPosition + spanCount).coerceAtMost(itemCount - 1)
                scrollToPosition(targetPosition)
            }
        }
    }
    
    private fun navigateLeft(currentPosition: Int, layoutManager: RecyclerView.LayoutManager?) {
        if (layoutManager !is GridLayoutManager) return
        val targetPosition = (currentPosition - 1).coerceAtLeast(0)
        scrollToPosition(targetPosition)
    }
    
    private fun navigateRight(currentPosition: Int, layoutManager: RecyclerView.LayoutManager?) {
        if (layoutManager !is GridLayoutManager) return
        val itemCount = callbacks.getMediaFilesCount()
        val targetPosition = (currentPosition + 1).coerceAtMost(itemCount - 1)
        scrollToPosition(targetPosition)
    }
    
    private fun scrollToPosition(position: Int) {
        if (position < 0) return
        recyclerView.scrollToPosition(position)
        recyclerView.post { recyclerView.findViewHolderForAdapterPosition(position)?.itemView?.requestFocus() }
    }
    
    private fun scrollPage(direction: Int) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val pageSize = lastVisible - firstVisible
        
        val targetPosition = when {
            direction < 0 -> (firstVisible - pageSize).coerceAtLeast(0)
            else -> (lastVisible + pageSize).coerceAtMost(callbacks.getMediaFilesCount() - 1)
        }
        
        scrollToPosition(targetPosition)
    }
}
