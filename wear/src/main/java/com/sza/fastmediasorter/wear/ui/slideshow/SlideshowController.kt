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
     * Report that the host navigated on its own, so the controller can follow instead of leading.
     *
     * The controller must adopt [index] without emitting its index-changed callback: the host has
     * already moved, and calling back would navigate a second time (S1683).
     */
    fun onManualNavigation(index: Int)
}
