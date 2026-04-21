package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sza.fastmediasorter.domain.model.AppSettings
import timber.log.Timber

/**
 * Manages system UI visibility (status bar, navigation bar) for PlayerActivity.
 * 
 * Responsibilities:
 * - Hide/show system bars (status bar + navigation bar) for fullscreen mode
 * - Configure immersive sticky mode (bars stay hidden, no swipe to reveal)
 * - Handle different Android API levels (API 30+ vs legacy)
 * - Restore system bars when exiting fullscreen
 * 
 * Fullscreen Mode Behavior:
 * - Hides both status bar and navigation bar
 * - Immersive sticky mode: bars stay hidden, NO swipe to reveal
 * - Supports display cutouts (notch/punch-hole)
 * - Content extends behind system bars (edge-to-edge)
 * - Touch zones handle all navigation (no system gestures needed)
 * 
 * Normal Mode Behavior:
 * - Shows system bars
 * - Standard behavior (bars stay visible)
 */
class SystemBarsManager(
    private val activity: Activity
) {
    private val window = activity.window
    private var isFullscreenMode = false
    
    /**
     * Enter fullscreen mode - hide system bars.
     * Edge-to-edge mode is ALWAYS enabled in this activity to ensure consistent layout control.
     * This method only handles visibility toggling.
     */
    fun enterFullscreenMode() {
        if (isFullscreenMode) return
        
        isFullscreenMode = true
        
        // Ensure edge-to-edge is active (content draws behind system bars)
        // We handle insets manually in PlayerActivity via setOnApplyWindowInsetsListener
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ (Android 11+): Use WindowInsetsController
            window.insetsController?.let { controller ->
                // Hide both status bar and navigation bar
                controller.hide(
                    WindowInsetsCompat.Type.statusBars() or 
                    WindowInsetsCompat.Type.navigationBars()
                )
                
                // Immersive sticky mode: bars stay hidden, no swipe to reveal
                controller.systemBarsBehavior = 
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                
                Timber.d("SystemBars: Entered fullscreen mode (API 30+) - immersive sticky")
            }
        } else {
            // Legacy API: Use WindowInsetsControllerCompat
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.let { controller ->
                // Hide both status bar and navigation bar
                controller.hide(
                    WindowInsetsCompat.Type.statusBars() or 
                    WindowInsetsCompat.Type.navigationBars()
                )
                
                // Immersive sticky mode: bars stay hidden, no swipe to reveal
                controller.systemBarsBehavior = 
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                
                Timber.d("SystemBars: Entered fullscreen mode (Legacy API) - immersive sticky")
            }
        }
    }
    
    /**
     * Exit fullscreen mode - show system bars.
     * We KEEP Edge-to-Edge enabled (setDecorFitsSystemWindows(false)) to allow manual insets handling.
     * If we switched to 'true', the system would forcibly resize the layout, potentially causing jumps
     * or conflicts with our manual padding logic.
     */
    fun exitFullscreenMode() {
        isFullscreenMode = false
        
        // Keep edge-to-edge active, relying on our OnApplyWindowInsetsListener to pad views.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ (Android 11+): Use WindowInsetsController
            window.insetsController?.let { controller ->
                // Show both status bar and navigation bar
                controller.show(
                    WindowInsetsCompat.Type.statusBars() or 
                    WindowInsetsCompat.Type.navigationBars()
                )
                
                // Restore default behavior
                controller.systemBarsBehavior = 
                    WindowInsetsController.BEHAVIOR_DEFAULT
                
                Timber.d("SystemBars: Exited fullscreen mode (API 30+)")
            }
        } else {
            // Legacy API: Use WindowInsetsControllerCompat
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.let { controller ->
                // Show both status bar and navigation bar
                controller.show(
                    WindowInsetsCompat.Type.statusBars() or 
                    WindowInsetsCompat.Type.navigationBars()
                )
                
                // Restore default behavior
                controller.systemBarsBehavior = 
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                
                Timber.d("SystemBars: Exited fullscreen mode (Legacy API)")
            }
        }
        
        // Request WindowInsets update to ensure all views (toolbar, etc.) receive new insets
        // CRITICAL: This triggers OnApplyWindowInsetsListener callbacks to update padding
        window.decorView.requestApplyInsets()
        Timber.d("SystemBars: Requested insets update after exiting fullscreen")
    }
    
    /**
     * Check if currently in fullscreen mode.
     */
    fun isInFullscreenMode(): Boolean = isFullscreenMode
    
    /**
     * Force update system bars visibility based on current command panel state.
     * Call this when state changes (e.g., after toggleCommandPanel).
     */
    fun updateSystemBarsVisibility(showCommandPanel: Boolean) {
        if (showCommandPanel) {
            exitFullscreenMode()
        } else {
            enterFullscreenMode()
        }
    }

    /**
     * Central player system-bars update logic extracted from PlayerActivity.
     *
     * Evaluates document fullscreen, explicit fullscreen, slideshow, and audio-slideshow-photo
     * states to decide whether to hide or show system bars. Also manages topCommandPanel
     * top-padding: zero in fullscreen (status bar hidden), restored via insets in normal mode.
     *
     * @return updated value of isExplicitFullscreenMode (cleared when command panel is shown)
     */
    fun updateForPlayerState(
        showCommandPanel: Boolean,
        isExplicitFullscreenMode: Boolean,
        settings: AppSettings?,
        isDocumentFullscreen: Boolean,
        isMediaSlideshow: Boolean,
        isAudioSlideshowPhotoMode: Boolean,
        topCommandPanel: View
    ): Boolean {
        val newExplicitFullscreen = if (showCommandPanel) false else isExplicitFullscreenMode
        val hideSystemUiEnabled = settings?.hideSystemUiInFullscreen != false

        if (isDocumentFullscreen) {
            if (hideSystemUiEnabled) enterFullscreenMode() else exitFullscreenMode()
            return newExplicitFullscreen
        }

        val shouldHide = hideSystemUiEnabled &&
            (newExplicitFullscreen || isMediaSlideshow || isAudioSlideshowPhotoMode)

        if (shouldHide) {
            enterFullscreenMode()
            // Remove top padding so command panel doesn't leave gap when status bar is hidden
            topCommandPanel.setPadding(
                topCommandPanel.paddingLeft, 0,
                topCommandPanel.paddingRight, topCommandPanel.paddingBottom
            )
        } else {
            exitFullscreenMode()
            // Force insets reapplication so command panel moves below the restored status bar
            topCommandPanel.post {
                topCommandPanel.requestApplyInsets()
                Timber.d("TopCommandPanel: Forced insets reapply after exitFullscreen")
            }
        }
        return newExplicitFullscreen
    }
}
