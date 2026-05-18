package com.sza.fastmediasorter.ui.player.callbacks

import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.CommandPanelController
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.StereoDetector
import timber.log.Timber

/**
 * Implementation of CommandPanelController.CommandPanelCallback extracted from PlayerActivity.
 * Handles all command panel button actions (back, nav, rename, delete, share, edit, etc.).
 */
class PlayerCommandPanelCallbackImpl(
    private val activity: PlayerActivity,
    private val viewModel: PlayerViewModel
) : CommandPanelController.CommandPanelCallback {

    // S0238: user-initiated stereo detection on VR-toolbar tap (image / gif only).
    // Mirrors the construction pattern used by PlayerMediaLoaderManager.
    private val stereoDetector = StereoDetector()

    override fun onBackClicked() {
        activity.exitPlayerWithAudioCheck()
    }

    override fun onPreviousClicked() {
        activity.navigationManager.navigatePreviousFromButton()
    }

    override fun onRandomClicked() {
        activity.navigationManager.navigateRandomFromButton()
    }

    override fun onNextClicked() {
        activity.navigationManager.navigateNextFromButton()
    }

    override fun onRenameClicked() {
        activity.dialogAndUiStateManager.showRenameDialog()
    }

    override fun onDeleteClicked() {
        activity.fileOperationsHandler.deleteCurrentFile()
    }

    override fun onShareClicked() {
        activity.shareCurrentFile()
    }

    override fun onEditClicked() {
        val currentFile = viewModel.state.value.currentFile
        when (currentFile?.type) {
            // Video now uses the same Control dialog from the bottom bar to avoid duplicate UX paths.
            MediaType.VIDEO -> activity.dialogHelper.showPlaybackControlDialog()
            MediaType.AUDIO -> activity.dialogHelper.showPlaybackControlDialog()
            MediaType.IMAGE -> {
                if (activity.isAnimatedImagePath(currentFile.path)) activity.dialogAndUiStateManager.showGifEditDialog() else activity.dialogAndUiStateManager.showImageEditDialog()
            }
            MediaType.GIF -> activity.dialogAndUiStateManager.showGifEditDialog()
            MediaType.PDF -> activity.dialogAndUiStateManager.showPdfEditDialog()
            else -> {}
        }
    }

    override fun onUndoClicked() {
        viewModel.undoLastOperation()
    }

    override fun onFullscreenClicked() {
        if (activity.tryHandleFullscreenCommandOverride()) {
            return
        }

        // Transition FROM command panel mode TO fullscreen mode
        // (no return logic needed - fullscreen has no buttons!)
        activity.isExplicitFullscreenMode = true
        if (viewModel.state.value.showCommandPanel) {
            viewModel.toggleCommandPanel()
        }
        // CRITICAL: Pass false directly, not viewModel.state.value.showCommandPanel
        // because toggleCommandPanel() is async and state hasn't updated yet
        activity.updateSystemBarsForPlayer(false)
    }

    override fun onSlideshowClicked() {
        val wasActive = viewModel.state.value.isSlideShowActive

        // Pre-check: if about to enter audio slideshow photo mode,
        // set background music flag BEFORE toggle to prevent brief playback
        val currentFile = viewModel.state.value.currentFile
        val isAudioWithPhotos = currentFile?.type == MediaType.AUDIO &&
            viewModel.state.value.enablePhotosDuringAudio &&
            viewModel.state.value.audioBackgroundPhotosResourceId != null
        if (!wasActive && isAudioWithPhotos) {
            activity.backgroundMusicManager.isAudioSlideshowPhotoMode = true
        }

        activity.navigationManager.toggleSlideshow()

        val isNowActive = viewModel.state.value.isSlideShowActive

        if (!wasActive && isNowActive) {
            activity.showSlideshowEnabledMessage()
            if (isAudioWithPhotos) {
                activity.audioSlideshowPhotoModeManager.enter()
            }
        } else if (wasActive && !isNowActive) {
            if (activity.audioSlideshowPhotoModeManager.isActive) {
                activity.audioSlideshowPhotoModeManager.exit()
            }
        }

        activity.dialogAndUiStateManager.updateSlideShowButton()
        activity.navigationManager.updateSlideshowState()
        activity.updateSystemBarsForPlayer(viewModel.state.value.showCommandPanel)
    }

    override fun onCopyPanelHeaderClicked() {
        activity.dialogAndUiStateManager.toggleCopyPanel()
    }

    override fun onMovePanelHeaderClicked() {
        activity.dialogAndUiStateManager.toggleMovePanel()
    }

    override fun onInfoClicked() {
        activity.showFileInfo()
    }

    override fun onLyricsClicked() {
        activity.searchAndShowLyrics()
    }

    override fun onSearchYoutubeMusicClicked() {
        activity.searchInYoutubeMusic()
    }

    override fun onCastClicked() {
        activity.castCurrentMedia()
    }

    override fun onFavoriteClicked() {
        viewModel.toggleFavorite()
    }

    override fun onSearchClicked() {
        activity.searchControlsManager.showSearchPanel()
    }

    override fun onTranslateClicked() {
        val currentFile = viewModel.state.value.currentFile ?: return
        when (currentFile.type) {
            MediaType.PDF -> if (activity._pdfViewerManager != null) activity.pdfViewerManager.toggleTranslation()
            MediaType.TEXT -> activity.activityBinding.btnTranslateTextCmd.performClick()
            MediaType.EPUB -> {
                if (activity._epubViewerManager != null) activity.epubViewerManager.toggleTranslation()
            }
            MediaType.IMAGE, MediaType.GIF -> activity.activityBinding.btnTranslateImageCmd.performClick()
            else -> {}
        }
    }

    override fun onOcrClicked() {
        val currentFile = viewModel.state.value.currentFile ?: return
        when (currentFile.type) {
            MediaType.PDF -> if (activity._pdfViewerManager != null) activity.pdfViewerManager.extractTextFromCurrentPage()
            MediaType.EPUB -> {
                if (activity._epubViewerManager != null) activity.epubViewerManager.extractTextFromCurrentChapter()
            }
            MediaType.IMAGE, MediaType.GIF -> activity.extractTextFromCurrentImage()
            else -> {}
        }
    }

    override fun onGoogleLensClicked() {
        val currentFile = viewModel.state.value.currentFile ?: return
        when (currentFile.type) {
            MediaType.PDF -> if (activity._pdfViewerManager != null) activity.pdfViewerManager.shareCurrentPageToGoogleLens()
            MediaType.IMAGE, MediaType.GIF -> activity.shareCurrentFileToGoogleLens()
            else -> {}
        }
    }

    override fun onCopyTextClicked() {
        val currentFile = viewModel.state.value.currentFile
        if (currentFile?.type == MediaType.PDF) {
            activity.pdfViewerManager.copyPageTextToClipboard()
        } else {
            activity.activityBinding.btnCopyTextCmd.performClick()
        }
    }

    override fun onEditTextClicked() {
        activity.activityBinding.btnEditTextCmd.performClick()
    }

    override fun onOcrSettingsClicked() {
        // Long-press on OCR shows translation settings (same as translate button)
        Timber.d("OCR settings requested - showing translation settings dialog")
        activity.translationButtonManager.showTranslationSettingsDialog()
    }

    override fun onTranslationSettingsClicked() {
        activity.translationButtonManager.showTranslationSettingsDialog()
    }

    override fun onSleepTimerClicked() {
        activity.dialogHelper.showSleepTimerDialog()
    }

    override fun onReopenEncodingClicked() {
        activity.dialogHelper.showEncodingDialog()
    }

    override fun onToggleMarkdownClicked() {
        activity.textViewerManager.toggleMarkdownRendering()
    }

    override fun onReaderSettingsClicked() {
        activity.dialogHelper.showReaderSettingsDialog()
    }

    override fun onReadAloudClicked() {
        val currentFile = viewModel.state.value.currentFile ?: return
        when (currentFile.type) {
            MediaType.TEXT -> activity.textViewerManager.toggleReadAloud()
            MediaType.PDF  -> if (activity._pdfViewerManager != null) activity.pdfViewerManager.toggleReadAloud()
            MediaType.EPUB -> if (activity._epubViewerManager != null) activity.epubViewerManager.toggleReadAloud()
            else -> {}
        }
    }

    override fun onPdfScrollModeClicked() {
        activity.pdfViewerManager.toggleScrollMode()
    }

    override fun onPdfColorModeClicked() {
        activity.pdfViewerManager.toggleColorMode()
    }

    override fun onPdfThumbnailsClicked() {
        activity.pdfViewerManager.showThumbnailNavigation()
    }

    override fun onEpubReaderSettingsClicked() {
        activity.epubViewerManager.showReaderSettingsDialog()
    }

    override fun onEpubSearchAllClicked() {
        activity.epubViewerManager.showCrossChapterSearch()
    }

    override fun onPrintClicked() {
        val currentFile = viewModel.state.value.currentFile ?: return
        activity.printManager.printCurrentFile(currentFile)
    }

    override fun onSaveFrameClicked() {
        if (activity.tryHandleSaveFrameCommandOverride()) {
            return
        }

        // PlayerActivity decides the correct capture backend (TextureView on phone/tablet).
        activity.saveCurrentFrame()
    }

    override fun onBlackScreenClicked() {
        activity.blackScreenOverlayManager.show()
    }

    override fun onOpenInSeparateWindowClicked() {
        activity.tearOffPlayer()
    }

    override fun onCropClicked() {
        activity.enterImageCropMode(com.sza.fastmediasorter.ui.player.helpers.ImageCropManager.CropMode.CROP)
    }

    override fun onCropToFileClicked() {
        activity.enterImageCropMode(com.sza.fastmediasorter.ui.player.helpers.ImageCropManager.CropMode.CROP_TO_FILE)
    }

    override fun onCompressCopyClicked() {
        activity.startCompressedCopy()
    }

    override fun onDrawOverlayClicked() {
        activity.imageDrawOverlayManager.enterDrawMode()
    }

    override fun onRotationToggleClicked() {
        viewModel.toggleRotationSensor()
    }
}
