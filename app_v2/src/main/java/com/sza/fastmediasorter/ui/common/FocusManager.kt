package com.sza.fastmediasorter.ui.common

import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import timber.log.Timber

/**
 * Focus manager for arrow key navigation in RecyclerView
 * Task 8: Full keyboard and mouse support
 * 
 * Handles:
 * - Arrow key navigation (up/down/left/right)
 * - Home/End keys for quick navigation
 * - Page Up/Page Down for fast scrolling
 * - Grid and list layout support
 */
class FocusManager(
    private val recyclerView: RecyclerView,
    private val callbacks: FocusCallbacks
) {
    
    interface FocusCallbacks {
        /** Called when focus moves to a new item */
        fun onItemFocused(position: Int)
        
        /** Called when item is selected (Enter/Space) */
        fun onItemSelected(position: Int)
        
        /** Get total item count in adapter */
        fun getItemCount(): Int
    }
    
    companion object {
        private const val TAG = "FocusManager"
        private const val PAGE_SIZE = 10 // Items to skip for Page Up/Down
    }
    
    private var currentFocusPosition = -1
    private var focusHighlightEnabled = true
    
    /**
     * Handle arrow key navigation
     * @return true if event was handled
     */
    fun handleArrowKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        
        val itemCount = callbacks.getItemCount()
        if (itemCount == 0) {
            Timber.d("$TAG: No items to navigate")
            return false
        }
        
        // Initialize focus if not set
        if (currentFocusPosition == -1) {
            currentFocusPosition = 0
            moveFocus(0)
            return true
        }
        
        val layoutManager = recyclerView.layoutManager ?: return false
        val spanCount = getSpanCount(layoutManager)
        val isGridLayout = spanCount > 1
        
        Timber.d("$TAG: Handling keyCode=$keyCode, currentPos=$currentFocusPosition, spanCount=$spanCount")
        
        return when (keyCode) {
            // Up arrow
            KeyEvent.KEYCODE_DPAD_UP -> {
                val newPosition = if (isGridLayout) {
                    (currentFocusPosition - spanCount).coerceAtLeast(0)
                } else {
                    (currentFocusPosition - 1).coerceAtLeast(0)
                }
                moveFocus(newPosition)
                true
            }
            
            // Down arrow
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val newPosition = if (isGridLayout) {
                    (currentFocusPosition + spanCount).coerceAtMost(itemCount - 1)
                } else {
                    (currentFocusPosition + 1).coerceAtMost(itemCount - 1)
                }
                moveFocus(newPosition)
                true
            }
            
            // Left arrow
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isGridLayout && currentFocusPosition % spanCount != 0) {
                    val newPosition = (currentFocusPosition - 1).coerceAtLeast(0)
                    moveFocus(newPosition)
                }
                true
            }
            
            // Right arrow
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isGridLayout && (currentFocusPosition + 1) % spanCount != 0) {
                    val newPosition = (currentFocusPosition + 1).coerceAtMost(itemCount - 1)
                    moveFocus(newPosition)
                }
                true
            }
            
            // Home - go to first item
            KeyEvent.KEYCODE_MOVE_HOME -> {
                moveFocus(0)
                true
            }
            
            // End - go to last item
            KeyEvent.KEYCODE_MOVE_END -> {
                moveFocus(itemCount - 1)
                true
            }
            
            // Page Up - jump up
            KeyEvent.KEYCODE_PAGE_UP -> {
                val jump = if (isGridLayout) spanCount * 3 else PAGE_SIZE
                val newPosition = (currentFocusPosition - jump).coerceAtLeast(0)
                moveFocus(newPosition)
                true
            }
            
            // Page Down - jump down
            KeyEvent.KEYCODE_PAGE_DOWN -> {
                val jump = if (isGridLayout) spanCount * 3 else PAGE_SIZE
                val newPosition = (currentFocusPosition + jump).coerceAtMost(itemCount - 1)
                moveFocus(newPosition)
                true
            }
            
            else -> false
        }
    }
    
    /**
     * Get span count from layout manager
     */
    private fun getSpanCount(layoutManager: RecyclerView.LayoutManager): Int {
        return when (layoutManager) {
            is GridLayoutManager -> layoutManager.spanCount
            is LinearLayoutManager -> 1
            else -> 1
        }
    }
    
    /**
     * Move focus to new position with visual feedback
     */
    private fun moveFocus(newPosition: Int) {
        if (newPosition == currentFocusPosition || newPosition < 0) {
            return
        }
        
        val itemCount = callbacks.getItemCount()
        if (newPosition >= itemCount) {
            return
        }
        
        Timber.d("$TAG: Moving focus from $currentFocusPosition to $newPosition")
        
        val oldPosition = currentFocusPosition
        currentFocusPosition = newPosition
        
        // Scroll to make item visible
        recyclerView.smoothScrollToPosition(newPosition)
        
        // Notify callback
        callbacks.onItemFocused(newPosition)
        
        // Update visual focus state
        recyclerView.post {
            // Remove focus from old item
            if (oldPosition >= 0) {
                recyclerView.findViewHolderForAdapterPosition(oldPosition)?.itemView?.let { view ->
                    setItemFocused(view, false)
                }
            }
            
            // Add focus to new item
            recyclerView.findViewHolderForAdapterPosition(newPosition)?.itemView?.let { view ->
                setItemFocused(view, true)
                view.requestFocus()
            }
        }
    }
    
    /**
     * Set visual focus state on item view
     */
    private fun setItemFocused(view: View, focused: Boolean) {
        if (!focusHighlightEnabled) return
        
        view.isActivated = focused
        if (focused) {
            view.alpha = 1.0f
        } else {
            view.alpha = 1.0f // Keep normal alpha, use activated state for styling
        }
    }
    
    /**
     * Handle selection on current focused item
     * @return true if there was a focused item to select
     */
    fun selectCurrentItem(): Boolean {
        if (currentFocusPosition >= 0 && currentFocusPosition < callbacks.getItemCount()) {
            Timber.d("$TAG: Selecting item at position $currentFocusPosition")
            callbacks.onItemSelected(currentFocusPosition)
            return true
        }
        return false
    }
    
    /**
     * Get current focus position
     */
    fun getCurrentPosition(): Int = currentFocusPosition
    
    /**
     * Set focus position programmatically
     */
    fun setPosition(position: Int) {
        val itemCount = callbacks.getItemCount()
        if (position in 0 until itemCount) {
            moveFocus(position)
        }
    }
    
    /**
     * Reset focus state
     */
    fun reset() {
        if (currentFocusPosition >= 0) {
            recyclerView.findViewHolderForAdapterPosition(currentFocusPosition)?.itemView?.let { view ->
                setItemFocused(view, false)
            }
        }
        currentFocusPosition = -1
    }
    
    /**
     * Enable/disable focus highlighting
     */
    fun setFocusHighlightEnabled(enabled: Boolean) {
        focusHighlightEnabled = enabled
    }
    
    /**
     * Check if any item is currently focused
     */
    fun hasFocus(): Boolean = currentFocusPosition >= 0
    
    /**
     * Focus first item if nothing is focused
     */
    fun ensureFocus() {
        if (currentFocusPosition == -1 && callbacks.getItemCount() > 0) {
            moveFocus(0)
        }
    }
}
