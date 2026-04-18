package com.sza.fastmediasorter.ui.player.callbacks

import android.os.Handler
import androidx.core.view.isVisible
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.ImageLoadingManager
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.SlideshowController
import com.sza.fastmediasorter.ui.player.VideoPlayerManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerSettingsManager
import com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController
import com.sza.fastmediasorter.domain.model.StereoMode
import timber.log.Timber

/**
 * Implementation of VideoPlayerManager.PlayerCallback extracted from PlayerActivity.
 * Handles events from VideoPlayerManager (ExoPlayer wrapper).
 */
class PlayerPlaybackCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    private val binding: ActivityPlayerUnifiedBinding,
    private val loadingIndicatorHandler: Handler,
    private val showLoadingIndicatorRunnable: Runnable,
    private val playerSettingsManagerProvider: () -> PlayerSettingsManager,
    private val imageLoadingManagerProvider: () -> ImageLoadingManager,
    private val slideshowController: SlideshowController,
    private val sleepTimerManagerProvider: () -> com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager? = { null },
    private val audioEmptyStateControllerProvider: () -> AudioEmptyStateController? = { null }
) : VideoPlayerManager.PlayerCallback {

    override fun onPlaybackReady() {
        loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
        binding.progressBar.isVisible = false

        // Apply player settings when ready
        playerSettingsManagerProvider().applyPlayerSettings()

        // Update track switcher buttons visibility (audio/subtitle)
        activity.updateTrackButtonsVisibility()

        // Update audio info and load cover art for audio files
        val currentFile = viewModel.state.value.currentFile
        if (currentFile?.type == MediaType.AUDIO) {
            activity.updateAudioFormatInfo()
            imageLoadingManagerProvider().loadAudioCoverArt(currentFile)
            activity.prefetchNextAudio()
            // Refresh song label in audio slideshow photo mode (covers auto-advance case)
            activity.updateAudioSlideshowCurrentSongLabel()
        }
    }
    
    override fun onPlaybackError(error: Throwable) {
        activity.handleMediaLoadErrorAndSkip()
    }
    
    override fun onBuffering(isBuffering: Boolean) {
        if (isBuffering) {
            binding.progressBar.isVisible = true
        } else {
            binding.progressBar.isVisible = false
        }
    }
    
    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        val currentFile = viewModel.state.value.currentFile
        val isAudioFile = currentFile?.type == MediaType.AUDIO
        sleepTimerManagerProvider()?.updateVinylState(isPlaying, isAudioFile)
        if (isAudioFile) {
            audioEmptyStateControllerProvider()?.onIsPlayingChanged(isPlaying)
        }
    }
    
    override fun onPlaybackEnded() {
        val wasAudio = viewModel.state.value.currentFile?.type == com.sza.fastmediasorter.domain.model.MediaType.AUDIO
        if (viewModel.state.value.isSlideShowActive) {
            Timber.tag("TOUCH_ZONE_DEBUG").d("NEXT triggered by: Playback ended (slideshow)")
            viewModel.nextFile(skipDocuments = true)
            slideshowController.restartTimer()
            // Advance background photo on audio track auto-advance (mirrors navigateNext behaviour)
            if (wasAudio) {
                activity.advanceAudioBackgroundPhoto()
            }
        }
    }
    
    override fun onAudioFormatChanged(format: VideoPlayerManager.AudioFormat?) {
        // Not used currently
    }
    
    override fun showError(message: String) {
        activity.showError(message)
    }

    override fun showFileNotFound(fileName: String) {
        activity.showFileNotFound(fileName)
    }

    override fun isActivityDestroyed(): Boolean {
        return activity.isDestroyed || activity.isFinishing
    }
    
    override fun showUnsupportedFormatError(message: String, filePath: String, isLocalFile: Boolean) {
        activity.showUnsupportedFormatError(message, filePath, isLocalFile)
    }

    override fun onBeforeVideoLoad(path: String) {
        // Keep explicit per-file behaviour predictable: every new video starts in AUTO,
        // then onTracksChanged may override it if the user has not picked a manual mode.
        viewModel.resetStereoModeForNewFile()
        // Reset flag so the 3D toast can fire once for the new file.
        stereoToastShownForCurrentFile = false
    }

    // Tracks whether the "3D detected" toast has already been shown for the current file.
    // Resets in onBeforeVideoLoad() so each new file gets one toast chance.
    private var stereoToastShownForCurrentFile = false

    override fun onStereoDetected(mode: StereoMode) {
        // Pass auto-detected mode to ViewModel; it will only apply if user is still on AUTO.
        viewModel.setAutoDetectedStereoMode(mode)

        // Show VR CTA or toast for actual 3D content (SBS/OU), not for MONO/AUTO/UNKNOWN.
        val is3d = mode == StereoMode.SBS_FULL || mode == StereoMode.SBS_HALF || mode == StereoMode.OU
        if (is3d && !stereoToastShownForCurrentFile && !activity.isDestroyed && !activity.isFinishing) {
            stereoToastShownForCurrentFile = true
            // In standard flavor (no VR support): show CTA dialog suggesting VR edition
            if (!com.sza.fastmediasorter.BuildConfig.SUPPORT_VR_PLAYER) {
                Timber.d("PlayerPlaybackCallbackImpl: showing VR install CTA for mode=$mode")
                viewModel.showVrInstallCta(mode)
            } else {
                // In VR flavor: show a brief toast confirming stereo detection.
                // The user can fine-tune format in the 3D tab of PlaybackControlDialog.
                Timber.d("PlayerPlaybackCallbackImpl: VR flavor, 3D detected mode=$mode — showing toast")
                val modeName = when (mode) {
                    StereoMode.SBS_FULL, StereoMode.SBS_HALF -> "SBS"
                    StereoMode.OU -> "OU"
                    else -> mode.name
                }
                android.widget.Toast.makeText(
                    activity,
                    "3D: $modeName",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}