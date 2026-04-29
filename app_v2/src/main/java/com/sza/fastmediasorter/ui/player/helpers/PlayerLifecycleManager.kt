package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.playback.PlaybackCompletionDetector
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
        
        // Resume image renderer (resumes prefetch operations)
        try {
            activity.imageLoadingManager.onResume()
        } catch (e: UninitializedPropertyAccessException) {
            // imageLoadingManager not yet initialized
        }

        // Restore overlay timer after returning from background
        try {
            val currentType = viewModel.state.value.currentFile?.type
            activity.dialogAndUiStateManager.filenameOverlayManager?.onHostResume(currentType)
        } catch (e: UninitializedPropertyAccessException) {
            // dialogAndUiStateManager not yet initialized
        }
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
        
        // Pause image renderer (stops prefetch operations)
        try {
            activity.imageLoadingManager.onPause()
        } catch (e: UninitializedPropertyAccessException) {
            // imageLoadingManager not yet initialized
        }

        // Save overlay timer remaining time so it can be restored on resume
        try {
            activity.dialogAndUiStateManager.filenameOverlayManager?.onHostPause()
        } catch (e: UninitializedPropertyAccessException) {
            // dialogAndUiStateManager not yet initialized
        }
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
        // Dismiss all tracked dialogs to prevent WindowLeaked
        try { activity.dialogHelper.dismissAll() } catch (_: UninitializedPropertyAccessException) {}

        // Release managers with explicit teardown
        activity.backgroundMusicManager.release()
        activity.audioBackgroundPhotosManager.release()
        activity.audioServiceController?.release()
        activity.audioServiceController = null
        activity.sleepTimerManager?.release()
        activity.sleepTimerManager = null
        activity.audioEmptyStateController?.release()
        activity.audioEmptyStateController = null
        activity.pipManager?.release()
        activity.pipManager = null
        if (activity._textViewerManager != null) activity.textViewerManager.release()
        try { activity.castMediaManager.release() } catch (_: UninitializedPropertyAccessException) {}

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

        // Cancel overlay auto-hide timer to prevent stale runnables after destroy
        try {
            activity.dialogAndUiStateManager.filenameOverlayManager?.cancel()
        } catch (e: UninitializedPropertyAccessException) {
            // dialogAndUiStateManager not yet initialized
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
        
        // Release PdfViewerManager - closes PdfRenderer, cancels render jobs, clears caches (ML-001 fix)
        try {
            activity.pdfViewerManager.close()
        } catch (e: UninitializedPropertyAccessException) {
            // Not initialized, skip
        }
        
        // Release PlayerSettingsManager - cancel pending coroutine operations (ML-005)
        try {
            activity.playerSettingsManager?.release()
        } catch (e: UninitializedPropertyAccessException) {
            // Not initialized, skip
        }
        
        // Release TranslationManager
        try {
            activity.translationManager.release()
        } catch (e: UninitializedPropertyAccessException) {
            // Not initialized, skip
        }
        
        // Note: TextViewerManager IS released at line 166
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

    /**
     * Save current playback position for video/audio files.
     * Called from PlayerActivity.onPause().
     */
    fun saveCurrentPlaybackPosition() {
        val currentFile = viewModel.state.value.currentFile ?: return
        if (currentFile.type != MediaType.VIDEO && currentFile.type != MediaType.AUDIO) return
        viewModel.saveResumeState()
        val player = if (activity._videoPlayerManager != null) activity.videoPlayerManager.getPlayer() else null
        val position = player?.currentPosition ?: return
        val duration = player.duration
        if (duration <= 0 || position < 0) return
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (PlaybackCompletionDetector.isNearEnd(position, duration)) {
                    activity.playbackPositionRepository.markPlaybackCompleted(
                        currentFile.path,
                        reason = "playback-completed-near-end"
                    )
                } else {
                    activity.playbackPositionRepository.savePosition(currentFile.path, position, duration)
                    Timber.d("PlayerLifecycleManager: Saved playback position $position/$duration for ${currentFile.name}")
                }
            } catch (e: Exception) {
                Timber.e(e, "PlayerLifecycleManager: Failed to save playback position for ${currentFile.name}")
            }
        }
    }

    /**
     * Register back-press handler for fullscreen viewers and overlay dismissal.
     * Called from PlayerActivity.setupViews() after all managers are initialized.
     */
    fun setupBackPressHandler() {
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activity._pdfViewerManager != null && activity.pdfViewerManager.isInFullscreenMode()) {
                    activity.pdfViewerManager.exitFullscreenMode()
                    return
                }
                if (activity._epubViewerManager != null && activity.epubViewerManager.isInFullscreenMode()) {
                    activity.epubViewerManager.onExitFullscreenRequest()
                    return
                }
                if (activity.isOverlayBlocking()) {
                    val binding = activity.activityBinding
                    when {
                        activity.safeViews.translationOverlay.isVisible || binding.translationLensOverlay.isVisible -> {
                            activity.stopTranslation()
                            return
                        }
                        activity.safeViews.textViewerContainer.isVisible -> {
                            // Text viewer: perform complete cleanup, then exit player
                            // Do NOT return - fall through to exitPlayerWithAudioCheck() for navigation
                            if (activity._textViewerManager != null) {
                                activity.textViewerManager.closeTextViewerFromBackPress()
                            }
                            // Fall through to exit player (don't return)
                        }
                        activity.safeViews.lyricsViewerContainer.isVisible -> {
                            activity.hideLyricsViewer()
                            return
                        }
                        else -> return  // Other overlays - exit normally
                    }
                }
                activity.exitPlayerWithAudioCheck()
            }
        })
    }

    /**
     * Exit the player, respecting the saved background-audio exit behavior preference.
     * If service audio is connected but already paused, treat exit as an implicit stop and finish
     * silently regardless of the saved preference.
     * - ALWAYS_STOP: stops service and finishes without a dialog.
     * - ALWAYS_CONTINUE: finishes without stopping service and without a dialog.
     * - ASK (default): shows a 4-item dialog (Stop / Keep Playing / Always Stop / Always Continue).
     */
    fun exitPlayerWithAudioCheck(withTransition: Boolean = false) {
        if (!activity.isMediaLoaderManagerInitialized || !activity.mediaLoaderManager.isServiceAudioActive) {
            doFinish(withTransition)
            return
        }

        val audioPlayer = activity.audioServiceController?.player
        if (audioPlayer?.isPlaying != true) {
            Timber.d("PlayerLifecycleManager: service audio is paused/inactive on exit - stopping silently")
            audioPlayer?.stop()
            doFinish(withTransition)
            return
        }

        when (viewModel.state.value.backgroundAudioExitBehavior) {
            BackgroundAudioExitBehavior.ALWAYS_STOP -> {
                audioPlayer?.stop()
                doFinish(withTransition)
            }
            BackgroundAudioExitBehavior.ALWAYS_CONTINUE -> {
                doFinish(withTransition)
            }
            BackgroundAudioExitBehavior.ASK -> showExitAudioDialog(withTransition)
        }
    }

    private fun showExitAudioDialog(withTransition: Boolean) {
        val items = arrayOf(
            activity.getString(R.string.background_audio_exit_stop),
            activity.getString(R.string.background_audio_exit_continue),
            activity.getString(R.string.background_audio_exit_always_stop),
            activity.getString(R.string.background_audio_exit_always_continue),
        )
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.background_audio_exit_message)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { // Stop (this time only)
                        activity.audioServiceController?.player?.stop()
                        doFinish(withTransition)
                    }
                    1 -> { // Keep Playing (this time only)
                        doFinish(withTransition)
                    }
                    2 -> { // Always Stop — save preference then stop
                        viewModel.updateExitBehavior(BackgroundAudioExitBehavior.ALWAYS_STOP)
                        activity.audioServiceController?.player?.stop()
                        doFinish(withTransition)
                    }
                    3 -> { // Always Continue — save preference then continue
                        viewModel.updateExitBehavior(BackgroundAudioExitBehavior.ALWAYS_CONTINUE)
                        doFinish(withTransition)
                    }
                }
            }
            .show()
    }

    fun doFinish(withTransition: Boolean = false) {
        viewModel.clearResumeState()
        activity.finish()
        if (withTransition) {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    /**
     * Set screen keep-awake state for slideshow.
     * @param enabled true to force screen on, false to restore global preventSleep setting
     */
    fun setSlideshowKeepAwake(enabled: Boolean) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (enabled) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            android.widget.Toast.makeText(
                activity, R.string.slideshow_keep_awake, android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            activity.lifecycleScope.launch {
                val settings = activity.settingsRepository.getSettings().first()
                if (settings.preventSleep) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    /**
     * Handle successful file deletion from any permission callback path.
     * Tracks the file, removes it from cache, and navigates away or finishes.
     */
    fun handleDeleteSuccess(deletedFilePath: String) {
        trackModifiedFile(deletedFilePath)
        viewModel.state.value.resource?.let { resource ->
            MediaFilesCacheManager.removeFile(resource.id, deletedFilePath)
        }
        val hasRemainingFiles = viewModel.removeDeletedFile(deletedFilePath)
        if (!hasRemainingFiles) activity.finish()
    }

    /**
     * Handle Android 11+ batch delete permission result (createDeleteRequest).
     * Called from the batchDeletePermissionLauncher result callback.
     */
    fun handleBatchDeleteResult(resultCode: Int, currentFilePath: String?) {
        if (resultCode == Activity.RESULT_OK) {
            Timber.i("PlayerLifecycleManager: Batch delete permission granted")
            if (currentFilePath != null) handleDeleteSuccess(currentFilePath)
        } else {
            Timber.w("PlayerLifecycleManager: Batch delete permission denied")
            Toast.makeText(
                activity,
                activity.getString(R.string.error_delete_failed, "Permission denied"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Handle Android 10 single-file delete permission result (RecoverableSecurityException).
     * Called from the deletePermissionLauncher result callback.
     */
    fun handleDeletePermissionResult(resultCode: Int) {
        if (resultCode == Activity.RESULT_OK) {
            Timber.d("PlayerLifecycleManager: Delete permission granted, retrying delete")
            activity.fileOperationsHandler.performDelete()
        } else {
            Timber.w("PlayerLifecycleManager: Delete permission denied")
            Toast.makeText(
                activity,
                activity.getString(R.string.error_delete_failed, "Permission denied"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Stop video playback and release ExoPlayer resources.
     * Called when switching to non-media files (EPUB, PDF, Text, Image).
     */
    fun stopVideoPlayback() {
        Timber.d("PlayerLifecycleManager: stopVideoPlayback()")
        if (activity._videoPlayerManager != null) {
            // pause() internally calls saveCurrentPosition in VideoPlayerManager
            activity.videoPlayerManager.pause()
            activity.videoPlayerManager.releasePlayer()
        }
    }

    /**
     * Clear Glide memory cache to prevent OOM during long slideshows.
     * No-op if imageLoadingManager is not yet initialized or Activity is finishing.
     */
    fun clearImageMemoryCache() {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            activity.imageLoadingManager.clearMemoryCache()
        } catch (_: UninitializedPropertyAccessException) {}
    }
}
