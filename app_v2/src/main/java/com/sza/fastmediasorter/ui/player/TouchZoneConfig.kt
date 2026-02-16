package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.domain.model.MediaType

/**
 * Touch zone map types based on TOUCH_ZONES.md specification.
 * Each map defines a specific layout of touch zones for different use cases.
 */
enum class TouchZoneMap {
    /**
     * REG-9100: 9 touch zones, 100% screen width and height.
     * Grid: 30%-40%-30% horizontal and vertical.
     * Used for: IMAGE/GIF in fullscreen mode.
     */
    REG_9100,
    
    /**
     * REG-975: 9 touch zones, 100% width, 75% height.
     * Bottom 25% reserved for player controls.
     * Grid: 30%-40%-30% horizontal and vertical.
     * Used for: VIDEO/AUDIO in fullscreen mode.
     */
    REG_975,
    
    /**
     * REG-3100: 3 vertical zones, 100% width and height.
     * Split: 25%-50%-25% horizontal.
     * Middle zone reserved for PhotoView gestures.
     * Used for: IMAGE/GIF in command panel mode.
     */
    REG_3100,
    
    /**
     * REG-375: 3 vertical zones, 100% width, 75% height.
     * Bottom 25% reserved for player controls.
     * Split: 25%-50%-25% horizontal.
     * Used for: VIDEO/AUDIO in command panel mode.
     */
    REG_375,
    
    /**
     * REG-DOC: No tap zones, swipe-only navigation.
     * Used for: PDF/EPUB/TXT in both modes.
     */
    REG_DOC
}

/**
 * Actions that can be triggered by touch zones.
 * Unified enum for all zone maps.
 */
enum class TouchZoneAction {
    // Navigation
    BACK,               // Return to Browse screen
    PREVIOUS,           // Previous file
    NEXT,               // Next file
    
    // File operations (dialogs)
    COPY,               // Show "Copy to destination" dialog
    RENAME,             // Show "Rename to" dialog
    MOVE,               // Show "Move to destination" dialog
    DELETE,             // Show "Delete" dialog
    
    // Mode switching
    COMMAND_PANEL,      // Switch to Command Panel mode
    SLIDESHOW,          // Start/Stop slideshow mode
    
    // Playback
    PAUSE_RESUME,       // Pause/Resume video/audio
    SEEK_START,         // Go to beginning of file
    SEEK_END,           // Go to end of file
    
    // Zoom (images)
    ZOOM_IN,            // Zoom in x2
    ZOOM_OUT,           // Zoom out x2
    
    // Document navigation
    PAGE_UP,            // One page up
    PAGE_DOWN,          // One page down
    
    // Special
    PHOTOVIEW_GESTURE,  // Let PhotoView handle (pan/zoom/rotate)
    NONE                // No action
}

/**
 * Swipe directions for gesture detection.
 */
enum class SwipeDirection {
    LEFT,   // Finger moved left (→ typically Next)
    RIGHT,  // Finger moved right (→ typically Previous)
    UP,     // Finger moved up (→ varies by zone map)
    DOWN    // Finger moved down (→ varies by zone map)
}

/**
 * Configuration for a specific zone map.
 * Defines zone boundaries and height percentage.
 */
data class ZoneConfiguration(
    /**
     * Number of horizontal zones (3 for REG-3100/REG-375, 3 for REG-9100/REG-975)
     */
    val horizontalZones: Int,
    
    /**
     * Number of vertical zones (1 for REG-3100/REG-375, 3 for REG-9100/REG-975)
     */
    val verticalZones: Int,
    
    /**
     * Percentage of screen height to use for zones (0.75 or 1.0).
     * Bottom portion is reserved for player controls when < 1.0.
     */
    val heightPercent: Float,
    
    /**
     * Width ratios for horizontal zones.
     * e.g., [0.30, 0.40, 0.30] for 9-zone, [0.25, 0.50, 0.25] for 3-zone
     */
    val widthRatios: List<Float>,
    
    /**
     * Height ratios for vertical zones.
     * e.g., [0.30, 0.40, 0.30] for 9-zone, [1.0] for 3-zone
     */
    val heightRatios: List<Float>,
    
    /**
     * Whether taps are enabled. False for REG-DOC.
     */
    val tapsEnabled: Boolean = true,
    
    /**
     * Whether swipes starting in middle zone should be ignored.
     * True for REG-3100 (PhotoView handles gestures).
     */
    val ignoreMiddleZoneSwipes: Boolean = false
)

/**
 * Helper object for touch zone configuration and resolution.
 */
object TouchZoneConfig {
    
