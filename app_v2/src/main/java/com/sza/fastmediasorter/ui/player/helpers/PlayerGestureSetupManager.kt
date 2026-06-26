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
    private val safeViews = PlayerBindingSafeViews(binding)
    private val videoTouchDelegate = VideoTouchDelegate(activity, binding)

    private lateinit var gestureDetector: GestureDetector
    private lateinit var imageTouchGestureDetector: GestureDetector

    // S0694: a live video stream toggles between immersive fullscreen and command-panel mode on a single
    // tap. Entering command-panel mode reveals topCommandPanel (with btnFullscreenCmd, the return-to-
    // fullscreen affordance) and shows the ExoPlayer bottom controller; returning to fullscreen hides both.
    // A dedicated detector keeps this off the 9/3-zone path and ignores scroll/long-press so it never
    // competes with future stream gestures.
    private val streamSurfaceGestureDetector: GestureDetector by lazy {
        GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!viewModel.state.value.isLiveVideoStream) return false
                if (viewModel.state.value.showCommandPanel) {
                    viewModel.enterFullscreenMode()
                    binding.playerView.hideController()
                } else {
                    viewModel.enterCommandPanelMode()
                    binding.playerView.showController()
                }
                activity.updateSystemBarsForPlayer(viewModel.state.value.showCommandPanel)
                return true
            }
        })
    }

    // Last ACTION_DOWN point on the PDF PhotoView (view coordinates), used to pre-select
    // the word under a long-press. NaN until the first PDF touch-down is observed.
    private var lastPdfDownX: Float = Float.NaN
    private var lastPdfDownY: Float = Float.NaN

    // ════════════════════════════════════════════════════════════════════════
    // DUAL-SURFACE SUPPORT (D.5 Gesture Unification)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Currently active PhotoView surface (A or B).
     * In legacy mode, always returns binding.photoView.
     * In renderer mode, returns the surface currently showing content.
     */
    val activePhotoView: com.github.chrisbanes.photoview.PhotoView
        get() = getVisiblePhotoView() ?: binding.photoView

    /**
     * Check if any PhotoView surface is visible and active.
     * Supports both legacy single-surface and dual-surface modes.
     */
    fun isAnyPhotoViewActive(): Boolean {
        return binding.photoView.isVisible || (binding.photoViewSurfaceB?.isVisible == true)
    }

    /**
     * Get the visible PhotoView (for zoom queries, scale checks, etc.).
     * Returns the first visible surface, preferring A (current) over B (prepared).
     */
    fun getVisiblePhotoView(): com.github.chrisbanes.photoview.PhotoView? {
        return when {
            binding.photoView.isVisible -> binding.photoView
            binding.photoViewSurfaceB?.isVisible == true -> binding.photoViewSurfaceB
            else -> null
        }
    }
    
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
        Timber.d("TOUCH_DEBUG: ========== setupGestureDetector() CALLED ==========")
        gestureDetector = touchZoneGestureManager.createGestureDetector(activity)
        imageTouchGestureDetector = touchZoneGestureManager.createImageTouchGestureDetector(activity)
        
        setupRootTouchListener()
        setupPlayerViewTouchListener()
        setupPhotoViewTouchListener()
        setupImageViewTouchListener()
        Timber.d("TOUCH_DEBUG: ========== setupGestureDetector() COMPLETE ==========")
    }
    
    /**
     * Setup touch listener for root view (main container).
     * Handles touch zone routing for all media types.
     */
    private fun setupRootTouchListener() {
        binding.root.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            val currentFile = viewModel.state.value.currentFile
            val isInFullscreenMode = !viewModel.state.value.showCommandPanel
            val isVideo = currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO
            val isImage = currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF
            val isPdfOrEpub = currentFile?.type == MediaType.PDF || currentFile?.type == MediaType.EPUB
            val isText = currentFile?.type == MediaType.TEXT

            // S0694: never consume a live-video-stream tap here - the 9/3-zone grid does not apply to a
            // stream. Let it reach the PlayerView surface, where VideoTouchDelegate toggles the controller.
            if (viewModel.state.value.isLiveVideoStream) {
                return@setOnTouchListener false
            }

            Timber.d("PlayerActivity.root.onTouch: action=${event.action}, type=${currentFile?.type}, fullscreen=$isInFullscreenMode, touchZones=$useTouchZones")
            
            // For Text files: don't intercept touches, let TextViewerManager handle scrolling/gestures
            if (isText && safeViews.textViewerContainer.isVisible && !isOverlayBlocking()) {
                return@setOnTouchListener false
            }

            // For Audio Slideshow Photo Mode: don't intercept touches - let imageView handle tap-to-exit
            if (activity.isInAudioSlideshowPhotoMode() && binding.imageView.isVisible) {
                Timber.d("PlayerActivity.root.onTouch: Audio slideshow photo mode - passing touch to imageView")
                return@setOnTouchListener false
            }
            
            // For PDF/EPUB in non-fullscreen mode: intercept touches for navigation zones
            // But only when overlays are NOT blocking (translation/OCR/Lens)
            if (isPdfOrEpub && !isInFullscreenMode && event.action == MotionEvent.ACTION_DOWN) {
                // Check if touch is in PDF/EPUB controls area (bottom panel)
                val pdfControlsVisible = safeViews.pdfControlsLayout.isVisible
                val epubControlsVisible = safeViews.epubControlsLayout.isVisible
                
                if (pdfControlsVisible && event.y >= safeViews.pdfControlsLayout.top) {
                    // Touch is in PDF controls area - let buttons handle it
                    return@setOnTouchListener false
                }
                
                if (epubControlsVisible && event.y >= safeViews.epubControlsLayout.top) {
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

            // In command panel mode: don't intercept touches on bottom panels (Copy/Move sections)
            // Let panel headers and destination buttons handle their own clicks.
            if (!isInFullscreenMode && event.action == MotionEvent.ACTION_DOWN) {
                if (safeViews.bottomPanelsContainer.isVisible && event.y >= safeViews.bottomPanelsContainer.top) {
                    Timber.d("PlayerActivity.root.onTouch: touch in bottomPanelsContainer - passing through")
                    return@setOnTouchListener false
                }
            }
            
            
            // For images: Only delegate to PhotoView/ImageView if they're actually visible
            // If they're not visible (e.g., PDF showing as IMAGE type), handle with gesture detector
            //  - Fullscreen mode: 9-zone grid
            // - Command panel mode: 3-zone navigation (handleCommandPanelTouchZones)
            // This prevents double-processing of touch events which breaks double-tap detection
            if (isImage && useTouchZones && !isOverlayBlocking()) {
                val isPhotoViewVisible = isAnyPhotoViewActive() // D.5: Check both surfaces
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
        binding.playerView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            val state = viewModel.state.value
            val currentFile = state.currentFile
            val isInFullscreenMode = !state.showCommandPanel
            val isVideo = currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO

            // S0694: a live video stream taps between immersive fullscreen and command-panel mode. The
            // 9/3-zone grid carries managed-file actions that do not apply to a stream, so route the tap
            // to the dedicated stream detector instead. Consume the sequence so PlayerView's own controller
            // does not also react (it is driven explicitly from the detector).
            if (state.isLiveVideoStream) {
                streamSurfaceGestureDetector.onTouchEvent(event)
                return@setOnTouchListener true
            }

            // DISABLED: Video gestures (F.1) - using 9-zone touch zones instead (per specification)
            // Previously: videoTouchDelegate handled brightness/volume/seek gestures for video
            // Now: using TouchZoneGestureManager with 9-zone grid (REG-975) for video navigation
            if (false) { // Disabled VideoTouchDelegate to enable 9-zone touch zones for video
                if (videoTouchDelegate.handleTouchEvent(event)) {
                    return@setOnTouchListener true
                }
                return@setOnTouchListener false
            }

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

                // For video/audio in fullscreen mode with 9-zone layout:
                // - Let TouchZoneGestureManager handle ALL taps in upper area (9 zones)
                // - Center zone action (Pause/Resume) is handled by TouchZoneGestureManager
                // - Do NOT pass center taps to PlayerView to avoid conflict with 9-zone layout
                
                // Use our gesture detector for touch zones
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
     * Configures gestures for BOTH surfaces (A and B) for dual-surface support.
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
        // Configure gestures for both surfaces (dual-surface D.5)
        configurePhotoViewGestures(binding.photoView, "A")
        // Surface B may be null in landscape layout
        binding.photoViewSurfaceB?.let { surfaceB ->
            configurePhotoViewGestures(surfaceB, "B")
        }
        Timber.d("TOUCH_DEBUG: Gesture listeners configured for PhotoView surfaces")
    }

    /**
     * Configure gesture listeners for a single PhotoView surface.
     * Extracted for dual-surface support (D.5 Gesture Unification).
     *
     * @param photoView The PhotoView to configure
     * @param surfaceId Surface identifier for logging ("A" or "B")
     */
    private fun configurePhotoViewGestures(
        photoView: com.github.chrisbanes.photoview.PhotoView,
        surfaceId: String
    ) {
        // Handle single-tap (zone navigation) and double-tap (zoom) via attacher's GestureDetector
        photoView.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                Timber.d("TOUCH_DEBUG: photoView$surfaceId.onSingleTapConfirmed - x=${e.x.toInt()}, y=${e.y.toInt()}")
                
                if (isOverlayBlocking()) {
                    Timber.d("TOUCH_DEBUG: photoView$surfaceId.onSingleTapConfirmed - overlay blocking, returning false")
                    return false
                }
                
                val isPdf = viewModel.state.value.currentFile?.type == MediaType.PDF
                Timber.d("TOUCH_DEBUG: photoView$surfaceId.onSingleTapConfirmed - isPdf=$isPdf")
                
                // PDF: forward tap to link detector (opens URLs); falls through to false if no link
                // IMAGE: Use touch zones (REG-3100 command panel / REG-9100 fullscreen)
                if (isPdf) {
                    Timber.d("TOUCH_DEBUG: photoView$surfaceId.onSingleTapConfirmed - PDF active, checking link tap x=${e.x} y=${e.y}")
                    return activity._pdfViewerManager?.handlePdfTap(e.x, e.y) ?: false
                }
                
                Timber.d("TOUCH_DEBUG: photoView$surfaceId.onSingleTapConfirmed - routing to handleImageSingleTap")
                val rootEvent = toRootCoordinatesEvent(photoView, e)
                val result = touchZoneGestureManager.handleImageSingleTap(rootEvent)
                rootEvent.recycle()
                Timber.d("TOUCH_DEBUG: photoView$surfaceId.onSingleTapConfirmed - handleImageSingleTap returned $result")
                return result
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isOverlayBlocking()) return false
                
                // PDF: No double-tap zoom (use pinch-to-zoom only)
                // IMAGE: Toggle zoom 2x/1x (command panel) or 3x/1x (fullscreen)
                if (viewModel.state.value.currentFile?.type == MediaType.PDF) {
                    return false // PDF spec: pinch-to-zoom only
                }
                
                val rootEvent = toRootCoordinatesEvent(photoView, e)
                val result = touchZoneGestureManager.handleImageDoubleTap(rootEvent)
                rootEvent.recycle()
                return result
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean = false
        })

        // Handle fling (swipe) gestures - ONLY for PDF (vertical swipes for page navigation)
        // For IMAGES: fling is DISABLED to avoid conflict with onSingleTapConfirmed
        // (GestureDetector cannot reliably distinguish between quick tap and slow swipe)
        photoView.setOnSingleFlingListener(
            OnSingleFlingListener { e1, e2, velocityX, velocityY ->
                if (isOverlayBlocking()) return@OnSingleFlingListener false
                
                // Only handle fling for PDF (vertical swipes for page navigation)
                // For images: fling disabled to prevent conflict with tap zones
                if (viewModel.state.value.currentFile?.type == MediaType.PDF) {
                    activity.pdfViewerManager.handlePdfFling(e1, e2, velocityX, velocityY)
                } else {
                    // IMAGE: fling disabled, return false to let PhotoView handle pan gestures
                    false
                }
            }
        )

        // Record the PDF touch-down point so a long-press can pre-select the word under it.
        // Forward every event to the PhotoView attacher so zoom/pan/fling stay intact.
        val attacher = photoView.attacher
        photoView.setOnTouchListener { v, ev ->
            if (ev.actionMasked == MotionEvent.ACTION_DOWN &&
                viewModel.state.value.currentFile?.type == MediaType.PDF
            ) {
                lastPdfDownX = ev.x
                lastPdfDownY = ev.y
            }
            attacher.onTouch(v, ev)
        }

        // Handle long press - route to PDF text selection or image zoom
        photoView.setOnLongClickListener {
            if (isOverlayBlocking()) return@setOnLongClickListener false

            // Route to PDF handler if PDF is active, else image zoom
            if (viewModel.state.value.currentFile?.type == MediaType.PDF) {
                activity.pdfViewerManager.handlePdfLongPress(lastPdfDownX, lastPdfDownY)
            } else {
                touchZoneGestureManager.handleImageLongPress()
            }
        }
    }

    private fun toRootCoordinatesEvent(
        sourceView: android.view.View,
        sourceEvent: MotionEvent
    ): MotionEvent {
        val rootLocation = IntArray(2)
        val sourceLocation = IntArray(2)
        binding.root.getLocationOnScreen(rootLocation)
        sourceView.getLocationOnScreen(sourceLocation)

        val rootX = sourceEvent.x + (sourceLocation[0] - rootLocation[0])
        val rootY = sourceEvent.y + (sourceLocation[1] - rootLocation[1])

        return MotionEvent.obtain(sourceEvent).apply {
            setLocation(rootX, rootY)
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
