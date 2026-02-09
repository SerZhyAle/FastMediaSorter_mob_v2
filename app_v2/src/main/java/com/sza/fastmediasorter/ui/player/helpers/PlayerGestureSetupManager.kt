package com.sza.fastmediasorter.ui.player.helpers

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.github.chrisbanes.photoview.OnSingleFlingListener
import timber.log.Timber

/**
 * Manages gesture detector setup and touch event handling for PlayerActivity.
 * 
 * Consolidates complex touch zone logic for different media types and screen modes:
 * - Images: Touch zones (9-zone grid in fullscreen, 3-zone in command panel)
 * - Video/Audio: Upper area for navigation, bottom reserved for player controls
 * - PDF/EPUB: Touch zones when not in fullscreen, respect control panels
 * - Text: Delegates to TextViewerManager for scrolling
 * 
 * Handles overlay blocking (translation, OCR, lyrics) to prevent conflicts.
 * 
 * Extracted from PlayerActivity to reduce size and improve organization.
 */
class PlayerGestureSetupManager(
    private val activity: PlayerActivity,
    private val binding: ActivityPlayerUnifiedBinding,
    private val viewModel: PlayerViewModel,
    private val touchZoneGestureManager: TouchZoneGestureManager
) {
    
    private lateinit var gestureDetector: GestureDetector
    private lateinit var imageTouchGestureDetector: GestureDetector
    
    /**
     * Get whether touch zones are enabled.
     * Accessed by touch listeners.
     */
    private val useTouchZones: Boolean
        get() = activity.getTouchZonesEnabled()
    
    /**
     * Check if overlays are blocking touch zones.
     * Accessed by touch listeners.
     */
    private fun isOverlayBlocking(): Boolean {
        return activity.isOverlayBlocking()
    }
    
    /**
     * Setup all gesture detectors and touch listeners.
     * Called once from PlayerActivity.setupViews().
     */
    fun setupGestureDetector() {
        Timber.w("TOUCH_DEBUG: ========== setupGestureDetector() CALLED ==========")
        gestureDetector = touchZoneGestureManager.createGestureDetector(activity)
        imageTouchGestureDetector = touchZoneGestureManager.createImageTouchGestureDetector(activity)
        
        setupRootTouchListener()
        setupPlayerViewTouchListener()
        setupPhotoViewTouchListener()
        setupImageViewTouchListener()
        Timber.w("TOUCH_DEBUG: ========== setupGestureDetector() COMPLETE ==========")
    }
    
    /**
     * Setup touch listener for root view (main container).
     * Handles touch zone routing for all media types.
     */
    private fun setupRootTouchListener() {
        binding.root.setOnTouchListener { _, event ->
            val currentFile = viewModel.state.value.currentFile
            val isInFullscreenMode = !viewModel.state.value.showCommandPanel
            val isVideo = currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO
            val isImage = currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF
            val isPdfOrEpub = currentFile?.type == MediaType.PDF || currentFile?.type == MediaType.EPUB
            val isText = currentFile?.type == MediaType.TEXT
            
            Timber.d("PlayerActivity.root.onTouch: action=${event.action}, type=${currentFile?.type}, fullscreen=$isInFullscreenMode, touchZones=$useTouchZones")
            
            // For Text files: don't intercept touches, let TextViewerManager handle scrolling/gestures
            if (isText && binding.textViewerContainer.isVisible && !isOverlayBlocking()) {
                return@setOnTouchListener false
            }
            
            // For PDF/EPUB in non-fullscreen mode: intercept touches for navigation zones
            // But only when overlays are NOT blocking (translation/OCR/Lens)
            if (isPdfOrEpub && !isInFullscreenMode && event.action == MotionEvent.ACTION_DOWN) {
                // Check if touch is in PDF/EPUB controls area (bottom panel)
                val pdfControlsVisible = binding.pdfControlsLayout.isVisible
                val epubControlsVisible = binding.epubControlsLayout.isVisible
                
                if (pdfControlsVisible && event.y >= binding.pdfControlsLayout.top) {
                    // Touch is in PDF controls area - let buttons handle it
                    return@setOnTouchListener false
                }
                
                if (epubControlsVisible && event.y >= binding.epubControlsLayout.top) {
                    // Touch is in EPUB controls area - let buttons handle it
                    return@setOnTouchListener false
                }
                
                if (!isOverlayBlocking()) {
                    // Let gesture detector handle for document touch zones
                    gestureDetector.onTouchEvent(event)
                    return@setOnTouchListener false // Don't consume - allow PhotoView/WebView zoom/pan
                }
                // Overlays visible - don't use touch zones, let views handle normally
                return@setOnTouchListener false
            }
            
            // For video/audio in fullscreen mode, reserve bottom area for PlayerView controls
            if (isVideo && isInFullscreenMode && event.action == MotionEvent.ACTION_DOWN) {
                val screenHeight = binding.root.height
                val effectiveHeight = when (currentFile?.type) {
                    MediaType.AUDIO -> (screenHeight * 0.66f).toInt() // Upper 66% (2/3) for audio
                    MediaType.VIDEO -> (screenHeight * 0.75f).toInt() // Upper 75% for video
                    else -> screenHeight
                }
                
                // If touch is in bottom reserved area, don't consume the event - let it pass to PlayerView
                if (event.y > effectiveHeight) {
                    return@setOnTouchListener false
                }
            }
            
            // For video/audio in command panel mode, reserve bottom 30% for player controls
            if (isVideo && !isInFullscreenMode && event.action == MotionEvent.ACTION_DOWN) {
                val screenHeight = binding.root.height
                val effectiveHeight = (screenHeight * 0.7f).toInt() // Upper 70% for navigation
                
                // If touch is in bottom 30%, don't consume the event - let it pass to PlayerView
                if (event.y > effectiveHeight) {
                    return@setOnTouchListener false
                }
            }
            
            // In command panel mode: don't intercept touches on toolbar/top command panel
            // Let buttons (Google Lens, OCR, etc.) receive their clicks
            if (!isInFullscreenMode && event.action == MotionEvent.ACTION_DOWN) {
                val toolbarBottom = binding.toolbar.bottom
                val topCommandPanelBottom = if (binding.topCommandPanel.isVisible) binding.topCommandPanel.bottom else 0
                val topAreaBottom = maxOf(toolbarBottom, topCommandPanelBottom)
                
                if (event.y < topAreaBottom) {
                    return@setOnTouchListener false // Let toolbar/command panel buttons handle it
                }
            }
            
            
            // For images: Only delegate to PhotoView/ImageView if they're actually visible
            // If they're not visible (e.g., PDF showing as IMAGE type), handle with gesture detector
            //  - Fullscreen mode: 9-zone grid
            // - Command panel mode: 3-zone navigation (handleCommandPanelTouchZones)
            // This prevents double-processing of touch events which breaks double-tap detection
            if (isImage && useTouchZones && !isOverlayBlocking()) {
                val isPhotoViewVisible = binding.photoView.isVisible
                val isImageViewVisible = binding.imageView.isVisible
                
                if (isPhotoViewVisible || isImageViewVisible) {
                    // Image views are visible - let their listeners handle touch zones
                    return@setOnTouchListener false 
                } else {
                    // No image view visible - file type may be misdetected (e.g., PDF detected as IMAGE)
                    // Handle with gesture detector instead
                    Timber.d("Image type but no image view visible - using gesture detector")
                    // Continue to gestureDetector.onTouchEvent below
                }
            }
            
            // Let gesture detector handle the event for video/audio
            // For images  without visible views and upper area of video, consume the event (return true)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    
    /**
     * Setup touch listener for PlayerView (ExoPlayer container).
     * Handles video/audio touch zones with reserved bottom area for player controls.
     */
    private fun setupPlayerViewTouchListener() {
        binding.playerView.setOnTouchListener { _, event ->
            val currentFile = viewModel.state.value.currentFile
            val isInFullscreenMode = !viewModel.state.value.showCommandPanel
            val isVideo = currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO
            
            // In fullscreen mode with touch zones enabled, let our gesture detector handle it
            // BUT: allow bottom area to pass through for player controls
            if (isInFullscreenMode && useTouchZones) {
                val screenHeight = binding.root.height
                val effectiveHeight = when (currentFile?.type) {
                    MediaType.AUDIO -> (screenHeight * 0.66f).toInt() // Upper 66% (2/3) for audio
                    MediaType.VIDEO -> (screenHeight * 0.75f).toInt() // Upper 75% for video
                    else -> screenHeight
                }
                
                // If touch is in bottom reserved area, let PlayerView handle it (show controls)
                if (event.y > effectiveHeight) {
                    return@setOnTouchListener false // Don't consume - let PlayerView handle controls
                }

                // Check for center zone tap (Video/Audio only)
                // If tap is in the middle 30% (35% to 65%), let PlayerView handle it (Toggle Controls)
                val isVideoOrAudio = currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO
                if (isVideoOrAudio) {
                    val screenWidth = binding.root.width
                    val leftBoundary = screenWidth * 0.35f
                    val rightBoundary = screenWidth * 0.65f
                    
                    if (event.x > leftBoundary && event.x < rightBoundary) {
                         Timber.d("PlayerGestureSetupManager: Center tap/touch detected (${event.x}) - Letting PlayerView handle toggle")
                         return@setOnTouchListener false
                    }
                }
                
                // Otherwise, use our gesture detector for touch zones
                gestureDetector.onTouchEvent(event)
                return@setOnTouchListener true // Consume event to prevent PlayerView from handling it
            }
            
            // In command panel mode for video/audio, also use touch zones for navigation
            // Reserve bottom 30% for player controls
            if (!isInFullscreenMode && isVideo) {
                val screenHeight = binding.root.height
                val effectiveHeight = (screenHeight * 0.7f).toInt() // Upper 70% for navigation
                
                // If touch is in bottom 30%, let PlayerView handle it (show controls)
                if (event.y > effectiveHeight) {
                    return@setOnTouchListener false // Don't consume - let PlayerView handle controls
                }
                
                // Check for center zone tap (Video/Audio only) - consistent with Fullscreen logic
                // If tap is in the middle 20% (40% to 60%), let PlayerView handle it (Toggle Controls)
                // Using 40-60 split to match TouchZoneGestureManager.handleCommandPanelTouchZones
                val screenWidth = binding.root.width
                val leftBoundary = screenWidth * 0.40f
                val rightBoundary = screenWidth * 0.60f
                
                if (event.x > leftBoundary && event.x < rightBoundary) {
                     Timber.d("PlayerGestureSetupManager: Center tap/touch detected (${event.x}) - Letting PlayerView handle toggle")
                     return@setOnTouchListener false
                }
                
                // Otherwise, use our gesture detector for simplified touch zones
                gestureDetector.onTouchEvent(event)
                return@setOnTouchListener true // Consume event to prevent PlayerView from handling it
            }
            
            // Otherwise, let PlayerView handle its own touches (controls)
            false
        }
    }
    
    /**
     * Setup PhotoView gesture handling using native PhotoView API listeners.
     *
     * CRITICAL: Do NOT call binding.photoView.setOnTouchListener() here!
     * PhotoView 2.3.0 uses PhotoViewAttacher which registers itself as the View's OnTouchListener
     * in its constructor. Replacing it with a custom listener breaks ALL native PhotoView gestures
     * (pinch zoom, pan, double-tap) because the attacher's onTouch() is no longer called,
     * and returning false from a custom listener causes nobody to claim the gesture.
     *
     * Instead, we use PhotoView's native callback API which hooks into the attacher's
     * internal GestureDetector without breaking its touch event processing:
     * - setOnDoubleTapListener: handles single-tap zone navigation + double-tap zoom
     * - setOnSingleFlingListener: handles swipe gestures
     * - setOnLongClickListener: handles long-press zoom
     */
    private fun setupPhotoViewTouchListener() {
        Timber.d("TOUCH_DEBUG: setupPhotoViewTouchListener() called")
        // Handle single-tap (zone navigation) and double-tap (zoom) via attacher's GestureDetector
        binding.photoView.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                Timber.d("TOUCH_DEBUG: photoView.onSingleTapConfirmed - x=${e.x.toInt()}, y=${e.y.toInt()}")
                
                if (isOverlayBlocking()) {
                    Timber.d("TOUCH_DEBUG: photoView.onSingleTapConfirmed - overlay blocking, returning false")
                    return false
                }
                
                val isPdf = activity.isPdfActive()
                Timber.d("TOUCH_DEBUG: photoView.onSingleTapConfirmed - isPdf=$isPdf")
                
                // PDF (REG-DOC): No tap zones, single tap does nothing
                // IMAGE: Use touch zones (REG-3100 command panel / REG-9100 fullscreen)
                if (isPdf) {
                    Timber.d("TOUCH_DEBUG: photoView.onSingleTapConfirmed - PDF active, returning false")
                    return false // PDF spec: no tap zones
                }
                
                Timber.d("TOUCH_DEBUG: photoView.onSingleTapConfirmed - routing to handleImageSingleTap")
                val result = touchZoneGestureManager.handleImageSingleTap(e)
                Timber.d("TOUCH_DEBUG: photoView.onSingleTapConfirmed - handleImageSingleTap returned $result")
                return result
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isOverlayBlocking()) return false
                
                // PDF: No double-tap zoom (use pinch-to-zoom only)
                // IMAGE: Toggle zoom 2x/1x (command panel) or 3x/1x (fullscreen)
                if (activity.isPdfActive()) {
                    return false // PDF spec: pinch-to-zoom only
                }
                
                return touchZoneGestureManager.handleImageDoubleTap(e)
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean = false
        })

        // Handle fling (swipe) gestures — ONLY for PDF (vertical swipes for page navigation)
        // For IMAGES: fling is DISABLED to avoid conflict with onSingleTapConfirmed
        // (GestureDetector cannot reliably distinguish between quick tap and slow swipe)
        binding.photoView.setOnSingleFlingListener(
            OnSingleFlingListener { e1, e2, velocityX, velocityY ->
                if (isOverlayBlocking()) return@OnSingleFlingListener false
                
                // Only handle fling for PDF (vertical swipes for page navigation)
                // For images: fling disabled to prevent conflict with tap zones
                if (activity.isPdfActive()) {
                    activity.pdfViewerManager.handlePdfFling(e1, e2, velocityX, velocityY)
                } else {
                    // IMAGE: fling disabled, return false to let PhotoView handle pan gestures
                    false
                }
            }
        )

        // Handle long press — route to PDF fullscreen or image zoom
        binding.photoView.setOnLongClickListener {
            if (isOverlayBlocking()) return@setOnLongClickListener false
            
            // Route to PDF handler if PDF is active, else image zoom
            if (activity.isPdfActive()) {
                activity.pdfViewerManager.handlePdfLongPress()
            } else {
                touchZoneGestureManager.handleImageLongPress()
            }
        }
    }
    
    /**
     * Setup touch listener for imageView (standard image viewer for thumbnails/quick view).
     * Handles image touch zones in command panel mode (3-zone).
     */
    private fun setupImageViewTouchListener() {
        Timber.d("TOUCH_DEBUG: setupImageViewTouchListener() called")
        binding.imageView.setOnTouchListener { _, event ->
            val actionStr = when (event.action) {
                MotionEvent.ACTION_DOWN -> "DOWN"
                MotionEvent.ACTION_UP -> "UP"
                MotionEvent.ACTION_MOVE -> "MOVE"
                MotionEvent.ACTION_CANCEL -> "CANCEL"
                else -> "OTHER(${event.action})"
            }
            Timber.d("TOUCH_DEBUG: imageView.onTouch - action=$actionStr, x=${event.x.toInt()}, y=${event.y.toInt()}")
            
            val currentFile = viewModel.state.value.currentFile
            val isImage = currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF
            
            // Don't handle touch zones when overlays (translation/OCR) are visible
            if (isOverlayBlocking()) {
                Timber.d("TOUCH_DEBUG: imageView.onTouch - overlay blocking, returning false")
                return@setOnTouchListener false // Let overlays handle their touches
            }
            
            // For images: always pass events to imageTouchGestureDetector (handles both fullscreen and command panel modes)
            if (isImage && binding.imageView.isVisible) {
                Timber.d("TOUCH_DEBUG: imageView.onTouch - passing to imageTouchGestureDetector")
                if (event.action == MotionEvent.ACTION_UP) {
                    touchZoneGestureManager.onUp(event)
                }
                val result = imageTouchGestureDetector.onTouchEvent(event)
                Timber.d("TOUCH_DEBUG: imageView.onTouch - gestureDetector returned $result, returning true")
                // MUST return true to claim the touch sequence.
                // If false is returned for ACTION_DOWN, Android will NOT send
                // subsequent MOVE/UP events, and GestureDetector can never fire
                // onSingleTapConfirmed (it needs the UP event to confirm).
                true
            } else {
                Timber.d("TOUCH_DEBUG: imageView.onTouch - not image or not visible, returning false")
                false // Not an image or not visible
            }
        }
    }
}
