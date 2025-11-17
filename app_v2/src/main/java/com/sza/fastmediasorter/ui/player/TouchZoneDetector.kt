package com.sza.fastmediasorter.ui.player

/**
 * Touch zones for static image in full-screen mode (3x3 grid)
 * According to V2 Specification section "Player Screen"
 */
enum class TouchZone {
    BACK,           // Top-left 30x30%
    COPY,           // Top-center 40x30%
    RENAME,         // Top-right 30x30%
    PREVIOUS,       // Left 30x40%
    MOVE,           // Center 40x40%
    NEXT,           // Right 30x40%
    COMMAND_PANEL,  // Bottom-left 30x30%
    DELETE,         // Bottom-center 40x30%
    SLIDESHOW,      // Bottom-right 30x30%
    NONE            // Outside zones or for video
}

/**
 * Helper class to detect touch zones on screen
 */
class TouchZoneDetector {
    
    /**
     * Detect which zone was touched based on screen coordinates
     * @param x Touch X coordinate
     * @param y Touch Y coordinate
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels (or active area height for video)
     * @return TouchZone that was touched
     */
    fun detectZone(x: Float, y: Float, screenWidth: Int, screenHeight: Int): TouchZone {
        // Calculate zone boundaries
        val leftBoundary = screenWidth * 0.3f
        val rightBoundary = screenWidth * 0.7f
        val topBoundary = screenHeight * 0.3f
        val bottomBoundary = screenHeight * 0.7f
        
        // Determine row (top, middle, bottom)
        val row = when {
            y < topBoundary -> 0 // Top row
            y < bottomBoundary -> 1 // Middle row
            else -> 2 // Bottom row
        }
        
        // Determine column (left, center, right)
        val column = when {
            x < leftBoundary -> 0 // Left column
            x < rightBoundary -> 1 // Center column
            else -> 2 // Right column
        }
        
        // Map row/column to zone
        return when (row) {
            0 -> when (column) {
                0 -> TouchZone.BACK
                1 -> TouchZone.COPY
                2 -> TouchZone.RENAME
                else -> TouchZone.NONE
            }
            1 -> when (column) {
                0 -> TouchZone.PREVIOUS
                1 -> TouchZone.MOVE
                2 -> TouchZone.NEXT
                else -> TouchZone.NONE
            }
            2 -> when (column) {
                0 -> TouchZone.COMMAND_PANEL
                1 -> TouchZone.DELETE
                2 -> TouchZone.SLIDESHOW
                else -> TouchZone.NONE
            }
            else -> TouchZone.NONE
        }
    }
    
    /**
     * Get zone description for debugging
     */
    fun getZoneDescription(zone: TouchZone): String {
        return when (zone) {
            TouchZone.BACK -> "Back to Browse"
            TouchZone.COPY -> "Copy file"
            TouchZone.RENAME -> "Rename file"
            TouchZone.PREVIOUS -> "Previous file"
            TouchZone.MOVE -> "Move file"
            TouchZone.NEXT -> "Next file"
            TouchZone.COMMAND_PANEL -> "Show command panel"
            TouchZone.DELETE -> "Delete file"
            TouchZone.SLIDESHOW -> "Toggle slideshow"
            TouchZone.NONE -> "No action"
        }
    }
}
