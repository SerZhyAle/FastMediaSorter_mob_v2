package com.sza.fastmediasorter.ui.player.callbacks

import androidx.media3.common.Player
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.helpers.PlayerHelpEntryManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler
import com.sza.fastmediasorter.ui.player.helpers.StillImageZoomManager
import com.sza.fastmediasorter.ui.player.helpers.ViewerKeyStepMath
import com.sza.fastmediasorter.ui.player.helpers.ZoomAction
import timber.log.Timber

/**
 * Implementation of PlayerKeyboardHandler.PlayerKeyboardCallback extracted from PlayerActivity.
 * Handles keyboard/remote-control events routed from PlayerKeyboardHandler.
 */
class PlayerKeyboardCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel
) : PlayerKeyboardHandler.PlayerKeyboardCallback {

    override fun onDeleteFile() {
        activity.fileOperationsHandler.deleteCurrentFile()
    }

    override fun onExitPlayer() {
        activity.exitPlayerWithAudioCheck()
    }

    override fun onToggleSlideshow() {
        val wasActive = viewModel.state.value.isSlideShowActive
        activity.navigationManager.toggleSlideshow()

        if (!wasActive && viewModel.state.value.isSlideShowActive) {
            activity.showSlideshowEnabledMessage()
        }

        activity.dialogAndUiStateManager.updateSlideShowButton()
        activity.navigationManager.updateSlideshowState()
    }

    override fun onShowRenameDialog() {
        activity.dialogAndUiStateManager.showRenameDialog()
    }

    override fun onShowFileInfo() {
        activity.showFileInfo()
    }

    override fun onToggleCommandPanel() {
        viewModel.toggleCommandPanel()
    }

    override fun onToggleCopyPanel() {
        activity.dialogAndUiStateManager.toggleCopyPanel()
    }

    // S0533: gate the COPY shortcut on the same availability the copy panel uses (copy enabled +
    // populated destinations), so it cannot toggle a panel kept hidden when copying is disabled or
    // no destinations are configured.
    override fun canCopyCurrent(): Boolean {
        val available = activity.isCommandPanelControllerReady() &&
            activity.commandPanelController.isCopyAvailable()
        return available
    }

    override fun onToggleMovePanel() {
        activity.dialogAndUiStateManager.toggleMovePanel()
    }

    // S0532: gate the MOVE shortcut on the same availability the move panel uses (move enabled +
    // populated destinations + write permission), so it cannot toggle a panel kept hidden on a
    // read-only / non-writable resource.
    override fun canMoveCurrent(): Boolean {
        val available = activity.isCommandPanelControllerReady() &&
            activity.commandPanelController.isMoveAvailable()
        return available
    }

    override fun onMoveToSlot(slot: Int): Boolean =
        activity.dialogAndUiStateManager.triggerMoveSlot(slot)

    override fun onCopyToSlot(slot: Int): Boolean =
        activity.dialogAndUiStateManager.triggerCopySlot(slot)

    // Created on first zoom key: the surface exists only while the activity binding is alive.
    private val stillZoom: StillImageZoomManager by lazy {
        StillImageZoomManager(activity.activityBinding.photoView) {
            val file = viewModel.state.value.currentFile
            if (file?.width != null && file.height != null) file.width to file.height else null
        }
    }

    override fun onZoom(action: ZoomAction): Boolean {
        Timber.d("S4362: onZoom action=$action")
        return when (viewModel.state.value.currentFile?.type) {
            MediaType.VIDEO -> videoZoomAction(action)
            MediaType.IMAGE, MediaType.GIF -> stillZoomAction(action)
            else -> false
        }
    }

    override fun onZoomAt(factor: Float, x: Float, y: Float): Boolean =
        when (viewModel.state.value.currentFile?.type) {
            MediaType.VIDEO -> activity.gestureSetupManager.videoZoom.zoomAt(factor, x, y)
            MediaType.IMAGE, MediaType.GIF -> stillZoom.zoomAt(factor, x, y)
            else -> false
        }

    private fun videoZoomAction(action: ZoomAction): Boolean {
        val video = activity.gestureSetupManager.videoZoom
        return when (action) {
            ZoomAction.IN -> video.zoomIn()
            ZoomAction.OUT -> video.zoomOut()
            ZoomAction.FIT -> video.fit()
            ZoomAction.ACTUAL_SIZE -> video.actualSize()
        }
    }

    private fun stillZoomAction(action: ZoomAction): Boolean = when (action) {
        ZoomAction.IN -> stillZoom.zoomIn()
        ZoomAction.OUT -> stillZoom.zoomOut()
        ZoomAction.FIT -> stillZoom.fit()
        ZoomAction.ACTUAL_SIZE -> stillZoom.actualSize()
    }

    override fun onShowEditDialog() {
        val currentFile = viewModel.state.value.currentFile
        when (currentFile?.type) {
            MediaType.IMAGE -> {
                if (activity.isAnimatedImagePath(
                        currentFile.path
                    )) {
                    activity.dialogAndUiStateManager.showGifEditDialog()
                } else {
                    activity.dialogAndUiStateManager.showImageEditDialog()
                }
            }
            MediaType.GIF -> activity.dialogAndUiStateManager.showGifEditDialog()
            // Keep keyboard-triggered Edit aligned with the visible video command-panel entry point.
            MediaType.VIDEO -> activity.dialogHelper.showPlaybackControlDialog()
            MediaType.AUDIO -> activity.dialogHelper.showPlaybackControlDialog()
            else -> {}
        }
    }

    override fun getActivePlayer(): Player? =
        activity.audioServiceController?.player
            ?: if (activity._videoPlayerManager != null) activity.videoPlayerManager.getPlayer() else null

    override fun getCurrentMediaType(): MediaType? = viewModel.state.value.currentFile?.type

    override fun onPdfNextPage() {
        if (activity._pdfViewerManager != null) activity.pdfViewerManager.showNextPage()
    }

    override fun onPdfPreviousPage() {
        if (activity._pdfViewerManager != null) activity.pdfViewerManager.showPreviousPage()
    }

    override fun onPdfHome() {
        if (activity._pdfViewerManager != null) activity.pdfViewerManager.showPdfPage(0)
    }

    override fun onPdfEnd() {
        if (activity._pdfViewerManager != null && activity.pdfViewerManager.pdfPageCount > 0) {
            activity.pdfViewerManager.showPdfPage(activity.pdfViewerManager.pdfPageCount - 1)
        }
    }

    override fun onEpubNextPage() {
        if (activity._epubViewerManager != null) activity.epubViewerManager.showNextChapter()
    }

    override fun onEpubPreviousPage() {
        if (activity._epubViewerManager != null) activity.epubViewerManager.showPreviousChapter()
    }

    override fun onEpubHome() {
        if (activity._epubViewerManager != null) activity.epubViewerManager.scrollToHome()
    }

    override fun onEpubEnd() {
        if (activity._epubViewerManager != null) activity.epubViewerManager.scrollToEnd()
    }

    override fun onTextScrollDown() {
        if (activity._textViewerManager != null) activity.textViewerManager.scrollDown()
    }

    override fun onTextScrollUp() {
        if (activity._textViewerManager != null) activity.textViewerManager.scrollUp()
    }

    override fun onTextHome() {
        if (activity._textViewerManager != null) activity.textViewerManager.scrollToTop()
    }

    override fun onTextEnd() {
        if (activity._textViewerManager != null) activity.textViewerManager.scrollToBottom()
    }

    override fun onSeekForward(seconds: Int) {
        val player = getActivePlayer() ?: return
        player.seekTo(
            (player.currentPosition + seconds * 1000L).coerceAtMost(
                player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            )
        )
    }

    override fun onSeekBackward(seconds: Int) {
        val player = getActivePlayer() ?: return
        player.seekTo((player.currentPosition - seconds * 1000L).coerceAtLeast(0))
    }

    override fun onEpubScrollDelta(verticalScroll: Float) {
        if (activity._epubViewerManager != null) {
            if (verticalScroll > 0) {
                activity.epubViewerManager.showPreviousChapter()
            } else {
                activity.epubViewerManager.showNextChapter()
            }
        }
    }

    override fun onNavigationScroll(verticalScroll: Float) {
        activity.navigationManager.handleMouseWheelScroll(verticalScroll)
    }

    override fun onToggleMute() {
        if (activity._videoPlayerManager != null) {
            val player = activity.videoPlayerManager.getPlayer() ?: return
            player.volume = if (player.volume > 0f) 0f else 1f
        }
    }

    // Fullscreen toggle reuses the command-panel toggle: hiding the panel enters fullscreen.
    override fun onToggleFullscreen() {
        viewModel.toggleCommandPanel()
    }

    // delta is ±1 step; PlayerActivity.adjustVolume expects ±0.1 per step.
    override fun onChangeVolume(delta: Int) {
        activity.adjustVolume(delta * 0.1f)
    }

    override fun onShowHelp() {
        PlayerHelpEntryManager.open(activity)
    }

    override fun onDocumentSearch() {
        // The handler already gates this to document surfaces; runCatching avoids lateinit crashes
        // on hosts where the search controls manager has not been prepared yet.
        runCatching {
            activity.searchControlsManager.showSearchPanel()
        }
    }

    // Ctrl+S saves the current video frame as an image.
    override fun onSaveCurrent() {
        activity.saveVideoFrameManager.saveCurrentFrame()
    }

    // F9 / Ctrl+M in player shows the command/context panel.
    override fun onShowContextMenu() {
        viewModel.toggleCommandPanel()
    }

    override fun onNextFile() = activity.navigationManager.navigateNextFromControl()
    override fun onPreviousFile() = activity.navigationManager.navigatePreviousFromControl()
    override fun onToggleFavourite() = viewModel.toggleFavorite()
    override fun onUndoOperation() = viewModel.undoLastOperation()
    override fun onToggleBlackScreen() = activity.toggleBlackScreenOverlay()

    // S0162: keyboard shortcut for rotation toggle - same action as the command panel button
    override fun onToggleRotationSensor() = viewModel.toggleRotationSensor()

    // ── VIEWER-KEYBOARD actions (INPUT-PARITY 0.4) ──────────────────────────

    // A step that cannot move (already at the edge) is still consumed so the key does not fall to focus traversal.
    override fun onStepFiles(offset: Int): Boolean {
        val state = viewModel.state.value
        ViewerKeyStepMath.offsetTarget(state.currentIndex, state.files.size, offset)
            ?.let { activity.navigationManager.navigateToIndexFromControl(it) }
        return true
    }

    override fun onEdgeFile(last: Boolean): Boolean {
        val state = viewModel.state.value
        ViewerKeyStepMath.edgeTarget(state.currentIndex, state.files.size, last)
            ?.let { activity.navigationManager.navigateToIndexFromControl(it) }
        return true
    }

    override fun onRandomFile(): Boolean {
        activity.navigationManager.navigateRandomFromButton()
        return true
    }

    override fun onRotateContent(clockwise: Boolean): Boolean {
        if (clockwise) viewModel.rotateSession90() else viewModel.rotateSessionCounter90()
        activity.applyContentRotation(viewModel.state.value.sessionRotationAngle)
        return true
    }

    override fun onChangeSpeed(faster: Boolean): Boolean {
        val handle = activity.videoPlayerHandle
        handle.setPlaybackSpeed(ViewerKeyStepMath.nextSpeed(handle.getPlaybackSpeed(), faster))
        return true
    }

    override fun onCycleAudioTrack(): Boolean {
        val manager = activity._videoPlayerManager ?: return false
        val tracks = manager.getAvailableAudioTracks()
        val next = ViewerKeyStepMath.nextTrackIndex(tracks.indexOfFirst { it.isSelected }, tracks.size)
            ?.let { tracks[it] }
        next?.let { manager.selectAudioTrack(it.groupIndex, it.trackIndex) }
        return next != null
    }

    // S starts the slideshow and, while it runs, halves the interval down to the MEDIA-VIEWER 1 s floor;
    // I switches to shuffled order first and starts it.
    override fun onStartSlideshow(random: Boolean): Boolean {
        val state = viewModel.state.value
        if (random) viewModel.setPlaybackOrderMode(PlaybackOrderMode.SHUFFLE)
        if (!state.isSlideShowActive) {
            viewModel.setSlideShowActive(true)
            activity.showSlideshowEnabledMessage()
        } else if (!random) {
            viewModel.setSlideShowInterval(ViewerKeyStepMath.fasterSlideshowIntervalMs(state.slideShowInterval))
        }
        activity.dialogAndUiStateManager.updateSlideShowButton()
        activity.navigationManager.updateSlideshowState()
        return true
    }
}
