package com.sza.fastmediasorter.wear.ui.slideshow

import kotlinx.coroutines.flow.StateFlow

/**
 * Controller interface for managing slideshow state and auto-advance logic.
 * Can be implemented for different media types (images, audio, video).
 */
interface SlideshowController {
    
    /**
     * Whether slideshow is currently active.
     */
    val isActive: StateFlow<Boolean>
    
    /**
     * Current media index in the slideshow.
     */
    val currentIndex: StateFlow<Int>
    
    /**
     * Start the slideshow from the current index.
     */
    fun start()
    
    /**
     * Stop the slideshow completely.
     */
    fun stop()
    
    /**
     * Pause the slideshow (can be resumed).
     */
    fun pause()
    
    /**
     * Resume the slideshow from pause.
     */
    fun resume()
    
    /**
     * Manually advance to next item.
     */
    fun next()
    
    /**
     * Manually go to previous item.
     */
    fun previous()
}
