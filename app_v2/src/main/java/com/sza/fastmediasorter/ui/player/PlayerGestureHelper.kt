package com.sza.fastmediasorter.ui.player

/**
 * Type holder for the player's gesture contract. The detector that once lived here had no caller;
 * gestures are wired by the setup managers, and only these two nested types are still referenced
 * through this name ([com.sza.fastmediasorter.ui.player.callbacks.PlayerGestureCallbackImpl],
 * [com.sza.fastmediasorter.ui.player.helpers.PlayerNavigationManager]).
 */
object PlayerGestureHelper {

    /**
     * Callback interface for gesture events
     */
    interface GestureCallback {
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onSwipeUp()
        fun onSwipeDown()
        fun onDoubleTap()
        fun onLongPress()
        fun onTouchZone(zone: TouchZone)
    }

    /**
     * Touch zones for different areas of the screen
     */
    enum class TouchZone {
        LEFT,           // Left third of screen
        CENTER,         // Center third of screen
        RIGHT,          // Right third of screen
        COPY_PANEL,     // Command panel - copy button area
        MOVE_PANEL,     // Command panel - move button area
        DELETE          // Command panel - delete button area
    }
}
