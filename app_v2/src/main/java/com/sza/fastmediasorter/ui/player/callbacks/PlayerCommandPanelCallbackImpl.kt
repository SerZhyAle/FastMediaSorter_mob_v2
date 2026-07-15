package com.sza.fastmediasorter.ui.player.callbacks

import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.share.ShareableContent
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.CommandPanelController
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.StereoDetector
import com.sza.fastmediasorter.ui.share.SendToMenuManager
import timber.log.Timber
import java.io.File

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

    override fun onSendToClicked() {
        val content = buildShareableContent() ?: return
        val settings = activity.currentSettings ?: return
        // S0681: the pinned «Select resource..» row reuses the player's existing copy dialog, which
        // already excludes the current resource and honours the goToNextAfterCopy setting.
        activity.sendToMenuManager.show(
            activity, content, settings,
            onPickResource = { activity.dialogAndUiStateManager.showCopyDialog() },
        )
    }

    // S0459 ADR-2: overflow PopupMenu renders «Send to..» as a native nested submenu.
    override fun onSendToOverflowSubMenuRequested(menu: android.view.Menu, order: Int) {
        val content = buildShareableContent() ?: return
        val settings = activity.currentSettings ?: return
        activity.sendToMenuManager.buildOverflowSubMenu(
            menu, order, content, settings, activity,
            onPickResource = { activity.dialogAndUiStateManager.showCopyDialog() },
        )
    }

    // Builds the unified-menu payload for the current file. Local file: a FileProvider Uri is ready
    // now. Remote file (S0493): leave uris empty and carry the source path so the dispatch layer
    // downloads a local copy on demand. Null only when no file is open, or a local file whose
    // FileProvider Uri could not be built (kept hidden, as before).
    private fun buildShareableContent(): ShareableContent? {
        val file = viewModel.state.value.currentFile ?: return null
        // S0631: a live video stream is shared as a link, not a file. Carry the stream URL as plain
        // text so text-capable receivers send it via ACTION_SEND/EXTRA_TEXT. sourcePath = null keeps
        // requiresMaterialization false, so the dispatch layer never tries to download the live stream.
        if (viewModel.state.value.isLiveVideoStream) {
            return ShareableContent(
                uris = emptyList(),
                mime = "text/plain",
                mediaType = file.type,
                text = file.path,
                displayName = file.name,
                mediaFile = null,
                sourcePath = null,
            )
        }
        val localUri = buildShareUri(file.path)
        val isRemote = localUri == null && (file.path.contains("://") || file.path.startsWith("cloud:/"))
        if (localUri == null && !isRemote) return null
        return ShareableContent(
            uris = listOfNotNull(localUri),
            mime = mimeTypeForFile(file.name, file.type),
            mediaType = file.type,
            displayName = file.name,
            mediaFile = file,
            sourcePath = if (isRemote) file.path else null,
        )
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
            // S0301 Phase 05: the embedded Office viewer is strictly read-only - no edit affordance
            // is offered even when the underlying file is writable.
            MediaType.OFFICE_DOCUMENT -> {}
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

        // PDF page-fullscreen has its own zoomable overlay; the long-press gesture now opens
        // text selection, so the overflow menu_fullscreen is the entry point for PDF fullscreen.
        if (viewModel.state.value.currentFile?.type == MediaType.PDF && activity._pdfViewerManager != null) {
            activity.pdfViewerManager.requestPdfFullscreen()
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

    override fun onCopyPanelExpandedChanged(expanded: Boolean) {
        activity.dialogAndUiStateManager.setCopyPanelExpanded(expanded)
    }

    override fun onMovePanelExpandedChanged(expanded: Boolean) {
        activity.dialogAndUiStateManager.setMovePanelExpanded(expanded)
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
            MediaType.OFFICE_DOCUMENT -> translateOfficeDocumentText()
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
            MediaType.OFFICE_DOCUMENT -> showOfficeExtractedText()
            MediaType.IMAGE, MediaType.GIF -> activity.extractTextFromCurrentImage()
            else -> {}
        }
    }

    private fun showOfficeExtractedText(): Boolean {
        val text = activity.officeDocumentViewerManager.extractPlainText()?.takeIf { it.isNotBlank() }
        if (text == null) {
            Toast.makeText(activity, R.string.ocr_no_text_found, Toast.LENGTH_SHORT).show()
            return false
        }
        activity.textViewerManager.displayOcrText(text)
        return true
    }

    private fun translateOfficeDocumentText() {
        if (showOfficeExtractedText()) {
            activity.textViewerManager.forceTranslate()
        }
    }

    // S0459: GOOGLE_LENS_PDF stays a dedicated command - it renders the current PDF page to a bitmap
    // and hands that to Lens, which the unified image-Lens receiver (image Uri only) cannot do. The
    // image/GIF Lens branch was removed; that path is now a unified-menu receiver.
    override fun onGoogleLensClicked() {
        val currentFile = viewModel.state.value.currentFile ?: return
        if (currentFile.type == MediaType.PDF && activity._pdfViewerManager != null) {
            activity.pdfViewerManager.shareCurrentPageToGoogleLens()
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

    override fun onOpenInVrClicked() {
        activity.playerVrLaunchManager?.launchFromOverflow()
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
        activity.syncDrawOverlayCurrentFile()
        activity.imageDrawOverlayManager.enterDrawMode()
    }

    override fun onRotationToggleClicked() {
        viewModel.toggleRotationSensor()
    }

    override fun onRotateContent90Clicked() {
        viewModel.rotateSession90()
        val newAngle = viewModel.state.value.sessionRotationAngle
        Timber.d("S0995: internal rotate90 tap -> $newAngle")
        activity.applyContentRotation(newAngle)
    }

    // Builds a content:// URI for a local file via FileProvider; returns null for network paths
    // where async download would be needed (network sharing remains via the legacy share path).
    private fun buildShareUri(path: String): Uri? {
        if (path.contains("://") || path.startsWith("cloud:/")) return null // remote path - materialized on dispatch
        return try {
            FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", File(path))
        } catch (e: Exception) {
            Timber.w("SendTo: FileProvider URI failed for %s: %s", path, e.message)
            null
        }
    }

    // Derives MIME type from file extension and MediaType for ShareableContent.mime.
    private fun mimeTypeForFile(name: String, type: MediaType): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (type) {
            MediaType.IMAGE -> when (ext) {
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "bmp" -> "image/bmp"
                else -> "image/*"
            }
            MediaType.GIF -> "image/gif"
            MediaType.VIDEO -> when (ext) {
                "mp4" -> "video/mp4"
                "webm" -> "video/webm"
                "mkv" -> "video/x-matroska"
                else -> "video/*"
            }
            MediaType.AUDIO -> when (ext) {
                "mp3" -> "audio/mpeg"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                "flac" -> "audio/flac"
                else -> "audio/*"
            }
            MediaType.PDF -> "application/pdf"
            MediaType.TEXT -> "text/plain"
            else -> "*/*"
        }
    }
}
