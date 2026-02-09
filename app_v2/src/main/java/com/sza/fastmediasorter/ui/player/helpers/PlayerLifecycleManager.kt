package com.sza.fastmediasorter.ui.player.helpers

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import kotlinx.coroutines.Job
import timber.log.Timber

/**
 * Manages PlayerActivity lifecycle coordination.
 * 
 * Responsibilities:
 * - Initialize all managers in correct order
 * - Handle onResume/onPause state transitions  
 * - Coordinate resource cleanup on onDestroy
 * - Track modified files for result intent
 * - Manage first-resume logic
 * 
 * This consolidates logic previously scattered across onCreate, onResume, onPause, onDestroy.
 */
class PlayerLifecycleManager(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    private val lifecycle: Lifecycle
) {
    // Lifecycle state tracking
    private var isFirstResume = true
    private val modifiedFiles = mutableSetOf<String>()
    private var slideshowModeRequested = false
    
    // Resource tracking for cleanup
    private var activeResourceKey: String? = null
    private val preloadJobs = mutableListOf<Job>()
    
    /**
     * Called from PlayerActivity.onCreate()
     * Coordinates manager initialization and setup.
     */
    @Suppress("UNUSED_PARAMETER")
    fun onCreate(savedInstanceState: Bundle?) {
        // Check if slideshow mode was requested (from main screen slideshow button)
        slideshowModeRequested = activity.intent.getBooleanExtra("slideshow_mode", false)
        
        Timber.d("PlayerLifecycleManager.onCreate: slideshowMode=$slideshowModeRequested")
    }
    
    /**
     * Indicates if slideshow should auto-start after files load.
     */
    fun isSlideshowModeRequested(): Boolean = slideshowModeRequested
    
    /**
     * Called from PlayerActivity.onResume()
     * Handles cloud auth, file reloading, and button visibility updates.
     */
    fun onResume() {
        // Handle any pending cloud authentication results
        try {
            activity.cloudAuthManager.onResume()
        } catch (e: UninitializedPropertyAccessException) {
            // cloudAuthManager not yet initialized, skip
        }
        
        if (isFirstResume) {
            Timber.d("PlayerLifecycleManager.onResume: First resume, skipping reload")
            isFirstResume = false
        } else {
            // Reload files when returning from background
            // This ensures deleted/renamed files from external apps are reflected
            Timber.d("PlayerLifecycleManager.onResume: Reloading files")
            viewModel.reloadFiles()
            
            // Force update button visibility (settings might have changed)
            updateButtonVisibility()
        }
        
        // Clear expired undo operations (5 minutes timeout)
        viewModel.clearExpiredUndoOperation()
    }
    
    /**
     * Update button visibility for managers that depend on settings.
     */
    private fun updateButtonVisibility() {
        try {
            activity.pdfViewerManager.updateButtonVisibility()
        } catch (e: UninitializedPropertyAccessException) {
            // pdfViewerManager not yet initialized
        }
        try {
            activity.imageLoadingManager.updateButtonVisibility()
        } catch (e: UninitializedPropertyAccessException) {
            // imageLoadingManager not yet initialized
        }
    }
    
    /**
     * Called from PlayerActivity.onPause()
     * Minimal pause handling - most state is managed by other managers.
     */
    fun onPause() {
        // VideoPlayerManager handles its own pause/resume
        // SlideshowController observes lifecycle automatically
        Timber.d("PlayerLifecycleManager.onPause")
    }
    
    /**
     * Called from PlayerActivity.onDestroy()
     * Centralizes all resource cleanup to prevent memory leaks.
     */
    fun onDestroy() {
        Timber.d("PlayerLifecycleManager.onDestroy: Starting cleanup")
        releaseResources()
        returnModifiedFilesResult()
    }
    
    /**
     * Release all resources and cleanup handlers.
     * Prevents memory leaks and ensures proper resource disposal.
     */
    private fun releaseResources() {
        // Cancel all active network operations for current resource
        activeResourceKey?.let { resourceKey ->
            com.sza.fastmediasorter.data.network.ConnectionThrottleManager.cancelAllForResource(resourceKey)
            com.sza.fastmediasorter.data.network.ConnectionThrottleManager.deactivateVideoPlayerMode(resourceKey)
            Timber.d("PlayerLifecycleManager: Cancelled all operations for $resourceKey")
        }
        
        // Save current file position before destroying
        viewModel.state.value.currentFile?.let { currentFile ->
            viewModel.saveLastViewedFile(currentFile.path)
        }
        
        // Cleanup handlers (moved from PlayerActivity)
        activity.hideControlsHandler.removeCallbacks(activity.hideControlsRunnable)
        activity.loadingIndicatorHandler.removeCallbacks(activity.showLoadingIndicatorRunnable)
        
        // Cancel any pending retry
        activity.retryRunnable?.let { activity.retryHandler.removeCallbacks(it) }
        activity.retryRunnable = null
        
        // Cancel all preload jobs to prevent memory leaks
        preloadJobs.forEach { it.cancel() }
        preloadJobs.clear()
        
        // Release VideoPlayerManager
        try {
            activity.videoPlayerManager.releasePlayer()
        } catch (e: UninitializedPropertyAccessException) {
            // Not initialized, skip
        }
        
        // Release ImageLoadingManager - cancel all Glide requests and handlers
        try {
            activity.imageLoadingManager.cleanup()
        } catch (e: UninitializedPropertyAccessException) {
            // Not initialized, skip
        }
        
        // Release EpubViewerManager
        try {
            activity.epubViewerManager.release()
        } catch (e: UninitializedPropertyAccessException) {
            // Not initialized, skip
        }
        
        // Release TranslationManager
        try {
            activity.translationManager.release()
        } catch (e: UninitializedPropertyAccessException) {
            // Not initialized, skip
        }
        
        // Note: PdfViewerManager and TextViewerManager don't require explicit cleanup
        // Note: Translation cache is NOT cleared here - it's global and managed by TranslationCacheManager
        // Cache is cleared only on app startup and when user clicks "Clear cache" in settings
        // SlideshowController handles its own cleanup via LifecycleObserver.onDestroy()
        
        Timber.d("PlayerLifecycleManager: Cleanup complete")
    }
    
    /**
     * Return modified files list to BrowseActivity.
     * Notifies parent activity which files were deleted/moved/renamed.
     */
    private fun returnModifiedFilesResult() {
        if (modifiedFiles.isNotEmpty()) {
            val resultIntent = Intent().apply {
                putStringArrayListExtra(PlayerActivity.EXTRA_MODIFIED_FILES, ArrayList(modifiedFiles))
            }
            activity.setResult(android.app.Activity.RESULT_OK, resultIntent)
            Timber.d("PlayerLifecycleManager: Returning ${modifiedFiles.size} modified files")
        }
    }
    
    /**
     * Track a file that was modified (deleted, moved, renamed).
     * Will be reported back to BrowseActivity on destroy.
     */
    fun trackModifiedFile(path: String) {
        modifiedFiles.add(path)
        Timber.d("PlayerLifecycleManager: Tracked modified file: $path")
    }
    
    /**
     * Set the active resource key for connection throttling cleanup.
     */
    fun setActiveResourceKey(key: String?) {
        activeResourceKey = key
    }
    
    /**
     * Add a preload job to be cancelled on destroy.
     */
    fun addPreloadJob(job: Job) {
        preloadJobs.add(job)
    }
    
    /**
     * Remove a completed preload job from tracking.
     */
    fun removePreloadJob(job: Job) {
        preloadJobs.remove(job)
    }
}
