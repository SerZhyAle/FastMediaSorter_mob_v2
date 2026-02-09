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
    private val slideshowController: SlideshowController
) : VideoPlayerManager.PlayerCallback {

    override fun onPlaybackReady() {
        loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
        binding.progressBar.isVisible = false
        
        // Apply player settings when ready
        playerSettingsManagerProvider().applyPlayerSettings()
        
        // Update audio info and load cover art for audio files
        val currentFile = viewModel.state.value.currentFile
        if (currentFile?.type == MediaType.AUDIO) {
            activity.updateAudioFormatInfo()
            imageLoadingManagerProvider().loadAudioCoverArt(currentFile)
        }
    }
    
    override fun onPlaybackError(error: Throwable) {
        // Error handling already done in manager
    }
    
    override fun onBuffering(isBuffering: Boolean) {
        if (isBuffering) {
            binding.progressBar.isVisible = true
        } else {
            binding.progressBar.isVisible = false
        }
    }
    
    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        // Handled by ViewModel state
    }
    
    override fun onPlaybackEnded() {
        if (viewModel.state.value.isSlideShowActive) {
            Timber.tag("TOUCH_ZONE_DEBUG").w("NEXT triggered by: Playback ended (slideshow)")
            viewModel.nextFile(skipDocuments = true)
            slideshowController.restartTimer()
        }
    }
    
    override fun onAudioFormatChanged(format: VideoPlayerManager.AudioFormat?) {
        // Not used currently
    }
    
    override fun showError(message: String) {
        activity.showError(message)
    }
    
    override fun isActivityDestroyed(): Boolean {
        return activity.isDestroyed || activity.isFinishing
    }
    
    override fun showUnsupportedFormatError(message: String, filePath: String, isLocalFile: Boolean) {
        activity.showUnsupportedFormatError(message, filePath, isLocalFile)
    }
}
