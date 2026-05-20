package com.sza.fastmediasorter.ui.player.callbacks

import android.widget.Toast
import androidx.core.view.isVisible
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.domain.model.AudioMetadata
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.ui.player.ImageLoadingManager
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import timber.log.Timber

/**
 * Implementation of ImageLoadingManager.ImageLoadingCallback extracted from PlayerActivity.
 * Bridges ImageLoadingManager events back to Activity lifecycle and media state.
 */
class PlayerImageLoadingCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel
) : ImageLoadingManager.ImageLoadingCallback {

    override fun isFinishing(): Boolean = activity.isFinishing

    override fun isDestroyed(): Boolean = activity.isDestroyed

    override fun releasePlayer() {
        activity.releasePlayer()
    }

    override fun showError(message: String, exception: Throwable?) {
        if (activity.slideshowResourceAvailabilityManager.handleImageLoadFailure(exception)) {
            return
        }
        activity.showError(message, exception)
    }

    override fun showToast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }

    override fun getWindowManager(): android.view.WindowManager {
        return activity.windowManager
    }

    override fun onAudioMetadataLoaded(metadata: AudioMetadata, originatingPath: String) {
        activity.onAudioMetadataLoaded(metadata, originatingPath)
    }

    override fun updateSlideShow() {
        activity.updateSlideShow()
    }

    override fun getAdjacentFiles(): List<MediaFile> = viewModel.getAdjacentFiles()

    override fun getCurrentFile(): MediaFile? = viewModel.state.value.currentFile

    override fun getCurrentResource(): MediaResource? = viewModel.state.value.resource

    override fun getExoPlayer(): ExoPlayer? =
        if (activity._videoPlayerManager != null) activity.videoPlayerManager.getPlayer() else null

    override fun getString(resId: Int): String = activity.getString(resId)

    override fun isShowingCommandPanel(): Boolean = viewModel.state.value.showCommandPanel

    override fun isSlideshowActive(): Boolean =
        viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused

    override fun isImageCropEditMode(): Boolean =
        activity.viewModel.state.value.imageEditMode == com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.CROP

    override fun setAnimatedBadgeVisible(visible: Boolean) {
        activity.activityBinding.tvAnimatedBadge.isVisible = visible
    }

    // S0107: expose loaded bitmap so ImageDrawOverlayManager can access it for merge
    override fun onStaticImageLoaded(bitmap: android.graphics.Bitmap?) {
        viewModel.currentDisplayedBitmap = bitmap
        activity.slideshowResourceAvailabilityManager.onImageLoadSucceeded()
    }

    override fun onImageContentLoaded() {
        activity.slideshowResourceAvailabilityManager.onImageLoadSucceeded()
    }
}