    /**
     * Get zone configuration for a specific zone map.
     */
    fun getConfiguration(map: TouchZoneMap): ZoneConfiguration {
        return when (map) {
            TouchZoneMap.REG_9100 -> ZoneConfiguration(
                horizontalZones = 3,
                verticalZones = 3,
                heightPercent = 1.0f,
                widthRatios = listOf(0.30f, 0.40f, 0.30f),
                heightRatios = listOf(0.30f, 0.40f, 0.30f)
            )
            
            TouchZoneMap.REG_975 -> ZoneConfiguration(
                horizontalZones = 3,
                verticalZones = 3,
                heightPercent = 0.75f,
                widthRatios = listOf(0.30f, 0.40f, 0.30f),
                heightRatios = listOf(0.30f, 0.40f, 0.30f)
            )
            
            TouchZoneMap.REG_3100 -> ZoneConfiguration(
                horizontalZones = 3,
                verticalZones = 1,
                heightPercent = 1.0f,
                widthRatios = listOf(0.25f, 0.50f, 0.25f),
                heightRatios = listOf(1.0f),
                ignoreMiddleZoneSwipes = true
            )
            
            TouchZoneMap.REG_375 -> ZoneConfiguration(
                horizontalZones = 3,
                verticalZones = 1,
                heightPercent = 0.75f,
                widthRatios = listOf(0.25f, 0.50f, 0.25f),
                heightRatios = listOf(1.0f)
            )
            
            TouchZoneMap.REG_DOC -> ZoneConfiguration(
                horizontalZones = 1,
                verticalZones = 1,
                heightPercent = 1.0f,
                widthRatios = listOf(1.0f),
                heightRatios = listOf(1.0f),
                tapsEnabled = false
            )
        }
    }
    
    /**
     * Determine which zone map to use based on media type and screen mode.
     * 
     * @param mediaType The type of media being displayed
     * @param isFullscreen True if in fullscreen mode, false if command panel is visible
     * @return The appropriate TouchZoneMap for the combination
     */
    fun getZoneMapForMediaType(mediaType: MediaType?, isFullscreen: Boolean): TouchZoneMap {
        return when (mediaType) {
            MediaType.IMAGE, MediaType.GIF -> {
                if (isFullscreen) TouchZoneMap.REG_9100 else TouchZoneMap.REG_3100
            }
            MediaType.VIDEO, MediaType.AUDIO -> {
                if (isFullscreen) TouchZoneMap.REG_975 else TouchZoneMap.REG_375
            }
            MediaType.PDF, MediaType.EPUB, MediaType.TEXT -> {
                TouchZoneMap.REG_DOC
            }
            else -> {
                // Default to 9-zone for unknown types
                if (isFullscreen) TouchZoneMap.REG_9100 else TouchZoneMap.REG_3100
            }
        }
    }
    
    /**
     * Get tap action for a specific zone position in 9-zone layout (REG-9100, REG-975).
     * 
     * @param row Row index (0=top, 1=middle, 2=bottom)
     * @param column Column index (0=left, 1=center, 2=right)
     * @param zoneMap The zone map to differentiate between image and video layouts
     * @return The action for that zone
     */
    fun get9ZoneTapAction(row: Int, column: Int, zoneMap: TouchZoneMap): TouchZoneAction {
        return when (row) {
            0 -> when (column) {
                0 -> TouchZoneAction.BACK
                1 -> TouchZoneAction.COPY
                2 -> TouchZoneAction.RENAME
                else -> TouchZoneAction.NONE
            }
            1 -> when (column) {
                0 -> TouchZoneAction.PREVIOUS
                1 -> {
                    // Center zone: PAUSE_RESUME for video (REG-975), MOVE for images (REG-9100)
                    if (zoneMap == TouchZoneMap.REG_975) {
                        TouchZoneAction.PAUSE_RESUME
                    } else {
                        TouchZoneAction.MOVE
                    }
                }
                2 -> TouchZoneAction.NEXT
                else -> TouchZoneAction.NONE
            }
            2 -> when (column) {
                0 -> TouchZoneAction.COMMAND_PANEL
                1 -> TouchZoneAction.DELETE
                2 -> TouchZoneAction.SLIDESHOW
                else -> TouchZoneAction.NONE
            }
            else -> TouchZoneAction.NONE
        }
    }
    
    /**
     * Get tap action for 3-zone layout (REG-3100 for images).
     * 
     * @param column Column index (0=left, 1=center, 2=right)
     * @return The action for that zone
     */
    fun get3ZoneImageTapAction(column: Int): TouchZoneAction {
        return when (column) {
            0 -> TouchZoneAction.PREVIOUS
            1 -> TouchZoneAction.PHOTOVIEW_GESTURE  // Middle = PhotoView handles
            2 -> TouchZoneAction.NEXT
            else -> TouchZoneAction.NONE
        }
    }
    
