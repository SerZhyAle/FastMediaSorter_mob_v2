package com.sza.fastmediasorter.ui.player.callbacks

import android.widget.Toast
import androidx.core.view.isVisible
import androidx.media3.exoplayer.ExoPlayer
import com.sza.fastmediasorter.domain.model.AudioMetadata
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.stats.ViewKind
import com.sza.fastmediasorter.ui.player.ImageLoadingManager
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel

/**
 * Implementation of ImageLoadingManager.ImageLoadingCallback extracted from PlayerActivity.
 * Bridges ImageLoadingManager events back to Activity lifecycle and media state.
 */
class PlayerImageLoadingCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink
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

    override suspend fun getCredentialsIdForResource(resourceId: Long): String? =
        viewModel.getCredentialsIdForResource(resourceId)

    override fun onNetworkMediaLoadFailed(candidates: List<Throwable>): Boolean =
        viewModel.onMediaLoadFailed(candidates, viewModel.state.value.currentFile?.name.orEmpty())

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
        // S0473: one image viewed. Fires once per fully-loaded static image or GIF (Glide
        // onResourceReady), never per progress tick.
        statsSink.record(StatsEvent.View(ViewKind.IMAGE))
        activity.slideshowResourceAvailabilityManager.onImageLoadSucceeded()
    }
}
