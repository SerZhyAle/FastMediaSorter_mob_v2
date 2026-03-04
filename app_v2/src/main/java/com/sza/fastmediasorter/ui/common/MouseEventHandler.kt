package com.sza.fastmediasorter.ui.common

import android.view.MotionEvent
import android.view.View
import timber.log.Timber

/**
 * Mouse event handler for double-click and right-click support
 * Task 8: Full keyboard and mouse support
 * 
 * Handles:
 * - Single click (left button)
 * - Double click (left button)
 * - Right click (secondary button) - context menu
 * - Middle click (tertiary button) - optional action
 */
class MouseEventHandler(
    private val callbacks: MouseEventCallbacks
) {
    
    interface MouseEventCallbacks {
        /** Single left-click on view */
        fun onSingleClick(view: View) {}
        
        /** Double left-click on view (open file) */
        fun onDoubleClick(view: View) {}
        
        /** Right-click on view (context menu) */
        fun onRightClick(view: View, x: Float, y: Float) {}
        
        /** Middle-click on view (optional action) */
        fun onMiddleClick(view: View) {}
    }
    
    companion object {
        private const val DOUBLE_CLICK_THRESHOLD_MS = 300L
        private const val TAG = "MouseEventHandler"
    }
    
    private var lastClickTime = 0L
    private var lastClickView: View? = null
    
    /**
     * Handle motion event for mouse interactions
     * Call this from View.setOnTouchListener or similar
     * 
     * @param view The view receiving the event
     * @param event The MotionEvent to handle
     * @return true if event was consumed, false to continue processing
     */
    fun handleMotionEvent(view: View, event: MotionEvent): Boolean {
        // Only handle events from mouse (not touch)
        if (!isMouseEvent(event)) {
            return false
        }
        
        Timber.d("$TAG: action=${event.actionMasked}, buttonState=${event.buttonState}, source=${event.source}")
        
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleMouseDown(view, event)
            MotionEvent.ACTION_UP -> handleMouseUp(view, event)
            else -> false
        }
    }
    
    /**
     * Check if event is from mouse device (not touchscreen)
     */
    private fun isMouseEvent(event: MotionEvent): Boolean {
        // Check source - mouse has different source than touchscreen
        val source = event.source
        return (source and android.view.InputDevice.SOURCE_MOUSE) == android.view.InputDevice.SOURCE_MOUSE ||
               (source and android.view.InputDevice.SOURCE_STYLUS) == android.view.InputDevice.SOURCE_STYLUS ||
               event.buttonState != 0 // Any button pressed indicates mouse
    }
    
    /**
     * Handle mouse button down event
     */
    private fun handleMouseDown(view: View, event: MotionEvent): Boolean {
        // Right-click (secondary button) - show context menu
        if (isRightClick(event)) {
            Timber.d("$TAG: Right-click detected at (${event.x}, ${event.y})")
            callbacks.onRightClick(view, event.x, event.y)
            return true
        }
        
        // Middle-click (tertiary button) - optional action
        if (isMiddleClick(event)) {
            Timber.d("$TAG: Middle-click detected")
            callbacks.onMiddleClick(view)
            return true
        }
        
        return false
    }
    
    /**
     * Handle mouse button up event (for click detection)
     */
    private fun handleMouseUp(view: View, event: MotionEvent): Boolean {
        // Skip if this was a right or middle click
        if (isRightClick(event) || isMiddleClick(event)) {
            return false
        }
        
        // Primary button (left-click) - check for double-click
        val currentTime = System.currentTimeMillis()
        val timeSinceLastClick = currentTime - lastClickTime
        val sameView = lastClickView == view
        
        if (sameView && timeSinceLastClick < DOUBLE_CLICK_THRESHOLD_MS) {
            // Double-click detected
            Timber.d("$TAG: Double-click detected on view")
            callbacks.onDoubleClick(view)
            lastClickTime = 0L // Reset to prevent triple-click triggering double
            lastClickView = null
            return true
        } else {
            // Single click - store time for potential double-click
            lastClickTime = currentTime
            lastClickView = view
            Timber.d("$TAG: Single click, waiting for potential double-click")
            callbacks.onSingleClick(view)
            return true
        }
    }
    
    /**
     * Check if event is right-click (secondary button)
     */
    private fun isRightClick(event: MotionEvent): Boolean {
        return (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0
    }
    
    /**
     * Check if event is middle-click (tertiary button)
     */
    private fun isMiddleClick(event: MotionEvent): Boolean {
        return (event.buttonState and MotionEvent.BUTTON_TERTIARY) != 0
    }
    
    /**
     * Reset click state (call when view is recycled or detached)
     */
    fun reset() {
        lastClickTime = 0L
        lastClickView = null
    }
    
    /**
     * Create touch listener that integrates mouse handling
     * Use this to attach mouse handling to any View
     * 
     * @param fallbackTouchListener Optional touch listener for non-mouse events
     * @return OnTouchListener that handles mouse events
     */
    fun createTouchListener(
        fallbackTouchListener: View.OnTouchListener? = null
    ): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) view.performClick()
            // Try mouse handling first
            if (handleMotionEvent(view, event)) {
                true
            } else {
                // Fall back to regular touch handling
                fallbackTouchListener?.onTouch(view, event) ?: false
            }
        }
    }
}
