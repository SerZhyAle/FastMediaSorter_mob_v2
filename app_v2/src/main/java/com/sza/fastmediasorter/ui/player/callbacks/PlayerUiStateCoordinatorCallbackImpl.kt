package com.sza.fastmediasorter.ui.player.callbacks

import androidx.core.view.isVisible
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.helpers.PlayerUiStateCoordinator
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
import timber.log.Timber

/**
 * Implementation of PlayerUiStateCoordinator.Callback extracted from PlayerActivity.
 * Provides PlayerUiStateCoordinator with access to Activity state and UI methods.
 */
class PlayerUiStateCoordinatorCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel
) : PlayerUiStateCoordinator.Callback {

    override fun isActivityAlive(): Boolean = !(activity.isFinishing || activity.isDestroyed)

    override fun getCurrentSettings(): AppSettings? = activity.currentSettings
    override fun setCurrentSettings(settings: AppSettings?) {
        activity.currentSettings = settings
    }

    override fun getCurrentFilePath(): String? = activity.currentFilePath
    override fun setCurrentFilePath(path: String?) {
        activity.currentFilePath = path
    }

    override fun isImageVisible(): Boolean {
        val binding = activity.activityBinding
        val imageViewVisible = binding.imageView.isVisible
        val photoSurfaceVisible =
            (binding.photoDualSurfaceContainer?.isVisible == true) &&
                (binding.photoView.isVisible || (binding.photoViewSurfaceB?.isVisible == true))
        return imageViewVisible || photoSurfaceVisible
    }

    override fun hasImageDrawable(): Boolean {
        val binding = activity.activityBinding
        if (binding.imageView.isVisible && binding.imageView.drawable != null) {
            return true
        }

        if (binding.photoDualSurfaceContainer?.isVisible == true) {
            if (binding.photoView.isVisible && binding.photoView.drawable != null) {
                return true
            }
            if (binding.photoViewSurfaceB?.isVisible == true && binding.photoViewSurfaceB?.drawable != null) {
                return true
            }
        }

        return false
    }

    override fun isSlideshowModeRequested(): Boolean = activity.slideshowModeRequested
    override fun clearSlideshowModeRequested() {
        activity.slideshowModeRequested = false
    }

    override fun hasShownHintType(type: TouchZoneHintType): Boolean = type in activity.shownHintTypes
    override fun markHintTypeShown(type: TouchZoneHintType) {
        activity.shownHintTypes.add(type)
    }

    override fun getUseTouchZones(): Boolean = activity.useTouchZones

    override fun displayImage(path: String) {
        activity.stopVideoPlayback()
        activity.displayImage(path)
    }

    override fun playVideo(path: String) = activity.playVideo(path)

    override fun displayText(file: MediaFile) {
        activity.stopVideoPlayback()
        activity.displayText(file)
    }

    override fun displayPdf(file: MediaFile) {
        activity.stopVideoPlayback()
        // Disable touch zones for PDF - use PDF navigation controls instead
        activity.safeViews.touchZonesOverlay.isVisible = false
        activity.safeViews.touchZones3Overlay.isVisible = false
        if (activity._pdfViewerManager != null) activity.pdfViewerManager.displayPdf(file)
    }

    override fun displayEpub(file: MediaFile) {
        activity.stopVideoPlayback()
        // Disable touch zones for EPUB - use EPUB navigation controls instead
        activity.safeViews.touchZonesOverlay.isVisible = false
        activity.safeViews.touchZones3Overlay.isVisible = false
        if (activity._epubViewerManager != null) activity.epubViewerManager.displayEpub(file)
    }

    override fun adjustTouchZonesForVideo(isVideo: Boolean) =
        activity.adjustTouchZonesForVideo(isVideo)

    override fun updatePanelVisibility(showCommandPanel: Boolean) =
        activity.updatePanelVisibility(showCommandPanel)

    override fun updateCommandAvailability(state: PlayerViewModel.PlayerState) =
        activity.updateCommandAvailability(state)

    override fun updatePlayPauseButton() = activity.updatePlayPauseButton()
    override fun updateSlideShowButton() = activity.dialogAndUiStateManager.updateSlideShowButton()
    override fun updateVolumeButtonsVisibility() = activity.dialogAndUiStateManager.updateVolumeButtonsVisibility()

    override fun showTouchZoneHintOverlay(type: TouchZoneHintType) = activity.showTouchZoneHintOverlay(type)
    override fun showSlideshowEnabledMessage() = activity.showSlideshowEnabledMessage()

    override fun toggleSlideShow() = viewModel.toggleSlideShow()
    override fun startSlideshow(intervalSeconds: Int) = activity.slideshowController.startSlideshow(intervalSeconds)
    override fun getLatestState(): PlayerViewModel.PlayerState = viewModel.state.value
    override fun forceStateUpdate() = viewModel.forceStateUpdate()
    override fun enterAudioSlideshowPhotoModeIfNeeded() {
        val state = viewModel.state.value
        val currentFile = state.currentFile
        val isAudioWithPhotos = currentFile?.type == MediaType.AUDIO &&
            state.enablePhotosDuringAudio &&
            state.audioBackgroundPhotosResourceId != null

        Timber.d("PlayerActivity: enterAudioSlideshowPhotoModeIfNeeded - isAudio=${currentFile?.type == MediaType.AUDIO}, enablePhotos=${state.enablePhotosDuringAudio}, resourceId=${state.audioBackgroundPhotosResourceId}, slideshowActive=${state.isSlideShowActive}, alreadyInMode=${activity.audioSlideshowPhotoModeManager.isActive}")

        if (isAudioWithPhotos && state.isSlideShowActive && !activity.audioSlideshowPhotoModeManager.isActive) {
            Timber.i("PlayerActivity: ✓ Auto-entering audio slideshow photo mode")
            activity.backgroundMusicManager.isAudioSlideshowPhotoMode = true
            activity.audioSlideshowPhotoModeManager.enter()
        } else {
            Timber.d("PlayerActivity: ✗ Not entering audio slideshow photo mode (conditions not met)")
        }
    }

    override fun updateTouchZonesHelpButtonVisibility(visible: Boolean) {
        activity.activityBinding.btnTouchZonesHelp?.isVisible = visible
    }
}
