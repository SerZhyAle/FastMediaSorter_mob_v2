package com.sza.fastmediasorter.ui.player

import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager
import com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager
import com.sza.fastmediasorter.ui.player.helpers.TextViewerManager

/**
 * Factory for the lazily-initialized viewer and player managers in PlayerActivity.
 * Extracted from PlayerActivity to reduce file size (~165 lines).
 * Each create*() method corresponds to a former private factory method in PlayerActivity.
 */
internal class PlayerViewerFactory(private val activity: PlayerActivity) {

    fun createVideoPlayerManager(): VideoPlayerManager {
        return VideoPlayerManager(
            context = activity,
            lifecycle = activity.lifecycle,
            playerCallback = com.sza.fastmediasorter.ui.player.callbacks.PlayerPlaybackCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel,
                binding = activity.activityBinding,
                loadingIndicatorHandler = activity.loadingIndicatorHandler,
                showLoadingIndicatorRunnable = activity.showLoadingIndicatorRunnable,
                playerSettingsManagerProvider = { activity.playerSettingsManager },
                imageLoadingManagerProvider = { activity.imageLoadingManager },
                slideshowController = activity.slideshowController,
                sleepTimerManagerProvider = { activity.sleepTimerManager },
                audioEmptyStateControllerProvider = { activity.audioEmptyStateController }
            ),
            credentialsRepository = activity.credentialsRepository,
            smbClient = activity.smbClient,
            sftpClient = activity.sftpClient,
            ftpClient = activity.ftpClient,
            googleDriveClient = activity.googleDriveClient,
            oneDriveClient = activity.oneDriveClient,
            dropboxClient = activity.dropboxClient,
            playbackPositionRepository = activity.playbackPositionRepository,
            settingsRepository = activity.settingsRepository,
            panelStereoSingleEyeNotifier = activity.panelStereoSingleEyeNotifier
        ).also {
            it.onPositionSaved = { activity.viewModel.saveResumeState() }
        }
    }

    fun createPdfViewerManager(): PdfViewerManager {
        return PdfViewerManager(
            binding = activity.activityBinding,
            networkFileManager = activity.networkFileManager,
            settingsRepository = activity.settingsRepository,
            coroutineScope = activity.lifecycleScope,
            callback = object : PdfViewerManager.PdfViewerCallback {
                override fun showError(message: String) { activity.showError(message) }

                override fun displayOcrText(text: String) {
                    activity.textViewerManager.displayOcrText(text)
                }

                override fun displayTranslatedText(text: String) {
                    activity.textViewerManager.displayTranslatedText(text)
                }

                override fun shareFileToGoogleLens(file: java.io.File) {
                    activity.shareManager.shareFileToGoogleLens(file)
                }

                override fun isLandscapeMode(): Boolean =
                    activity.resources.configuration.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE

                override fun onEnterFullscreenMode() {
                    if (activity.currentSettings?.hideSystemUiInFullscreen != false) {
                        activity.systemBarsManager.enterFullscreenMode()
                    }
                    activity.activityBinding.toolbar.isVisible = false
                    activity.safeViews.copyToPanel.isVisible = false
                    activity.safeViews.moveToPanel.isVisible = false
                    activity.safeViews.pdfControlsLayout.isVisible = false
                    activity.safeViews.translationOverlay.isVisible = false
                    activity.activityBinding.translationLensOverlay.isVisible = false
                }

                override fun onExitFullscreenMode() {
                    activity.systemBarsManager.exitFullscreenMode()
                    activity.activityBinding.toolbar.isVisible = true
                    activity.safeViews.pdfControlsLayout.isVisible = true
                }
            },
            translationManager = activity.translationManager,
            playbackPositionRepository = activity.playbackPositionRepository
        )
    }

    fun createEpubViewerManager(): EpubViewerManager {
        return EpubViewerManager(
            binding = activity.activityBinding,
            networkFileManager = activity.networkFileManager,
            settingsRepository = activity.settingsRepository,
            coroutineScope = activity.lifecycleScope,
            callback = object : EpubViewerManager.EpubViewerCallback {
                override fun showError(message: String) { activity.showError(message) }

                override fun displayTranslatedText(text: String) {
                    activity.textViewerManager.displayTranslatedText(text)
                }

                override fun onEnterFullscreenMode() {
                    if (activity.currentSettings?.hideSystemUiInFullscreen != false) {
                        activity.systemBarsManager.enterFullscreenMode()
                    }
                }

                override fun onExitFullscreenMode() {
                    activity.systemBarsManager.exitFullscreenMode()
                }
            },
            playbackPositionRepository = activity.playbackPositionRepository,
            translationManager = activity.translationManager
        )
    }

    fun createTextViewerManager(): TextViewerManager {
        return TextViewerManager(
            context = activity,
            binding = activity.activityBinding,
            networkFileManager = activity.networkFileManager,
            settingsRepository = activity.settingsRepository,
            coroutineScope = activity.lifecycleScope,
            callback = object : TextViewerManager.TextViewerCallback {
                override fun showError(message: String) { activity.showError(message) }

                override fun showTranslationSettingsDialog() {
                    if (activity.isTranslationButtonManagerInitialized) {
                        activity.translationButtonManager.showTranslationSettingsDialog()
                    }
                }

                override fun exitFullscreenMode() {
                    activity.systemBarsManager.exitFullscreenMode()
                    activity.viewModel.toggleCommandPanel()
                }

                override fun setTouchZonesEnabled(enabled: Boolean) {
                    activity.safeViews.touchZonesOverlay.isVisible = enabled && activity.useTouchZones
                }

                override fun showEncodingDialog() {
                    activity.dialogHelper.showEncodingDialog()
                }
            },
            translationManager = activity.translationManager
        )
    }
}
