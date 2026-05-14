package com.sza.fastmediasorter.ui.player.callbacks

import androidx.media3.common.Player
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.InputSurface
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler

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

    override fun canCopyCurrent(): Boolean = true

    override fun onToggleMovePanel() {
        activity.dialogAndUiStateManager.toggleMovePanel()
    }

    override fun canMoveCurrent(): Boolean = true

    override fun onShowEditDialog() {
        val currentFile = viewModel.state.value.currentFile
        when (currentFile?.type) {
            MediaType.IMAGE -> {
                if (activity.isAnimatedImagePath(currentFile.path)) activity.dialogAndUiStateManager.showGifEditDialog() else activity.dialogAndUiStateManager.showImageEditDialog()
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
        player.seekTo((player.currentPosition + seconds * 1000L).coerceAtMost(
            player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        ))
    }

    override fun onSeekBackward(seconds: Int) {
        val player = getActivePlayer() ?: return
        player.seekTo((player.currentPosition - seconds * 1000L).coerceAtLeast(0))
    }

    override fun onEpubScrollDelta(verticalScroll: Float) {
        if (activity._epubViewerManager != null) {
            if (verticalScroll > 0) activity.epubViewerManager.showPreviousChapter()
            else activity.epubViewerManager.showNextChapter()
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
        InputHelpDialogFragment.show(activity.supportFragmentManager, InputSurface.PLAYER)
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
    // S0162: keyboard shortcut for rotation toggle — same action as the command panel button
    override fun onToggleRotationSensor() = viewModel.toggleRotationSensor()
}
