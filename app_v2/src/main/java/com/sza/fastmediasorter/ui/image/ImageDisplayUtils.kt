package com.sza.fastmediasorter.ui.image

import android.widget.ImageView

/**
 * Utility functions for determining how images should be displayed based on
 * orientation matching and user settings.
 */
object ImageDisplayUtils {
    
    /**
     * Determines if the image orientation matches the device orientation.
     * 
     * Both landscape, both portrait, or both square = match
     * Landscape vs portrait = no match
     * 
     * @param imageWidth Width of the image in pixels
     * @param imageHeight Height of the image in pixels
     * @param deviceWidth Width of the device display in pixels
     * @param deviceHeight Height of the device display in pixels
     * @return true if orientations match, false otherwise
     */
    fun isOrientationMatch(
        imageWidth: Int,
        imageHeight: Int,
        deviceWidth: Int,
        deviceHeight: Int
    ): Boolean {
        val isImageLandscape = imageWidth > imageHeight
        val isImagePortrait = imageWidth < imageHeight
        val isImageSquare = imageWidth == imageHeight
        
        val isDeviceLandscape = deviceWidth > deviceHeight
        val isDevicePortrait = deviceWidth < deviceHeight
        val isDeviceSquare = deviceWidth == deviceHeight
        
        // Square images match any orientation
        if (isImageSquare || isDeviceSquare) {
            return true
        }
        
        // Orientations match if both landscape or both portrait
        return (isImageLandscape && isDeviceLandscape) || (isImagePortrait && isDevicePortrait)
    }
    
    /**
     * Determines the appropriate ImageView.ScaleType based on settings and context.
     * 
     * Logic:
     * - If setting is OFF: always FIT_CENTER
     * - If setting is ON but not fullscreen/slideshow: FIT_CENTER
     * - If setting is ON and fullscreen/slideshow:
     *   - Orientations match: CENTER_CROP (fill screen)
     *   - Orientations mismatch: FIT_CENTER (preserve full image)
     * 
     * @param cropImagesToFullscreen User's crop setting value
     * @param isFullscreenOrSlideshow Whether currently in fullscreen or slideshow mode
     * @param imageWidth Width of the image in pixels
     * @param imageHeight Height of the image in pixels
     * @param deviceWidth Width of the device display in pixels
     * @param deviceHeight Height of the device display in pixels
     * @return The appropriate ScaleType to use
     */
    fun determineImageScaleType(
        cropImagesToFullscreen: Boolean,
        isFullscreenOrSlideshow: Boolean,
        imageWidth: Int,
        imageHeight: Int,
        deviceWidth: Int,
        deviceHeight: Int
    ): ImageView.ScaleType {
        // Setting disabled: always fit center
        if (!cropImagesToFullscreen) {
            return ImageView.ScaleType.FIT_CENTER
        }
        
        // Not in fullscreen/slideshow: always fit center
        if (!isFullscreenOrSlideshow) {
            return ImageView.ScaleType.FIT_CENTER
        }
        
        // In fullscreen/slideshow with setting enabled: check orientation match
        return if (isOrientationMatch(imageWidth, imageHeight, deviceWidth, deviceHeight)) {
            ImageView.ScaleType.CENTER_CROP
        } else {
            ImageView.ScaleType.FIT_CENTER
        }
    }
}
