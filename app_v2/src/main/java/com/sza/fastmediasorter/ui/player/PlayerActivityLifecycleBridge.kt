package com.sza.fastmediasorter.ui.player

import android.content.Intent
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sza.fastmediasorter.domain.model.MediaType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Lifecycle method bodies (observeData, onPause, onResumeWithViews) for [PlayerActivity]. Extracted to keep the host class under the 1000-LOC budget. */
internal class PlayerActivityLifecycleBridge(private val activity: PlayerActivity) {

    fun observeData() {
        activity.observerManager = PlayerObserverManager(activity, activity.settingsRepository)
        activity.observerManager.startObserving()
        activity.lifecycleScope.launch {
            activity.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                activity.viewModel.state
                    .map { it.currentFile?.type }
                    .distinctUntilChanged()
                    .collect { type ->
                        val isAudioOrVideo = type == MediaType.AUDIO || type == MediaType.VIDEO
                        activity.blackScreenOverlayManager.onFileTypeChanged(isAudioOrVideo)
                    }
            }
        }
        // S0213 Pillar C: surface a one-shot snackbar with "Close player" CTA on memory FAIL.
        activity.lifecycleScope.launch {
            activity.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                activity.memoryDegradationSignal.events.collect { event ->
                    if (activity.memoryAlertShownInSessionAccess) return@collect
                    activity.memoryAlertShownInSessionAccess = true
                    activity.dialogAndUiStateManager.showMemoryDegradationSnackbar {
                        timber.log.Timber.i("S0213 user closed player from memory alert; event=$event")
                        activity.finish()
                    }
                }
            }
        }
    }

    fun onPause() {
        activity.lifecycleManager.onPause()
        // S0289: propagate parent resource id back so a future onActivityResult-based MainActivity can restore focus to the launched item.
        val currentResourceId = activity.viewModel.state.value.resourceId
        if (currentResourceId > 0L && activity.isFinishing) {
            activity.setResult(android.app.Activity.RESULT_OK,
                Intent().putExtra(PlayerActivity.EXTRA_LAST_PLAYED_RESOURCE_ID, currentResourceId))
        }
        val serviceAudioActiveOnPause = activity.isMediaLoaderManagerInitialized && activity.mediaLoaderManager.isServiceAudioActive
        if (serviceAudioActiveOnPause) {
            activity.activityBinding.playerView.player = null
        } else {
            // Track lifecycle-induced togglePause() so onResumeWithViews() can reverse it. Must be set before togglePause() to survive the state emission.
            activity.wasToggledPausedByLifecycle = true
            activity.viewModel.togglePause()
            // When finishing, release ExoPlayer from PlayerView Surface immediately. Without this CCodec keeps the Surface alive across ATMS window transition (permanent black screen on back press for non-service audio/video). serviceAudioActiveOnPause branch already does this.
            if (activity.isFinishing) activity.activityBinding.playerView.player = null
        }
        activity.audioEmptyStateController?.onPause()
        activity.lifecycleManager.saveCurrentPlaybackPosition()
        // S0021: stop FPS meter when activity loses foreground; overlay hides via updatePlayerFpsOverlay().
        activity.playerFpsMeter.stop()
        activity.activityBinding.tvPlayerFpsOverlay.isVisible = false
    }

    fun onResumeWithViews() {
        activity.lifecycleManager.onResume()
        // Reverse lifecycle-induced togglePause() from onPause() so isPaused does not persist across background/resume. Without this, isPaused=true causes every subsequent video load to start with playWhenReady=false, breaking slideshow continuity and requiring user to press PLAY after errors.
        if (activity.wasToggledPausedByLifecycle) {
            activity.wasToggledPausedByLifecycle = false
            activity.viewModel.togglePause()
        }
        // S0162: re-apply orientation on resume (re-reads OS auto-rotate state - ADR-1).
        val rs = activity.viewModel.state.value
        activity.screenRotationManager.apply(activity, rs.followSystemRotation, rs.playerRotationSensorEnabled, activity.hasAccelerometer)
        activity.audioEmptyStateController?.onResume()
        activity.nowPlayingManager?.onStart(
            activity.viewModel.state.value.currentFile?.type,
            activity.viewModel.state.value.showNowPlayingPanel,
        )
        if (activity.isMediaLoaderManagerInitialized) activity.mediaLoaderManager.reattachServicePlayerToView()
        // S0021: start collecting FPS once per Activity lifetime; reconcile visibility.
        if (!activity.playerFpsCollectorStartedAccess) {
            activity.playerFpsCollectorStartedAccess = true
            activity.lifecycleScope.launch {
                activity.playerFpsMeter.fps.collect { fps ->
                    activity.activityBinding.tvPlayerFpsOverlay.text = "$fps fps"
                }
            }
            activity.lifecycleScope.launch {
                activity.viewModel.settings.collect { activity.updatePlayerFpsOverlay() }
            }
        }
        activity.updatePlayerFpsOverlay()
    }
}
