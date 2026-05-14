package com.sza.fastmediasorter.ui.player.helpers

import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.ui.player.PlayerActivity
import com.sza.fastmediasorter.ui.player.PlayerViewModel
import com.sza.fastmediasorter.ui.player.SlideshowController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.sza.fastmediasorter.utils.UserActionLogger
import com.sza.fastmediasorter.ui.player.SlideshowSettingsDialogFragment

/**
 * Manages setup of all controls in PlayerActivity.
 * 
 * Consolidates button click listeners and delegates to appropriate managers:
 * - Navigation controls (previous/next)
 * - Playback controls (play/pause, volume)
 * - Slideshow controls
 * - Document controls (PDF, EPUB navigation)
 * - Translation controls
 * - Text viewer controls
 * - Search controls
 * 
 * Extracted from PlayerActivity to reduce size and improve organization.
 */
class PlayerControlsSetupManager(
    private val activity: PlayerActivity,
    private val binding: ActivityPlayerUnifiedBinding,
    private val viewModel: PlayerViewModel,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val slideshowController: SlideshowController,
    private val pdfViewerManagerProvider: () -> PdfViewerManager,
    private val epubViewerManagerProvider: () -> EpubViewerManager,
    private val textViewerManagerProvider: () -> TextViewerManager,
    private val translationManager: TranslationManager,
    private val translationButtonManager: TranslationButtonManager,
    private val exoPlayerControlsManager: ExoPlayerControlsManager,
    private val searchControlsManager: SearchControlsManager,
    private val blackScreenOverlayManager: BlackScreenOverlayManager,
    private val bigButtonsMode: Boolean = false
) {
    private val safeViews = PlayerBindingSafeViews(binding)
    private val bigButtonsModeManager = PlayerBigButtonsModeManager(activity)
    
    /**
     * Setup all controls in the activity.
     * Called once from onCreate.
     */
    fun setupAllControls() {
        setupNavigationControls()
        setupPlaybackControls()
        setupSlideshowControls()
        setupCommandButtons()
        setupPdfControls()
        setupEpubControls()
        setupTranslationControls()
        setupLyricsViewerControls()
        setupTextViewerControls()
        setupSearchControls()
        setupDocumentFullscreenExitButton()
        setupExoPlayerControls()
        setupTouchZonesHelpButton()
        applyBigButtonsModeToPlaybackRow()
    }

    /**
     * S0158: Expand ExoPlayer controls button row to full-width 2× height when big buttons mode
     * is active. Targets [R.id.exoPlayerButtonRow] inside the PlayerView — the visible playback
     * controls in both portrait and landscape non-fullscreen mode.
     */
    fun applyBigButtonsModeToPlaybackRow() {
        if (!bigButtonsMode) return
        val exoRow = binding.playerView.findViewById<LinearLayout>(R.id.exoPlayerButtonRow)
            ?: return
        exoRow.post {
            bigButtonsModeManager.applyToBottomPlaybackRow(exoRow, bigButtonsMode = true)
        }
    }

    /**
     * Setup navigation controls (Previous/Next buttons).
     */
    private fun setupNavigationControls() {
        binding.btnPrevious.setOnClickListener {
            UserActionLogger.logButtonClick("Previous", "PlayerActivity")
            activity.navigationManager.navigatePreviousFromButton()
        }

        binding.btnNext.setOnClickListener {
            UserActionLogger.logButtonClick("Next", "PlayerActivity")
            activity.navigationManager.navigateNextFromButton()
        }
    }
    
    /**
     * Setup playback controls (Play/Pause, Volume).
     */
    private fun setupPlaybackControls() {
        binding.btnPlayPause.setOnClickListener {
            UserActionLogger.logButtonClick("PlayPause", "PlayerActivity")

            if (activity.isCurrentAnimatedContent()) {
                val paused = activity.toggleAnimatedPlayback()
                if (paused != null) {
                    viewModel.setPaused(paused)
                    if (paused) {
                        slideshowController.pauseSlideshow()
                        activity.clearUiOverlayForAnimatedPause()
                    } else {
                        slideshowController.resumeSlideshow()
                    }
                    activity.updatePlayPauseButton()
                    activity.scheduleHideControls()
                    return@setOnClickListener
                }
            }

            viewModel.togglePause()
            
            // Update slideshow controller
            if (viewModel.state.value.isPaused) {
                slideshowController.pauseSlideshow()
            } else {
                slideshowController.resumeSlideshow()
            }
            
            activity.updatePlayPauseButton()
            activity.scheduleHideControls()
        }

        // Volume controls for audio files
        binding.btnVolumeDown.setOnClickListener {
            UserActionLogger.logButtonClick("VolumeDown", "PlayerActivity")
            activity.adjustVolume(-0.2f)
            activity.scheduleHideControls()
        }

        binding.btnVolumeUp.setOnClickListener {
            UserActionLogger.logButtonClick("VolumeUp", "PlayerActivity")
            activity.adjustVolume(0.2f)
            activity.scheduleHideControls()
        }
    }
    
    /**
     * Setup slideshow controls.
     */
    private fun setupSlideshowControls() {
        binding.btnSlideShow.setOnClickListener {
            UserActionLogger.logButtonClick("SlideShow", "PlayerActivity")
            val wasActive = viewModel.state.value.isSlideShowActive
            viewModel.toggleSlideShow()
            
            // Show popup when enabling slideshow
            if (!wasActive && viewModel.state.value.isSlideShowActive) {
                val intervalSeconds = (viewModel.state.value.slideShowInterval / 1000).toInt()
                slideshowController.startSlideshow(intervalSeconds)
                activity.showSlideshowEnabledMessage()
            } else if (wasActive && !viewModel.state.value.isSlideShowActive) {
                slideshowController.stopSlideshow()
            }
            
            activity.dialogAndUiStateManager.updateSlideShowButton()
            activity.scheduleHideControls()
        }
        
        binding.btnSlideShow.setOnLongClickListener {
            UserActionLogger.logButtonClick("SlideShowLong", "PlayerActivity")
            val dialog = SlideshowSettingsDialogFragment()
            dialog.show(activity.supportFragmentManager, "SlideshowSettingsDialogFragment")
            activity.scheduleHideControls()
            true
        }
    }
    
    /**
     * Setup command panel buttons (Info, Lyrics, Favorite, Delete).
     */
    private fun setupCommandButtons() {
        binding.btnInfoCmd.setOnClickListener {
            UserActionLogger.logButtonClick("InfoCmd", "PlayerActivity")
            activity.showFileInfo()
            activity.scheduleHideControls()
        }

        binding.btnLyricsCmd.setOnClickListener {
            UserActionLogger.logButtonClick("LyricsCmd", "PlayerActivity")
            activity.searchAndShowLyrics()
            activity.scheduleHideControls()
        }

        binding.btnSearchYoutubeMusicCmd?.setOnClickListener {
            UserActionLogger.logButtonClick("SearchYoutubeMusicCmd", "PlayerActivity")
            activity.searchInYoutubeMusic()
            activity.scheduleHideControls()
        }

        binding.root.findViewById<android.widget.ImageButton>(com.sza.fastmediasorter.R.id.btnCastCmd)?.setOnClickListener {
            UserActionLogger.logButtonClick("CastCmd", "PlayerActivity")
            activity.castCurrentMedia()
            activity.scheduleHideControls()
        }

        binding.btnFavorite.setOnClickListener {
            UserActionLogger.logButtonClick("Favorite", "PlayerActivity")
            viewModel.toggleFavorite()
            activity.scheduleHideControls()
        }

        binding.btnDelete.setOnClickListener {
            UserActionLogger.logButtonClick("Delete", "PlayerActivity")
            activity.fileOperationsHandler.deleteCurrentFile()
            activity.scheduleHideControls()
        }

        binding.btnBlackScreenCmd?.setOnClickListener {
            UserActionLogger.logButtonClick("BlackScreenCmd", "PlayerActivity")
            blackScreenOverlayManager.show()
        }
    }
    
    /**
     * Setup PDF viewer controls (page navigation, zoom).
     */
    private fun setupPdfControls() {
        binding.btnPdfPrevPage.setOnClickListener {
            UserActionLogger.logButtonClick("PdfPrevPage", "PlayerActivity")
            pdfViewerManagerProvider().showPreviousPage()
            activity.scheduleHideControls()
        }
        
        binding.btnPdfHome.setOnClickListener {
            UserActionLogger.logButtonClick("PdfHome", "PlayerActivity")
            pdfViewerManagerProvider().showFirstPage()
            activity.scheduleHideControls()
        }
        
        binding.btnPdfNextPage.setOnClickListener {
            UserActionLogger.logButtonClick("PdfNextPage", "PlayerActivity")
            pdfViewerManagerProvider().showNextPage()
            activity.scheduleHideControls()
        }

        binding.btnPdfZoomIn.setOnClickListener {
            UserActionLogger.logButtonClick("PdfZoomIn", "PlayerActivity")
            val currentScale = binding.photoView.scale
            val maxScale = binding.photoView.maximumScale
            val newScale = (currentScale + 0.5f).coerceAtMost(maxScale)
            if (newScale > currentScale) {
                binding.photoView.setScale(newScale, true)
            }
        }
        
        binding.btnPdfZoomOut.setOnClickListener {
            UserActionLogger.logButtonClick("PdfZoomOut", "PlayerActivity")
            val currentScale = binding.photoView.scale
            val minScale = binding.photoView.minimumScale
            val newScale = (currentScale - 0.5f).coerceAtLeast(minScale)
            if (newScale < currentScale) {
                binding.photoView.setScale(newScale, true)
            }
        }
        
        // PDF Translation Button (in command panel)
        binding.btnTranslatePdfCmd.setOnClickListener {
            UserActionLogger.logButtonClick("TranslatePdfCmd", "PlayerActivity")
            pdfViewerManagerProvider().toggleTranslation()
            activity.scheduleHideControls()
        }
        binding.btnTranslatePdfCmd.setOnLongClickListener {
            Timber.d("PlayerActivity: btnTranslatePdfCmd long-clicked")
            translationButtonManager.showTranslationSettingsDialog()
            true
        }
    }
    
    /**
     * Setup EPUB viewer controls (chapter navigation, TOC, font size).
     */
    private fun setupEpubControls() {
        binding.btnEpubPrevChapter.setOnClickListener {
            UserActionLogger.logButtonClick("EpubPrevChapter", "PlayerActivity")
            epubViewerManagerProvider().showPreviousChapter()
            activity.scheduleHideControls()
        }
        
        binding.btnEpubHome.setOnClickListener {
            UserActionLogger.logButtonClick("EpubHome", "PlayerActivity")
            epubViewerManagerProvider().showFirstChapter()
            activity.scheduleHideControls()
        }
        
        binding.btnEpubNextChapter.setOnClickListener {
            UserActionLogger.logButtonClick("EpubNextChapter", "PlayerActivity")
            epubViewerManagerProvider().showNextChapter()
            activity.scheduleHideControls()
        }
        
        binding.btnEpubToc.setOnClickListener {
            UserActionLogger.logButtonClick("EpubToc", "PlayerActivity")
            epubViewerManagerProvider().showTableOfContents()
            activity.scheduleHideControls()
        }
        
        binding.btnEpubFontSizeDecrease.setOnClickListener {
            UserActionLogger.logButtonClick("EpubFontSizeDecrease", "PlayerActivity")
            epubViewerManagerProvider().decreaseFontSize()
            activity.scheduleHideControls()
        }
        
        binding.btnEpubFontSizeIncrease.setOnClickListener {
            UserActionLogger.logButtonClick("EpubFontSizeIncrease", "PlayerActivity")
            epubViewerManagerProvider().increaseFontSize()
            activity.scheduleHideControls()
        }
        
        // EPUB Exit Fullscreen Button
        binding.btnExitEpubFullscreen.setOnClickListener {
            UserActionLogger.logButtonClick("ExitEpubFullscreen", "PlayerActivity")
            epubViewerManagerProvider().exitFullscreenMode()
        }
    }
    
    /**
     * Setup translation controls (image, PDF, EPUB, text translation buttons).
     */
    private fun setupTranslationControls() {
        // Image Translation Button (deprecated - kept for compatibility)
        safeViews.btnTranslateImage.setOnClickListener {
            UserActionLogger.logButtonClick("TranslateImage", "PlayerActivity (deprecated)")
            activity.translateCurrentImage()
            activity.scheduleHideControls()
        }
        safeViews.btnTranslateImage.setOnLongClickListener {
            Timber.d("PlayerActivity: btnTranslateImage long-clicked (deprecated)")
            translationButtonManager.showTranslationSettingsDialog()
            true
        }
        
        // Image Translation Button (command panel)
        binding.btnTranslateImageCmd.setOnClickListener {
            UserActionLogger.logButtonClick("TranslateImageCmd", "PlayerActivity")
            activity.translateCurrentImage()
            activity.scheduleHideControls()
        }
        binding.btnTranslateImageCmd.setOnLongClickListener {
            Timber.d("PlayerActivity: btnTranslateImageCmd long-clicked")
            translationButtonManager.showTranslationSettingsDialog()
            true
        }
        
        // Text Settings Buttons (all types) - show translation/OCR settings dialog
        binding.btnTextSettingsCmd.setOnClickListener {
            UserActionLogger.logButtonClick("TextSettingsCmd", "PlayerActivity")
            translationButtonManager.showTranslationSettingsDialog()
        }
        binding.btnPdfTextSettingsCmd.setOnClickListener {
            UserActionLogger.logButtonClick("PdfTextSettingsCmd", "PlayerActivity")
            translationButtonManager.showTranslationSettingsDialog()
        }
        // Thumbnail navigation button (landscape cmd panel) — same action as overflow menu_pdf_thumbnails
        binding.btnPdfThumbnailsCmd?.setOnClickListener {
            UserActionLogger.logButtonClick("PdfThumbnailsCmd", "PlayerActivity")
            if (activity._pdfViewerManager != null) activity.pdfViewerManager.showThumbnailNavigation()
        }
        binding.btnEpubTextSettingsCmd.setOnClickListener {
            UserActionLogger.logButtonClick("EpubTextSettingsCmd", "PlayerActivity")
            translationButtonManager.showTranslationSettingsDialog()
        }
        binding.btnImageTextSettingsCmd.setOnClickListener {
            UserActionLogger.logButtonClick("ImageTextSettingsCmd", "PlayerActivity")
            translationButtonManager.showTranslationSettingsDialog()
        }
        
        // Translation Font Size Controls
        binding.btnTranslationFontDecrease?.setOnClickListener {
            UserActionLogger.logButtonClick("TranslationFontDecrease", "PlayerActivity")
            binding.translationLensOverlay.decreaseFontSize()
            activity.scheduleHideControls()
        }
        
        binding.btnTranslationFontIncrease?.setOnClickListener {
            UserActionLogger.logButtonClick("TranslationFontIncrease", "PlayerActivity")
            binding.translationLensOverlay.increaseFontSize()
            activity.scheduleHideControls()
        }
    }
    
    /**
     * Setup lyrics viewer controls.
     */
    private fun setupLyricsViewerControls() {
        safeViews.btnCloseLyricsViewer.setOnClickListener {
            UserActionLogger.logButtonClick("CloseLyricsViewer", "PlayerActivity")
            activity.hideLyricsViewer()
            activity.scheduleHideControls()
        }

        safeViews.btnTranslateLyrics.setOnClickListener {
            UserActionLogger.logButtonClick("TranslateLyrics", "PlayerActivity")
            val currentText = safeViews.tvLyricsContent.text.toString()
            if (currentText.isBlank()) return@setOnClickListener

            // Show loading state
            Toast.makeText(activity, R.string.translation_started, Toast.LENGTH_SHORT).show()

            // Execute translation
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Translate to default target language (user's preferred language or Russian)
                    val targetLang = translationManager.getTargetLanguageCode()
                        ?: com.google.mlkit.nl.translate.TranslateLanguage.RUSSIAN

                    val translatedText = translationManager.translate(currentText, targetLang = targetLang)

                    withContext(Dispatchers.Main) {
                        if (translatedText != null) {
                            safeViews.tvLyricsContent.text = translatedText
                        } else {
                            Toast.makeText(activity, R.string.translation_error, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Lyrics translation failed")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(activity, R.string.translation_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // Apply WindowInsets to lyricsViewerContainer so close/translate buttons don't hide
        // behind the status bar (top) or navigation bar (sides in landscape).
        // Mirrors the same insets handling used for topCommandPanel and bottomPanelsContainer.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(safeViews.lyricsViewerContainer) { view, insets ->
            val statusBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            val navBar = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.setPadding(navBar.left, statusBar.top, navBar.right, navBar.bottom)
            Timber.d("LyricsContainer: Applied insets top=${statusBar.top} left=${navBar.left} right=${navBar.right} bottom=${navBar.bottom}")
            insets
        }
        safeViews.lyricsViewerContainer.post {
            safeViews.lyricsViewerContainer.requestApplyInsets()
        }
    }
    
    /**
     * Setup text viewer controls.
     * Delegates to TextViewerManager.
     */
    private fun setupTextViewerControls() {
        textViewerManagerProvider().setupControls()
    }
    
    /**
     * Setup search controls.
     * Delegates to SearchControlsManager.
     */
    private fun setupSearchControls() {
        searchControlsManager.setupSearchControls()
    }
    
    /**
     * Setup ExoPlayer custom navigation buttons.
     * Delegates to ExoPlayerControlsManager.
     */
    private fun setupExoPlayerControls() {
        exoPlayerControlsManager.setupExoPlayerNavigationButtons()
    }
    
    /**
     * Setup document fullscreen exit button.
     * This button is shown in fullscreen mode for PDF/EPUB/TXT files.
     */
    fun setupDocumentFullscreenExitButton() {
        safeViews.btnDocumentFullscreenExit.setOnClickListener {
            UserActionLogger.logButtonClick("DocumentFullscreenExit", "PlayerActivity")
            if (!viewModel.state.value.showCommandPanel) {
                viewModel.toggleCommandPanel()
            }
        }
    }
    
    /**
     * Update document fullscreen exit button visibility.
     * Shows only for PDF/EPUB/TXT in fullscreen mode.
     */
    fun updateDocumentFullscreenExitButtonVisibility() {
        val currentFile = viewModel.state.value.currentFile
        val isFullscreen = !viewModel.state.value.showCommandPanel
        val isDocument = currentFile?.type == com.sza.fastmediasorter.domain.model.MediaType.PDF ||
                        currentFile?.type == com.sza.fastmediasorter.domain.model.MediaType.EPUB ||
                        currentFile?.type == com.sza.fastmediasorter.domain.model.MediaType.TEXT
        
        safeViews.btnDocumentFullscreenExit.visibility = 
            if (isFullscreen && isDocument) android.view.View.VISIBLE else android.view.View.GONE
    }

    /**
     * Setup Touch Zones Help button (?).
     * Shown in fullscreen + touch zones enabled + IMAGE/GIF.
     * Tapping it re-displays the audio touch zones overlay (first-run hint layout).
     */
    fun setupTouchZonesHelpButton() {
        binding.btnTouchZonesHelp?.setOnClickListener {
            UserActionLogger.logButtonClick("TouchZonesHelp", "PlayerActivity")
            activity.showTouchZonesHelpOverlay()
        }
    }

    /**
     * Setup toolbar with navigation, WindowInsets for status bar / nav bar overlap avoidance,
     * and bottom panels container insets.
     * Extracted from PlayerActivity.setupToolbar().
     */
    fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            Timber.d("PlayerActivity: toolbar navigation (back) clicked")
            activity.onBackPressedDispatcher.onBackPressed()
        }

        // Apply WindowInsets to toolbar to avoid overlap with status bar
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, insets ->
            val statusBarInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
            view.setPadding(
                view.paddingLeft,
                statusBarInsets.top,
                view.paddingRight,
                view.paddingBottom
            )
            Timber.d("Toolbar: Applied status bar insets - top=${statusBarInsets.top}")
            insets
        }

        // Apply WindowInsets to topCommandPanel.
        // Use statusBars | captionBar so the panel is also pushed below the Chrome OS window
        // title bar (captionBar), which is not reported under statusBars() in windowed mode.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.topCommandPanel) { view, insets ->
            val topInsets = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.statusBars() or
                androidx.core.view.WindowInsetsCompat.Type.captionBar()
            )
            val navBarInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                navBarInsets.left,
                topInsets.top,
                navBarInsets.right,
                view.paddingBottom
            )
            Timber.d("TopCommandPanel: Applied insets - top=${topInsets.top}, navBar.left=${navBarInsets.left}, navBar.right=${navBarInsets.right}")
            insets
        }

        // Force insets application after View is attached to window
        binding.topCommandPanel.post {
            binding.topCommandPanel.requestApplyInsets()
            Timber.d("TopCommandPanel: Requested apply insets in setupToolbar() [posted]")
        }

        // Apply WindowInsets to bottomPanelsContainer
        safeViews.bottomPanelsContainer.let { container ->
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                val systemBarsInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                view.setPadding(
                    systemBarsInsets.left,
                    view.paddingTop,
                    systemBarsInsets.right,
                    systemBarsInsets.bottom
                )
                Timber.d("BottomPanelsContainer: Applied system bar insets - left=${systemBarsInsets.left}, right=${systemBarsInsets.right}, bottom=${systemBarsInsets.bottom}")
                insets
            }
        }
    }
}
