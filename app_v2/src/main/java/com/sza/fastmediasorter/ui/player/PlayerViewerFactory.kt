package com.sza.fastmediasorter.ui.player

import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager
import com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager
import com.sza.fastmediasorter.ui.player.helpers.TextViewerManager
import com.sza.fastmediasorter.ui.player.helpers.openCalculatorForSelection

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
            panelStereoSingleEyeNotifier = activity.panelStereoSingleEyeNotifier,
            // S0207 Phase 01: forwarded for PRE_PLAY / AFTER_STATE_READY MEM_PROBE checkpoints.
            memoryProbe = activity.memoryProbe,
            memoryProfileCoordinator = activity.memoryProfileCoordinator,
            // S0213 Pillar A: cooldown tracker for decoder-failure replay throttling.
            decoderFailureTracker = activity.recentDecoderFailureTracker,
            // S0391: source-availability gate for playback-dispatch gating.
            remoteSourceGate = activity.remoteSourceGate,
        ).also {
            it.onPositionSaved = { activity.viewModel.saveResumeState() }
        }
    }

    fun createPdfViewerManager(): PdfViewerManager {
        return PdfViewerManager(
            root = activity.activityBinding.root,
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
            root = activity.activityBinding.root,
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
        val saveFlow = com.sza.fastmediasorter.ui.player.helpers.TextEditorSaveFlow(
            context = activity,
            saveTextNoteUseCase = activity.saveTextNoteUseCase,
            scope = activity.lifecycleScope
        )
        return TextViewerManager(
            context = activity,
            root = activity.activityBinding.root,
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

                override fun launchEditorCalculator(initialInput: String) {
                    activity.textEditorCalculatorBridge.launch(initialInput)
                }

                override fun launchSelectionCalculator(initialInput: String) {
                    openCalculatorForSelection(activity, initialInput)
                }

                override fun finishActivity() {
                    activity.setResult(android.app.Activity.RESULT_OK)
                    activity.finish()
                }
            },
            translationManager = activity.translationManager,
            saveFlow = saveFlow,
            textNoteStagingRegistry = activity.textNoteStagingRegistry,
        )
    }

    /** Creates the flavor-specific Office document viewer provider (S0301 Phase 02). */
    fun createOfficeDocumentViewerProvider(): com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerProvider =
        com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerProviderFactory().create()

    /** Creates the flavor-specific embedded Office viewer host (S0301 Phase 03). */
    fun createOfficeDocumentViewerHost(): com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerHost =
        com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerProviderFactory().createViewerHost(
            root = activity.activityBinding.root,
            coroutineScope = activity.lifecycleScope,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerHost.Callback {
                override fun showError(message: String) { activity.showError(message) }

                override fun onEnterFullscreenMode() {
                    if (activity.currentSettings?.hideSystemUiInFullscreen != false) {
                        activity.systemBarsManager.enterFullscreenMode()
                    }
                }

                override fun onExitFullscreenMode() {
                    activity.systemBarsManager.exitFullscreenMode()
                }

                override fun onTranslateSelection(text: String) {
                    activity.textViewerManager.displayOcrText(text)
                    activity.textViewerManager.forceTranslate()
                }

                override fun onRequireExternalFallback(mediaFile: com.sza.fastmediasorter.domain.model.MediaFile) {
                    // S0301 Phase 05: engine could not render internally - show the explicit
                    // external / share / cancel fallback dialog instead of a silent handoff or a
                    // blank surface. Cancel keeps the player open.
                    activity.shareManager.showOfficeFallbackDialog(mediaFile)
                }
            },
        )
}
