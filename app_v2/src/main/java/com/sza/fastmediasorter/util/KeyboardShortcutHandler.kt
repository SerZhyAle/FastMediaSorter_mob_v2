package com.sza.fastmediasorter.util

import android.view.KeyEvent
import timber.log.Timber

/**
 * Universal keyboard shortcut handler
 * Task 8: Full keyboard and mouse support
 */
class KeyboardShortcutHandler(
    private val callbacks: KeyboardShortcutCallbacks
) {
    
    interface KeyboardShortcutCallbacks {
        fun onSelectAll() {}
        fun onCopy() {}
        fun onCut() {}
        fun onDelete() {}
        fun onRename() {}
        fun onRefresh() {}
        fun onBack() {}
        fun onEscape() {}
        fun onSpace() {}
        fun onEnter() {}
    }
    
    /**
     * Handle key event and trigger appropriate callback
     * @return true if event was handled
     */
    fun handleKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return false
        }
        
        // Check modifiers
        val isCtrlPressed = event.isCtrlPressed
        val isShiftPressed = event.isShiftPressed
        val isAltPressed = event.isAltPressed
        
        Timber.d("KeyboardShortcutHandler: keyCode=$keyCode, ctrl=$isCtrlPressed, shift=$isShiftPressed, alt=$isAltPressed")
        
        return when {
            // Ctrl+A - Select All
            isCtrlPressed && keyCode == KeyEvent.KEYCODE_A -> {
                callbacks.onSelectAll()
                true
            }
            
            // Ctrl+C - Copy
            isCtrlPressed && keyCode == KeyEvent.KEYCODE_C -> {
                callbacks.onCopy()
                true
            }
            
            // Ctrl+X - Cut (Move)
            isCtrlPressed && keyCode == KeyEvent.KEYCODE_X -> {
                callbacks.onCut()
                true
            }
            
            // Delete - Delete files
            keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL -> {
                callbacks.onDelete()
                true
            }
            
            // F2 - Rename
            keyCode == KeyEvent.KEYCODE_F2 -> {
                callbacks.onRename()
                true
            }
            
            // F5 - Refresh
            keyCode == KeyEvent.KEYCODE_F5 -> {
                callbacks.onRefresh()
                true
            }
            
            // Backspace - Back
            keyCode == KeyEvent.KEYCODE_BACK && !isCtrlPressed -> {
                callbacks.onBack()
                true
            }
            
            // Escape - Cancel/Close
            keyCode == KeyEvent.KEYCODE_ESCAPE -> {
                callbacks.onEscape()
                true
            }
            
            // Space - Toggle selection or play
            keyCode == KeyEvent.KEYCODE_SPACE -> {
                callbacks.onSpace()
                true
            }
            
            // Enter - Open file
            keyCode == KeyEvent.KEYCODE_ENTER -> {
                callbacks.onEnter()
                true
            }
            
            else -> false
        }
    }
}
