package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import timber.log.Timber

/**
 * Base class for document viewers (PDF, EPUB) with shared touch zone gesture logic.
 * Provides unified navigation zones for page/chapter turning in both fullscreen and normal modes.
 * 
 * Touch Zones (Fullscreen Mode):
 * - Top zone (0-20%): Exit fullscreen
 * - Center-left (20-80% height, 0-20% width): Previous page/chapter
 * - Center-right (20-80% height, 80-100% width): Next page/chapter
 * - Center-middle (20-80% height, 20-80% width): Zoom/pan area (no action)
 * - Bottom zone (80-100%): Reserved (no action)
 * 
 * Touch Zones (Normal Mode):
 * - Active zone: 20-80% height only
 * - Left (0-20% width): Previous page/chapter
 * - Right (80-100% width): Next page/chapter
 * - Center (20-80% width): Zoom/pan area (no action)
 */
abstract class BaseDocumentViewerManager(
    protected val binding: ActivityPlayerUnifiedBinding
) {
    
    /**
     * Handle touch zones for document navigation.
     * @param x Touch X coordinate
     * @param y Touch Y coordinate
     * @param isFullscreen Whether command panel is hidden (fullscreen mode)
     */
    fun handleTouchZones(x: Float, y: Float, isFullscreen: Boolean) {
        val screenWidth = binding.root.width
        val screenHeight = binding.root.height
        
        if (screenWidth <= 0 || screenHeight <= 0) {
            Timber.w("BaseDocumentViewerManager: Invalid screen dimensions - ignoring touch")
            return
        }
        
        if (isFullscreen) {
            handleFullscreenTouchZones(x, y, screenWidth, screenHeight)
        } else {
            handleNormalTouchZones(x, y, screenWidth, screenHeight)
        }
    }
    
    /**
     * Handle touch zones in fullscreen mode (4 zones: top, center-left/middle/right, bottom)
     */
    private fun handleFullscreenTouchZones(x: Float, y: Float, screenWidth: Int, screenHeight: Int) {
        val topZoneHeight = screenHeight * 0.20f  // Top zone: 0-20%
        val centerZoneTop = screenHeight * 0.20f   // Center start: 20%
        val centerZoneBottom = screenHeight * 0.80f // Center end: 80%
        
        when {
            y < topZoneHeight -> {
                // Top zone (0-20%): Exit fullscreen
                Timber.d("DocumentTouchZones (fullscreen): Top zone - exit fullscreen (y=$y)")
                onExitFullscreenRequest()
            }
            y in centerZoneTop..centerZoneBottom -> {
                // Center zone (20-80%): 3 sub-zones by width
                val leftBoundary = screenWidth * 0.20f
                val rightBoundary = screenWidth * 0.80f
                
                when {
                    x < leftBoundary -> {
                        // Left sub-zone: Previous page
                        Timber.d("DocumentTouchZones (fullscreen): Previous page (x=$x < $leftBoundary)")
                        onPreviousPageRequest()
                    }
                    x > rightBoundary -> {
                        // Right sub-zone: Next page
                        Timber.d("DocumentTouchZones (fullscreen): Next page (x=$x > $rightBoundary)")
                        onNextPageRequest()
                    }
                    else -> {
                        // Center sub-zone: zoom/pan (no action)
                        Timber.d("DocumentTouchZones (fullscreen): Center - skip for zoom")
                    }
                }
            }
            else -> {
                // Bottom zone (80-100%): reserved (no action)
                Timber.d("DocumentTouchZones (fullscreen): Bottom zone - skip (y=$y)")
            }
        }
    }
    
    /**
     * Handle touch zones in normal mode (3 zones within 20-80% height)
     */
    private fun handleNormalTouchZones(x: Float, y: Float, screenWidth: Int, screenHeight: Int) {
        val topMargin = screenHeight * 0.20f
        val bottomMargin = screenHeight * 0.80f
        
        // Check if touch is in active zone
        if (y < topMargin || y > bottomMargin) {
            Timber.d("DocumentTouchZones (normal): Outside active zone (y=$y)")
            return  // Outside active zone
        }
        
        // Divide by width: 20% | 60% | 20%
        val leftBoundary = screenWidth * 0.20f
        val rightBoundary = screenWidth * 0.80f
        
        when {
            x < leftBoundary -> {
                // Left zone: Previous page
                Timber.d("DocumentTouchZones (normal): Previous page (x=$x)")
                onPreviousPageRequest()
            }
            x > rightBoundary -> {
                // Right zone: Next page
                Timber.d("DocumentTouchZones (normal): Next page (x=$x)")
                onNextPageRequest()
            }
            else -> {
                // Center zone: zoom/pan (no action)
                Timber.d("DocumentTouchZones (normal): Center - skip for zoom")
            }
        }
    }
    
    /**
     * Abstract methods to be implemented by subclasses
     */
    abstract fun onPreviousPageRequest()
    abstract fun onNextPageRequest()
    abstract fun onExitFullscreenRequest()
    abstract fun isInFullscreenMode(): Boolean
}
