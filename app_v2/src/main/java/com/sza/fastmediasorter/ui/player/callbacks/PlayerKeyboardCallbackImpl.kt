package com.sza.fastmediasorter.ui.player.callbacks

import androidx.media3.common.Player
import com.sza.fastmediasorter.domain.model.MediaType
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
        activity.deleteCurrentFile()
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

        activity.updateSlideShowButton()
        activity.navigationManager.updateSlideshowState()
    }

    override fun onShowRenameDialog() {
        activity.showRenameDialog()
    }

    override fun onShowFileInfo() {
        activity.showFileInfo()
    }

    override fun onToggleCommandPanel() {
        viewModel.toggleCommandPanel()
    }

    override fun onToggleCopyPanel() {
        activity.toggleCopyPanel()
    }

    override fun onToggleMovePanel() {
        activity.toggleMovePanel()
    }

    override fun onShowEditDialog() {
        val currentFile = viewModel.state.value.currentFile
        when (currentFile?.type) {
            MediaType.IMAGE -> {
                if (activity.isAnimatedImagePath(currentFile.path)) activity.showGifEditDialog() else activity.showImageEditDialog()
            }
            MediaType.GIF -> activity.showGifEditDialog()
            MediaType.VIDEO, MediaType.AUDIO -> activity.playerSettingsManager.showPlayerSettingsDialog()
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
        if (activity._videoPlayerManager != null) activity.videoPlayerManager.seekForward(seconds)
    }

    override fun onSeekBackward(seconds: Int) {
        if (activity._videoPlayerManager != null) activity.videoPlayerManager.seekBackward(seconds)
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
}