    /**
     * Get tap action for 3-zone layout (REG-375 for video/audio).
     * 
     * @param column Column index (0=left, 1=center, 2=right)
     * @return The action for that zone
     */
    fun get3ZoneVideoTapAction(column: Int): TouchZoneAction {
        return when (column) {
            0 -> TouchZoneAction.PREVIOUS
            1 -> TouchZoneAction.PAUSE_RESUME  // Middle = Pause/Resume
            2 -> TouchZoneAction.NEXT
            else -> TouchZoneAction.NONE
        }
    }
    
    /**
     * Get swipe action for a specific zone map and direction.
     * 
     * @param map The zone map in use
     * @param direction The swipe direction
     * @return The action for that swipe
     */
    fun getSwipeAction(map: TouchZoneMap, direction: SwipeDirection): TouchZoneAction {
        return when (map) {
            TouchZoneMap.REG_9100, TouchZoneMap.REG_975 -> {
                // 9-zone fullscreen swipes
                when (direction) {
                    SwipeDirection.LEFT -> TouchZoneAction.NEXT
                    SwipeDirection.RIGHT -> TouchZoneAction.PREVIOUS
                    SwipeDirection.DOWN -> TouchZoneAction.BACK
                    SwipeDirection.UP -> TouchZoneAction.COMMAND_PANEL
                }
            }
            
            TouchZoneMap.REG_3100 -> {
                // Image command panel swipes (zoom)
                when (direction) {
                    SwipeDirection.LEFT -> TouchZoneAction.NEXT
                    SwipeDirection.RIGHT -> TouchZoneAction.PREVIOUS
                    SwipeDirection.DOWN -> TouchZoneAction.ZOOM_OUT
                    SwipeDirection.UP -> TouchZoneAction.ZOOM_IN
                }
            }
            
            TouchZoneMap.REG_375 -> {
                // Video command panel swipes (seek)
                when (direction) {
                    SwipeDirection.LEFT -> TouchZoneAction.NEXT
                    SwipeDirection.RIGHT -> TouchZoneAction.PREVIOUS
                    SwipeDirection.DOWN -> TouchZoneAction.SEEK_START
                    SwipeDirection.UP -> TouchZoneAction.SEEK_END
                }
            }
            
            TouchZoneMap.REG_DOC -> {
                // Document swipes (file nav + page nav)
                when (direction) {
                    SwipeDirection.LEFT -> TouchZoneAction.NEXT
                    SwipeDirection.RIGHT -> TouchZoneAction.PREVIOUS
                    SwipeDirection.DOWN -> TouchZoneAction.PAGE_UP    // Swipe down = scroll up = page up
                    SwipeDirection.UP -> TouchZoneAction.PAGE_DOWN    // Swipe up = scroll down = page down
                }
            }
        }
    }
    
    /**
     * Check if an action is destructive and may require confirmation.
     */
    fun isDestructiveAction(action: TouchZoneAction): Boolean {
        return action == TouchZoneAction.DELETE || action == TouchZoneAction.MOVE
    }
    
    /**
     * Get human-readable description for an action.
     */
    fun getActionDescription(action: TouchZoneAction): String {
        return when (action) {
            TouchZoneAction.BACK -> "Back to Browse"
            TouchZoneAction.PREVIOUS -> "Previous file"
            TouchZoneAction.NEXT -> "Next file"
            TouchZoneAction.COPY -> "Copy file"
            TouchZoneAction.RENAME -> "Rename file"
            TouchZoneAction.MOVE -> "Move file"
            TouchZoneAction.DELETE -> "Delete file"
            TouchZoneAction.COMMAND_PANEL -> "Show Command Panel"
            TouchZoneAction.SLIDESHOW -> "Toggle Slideshow"
            TouchZoneAction.PAUSE_RESUME -> "Pause/Resume"
            TouchZoneAction.SEEK_START -> "Go to start"
            TouchZoneAction.SEEK_END -> "Go to end"
            TouchZoneAction.ZOOM_IN -> "Zoom in"
            TouchZoneAction.ZOOM_OUT -> "Zoom out"
            TouchZoneAction.PAGE_UP -> "Page up"
            TouchZoneAction.PAGE_DOWN -> "Page down"
            TouchZoneAction.PHOTOVIEW_GESTURE -> "Photo gesture"
            TouchZoneAction.NONE -> "No action"
        }
    }
}
