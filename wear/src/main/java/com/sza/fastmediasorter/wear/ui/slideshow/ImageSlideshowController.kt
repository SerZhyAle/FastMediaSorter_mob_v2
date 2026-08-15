package com.sza.fastmediasorter.wear.ui.slideshow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Slideshow controller for images with timer-based auto-advance.
 * 
 * @param scope Coroutine scope for timer operations
 * @param intervalSeconds Interval in seconds between image transitions
 * @param totalItems Total number of items in the slideshow
 * @param onIndexChanged Callback when index changes (for navigation)
 */
class ImageSlideshowController(
    private val scope: CoroutineScope,
    private val intervalSeconds: Int,
    private val totalItems: Int,
    private val onIndexChanged: (Int) -> Unit
) : SlideshowController {
    
    private val _isActive = MutableStateFlow(false)
    override val isActive: StateFlow<Boolean> = _isActive.asStateFlow()
    
    private val _currentIndex = MutableStateFlow(0)
    override val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()
    
    private var timerJob: Job? = null
    private var isPaused = false
    
    override fun start() {
        Timber.d("Starting slideshow with interval: ${intervalSeconds}s")
        _isActive.value = true
        isPaused = false
        startTimer()
    }
    
    override fun stop() {
        Timber.d("Stopping slideshow")
        _isActive.value = false
        isPaused = false
        timerJob?.cancel()
        timerJob = null
    }
    
    override fun pause() {
        Timber.d("Pausing slideshow")
        isPaused = true
        timerJob?.cancel()
        timerJob = null
    }
    
    override fun resume() {
        Timber.d("Resuming slideshow")
        if (_isActive.value && isPaused) {
            isPaused = false
            startTimer()
        }
    }
    
    override fun onManualNavigation(index: Int) {
        Timber.d("Host navigated to $index")
        _currentIndex.value = index

        // Deliberately no onIndexChanged call: the host has already moved to this index and is only
        // telling the controller to follow. The previous next()/previous() pair did fire it, which
        // navigated a second time - and because _currentIndex was never seeded with the file the
        // user opened, that second hop always landed on index 1 (S1683).
        if (_isActive.value && !isPaused) {
            timerJob?.cancel()
            startTimer()
        }
    }

    private fun startTimer() {
        timerJob = scope.launch {
            delay(intervalSeconds * 1000L)
            if (_isActive.value && !isPaused) {
                advanceToNext()
                startTimer() // Schedule next advance
            }
        }
    }
    
    private fun advanceToNext() {
        val newIndex = if (_currentIndex.value < totalItems - 1) {
            _currentIndex.value + 1
        } else {
            0 // Loop to beginning
        }
        
        _currentIndex.value = newIndex
        onIndexChanged(newIndex)
    }
}
