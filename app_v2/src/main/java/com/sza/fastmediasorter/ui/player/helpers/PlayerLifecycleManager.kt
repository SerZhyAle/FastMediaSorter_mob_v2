package com.sza.fastmediasorter.ui.player.helpers

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.util.AnimationPolicy
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.domain.model.BackgroundAudioExitBehavior
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.mutation.Mutation
import com.sza.fastmediasorter.domain.mutation.MutationJournal
import com.sza.fastmediasorter.domain.path.PathNormalizer
import com.sza.fastmediasorter.domain.playback.PlaybackCompletionDetector
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperation
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * Manages PlayerActivity lifecycle coordination.
 *
 * Responsibilities:
 * - Initialize all managers in correct order
 * - Handle onResume/onPause state transitions
 * - Coordinate resource cleanup on onDestroy
 * - Journal successful mutations to [MutationJournal] (S0242 Phase 02 - Browse Reconciler reads it on its next onResume)
 * - Manage first-resume logic
 *
 * This consolidates logic previously scattered across onCreate, onResume, onPause, onDestroy.
 */
class PlayerLifecycleManager(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    private val lifecycle: Lifecycle,
    // S0242 Phase 02: real-time mutation journal - Reconciler reads from it on Browse onResume.
    private val mutationJournal: MutationJournal,
    private val pathNormalizer: PathNormalizer,
) {
    // Lifecycle state tracking
    private var isFirstResume = true
    private var slideshowModeRequested = false
    // Holds the path of the file that triggered the batch delete permission dialog.
    // Must be set before navigating away (optimistic advance) so that when the dialog
    // result arrives the correct file is attributed, not the one currently on screen.
    private var pendingBatchDeleteFilePath: String? = null
    private var pendingBatchDeleteOperation: PlayerFileOperation? = null
    
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
        if (activity._pdfViewerManager != null) activity.pdfViewerManager.updateButtonVisibility()
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
     *
     * S0242 Phase 02: Player no longer ships a modified-files intent payload back to
     * Browse. The Browse Reconciler reads the [MutationJournal] independently on its next
     * onResume. The note save-and-close flow keeps its own `setResult(RESULT_OK)` in
     * [com.sza.fastmediasorter.ui.player.PlayerViewerFactory] - handled there, not here.
     */
    fun onDestroy() {
        Timber.d("PlayerLifecycleManager.onDestroy: Starting cleanup")
        releaseResources()
    }
    
    /**
     * Release all resources and cleanup handlers.
     * Prevents memory leaks and ensures proper resource disposal.
     */
    private fun releaseResources() {
        // Dismiss all tracked dialogs to prevent WindowLeaked
        try { activity.dialogHelper.dismissAll() } catch (_: UninitializedPropertyAccessException) {}

        // Release managers with explicit teardown
        if (activity.areAudioBackgroundManagersConfigured) {
            activity.backgroundMusicManager.release()
            activity.audioBackgroundPhotosManager.release()
        }
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
        // S0704: drop every loading source and cancel all pending spinner work.
        activity.loadingIndicatorCoordinator.clearAll()
        
        activity.retryRunnable?.let { activity.retryHandler.removeCallbacks(it) }
        activity.retryRunnable = null
        
        // Cancel all preload jobs to prevent memory leaks
        preloadJobs.forEach { it.cancel() }
        preloadJobs.clear()
        
        // Release VideoPlayerManager
        // S0895: videoPlayerManager is a lazy-getter property, not a lateinit var - it never
        // throws UninitializedPropertyAccessException, so the try/catch this replaced never fired
        // its catch and instead lazily constructed a brand-new VideoPlayerManager during teardown
        // whenever no video was ever played this session. _videoPlayerManager (the nullable backing
        // field) is the correct guard, matching every other use site in PlayerActivity.kt.
        if (activity._videoPlayerManager != null) {
            activity.videoPlayerManager.releasePlayer()
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
        if (activity._epubViewerManager != null) activity.epubViewerManager.release()

        // Release embedded Office viewer (S0301 Phase 03) - destroys the WebView, hides container.
        if (activity._officeDocumentViewerManager != null) activity.officeDocumentViewerManager.release()

        // Release PdfViewerManager - closes PdfRenderer, cancels render jobs, clears caches (ML-001 fix)
        if (activity._pdfViewerManager != null) activity.pdfViewerManager.close()
        
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

        // S0871: LyricsManager owns a TextToSpeech engine bound to this Activity; release it here.
        // hideLyricsViewer() (its only other teardown path) runs on back-press/close, which onDestroy
        // does not traverse, so without this the TTS ServiceConnection leaks past teardown.
        try {
            activity.lyricsManager.release()
        } catch (e: UninitializedPropertyAccessException) {
            Timber.d("PlayerLifecycleManager: lyricsManager not initialized at teardown, skip")
        }

        // Note: TextViewerManager IS released at line 166
        // Note: Translation cache is NOT cleared here - it's global and managed by TranslationCacheManager
        // Cache is cleared only on app startup and when user clicks "Clear cache" in settings
        // SlideshowController handles its own cleanup via LifecycleObserver.onDestroy()
        
        Timber.d("PlayerLifecycleManager: Cleanup complete")
    }
    
    /**
     * Append a single [Mutation] to the shared [MutationJournal] (S0242 Phase 02).
     *
     * The Browse Reconciler (Phase 03) reads the per-resource journal on `onResume`, applies
     * the entries to its cached file list, and marks them as consumed. Callers obtain the
     * canonical paths via [pathNormalizer] before constructing the [Mutation] variant.
     */
    fun recordMutation(mutation: Mutation) {
        mutationJournal.record(mutation)
    }

    /**
     * Returns the [PathNormalizer] used to canonicalize paths before journaling.
     * Exposed for callers that need to build [Mutation] variants outside this manager.
     */
    fun pathNormalizer(): PathNormalizer = pathNormalizer

    /**
     * Returns the resource id of the currently loaded resource, or `null` if no resource
     * is bound to the player. Used by callers when building [Mutation] variants.
     */
    fun currentResourceId(): Long? = viewModel.state.value.resource?.id

    /**
     * Returns the [com.sza.fastmediasorter.domain.model.ResourceType] of the currently loaded
     * resource, or `null` if no resource is bound. Used by callers when normalizing paths.
     */
    fun currentResourceType(): com.sza.fastmediasorter.domain.model.ResourceType? =
        viewModel.state.value.resource?.type

    /**
     * Store the path of the file whose deletion requires a system permission dialog.
     * Called before the dialog is launched so that when the result arrives the correct
     * file is attributed - [handleBatchDeleteResult] reads this field, not currentFile,
     * because currentFile may have already advanced via optimistic navigation.
     */
    fun storePendingBatchDeleteFilePath(path: String) {
        pendingBatchDeleteFilePath = path
        Timber.d("PlayerLifecycleManager: Stored pending batch delete path: $path")
    }

    fun storePendingBatchDeleteOperation(operation: PlayerFileOperation?) {
        pendingBatchDeleteOperation = operation
    }

    fun consumePendingBatchDeleteOperation(): PlayerFileOperation? {
        val operation = pendingBatchDeleteOperation
        pendingBatchDeleteOperation = null
        return operation
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
                    activity.playerHostFactory.playbackPositionRepository.markPlaybackCompleted(
                        currentFile.path,
                        reason = "playback-completed-near-end"
                    )
                } else {
                    activity.playerHostFactory.playbackPositionRepository.savePosition(
                        currentFile.path,
                        position,
                        duration,
                    )
                    Timber.d("PlayerLifecycleManager: Saved playback position $position/$duration for ${currentFile.name}")
                }
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                Timber.e(e, "PlayerLifecycleManager: Failed to save playback position for ${currentFile.name}")
            }
        }
    }

    /**
     * Register back-press handler for fullscreen viewers and overlay dismissal.
     * Called from PlayerActivity.setupViews() after all managers are initialized.
     */
    fun setupBackPressHandler() {
        val backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // S0107: Draw Mode intercepts back before all other handlers
                if (activity.isDrawOverlayManagerReady &&
                    activity.imageDrawOverlayManager.handleBackPress()) return
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

                if (activity.isPlayerFileOperationQueueInitialized) {
                    val pendingCount = activity.playerFileOperationQueue.snapshotPending().size
                    if (pendingCount > 0) {
                        MaterialAlertDialogBuilder(activity)
                            .setTitle(R.string.dialog_player_exit_pending_queue_title)
                            .setMessage(activity.resources.getQuantityString(R.plurals.dialog_player_exit_pending_queue_message, pendingCount, pendingCount))
                            .setPositiveButton(R.string.dialog_player_exit_pending_queue_leave) { _, _ ->
                                activity.exitPlayerWithAudioCheck()
                            }
                            .setNegativeButton(R.string.dialog_player_exit_pending_queue_stay, null)
                            .showBoundTo(activity)
                        return
                    }
                }

                activity.exitPlayerWithAudioCheck()
            }
        }
        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
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
            warnIfBackgroundHandoffMissed()
            doFinish(withTransition)
            return
        }

        val audioPlayer = activity.audioServiceController?.player
        val action = AudioExitBehaviorResolver.resolve(
            serviceAudioActive = true,
            player = audioPlayer,
            behavior = viewModel.state.value.backgroundAudioExitBehavior,
        )
        when (action) {
            AudioExitAction.FINISH -> doFinish(withTransition)
            AudioExitAction.STOP_AND_FINISH -> {
                audioPlayer?.stop()
                doFinish(withTransition)
            }
            AudioExitAction.ASK -> showExitAudioDialog(withTransition)
        }
    }

    /**
     * Tell the user when a network/cloud audio track they expected to keep playing could not be handed
     * to the background service (e.g. it fell back to the in-app player for lack of credentials), so the
     * exit is an explicit "couldn't continue" rather than a silent stop. No-op for local audio or when
     * background playback is off / set to always stop.
     */
    private fun warnIfBackgroundHandoffMissed() {
        val state = viewModel.state.value
        if (!state.enablePersistentAudioPlayback) return
        if (state.backgroundAudioExitBehavior == BackgroundAudioExitBehavior.ALWAYS_STOP) return
        val file = state.currentFile ?: return
        if (file.type != MediaType.AUDIO) return
        val path = file.path
        val isNetworkOrCloud = path.startsWith("smb://") || path.startsWith("sftp://") ||
            path.startsWith("ftp://") || path.startsWith("cloud://")
        if (isNetworkOrCloud) {
            Toast.makeText(activity, R.string.audio_background_handoff_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExitAudioDialog(withTransition: Boolean) {
        BackgroundAudioExitDialog.show(
            context = activity,
            onStopThisTime = {
                activity.audioServiceController?.player?.stop()
                doFinish(withTransition)
            },
            onContinueThisTime = { doFinish(withTransition) },
            onAlwaysStop = {
                viewModel.updateExitBehavior(BackgroundAudioExitBehavior.ALWAYS_STOP)
                activity.audioServiceController?.player?.stop()
                doFinish(withTransition)
            },
            onAlwaysContinue = {
                viewModel.updateExitBehavior(BackgroundAudioExitBehavior.ALWAYS_CONTINUE)
                doFinish(withTransition)
            },
        )
    }

    fun doFinish(withTransition: Boolean = false) {
        viewModel.clearResumeState()
        activity.finish()
        if (withTransition) {
            // S2250: zero means "no transition" - the player still closes, only the slide is gone.
            val animate = AnimationPolicy.isAnimationAllowed
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(
                if (animate) R.anim.slide_in_left else 0,
                if (animate) R.anim.slide_out_right else 0
            )
        }
    }

    /**
     * Set screen keep-awake state for slideshow.
     * @param enabled true to force screen on, false to restore the effective player keep-awake rule
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
                // S0438: a player host keeps the screen on when either the global or the dependent
                // player setting is on - restore to that effective rule, not preventSleep alone.
                val settings = activity.playerHostFactory.settingsRepository.getSettings().first()
                if (settings.preventSleep || settings.keepScreenOnPlayer) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    /**
     * Handle successful file deletion from any permission callback path.
     * S0242 Phase 02: emits a [Mutation.Delete] to the [MutationJournal] so the Browse
     * Reconciler picks it up on its next onResume, then removes the file from cache and
     * advances the playlist (or finishes if the list is empty).
     */
    fun handleDeleteSuccess(deletedFilePath: String) {
        recordDeleteMutation(deletedFilePath)
        viewModel.state.value.resource?.let { resource ->
            MediaFilesCacheManager.removeFile(resource.id, deletedFilePath)
        }
        val hasRemainingFiles = viewModel.removeDeletedFile(deletedFilePath)
        if (!hasRemainingFiles) activity.finish()
    }

    /**
     * Build and journal a [Mutation.Delete] for [path] using the currently bound resource.
     * No-op when no resource is bound - without a resource id, the entry would be unroutable.
     */
    private fun recordDeleteMutation(path: String) {
        val resourceId = currentResourceId() ?: return
        val resourceType = currentResourceType() ?: return
        recordMutation(
            Mutation.Delete(
                resourceId = resourceId,
                canonicalPath = pathNormalizer.canonical(path, resourceType),
                opId = UUID.randomUUID().toString(),
                timestampMs = System.currentTimeMillis(),
            )
        )
    }

    /**
     * Handle Android 11+ batch delete permission result (createDeleteRequest).
     * Called from the batchDeletePermissionLauncher result callback.
     *
     * Uses [pendingBatchDeleteFilePath] rather than the current file because the player
     * may have already performed an optimistic advance to the next file before the dialog
     * was shown - reading currentFile here would attribute the deletion to the wrong file.
     */
    fun handleBatchDeleteResult(resultCode: Int) {
        val filePath = pendingBatchDeleteFilePath
        pendingBatchDeleteFilePath = null
        if (resultCode == Activity.RESULT_OK) {
            Timber.i("PlayerLifecycleManager: Batch delete permission granted")
            if (filePath != null) {
                handleDeleteSuccess(filePath)
            } else {
                Timber.e("PlayerLifecycleManager: Batch delete granted but pendingBatchDeleteFilePath was null - no file attributed")
            }
        } else {
            Timber.w("PlayerLifecycleManager: Batch delete permission denied")
            Toast.makeText(
                activity,
                activity.getString(R.string.error_delete_failed),
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
                activity.getString(R.string.error_delete_failed),
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
