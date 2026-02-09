package com.sza.fastmediasorter.ui.player

import com.sza.fastmediasorter.domain.model.MediaType
import timber.log.Timber

/**
 * Helper class to detect touch zones on screen
 */
class TouchZoneDetector {
    
    /**
     * Detect touch zone action using new zone map system.
     * 
     * @param x Touch X coordinate
     * @param y Touch Y coordinate
     * @param screenWidth Screen width in pixels
     * @param screenHeight Screen height in pixels
     * @param zoneMap The zone map to use for detection
     * @return The action for the detected zone
     */
    fun detectAction(
        x: Float,
        y: Float,
        screenWidth: Int,
        screenHeight: Int,
        zoneMap: TouchZoneMap
    ): TouchZoneAction {
        val config = TouchZoneConfig.getConfiguration(zoneMap)
        
        // If taps are disabled for this zone map (REG-DOC), return NONE
        if (!config.tapsEnabled) {
            Timber.d("TouchZoneDetector: Taps disabled for $zoneMap")
            return TouchZoneAction.NONE
        }
        
        // Calculate effective height (accounting for reserved bottom area)
        val effectiveHeight = (screenHeight * config.heightPercent).toInt()
        
        // If touch is below effective height, ignore (reserved for controls)
        if (y > effectiveHeight) {
            Timber.d("TouchZoneDetector: Touch below effective height ($y > $effectiveHeight)")
            return TouchZoneAction.NONE
        }
        
        // Calculate column based on width ratios
        val column = calculateColumn(x, screenWidth, config.widthRatios)
        
        // Calculate row based on height ratios (only for 9-zone layouts)
        val row = if (config.verticalZones > 1) {
            calculateRow(y, effectiveHeight, config.heightRatios)
        } else {
            0 // Single row for 3-zone layouts
        }
        
        Timber.d("TouchZoneDetector: $zoneMap - touch($x, $y) -> row=$row, col=$column")
        
        // Get action based on zone map type
        return when (zoneMap) {
            TouchZoneMap.REG_9100, TouchZoneMap.REG_975 -> {
                TouchZoneConfig.get9ZoneTapAction(row, column)
            }
            TouchZoneMap.REG_3100 -> {
                TouchZoneConfig.get3ZoneImageTapAction(column)
            }
            TouchZoneMap.REG_375 -> {
                TouchZoneConfig.get3ZoneVideoTapAction(column)
            }
            TouchZoneMap.REG_DOC -> {
                TouchZoneAction.NONE
            }
        }
    }
    
    /**
     * Calculate column index based on x position and width ratios.
     */
    private fun calculateColumn(x: Float, screenWidth: Int, widthRatios: List<Float>): Int {
        var accumulated = 0f
        for (i in widthRatios.indices) {
            accumulated += widthRatios[i]
            if (x < screenWidth * accumulated) {
                return i
            }
        }
        return widthRatios.size - 1
    }
    
    /**
     * Calculate row index based on y position and height ratios.
     */
    private fun calculateRow(y: Float, effectiveHeight: Int, heightRatios: List<Float>): Int {
        var accumulated = 0f
        for (i in heightRatios.indices) {
            accumulated += heightRatios[i]
            if (y < effectiveHeight * accumulated) {
                return i
            }
        }
        return heightRatios.size - 1
    }
    
    /**
     * Check if a swipe starting at the given position should be ignored.
     * For REG-3100, swipes in the middle zone are handled by PhotoView.
     */
    fun shouldIgnoreSwipe(
        startX: Float,
        screenWidth: Int,
        zoneMap: TouchZoneMap
    ): Boolean {
        val config = TouchZoneConfig.getConfiguration(zoneMap)
        if (!config.ignoreMiddleZoneSwipes) {
            return false
        }
        
        // Check if swipe started in middle zone
        val column = calculateColumn(startX, screenWidth, config.widthRatios)
        return column == 1 // Middle column
    }
}
