package com.sza.fastmediasorter.ui.player.callbacks

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.helpers.TouchZoneGestureManager

/**
 * Implementation of TouchZoneGestureManager.TouchZoneCallback extracted from PlayerActivity.
 * Handles touch zone gesture actions (navigation, zoom, document scrolling, etc.).
 */
class PlayerTouchZoneCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel
) : TouchZoneGestureManager.TouchZoneCallback {

    override fun isOverlayBlocking(): Boolean = activity.isOverlayBlocking()

    override fun getTouchZonesEnabled(): Boolean = activity.useTouchZones

    override fun getLoadFullSizeImages(): Boolean = activity.loadFullSizeImages

    override fun getNineZoneGridEnabled(): Boolean = activity.nineZoneGridEnabled

    override fun onBack() {
        activity.exitPlayerWithAudioCheck(withTransition = true)
    }

    override fun onCopy() = activity.dialogAndUiStateManager.showCopyDialog()

    override fun onRename() = activity.dialogAndUiStateManager.showRenameDialog()

    override fun onPrevious() = activity.navigationManager.navigatePreviousFromControl()

    override fun onMove() = activity.dialogAndUiStateManager.showMoveDialog()

    override fun onNext() = activity.navigationManager.navigateNextFromControl()

    override fun onSwitchToCommandPanel() {
        // Show command panel (exit fullscreen mode)
        if (!viewModel.state.value.showCommandPanel) {
            viewModel.toggleCommandPanel()
        }
    }

    override fun onToggleSlideshow() {
        viewModel.toggleSlideShow()
        activity.dialogAndUiStateManager.updateSlideShowButton()
    }

    override fun onDelete() = activity.fileOperationsHandler.deleteCurrentFile()

    override fun onPauseResume() {
        val player = activity.activityBinding.playerView.player
        if (player != null) {
            if (player.isPlaying) player.pause() else player.play()
            // S1005: reveal the transport controller on the video/audio center-tap. The unified player
            // routes video taps here (9-zone PAUSE_RESUME) and its VideoTouchDelegate reveal is disabled,
            // so without this exo_play_pause / btnPlaybackControl / seekbar stay unreachable after the
            // 15s auto-hide. Callback fires only from the video/audio center zone - no effect on images.
            activity.activityBinding.playerView.showController()
        }
    }

    override fun onSeekToStart() {
        activity.activityBinding.playerView.player?.seekTo(0)
    }

    override fun onSeekToEnd() {
        val player = activity.activityBinding.playerView.player
        player?.let { it.seekTo(it.duration) }
    }

    override fun onZoomIn() {
        val currentScale = activity.activityBinding.photoView.scale
        val newScale = (currentScale * 2f).coerceAtMost(8f)
        activity.activityBinding.photoView.setScale(newScale, true)
    }

    override fun onZoomOut() {
        val currentScale = activity.activityBinding.photoView.scale
        val newScale = (currentScale / 2f).coerceAtLeast(1f)
        activity.activityBinding.photoView.setScale(newScale, true)
    }

    override fun onPageUp() {
        // Document page up - handled by specific viewers
        when (viewModel.state.value.currentFile?.type) {
            MediaType.PDF -> if (activity._pdfViewerManager != null) activity.pdfViewerManager.showPreviousPage()
            MediaType.EPUB -> if (activity._epubViewerManager != null) activity.epubViewerManager.onPreviousPageRequest()
            MediaType.TEXT -> if (activity._textViewerManager != null) activity.textViewerManager.scrollUp()
            else -> {}
        }
    }

    override fun onPageDown() {
        // Document page down - handled by specific viewers
        when (viewModel.state.value.currentFile?.type) {
            MediaType.PDF -> if (activity._pdfViewerManager != null) activity.pdfViewerManager.showNextPage()
            MediaType.EPUB -> if (activity._epubViewerManager != null) activity.epubViewerManager.onNextPageRequest()
            MediaType.TEXT -> if (activity._textViewerManager != null) activity.textViewerManager.scrollDown()
            else -> {}
        }
    }

    override fun showSlideshowEnabledMessage() = activity.showSlideshowEnabledMessage()

    override fun updateSlideShowButton() = activity.dialogAndUiStateManager.updateSlideShowButton()

    override fun updateSlideShow() = activity.navigationManager.updateSlideshowState()

    override fun setPhotoViewZoom(scale: Float) = activity.playerGestureCallback.setPhotoViewZoom(scale)
}
