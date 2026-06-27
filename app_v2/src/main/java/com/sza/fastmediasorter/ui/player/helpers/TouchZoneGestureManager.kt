package com.sza.fastmediasorter.ui.player.helpers

import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.SwipeDirection
import com.sza.fastmediasorter.ui.player.TouchZoneAction
import com.sza.fastmediasorter.ui.player.TouchZoneConfig
import com.sza.fastmediasorter.ui.player.TouchZoneDetector
import com.sza.fastmediasorter.ui.player.TouchZoneMap
import com.sza.fastmediasorter.utils.UserActionLogger
import timber.log.Timber

/**
 * Manages touch zone gesture detection and handling for PlayerActivity.
 * 
 * Responsibilities:
 * - GestureDetector setup for images (9-zone and 2-zone modes)
 * - Touch zone detection and routing based on fullscreen/command panel mode
 * - Coordinate-based zone calculation with effective height adjustments
 * 
 * Zone layouts:
 * - Fullscreen mode: 9-zone grid (3x3) for full navigation control
 * - Command panel mode: 2-zone (left/right) for simple navigation
 * - Video/Audio: Upper portion only (75%/66%) to reserve space for player controls
 */
class TouchZoneGestureManager(
    private val binding: ActivityPlayerUnifiedBinding,
    private val viewModel: PlayerViewModel,
    private val touchZoneDetector: TouchZoneDetector,
    private val callback: TouchZoneCallback
) {

    private enum class HorizontalZone {
        LEFT,
        CENTER,
        RIGHT
    }

    // ════════════════════════════════════════════════════════════════════════
    // DUAL-SURFACE SUPPORT (D.5 Gesture Unification)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Check if any PhotoView surface is visible.
     * Supports both legacy single-surface and dual-surface modes.
     */
    private fun isAnyPhotoViewVisible(): Boolean {
        return binding.photoView.isVisible || (binding.photoViewSurfaceB?.isVisible == true)
    }

    /**
     * Get the currently visible PhotoView for scale/zoom queries.
     * Returns the first visible surface, preferring A (current) over B (prepared).
     */
    private fun getVisiblePhotoView(): com.github.chrisbanes.photoview.PhotoView? {
        return when {
            binding.photoView.isVisible -> binding.photoView
            binding.photoViewSurfaceB?.isVisible == true -> binding.photoViewSurfaceB
            else -> null
        }
    }

    /**
     * Get current zoom scale from visible PhotoView.
     * Returns 1.0 if no PhotoView is visible.
     */
    private fun getCurrentPhotoViewScale(): Float {
        return getVisiblePhotoView()?.scale ?: 1.0f
    }

    private fun resolveHorizontalZone(rootX: Float): HorizontalZone {
        val targetView = getVisiblePhotoView() ?: binding.imageView.takeIf { it.isVisible }
        val leftBoundaryRatio: Float
        val rightBoundaryRatio: Float
        val effectiveX: Float

        if (targetView != null) {
            val location = IntArray(2)
            targetView.getLocationOnScreen(location)
            val rootLocation = IntArray(2)
            binding.root.getLocationOnScreen(rootLocation)
            val targetLeftInRoot = (location[0] - rootLocation[0]).toFloat()
            val targetWidth = targetView.width.toFloat().coerceAtLeast(1f)
            val normalized = ((rootX - targetLeftInRoot) / targetWidth).coerceIn(0f, 1f)
            effectiveX = normalized
            leftBoundaryRatio = 0.25f
            rightBoundaryRatio = 0.75f
        } else {
            val screenWidth = binding.root.width.toFloat().coerceAtLeast(1f)
            effectiveX = (rootX / screenWidth).coerceIn(0f, 1f)
            leftBoundaryRatio = 0.25f
            rightBoundaryRatio = 0.75f
        }

        return when {
            effectiveX < leftBoundaryRatio -> HorizontalZone.LEFT
            effectiveX < rightBoundaryRatio -> HorizontalZone.CENTER
            else -> HorizontalZone.RIGHT
        }
    }

    private fun isAnyImageSurfaceVisible(): Boolean {
        return isAnyPhotoViewVisible() || binding.imageView.isVisible
    }
    
    interface TouchZoneCallback {
        fun isOverlayBlocking(): Boolean
        fun getTouchZonesEnabled(): Boolean
        fun getLoadFullSizeImages(): Boolean
        // S0620: false -> fullscreen uses the 3-zone fallback instead of the 9-zone grid
        fun getNineZoneGridEnabled(): Boolean
        // Navigation
        fun onBack()
        fun onPrevious()
        fun onNext()
        // File operations
        fun onCopy()
        fun onRename()
        fun onMove()
        fun onDelete()
        // Mode switching
        fun onSwitchToCommandPanel()
        fun onToggleSlideshow()
        // Playback
        fun onPauseResume()
        fun onSeekToStart()
        fun onSeekToEnd()
        // Zoom
        fun onZoomIn()
        fun onZoomOut()
        fun setPhotoViewZoom(scale: Float)
        // Document navigation
        fun onPageUp()
        fun onPageDown()
        // Slideshow
        fun showSlideshowEnabledMessage()
        fun updateSlideShowButton()
        fun updateSlideShow()
    }
    
    // Track swipe start position for middle zone detection
    private var swipeStartX: Float = 0f

    private var lastUpTime: Long = 0

    /**
     * Called when ACTION_UP event is received.
     * Used to fix Android Emulator bug where GestureDetector fails to register the UP event
     * and incorrectly triggers onLongPress.
     */
    fun onUp(event: MotionEvent) {
        lastUpTime = event.eventTime
    }
    
    /**
     * GestureDetector for handling touch zones on ImageView/PhotoView.
     * Called from setOnTouchListener with low priority (after other UI elements).
     */
    fun createImageTouchGestureDetector(context: android.content.Context): GestureDetector {
        return GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                Timber.d("TOUCH_DEBUG: imageTouchGestureDetector.onDown - x=${e.x.toInt()}, y=${e.y.toInt()}")
                // Track swipe start position for middle zone detection
                swipeStartX = e.x
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                Timber.d("TOUCH_DEBUG: imageTouchGestureDetector.onSingleTapConfirmed - FIRED! x=${e.x.toInt()}, y=${e.y.toInt()}")
                val currentFile = viewModel.state.value.currentFile
                val state = viewModel.state.value
                // CRITICAL: During photo slideshow, always use fullscreen mode (9 zones) even if command panel is enabled
                val isInFullscreenMode = !state.showCommandPanel || state.isPhotoSlideshowActive
                val isImage = currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF
                
                Timber.d("SingleTap: pos=(${e.x.toInt()},${e.y.toInt()}), duration=${e.eventTime - e.downTime}ms, image=$isImage, fullscreen=$isInFullscreenMode, slideshow=${state.isSlideShowActive}")
                
                // Don't handle touch zones when overlays (translation/OCR) are visible
                if (callback.isOverlayBlocking()) {
                    Timber.d("SingleTap: overlay blocking, skipping touch zones")
                    return false
                }
                
                if (isImage && isAnyImageSurfaceVisible()) {
                    // In fullscreen mode: use 9-zone grid for top area, 3-zone for rest
                    if (isInFullscreenMode) {
                        Timber.d("SingleTap: IMAGE fullscreen mode -> handleTouchZone (9 zones)")
                        handleTouchZone(e.x, e.y)
                        return true
                    }
                    
                    // In command panel mode: use REG-3100 configuration (25% left | 50% center | 25% right)
                    val screenWidth = binding.root.width
                    val zoneMap = TouchZoneConfig.getZoneMapForMediaType(currentFile?.type, isFullscreen = false)
                    val config = TouchZoneConfig.getConfiguration(zoneMap)
                    
                    // Calculate boundaries from config.widthRatios [0.25, 0.50, 0.25]
                    val leftBoundary = screenWidth * config.widthRatios[0]
                    val rightBoundary = screenWidth * (config.widthRatios[0] + config.widthRatios[1])
                    
                    when {
                        e.x < leftBoundary -> {
                            // Left zone (0-25%): Previous file
                            Timber.d("SingleTap: Left zone -> PREVIOUS")
                            callback.onPrevious()
                            return true
                        }
                        e.x < rightBoundary -> {
                            // Center zone (25-75%): Only unzoom if zoomed, otherwise do nothing (use double tap to zoom)
                            val currentScale = getCurrentPhotoViewScale()
                            Timber.d("SingleTap: Center zone, scale=${"%.2f".format(currentScale)}x")
                            
                            if (currentScale > 1.05f) {
                                // Already zoomed → Reset to 1x
                                Timber.d("SingleTap: Zoomed -> Reset to 1x")
                                callback.setPhotoViewZoom(1.0f)
                                return true
                            } else {
                                // Not zoomed → Do nothing (use double tap to zoom)
                                Timber.d("SingleTap: Not zoomed -> No action")
                                return false
                            }
                        }
                        else -> {
                            // Right zone (75-100%): Next file
                            Timber.d("SingleTap: Right zone -> NEXT")
                            callback.onNext()
                            return true
                        }
                    }
                }
                return false
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val currentFile = viewModel.state.value.currentFile
                val isImage = currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF
                val isInFullscreenMode = !viewModel.state.value.showCommandPanel
                
                Timber.d("DoubleTap: pos=(${e.x.toInt()},${e.y.toInt()}), image=$isImage, fullscreen=$isInFullscreenMode")
                
                if (isImage && isAnyPhotoViewVisible()) {
                    // REG-3100 spec: Double tap to Zoom In x2 / Zoom Out x2
                    // Only in command panel mode (REG-3100), middle zone
                    if (!isInFullscreenMode) {
                        val screenWidth = binding.root.width
                        val zoneMap = TouchZoneConfig.getZoneMapForMediaType(currentFile?.type, isFullscreen = false)
                        val config = TouchZoneConfig.getConfiguration(zoneMap)
                        
                        // Check if tap is in middle zone (25%-75%)
                        val leftBoundary = screenWidth * config.widthRatios[0]
                        val rightBoundary = screenWidth * (config.widthRatios[0] + config.widthRatios[1])
                        
                        if (e.x >= leftBoundary && e.x < rightBoundary) {
                            // Middle zone: Toggle 2x/1x zoom (per REG-3100 spec)
                            val currentScale = getCurrentPhotoViewScale()
                            if (currentScale > 1.05f) {
                                // Already zoomed → Reset to 1x
                                Timber.d("DoubleTap: Unzoom to 1x (was ${"%.2f".format(currentScale)}x)")
                                callback.setPhotoViewZoom(1.0f)
                            } else {
                                // Not zoomed → Zoom to 2x (per REG-3100 spec)
                                Timber.d("DoubleTap: Zoom to 2x (REG-3100 spec)")
                                callback.setPhotoViewZoom(2.0f)
                            }
                            return true
                        } else {
                            // Outside middle zone: ignore double-tap in command panel mode
                            Timber.d("DoubleTap: Outside middle zone in command panel mode, ignoring")
                            return false
                        }
                    } else {
                        // Fullscreen mode: use old 3x zoom behavior
                        val currentScale = getCurrentPhotoViewScale()
                        if (currentScale > 1.05f) {
                            Timber.d("DoubleTap: Unzoom to 1x (was ${"%.2f".format(currentScale)}x)")
                            callback.setPhotoViewZoom(1.0f)
                        } else {
                            Timber.d("DoubleTap: Zoom to 3x (fullscreen mode)")
                            callback.setPhotoViewZoom(3.0f)
                        }
                        return true
                    }
                }
                
                return false
            }
            
            override fun onLongPress(e: MotionEvent) {
            val currentFile = viewModel.state.value.currentFile
            val isImage = currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF
            val isInFullscreenMode = !viewModel.state.value.showCommandPanel
            
            // Fix for Android Emulator bug where click triggers LongPress because UP event is swallowed/delayed
            // If the touch was very quick (< 300ms), treat it as a tap, not a long press
            val touchDuration = e.eventTime - e.downTime
            if (touchDuration < 300) {
                Timber.d("LongPress: Emulator bug detected (${touchDuration}ms) - treating as single tap")
                onSingleTapConfirmed(e)
                return
            }
            
            Timber.d("LongPress: pos=(${e.x.toInt()},${e.y.toInt()}), duration=${touchDuration}ms, fullscreen=$isInFullscreenMode")
            
            if (isImage && isAnyPhotoViewVisible()) {
                // In command panel mode (REG-3100): long press zooms to 2x (consistent with double-tap)
                // In fullscreen mode: long press zooms to 3x (traditional behavior)
                val zoomLevel = if (!isInFullscreenMode) 2.0f else 3.0f
                Timber.d("LongPress: Zoom to ${zoomLevel}x")
                callback.setPhotoViewZoom(zoomLevel)
            } else {
                super.onLongPress(e)
            }
        }
        
        // onFling not overridden: conflicts with onSingleTapConfirmed - GestureDetector
        // cannot distinguish quick tap from slow swipe (any finger movement triggers
        // onFling, suppressing onSingleTapConfirmed). Images use tap zones only;
        // PDF uses PhotoView.setOnSingleFlingListener (see PlayerGestureSetupManager).
        })
    }
    
    /**
     * Execute a swipe action and return true if handled.
     */
    private fun executeSwipeAction(action: TouchZoneAction): Boolean {
        return when (action) {
            TouchZoneAction.NEXT -> { callback.onNext(); true }
            TouchZoneAction.PREVIOUS -> { callback.onPrevious(); true }
            TouchZoneAction.BACK -> { callback.onBack(); true }
            TouchZoneAction.COMMAND_PANEL -> { callback.onSwitchToCommandPanel(); true }
            TouchZoneAction.ZOOM_IN -> { callback.onZoomIn(); true }
            TouchZoneAction.ZOOM_OUT -> { callback.onZoomOut(); true }
            TouchZoneAction.SEEK_START -> { callback.onSeekToStart(); true }
            TouchZoneAction.SEEK_END -> { callback.onSeekToEnd(); true }
            TouchZoneAction.PAGE_UP -> { callback.onPageUp(); true }
            TouchZoneAction.PAGE_DOWN -> { callback.onPageDown(); true }
            else -> false
        }
    }

    // ===== Public methods for PhotoView native listener API =====
    // Called from PlayerGestureSetupManager via PhotoView's setOnDoubleTapListener,
    // setOnSingleFlingListener, and setOnLongClickListener.
    // These replace the broken setOnTouchListener approach that was overwriting
    // PhotoView's internal attacher and killing pinch-zoom/pan/double-tap.

    /**
     * Handle single tap on PhotoView (from PhotoView's native OnDoubleTapListener).
     * Routes to 9-zone grid (fullscreen) or 3-zone navigation (command panel).
     */
    fun handleImageSingleTap(e: MotionEvent): Boolean {
        val currentFile = viewModel.state.value.currentFile
        val isInFullscreenMode = !viewModel.state.value.showCommandPanel
        val isImage = currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF

        Timber.d("handleImageSingleTap: pos=(${e.x.toInt()},${e.y.toInt()}), image=$isImage, fullscreen=$isInFullscreenMode")

        if (callback.isOverlayBlocking()) {
            Timber.d("handleImageSingleTap: overlay blocking")
            return false
        }

        if (isImage && isAnyImageSurfaceVisible()) {
            if (isInFullscreenMode) {
                Timber.d("handleImageSingleTap: fullscreen -> 9-zone grid")
                handleTouchZone(e.x, e.y)
                return true
            }

            when (resolveHorizontalZone(e.x)) {
                HorizontalZone.LEFT -> {
                    Timber.d("handleImageSingleTap: Left zone -> PREVIOUS")
                    callback.onPrevious()
                    return true
                }
                HorizontalZone.CENTER -> {
                    val currentScale = getCurrentPhotoViewScale()
                    Timber.d("handleImageSingleTap: Center zone, scale=${"%.2f".format(currentScale)}x")
                    if (currentScale > 1.05f) {
                        Timber.d("handleImageSingleTap: Zoomed -> Reset to 1x")
                        callback.setPhotoViewZoom(1.0f)
                        return true
                    }
                    // Not zoomed: no action (double-tap or pinch to zoom)
                    return false
                }
                HorizontalZone.RIGHT -> {
                    Timber.d("handleImageSingleTap: Right zone -> NEXT")
                    callback.onNext()
                    return true
                }
            }
        }
        return false
    }

    /**
     * Handle double tap on PhotoView (from PhotoView's native OnDoubleTapListener).
     * Command panel center zone: 2x/1x toggle.
     * Fullscreen: 3x/1x toggle.
     */
    fun handleImageDoubleTap(e: MotionEvent): Boolean {
        val currentFile = viewModel.state.value.currentFile
        val isImage = currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF
        val isInFullscreenMode = !viewModel.state.value.showCommandPanel

        Timber.d("handleImageDoubleTap: pos=(${e.x.toInt()},${e.y.toInt()}), fullscreen=$isInFullscreenMode")

        if (isImage && isAnyImageSurfaceVisible()) {
            if (!isInFullscreenMode) {
                if (resolveHorizontalZone(e.x) == HorizontalZone.CENTER) {
                    val currentScale = getCurrentPhotoViewScale()
                    if (currentScale > 1.05f) {
                        Timber.d("handleImageDoubleTap: Reset to 1x (was ${"%.2f".format(currentScale)}x)")
                        callback.setPhotoViewZoom(1.0f)
                    } else {
                        Timber.d("handleImageDoubleTap: Zoom to 2x (REG-3100)")
                        callback.setPhotoViewZoom(2.0f)
                    }
                    return true
                }
                return false // Outside middle zone in command panel
            } else {
                val currentScale = getCurrentPhotoViewScale()
                if (currentScale > 1.05f) {
                    Timber.d("handleImageDoubleTap: Reset to 1x (was ${"%.2f".format(currentScale)}x)")
                    callback.setPhotoViewZoom(1.0f)
                } else {
                    Timber.d("handleImageDoubleTap: Zoom to 3x (fullscreen)")
                    callback.setPhotoViewZoom(3.0f)
                }
                return true
            }
        }
        return false
    }

    /**
     * Handle fling (swipe) on PhotoView (from PhotoView's native OnSingleFlingListener).
     * Only fires when not zoomed (scale <= 1.0) and single-touch.
     */
    fun handleImageFling(e1: MotionEvent?, @Suppress("UNUSED_PARAMETER") e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        val currentFile = viewModel.state.value.currentFile
        val isFullscreen = !viewModel.state.value.showCommandPanel
        // S0620: grid-off fullscreen resolves to REG-3100/REG-375, so a vertical swipe stays
        // zoom (image) / seek (video) instead of the 9-zone BACK/COMMAND_PANEL swipe actions.
        val zoneMap = TouchZoneConfig.getZoneMapForMediaType(
            currentFile?.type, isFullscreen, callback.getNineZoneGridEnabled()
        )
        val config = TouchZoneConfig.getConfiguration(zoneMap)

        // For REG-3100, ignore swipes that start in middle zone (PhotoView handles pan)
        val startX = e1?.x ?: 0f
        if (config.ignoreMiddleZoneSwipes) {
            if (touchZoneDetector.shouldIgnoreSwipe(startX, binding.root.width, zoneMap)) {
                Timber.d("handleImageFling: Started in middle zone, ignoring")
                return false
            }
        }

        val minSwipeVelocity = 100f
        val absVelocityX = kotlin.math.abs(velocityX)
        val absVelocityY = kotlin.math.abs(velocityY)

        val direction: SwipeDirection? = when {
            absVelocityX > absVelocityY && absVelocityX > minSwipeVelocity -> {
                if (velocityX > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
            }
            absVelocityY > absVelocityX && absVelocityY > minSwipeVelocity -> {
                if (velocityY > 0) SwipeDirection.DOWN else SwipeDirection.UP
            }
            else -> null
        }

        if (direction == null) return false

        val action = TouchZoneConfig.getSwipeAction(zoneMap, direction)
        Timber.d("handleImageFling: $direction in $zoneMap -> $action")
        return executeSwipeAction(action)
    }

    /**
     * Handle long press on PhotoView (from PhotoView's native OnLongClickListener).
     * Zooms to 2x (command panel) or 3x (fullscreen).
     */
    fun handleImageLongPress(): Boolean {
        val currentFile = viewModel.state.value.currentFile
        val isImage = currentFile?.type == MediaType.IMAGE || currentFile?.type == MediaType.GIF
        val isInFullscreenMode = !viewModel.state.value.showCommandPanel

        if (isImage && isAnyPhotoViewVisible()) {
            val zoomLevel = if (!isInFullscreenMode) 2.0f else 3.0f
            Timber.d("handleImageLongPress: Zoom to ${zoomLevel}x")
            callback.setPhotoViewZoom(zoomLevel)
            return true
        }
        return false
    }

    /**
     * GestureDetector for handling touch zones on PlayerView and general screen.
     * Handles PDF/EPUB/VIDEO/AUDIO/IMAGE/GIF with appropriate zone layouts.
     */
    fun createGestureDetector(context: android.content.Context): GestureDetector {
        return GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val state = viewModel.state.value
                val currentFile = state.currentFile
                val showCommandPanel = state.showCommandPanel
                val isInFullscreenMode = !showCommandPanel

                UserActionLogger.logGesture("SingleTap", "x=${e.x.toInt()} y=${e.y.toInt()}", "PlayerActivity")
                Timber.d("onSingleTapConfirmed: pos=(${e.x.toInt()},${e.y.toInt()}), fullscreen=$isInFullscreenMode, type=${currentFile?.type}")

                // S0694: a live video stream is not a managed-file surface - the 9/3-zone actions
                // (BACK/COPY/MOVE/DELETE/COMMAND_PANEL etc.) do not apply. Skip zone routing and report
                // the tap unhandled so the PlayerView touch dispatch can toggle the ExoPlayer controller.
                if (state.isLiveVideoStream) {
                    Timber.d("onSingleTapConfirmed: live video stream - skipping touch zones")
                    return false
                }

                // PDF/EPUB/TEXT: Handled by specialized managers
                val isPdfOrEpub = currentFile?.type == MediaType.PDF || currentFile?.type == MediaType.EPUB
                val isText = currentFile?.type == MediaType.TEXT
                
                if (isPdfOrEpub || isText) {
                    // These file types handle their own touch zones via specialized managers
                    return true
                }
                
                // For IMAGE/GIF/VIDEO/AUDIO:
                // - Fullscreen mode: ALWAYS use 9-zone grid (required to exit fullscreen)
                // - Command panel mode: 2-zone navigation (only if touchZones enabled)
                if (isInFullscreenMode) {
                    Timber.d("Fullscreen mode: using 9-zone grid for ${currentFile?.type}")
                    handleTouchZone(e.x, e.y)
                } else if (callback.getTouchZonesEnabled() && 
                          (currentFile?.type == MediaType.VIDEO || 
                           currentFile?.type == MediaType.AUDIO ||
                           currentFile?.type == MediaType.IMAGE ||
                           currentFile?.type == MediaType.GIF)) {
                    Timber.d("Command panel mode: using 3-zone layout")
                    handleCommandPanelTouchZones(e.x, e.y)
                } else {
                    Timber.d("Touch zones disabled or unsupported type")
                }
                
                return true
            }
        })
    }
    
    /**
     * Handle touch zones for static images (3x3 grid) and full-screen video/audio.
     * Uses REG-9100 for images (30-40-30 grid)
     * Uses REG-975 for video/audio (30-40-30 grid, upper 75% only)
     */
    private fun handleTouchZone(x: Float, y: Float) {
        val currentFile = viewModel.state.value.currentFile
        val screenWidth = binding.root.width
        val screenHeight = binding.root.height
        
        // Safety check
        if (screenWidth <= 0 || screenHeight <= 0) {
            Timber.d("handleTouchZone: Invalid dimensions - ignoring")
            return
        }
        
        // Get correct zone map. Grid on -> REG-9100/REG-975 (9 zones).
        // S0620: grid off -> REG-3100/REG-375 (3-zone fullscreen fallback).
        val gridEnabled = callback.getNineZoneGridEnabled()
        val zoneMap = TouchZoneConfig.getZoneMapForMediaType(
            currentFile?.type, isFullscreen = true, nineZoneGridEnabled = gridEnabled
        )

        val isThreeZoneFallback = zoneMap == TouchZoneMap.REG_3100 || zoneMap == TouchZoneMap.REG_375
        val action = if (isThreeZoneFallback) {
            // S0620: fullscreen 3-zone fallback (grid off) - left-edge band opens the command
            // panel so file operations stay reachable by touch. Respect the reserved bottom
            // area for video (REG-375 = top 75%) the same way TouchZoneDetector does.
            val config = TouchZoneConfig.getConfiguration(zoneMap)
            val effectiveHeight = (screenHeight * config.heightPercent).toInt()
            if (y > effectiveHeight) {
                TouchZoneAction.NONE
            } else {
                val xFraction = (x / screenWidth).coerceIn(0f, 1f)
                TouchZoneConfig.get3ZoneFullscreenTapAction(xFraction, zoneMap)
            }
        } else {
            touchZoneDetector.detectAction(x, y, screenWidth, screenHeight, zoneMap)
        }
        Timber.d("handleTouchZone: $zoneMap -> $action at ($x, $y)")
        
        // simple logging mapping
        val actionDesc = TouchZoneConfig.getActionDescription(action)
        Timber.d("9-zone: $actionDesc, pos=($x,$y)")
        
        // Stop slideshow on any touch zone except NEXT and NONE
        if (action != TouchZoneAction.NEXT && action != TouchZoneAction.NONE) {
            if (viewModel.state.value.isSlideShowActive) {
                viewModel.toggleSlideShow()
                callback.updateSlideShow()
            }
        }
        
        when (action) {
            TouchZoneAction.BACK -> callback.onBack()
            TouchZoneAction.COPY -> callback.onCopy()
            TouchZoneAction.RENAME -> callback.onRename()
            TouchZoneAction.PREVIOUS -> callback.onPrevious()
            TouchZoneAction.MOVE -> callback.onMove()
            TouchZoneAction.PAUSE_RESUME -> callback.onPauseResume()
            TouchZoneAction.NEXT -> {
                if (viewModel.state.value.isSlideShowActive) {
                    callback.updateSlideShow()
                }
                callback.onNext()
            }
            TouchZoneAction.COMMAND_PANEL -> callback.onSwitchToCommandPanel()
            TouchZoneAction.DELETE -> callback.onDelete()
            TouchZoneAction.SLIDESHOW -> callback.onToggleSlideshow()
            // S0620: grid-off fallback center on an image - PhotoView owns zoom gestures.
            TouchZoneAction.PHOTOVIEW_GESTURE -> { /* No tap action; pinch / double-tap to zoom */ }
            TouchZoneAction.NONE -> { /* Do nothing */ }
            else -> { Timber.d("Action $action not handled in 9-zone mode") }
        }
    }
    
    /**
     * Handle simplified touch zones for command panel mode.
     * Uses REG-3100 for images (25%|50%|25%) and REG-375 for video (25%|50%|25%, 75% height).
     */
    private fun handleCommandPanelTouchZones(x: Float, y: Float) {
        val screenWidth = binding.root.width
        val screenHeight = binding.root.height
        
        // Safety check
        if (screenWidth <= 0 || screenHeight <= 0) {
            Timber.d("handleCommandPanelTouchZones: Invalid screen dimensions - ignoring")
            return
        }
        
        val currentFile = viewModel.state.value.currentFile
        val zoneMap = TouchZoneConfig.getZoneMapForMediaType(currentFile?.type, isFullscreen = false)
        val config = TouchZoneConfig.getConfiguration(zoneMap)
        
        // Calculate effective height for video/audio (75% of screen)
        val effectiveHeight = (screenHeight * config.heightPercent).toInt()
        
        // If touch is below effective height, ignore (reserved for player controls)
        if (y > effectiveHeight) {
            Timber.d("handleCommandPanelTouchZones: Touch below effective height ($y > $effectiveHeight) - ignoring")
            return
        }
        
        // Get top command panel height (content starts below it)
        // Use measured height if available (before layout), otherwise actual height
        val topPanelHeight = if (binding.topCommandPanel.isVisible) {
            val measured = binding.topCommandPanel.measuredHeight
            val actual = binding.topCommandPanel.height
            // Use actual height if layout completed (height > 0 and < screenHeight/2)
            // Otherwise fallback to measured or safe default (200dp ~ 96-144px typical command panel)
            when {
                actual > 0 && actual < (screenHeight / 2) -> actual
                measured > 0 && measured < (screenHeight / 2) -> measured
                else -> 200 // Safe fallback: typical command panel height in dp
            }
        } else {
            0
        }
        
        // If click in top panel area - ignore (let buttons handle it)
        if (y < topPanelHeight) {
            Timber.d("handleCommandPanelTouchZones: Click in top panel area ($y < $topPanelHeight) - ignoring")
            return
        }
        
        // Use new zone detection for command panel modes
        val action = touchZoneDetector.detectAction(x, y, screenWidth, effectiveHeight, zoneMap)
        Timber.d("handleCommandPanelTouchZones: $zoneMap -> $action at ($x, $y)")
        
        when (action) {
            TouchZoneAction.PREVIOUS -> callback.onPrevious()
            TouchZoneAction.NEXT -> callback.onNext()
            TouchZoneAction.PAUSE_RESUME -> callback.onPauseResume()
            TouchZoneAction.PHOTOVIEW_GESTURE -> {
                // Middle zone for images - let PhotoView handle gestures
                Timber.d("handleCommandPanelTouchZones: Center zone - PhotoView handles gestures")
            }
            else -> {
                Timber.d("handleCommandPanelTouchZones: Action $action not handled")
            }
        }
    }
}
