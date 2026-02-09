package com.sza.fastmediasorter.ui.main.helpers

import android.content.Context
import android.view.KeyEvent
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.ui.main.MainViewModel
import timber.log.Timber

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
    private val onSettingsClick: () -> Unit,
    private val onFilterClick: () -> Unit,
    private val onExit: () -> Unit
) {
    
    /**
     * Main keyboard event handler. Returns true if event was consumed.
     * 
     * Supported keys:
     * - Arrow keys: Navigate through resources (grid/list aware)
     * - Page Up/Down, Home/End: Fast navigation
     * - Enter: Open Browse for selected resource
     * - Delete: Delete selected resource
     * - Escape: Exit app completely
     * - Insert/+: Add new resource
     * - F1-F5: Function shortcuts (slideshow, settings, filter, refresh, copy)
     */
    fun handleKeyDown(keyCode: Int, @Suppress("UNUSED_PARAMETER") event: KeyEvent?): Boolean {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
        val currentPosition = getCurrentFocusPosition()
        
        return when (keyCode) {
            // Arrow navigation through resources
            KeyEvent.KEYCODE_DPAD_UP -> {
                navigateUp(currentPosition, layoutManager)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                navigateDown(currentPosition, layoutManager)
                true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                // Left arrow in grid mode
                if (layoutManager is GridLayoutManager) {
                    val newPosition = (currentPosition - 1).coerceAtLeast(0)
                    scrollToPosition(newPosition)
                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                // Right arrow in grid mode
                if (layoutManager is GridLayoutManager) {
                    val maxPosition = viewModel.state.value.resources.size - 1
                    val newPosition = (currentPosition + 1).coerceAtMost(maxPosition)
                    scrollToPosition(newPosition)
                }
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
                val lastPosition = viewModel.state.value.resources.size - 1
                scrollToPosition(lastPosition)
                true
            }
            
            // Exit application (kill process completely)
            KeyEvent.KEYCODE_ESCAPE -> {
                onExit()
                true
            }
            
            // Add resource
            KeyEvent.KEYCODE_PLUS, KeyEvent.KEYCODE_INSERT -> {
                onAddResourceClick()
                true
            }
            
            // Browse selected resource
            KeyEvent.KEYCODE_ENTER -> {
                val selectedResource = getCurrentResource(currentPosition)
                if (selectedResource != null) {
                    viewModel.selectResource(selectedResource)
                    viewModel.openBrowse()
                }
                true
            }
            
            // Delete selected resource
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> {
                val selectedResource = getCurrentResource(currentPosition)
                if (selectedResource != null) {
                    onDeleteConfirmation(selectedResource)
                }
                true
            }
            
            // Function keys
            KeyEvent.KEYCODE_F1 -> handleF1Slideshow(currentPosition)
            KeyEvent.KEYCODE_F2 -> handleF2Settings()
            KeyEvent.KEYCODE_F3 -> handleF3Filter()
            KeyEvent.KEYCODE_F4 -> handleF4Refresh()
            KeyEvent.KEYCODE_F5 -> handleF5Copy(currentPosition)
            
            else -> false
        }
    }
    
    // ========== Function Key Handlers ==========
    
    /**
     * F1: Start slideshow for selected/first resource
     */
    private fun handleF1Slideshow(currentPosition: Int): Boolean {
        val selectedResource = getCurrentResource(currentPosition) 
            ?: viewModel.state.value.resources.firstOrNull()
        if (selectedResource != null) {
            viewModel.selectResource(selectedResource)
            viewModel.startPlayer()
        }
        return true
    }
    
    /**
     * F2: Open Settings
     */
    private fun handleF2Settings(): Boolean {
        onSettingsClick()
        return true
    }
    
    /**
     * F3: Open Filter dialog
     */
    private fun handleF3Filter(): Boolean {
        onFilterClick()
        return true
    }
    
    /**
     * F4: Refresh resources list
     */
    private fun handleF4Refresh(): Boolean {
        viewModel.refreshResources()
        Toast.makeText(context, R.string.toast_resources_refreshed, Toast.LENGTH_SHORT).show()
        return true
    }
    
    /**
     * F5: Copy selected resource
     */
    private fun handleF5Copy(currentPosition: Int): Boolean {
        val selectedResource = getCurrentResource(currentPosition)
        if (selectedResource != null) {
            viewModel.selectResource(selectedResource)
            viewModel.copySelectedResource()
        }
        return true
    }
    
    // ========== Navigation Helpers ==========
    
    /**
     * Get currently focused item position in RecyclerView.
     */
    private fun getCurrentFocusPosition(): Int {
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
     * Navigate up in list/grid. Grid-aware (moves by spanCount).
     */
    private fun navigateUp(currentPosition: Int, layoutManager: LinearLayoutManager?) {
        val newPosition = when (layoutManager) {
            is GridLayoutManager -> {
                val spanCount = layoutManager.spanCount
                (currentPosition - spanCount).coerceAtLeast(0)
            }
            else -> (currentPosition - 1).coerceAtLeast(0)
        }
        scrollToPosition(newPosition)
        selectResourceAt(newPosition)
    }
    
    /**
     * Navigate down in list/grid. Grid-aware (moves by spanCount).
     */
    private fun navigateDown(currentPosition: Int, layoutManager: LinearLayoutManager?) {
        val maxPosition = viewModel.state.value.resources.size - 1
        val newPosition = when (layoutManager) {
            is GridLayoutManager -> {
                val spanCount = layoutManager.spanCount
                (currentPosition + spanCount).coerceAtMost(maxPosition)
            }
            else -> (currentPosition + 1).coerceAtMost(maxPosition)
        }
        scrollToPosition(newPosition)
        selectResourceAt(newPosition)
    }
    
    /**
     * Scroll to specific position if valid.
     */
    private fun scrollToPosition(position: Int) {
        if (position in 0 until viewModel.state.value.resources.size) {
            recyclerView.scrollToPosition(position)
        }
    }
    
    /**
     * Scroll by page (visible item count) in specified direction.
     * Direction: -1 for up, +1 for down.
     */
    private fun scrollPage(direction: Int) {
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val firstVisible = layoutManager.findFirstVisibleItemPosition()
        val lastVisible = layoutManager.findLastVisibleItemPosition()
        val pageSize = lastVisible - firstVisible
        
        val newPosition = if (direction > 0) {
            // Page down
            (lastVisible + pageSize).coerceAtMost(viewModel.state.value.resources.size - 1)
        } else {
            // Page up
            (firstVisible - pageSize).coerceAtLeast(0)
        }
        
        scrollToPosition(newPosition)
        selectResourceAt(newPosition)
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
}
