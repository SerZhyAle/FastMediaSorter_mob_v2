package com.sza.fastmediasorter.ui.player

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.browse.managers.BrowseCloudAuthManager
import com.sza.fastmediasorter.ui.player.helpers.FilenameOverlayAutoHideManager
import com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.ui.player.helpers.AudioSlideshowPhotoModeManager
import com.sza.fastmediasorter.ui.player.helpers.CastMediaManager
import com.sza.fastmediasorter.ui.player.helpers.DocumentPrintManager
import com.sza.fastmediasorter.ui.player.helpers.SaveVideoFrameManager
import com.sza.fastmediasorter.ui.player.helpers.ExoPlayerControlsManager
import com.sza.fastmediasorter.ui.player.helpers.GoogleLensButtonsManager
import com.sza.fastmediasorter.ui.player.helpers.ImageOcrManager
import com.sza.fastmediasorter.ui.player.helpers.LyricsManager
import com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
import com.sza.fastmediasorter.ui.player.helpers.NowPlayingManager
import com.sza.fastmediasorter.ui.player.helpers.PictureInPictureManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerAudioMetadataManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerControlsSetupManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerDialogAndUiStateManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerEventHandler
import com.sza.fastmediasorter.ui.player.helpers.PlayerPrefetchManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerGestureSetupManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerTouchZoneSetupManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerImageTranslationManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerMediaLoaderManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerNavigationManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerSettingsManager
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.ui.player.helpers.PlayerShareManager
import com.sza.fastmediasorter.ui.player.helpers.SearchControlsManager
import com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager
import com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager
import com.sza.fastmediasorter.ui.player.helpers.TouchZoneGestureManager
import com.sza.fastmediasorter.ui.player.helpers.TranslationButtonManager
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.ui.player.helpers.UndoOperationManager
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Consolidates all manager initialization logic extracted from PlayerActivity.
 * Called once from PlayerActivity.initializeManagers() to reduce activity size by ~650 lines.
 * Each private init*() method corresponds to a former private method in PlayerActivity.
 */
internal class PlayerManagerInitializer(private val activity: PlayerActivity) {

    fun initialize() {
        initBackgroundMedia()
        initCoreCoordination()
        initDialogHelper()
        initFileOps()
        initCommandPanelAndImageLoading()
        initNetworkAndTranslation()
        initPlayerControlsAndOcr()
        initAudioAndMediaServices()
        initUiCoordinators()
        initSetupManagers()
        initPrefetchManager()
    }

    private fun initPrefetchManager() {
        activity.playerPrefetchManager = PlayerPrefetchManager(activity)
        activity.playerPrefetchManager.setup()
    }

    private fun initBackgroundMedia() {
        activity.backgroundMusicManager.initialize()

        activity.backgroundMusicManager.setOnTrackChangedListener { trackName ->
            activity.runOnUiThread {
                // dialogAndUiStateManager is initialized in initUiCoordinators() — safe here (deferred)
                activity.dialogAndUiStateManager.updateBackgroundMusicTrackDisplay(trackName)
            }
        }

        activity.backgroundMusicManager.setOnMusicErrorListener { errorMessage ->
            activity.runOnUiThread {
                Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }

        activity.safeViews.tvBackgroundMusicTrack.setOnClickListener {
            Timber.d("BackgroundMusic: User clicked track name - skipping to next random")
            activity.backgroundMusicManager.skipToNextRandomTrack()
        }

        activity.audioBackgroundPhotosManager.initialize()
        activity.audioBackgroundPhotosManager.setOnPhotoChangedListener { photo ->
            if (photo != null && activity.audioSlideshowPhotoModeManager.isActive) {
                activity.audioSlideshowPhotoModeManager.loadBackgroundPhoto(photo)
                activity.audioSlideshowPhotoModeManager.updatePhotoLabel(photo)
            } else if (photo == null) {
                activity.activityBinding.imageView.setImageDrawable(null)
                if (activity.audioSlideshowPhotoModeManager.isActive) {
                    activity.audioSlideshowPhotoModeManager.updatePhotoLabel(null)
                    activity.audioSlideshowPhotoModeManager.exit()
                }
            }
        }
        activity.audioBackgroundPhotosManager.setOnErrorListener { errorMessage ->
            Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initCoreCoordination() {
        activity.cloudAuthManager = BrowseCloudAuthManager(
            context = activity,
            coroutineScope = activity.lifecycleScope,
            googleDriveClient = activity.googleDriveClient,
            dropboxClient = activity.dropboxClient,
            oneDriveClient = activity.oneDriveClient,
            googleSignInLauncher = activity.googleSignInLauncher,
            callbacks = object : BrowseCloudAuthManager.CloudAuthCallbacks {
                override fun onAuthenticationSuccess() {}
                override fun onAuthenticationFailure() {}
            }
        )

        activity.navigationManager = PlayerNavigationManager(
            activity = activity,
            viewModel = activity.viewModel,
            lifecycle = activity.lifecycle
        )
        activity.slideshowController = activity.navigationManager.getSlideshowController()

        activity.keyboardHandler = com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler(
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerKeyboardCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel
            ),
            keyBindingManager = activity.keyBindingManager,
        )

        activity.uiStateCoordinator = com.sza.fastmediasorter.ui.player.helpers.PlayerUiStateCoordinator(
            binding = activity.activityBinding,
            settingsRepository = activity.settingsRepository,
            coroutineScope = activity.lifecycleScope,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerUiStateCoordinatorCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel
            )
        )

        activity.undoOperationManager = UndoOperationManager(
            rootView = activity.activityBinding.root,
            callback = object : UndoOperationManager.Callback {
                override fun isActivityAlive(): Boolean = !(activity.isFinishing || activity.isDestroyed)
                override fun getUndoActionText(): String =
                    UndoOperationManager.defaultUndoActionText(activity.activityBinding.root)
                override fun onUndoRequested() {
                    activity.viewModel.undoLastOperation()
                }
            }
        )

        activity.systemBarsManager = SystemBarsManager(activity = activity)
        activity.imageTranslationManager = PlayerImageTranslationManager(activity = activity)
        activity.shareManager = PlayerShareManager(activity = activity)
        activity.printManager = DocumentPrintManager(activity = activity)
        activity.saveVideoFrameManager = SaveVideoFrameManager(
            activity = activity,
            fileOperationUseCase = activity.fileOperationUseCase
        )
        activity.eventHandler = PlayerEventHandler(activity = activity)
    }

    private fun initDialogHelper() {
        activity.dialogHelper = PlayerDialogHelper(
            activity = activity,
            viewModel = activity.viewModel,
            settingsRepository = activity.settingsRepository,
            smbClient = activity.smbClient,
            sftpClient = activity.sftpClient,
            ftpClient = activity.ftpClient,
            credentialsRepository = activity.credentialsRepository,
            unifiedCache = activity.unifiedCache,
            rotateImageUseCase = activity.rotateImageUseCase,
            flipImageUseCase = activity.flipImageUseCase,
            networkImageEditUseCase = activity.networkImageEditUseCase,
            applyImageFilterUseCase = activity.applyImageFilterUseCase,
            adjustImageUseCase = activity.adjustImageUseCase,
            extractGifFramesUseCase = activity.extractGifFramesUseCase,
            saveGifFirstFrameUseCase = activity.saveGifFirstFrameUseCase,
            changeGifSpeedUseCase = activity.changeGifSpeedUseCase,
            downloadNetworkFileUseCase = activity.downloadNetworkFileUseCase,
            dialogCallback = object : PlayerDialogHelper.DialogCallback {
                override fun onImageEditComplete() {
                    activity.viewModel.refreshCurrentFileInfo()
                    activity.mediaLoaderManager.reloadCurrentImage()
                    Toast.makeText(activity, activity.getString(R.string.msg_image_edit_completed), Toast.LENGTH_SHORT).show()
                }
                override fun onGifEditComplete() {
                    activity.viewModel.refreshCurrentFileInfo()
                    activity.mediaLoaderManager.reloadCurrentImage()
                    Toast.makeText(activity, R.string.gif_edit_completed, Toast.LENGTH_SHORT).show()
                }
                override fun onRenameComplete() {
                    activity.viewModel.reloadAfterRename()
                }
            },
            videoPlayerManagerProvider = { activity.videoPlayerManager },
            textViewerManagerProvider = { activity.textViewerManager },
            sleepTimerManagerProvider = { activity.sleepTimerManager }
        )

        activity.dialogHelper.setAuthCallback { provider ->
            when (provider.lowercase()) {
                "dropbox" -> activity.cloudAuthManager.launchDropboxSignIn()
                "google drive", "google_drive" -> activity.cloudAuthManager.launchGoogleSignIn()
                "onedrive" -> activity.cloudAuthManager.launchOneDriveSignIn()
                else -> Timber.w("Unknown provider for auth request: $provider")
            }
        }
    }

    private fun initFileOps() {
        activity.fileOperationsHandler = FileOperationsHandler(
            context = activity,
            lifecycleScope = activity.lifecycleScope,
            appScope = activity.fileOpsAppScope,
            settingsRepository = activity.settingsRepository,
            fileOperationUseCase = activity.viewModel.fileOperationUseCase,
            callback = object : FileOperationsHandler.FileOperationCallback {
                override fun onCopySuccess(destination: com.sza.fastmediasorter.domain.model.MediaResource, goToNext: Boolean) {
                    if (goToNext) {
                        activity.navigationManager.navigateNextAfterOperation("Copy success with goToNext=true")
                    }
                }

                override fun onMoveSuccess(destination: com.sza.fastmediasorter.domain.model.MediaResource, movedFilePath: String, goToNext: Boolean) {
                    activity.lifecycleManager.trackModifiedFile(movedFilePath)
                    activity.viewModel.state.value.resource?.let { resource ->
                        MediaFilesCacheManager.removeFile(resource.id, movedFilePath)
                    }
                    val hasRemainingFiles = activity.viewModel.removeMovedFile(movedFilePath)
                    if (!hasRemainingFiles) {
                        activity.finish()
                    } else if (goToNext) {
                        activity.navigationManager.navigateNextAfterOperation("Move success with goToNext=true")
                    }
                }

                override fun onCopyToPathSuccess(destinationPath: String, goToNext: Boolean) {
                    if (goToNext) {
                        activity.navigationManager.navigateNextAfterOperation("CopyToPath success with goToNext=true")
                    }
                }

                override fun onMoveToPathSuccess(destinationPath: String, movedFilePath: String, goToNext: Boolean) {
                    activity.lifecycleManager.trackModifiedFile(movedFilePath)
                    activity.viewModel.state.value.resource?.let { resource ->
                        MediaFilesCacheManager.removeFile(resource.id, movedFilePath)
                    }
                    val hasRemainingFiles = activity.viewModel.removeMovedFile(movedFilePath)
                    if (!hasRemainingFiles) {
                        activity.finish()
                    } else if (goToNext) {
                        activity.navigationManager.navigateNextAfterOperation("MoveToPath success with goToNext=true")
                    }
                }

                override fun onDeleteSuccess(deletedFilePath: String) {
                    activity.handleDeleteSuccess(deletedFilePath)
                }

                override fun onOperationError(message: String, throwable: Throwable?) {
                    activity.showError(message, throwable)
                }

                override fun onAuthenticationRequired(provider: String, message: String) {
                    activity.eventHandler.showCloudAuthenticationError(provider)
                }

                override fun onBatchDeletePermissionRequired(pendingIntent: android.app.PendingIntent) {
                    timber.log.Timber.i("PlayerActivity: Launching batch delete permission dialog")
                    try {
                        activity.batchDeletePermissionLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } catch (e: Exception) {
                        timber.log.Timber.e(e, "PlayerActivity: Failed to launch batch delete permission request")
                        activity.showError(activity.getString(R.string.error_delete_failed, e.message))
                    }
                }

                override fun getCurrentFile(): com.sza.fastmediasorter.domain.model.MediaFile? =
                    activity.viewModel.state.value.currentFile

                override fun getCurrentResource(): com.sza.fastmediasorter.domain.model.MediaResource? =
                    activity.viewModel.state.value.resource
            }
        )

        activity.playerFolderPickerHandler = com.sza.fastmediasorter.ui.player.helpers.PlayerFolderPickerHandler(
            activity = activity,
            coroutineScope = activity.lifecycleScope,
            settingsRepository = activity.settingsRepository,
            fileOperationsHandler = activity.fileOperationsHandler,
            onLaunchPicker = { uri -> activity.folderPickerLauncher.launch(uri) }
        )

        activity.destinationButtonsManager = DestinationButtonsManager(
            binding = activity.activityBinding,
            settingsRepository = activity.settingsRepository,
            getDestinationsUseCase = activity.viewModel.getDestinationsUseCase,
            lifecycleScope = activity.lifecycleScope,
            callback = object : DestinationButtonsManager.DestinationButtonsCallback {
                override fun onCopyClicked(destination: com.sza.fastmediasorter.domain.model.MediaResource) {
                    activity.fileOperationsHandler.performCopy(destination)
                }

                override fun onMoveClicked(destination: com.sza.fastmediasorter.domain.model.MediaResource) {
                    Timber.d("PlayerActivity: onMoveClicked - destination=${destination.name}")
                    activity.fileOperationsHandler.performMove(destination)
                }

                override fun onCustomPathPickerRequested(operationType: com.sza.fastmediasorter.domain.model.FileOperationType) {
                    val credId = activity.viewModel.state.value.resource?.credentialsId
                    activity.playerFolderPickerHandler.requestFolderPick(operationType, credId)
                }

                override fun getCurrentResourceId(): Long =
                    activity.intent.getLongExtra("resourceId", -1)

                override fun onUpdateCommandAvailability() {
                    val state = activity.viewModel.state.value
                    Timber.d("PlayerActivity.onUpdateCommandAvailability: showCommandPanel=${state.showCommandPanel}, enableCopying=${state.enableCopying}, enableMoving=${state.enableMoving}")
                    activity.updateCommandAvailability(state)
                }

                override fun isCommandPanelVisible(): Boolean {
                    val state = activity.viewModel.state.value
                    return state.showCommandPanel || state.currentFile?.type == MediaType.AUDIO
                }
            }
        )
    }

    private fun initCommandPanelAndImageLoading() {
        activity.commandPanelController = CommandPanelController(
            binding = activity.activityBinding,
            settingsRepository = activity.settingsRepository,
            coroutineScope = activity.lifecycleScope,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerCommandPanelCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel
            )
        )
        activity.commandPanelController.updateOrientation(activity.resources.configuration)

        activity.imageLoadingManager = ImageLoadingManager(
            binding = activity.activityBinding,
            settingsRepository = activity.settingsRepository,
            searchAudioCoverUseCase = activity.searchAudioCoverUseCase,
            audioMetadataCacheRepository = activity.audioMetadataCacheRepository,
            okHttpClient = activity.okHttpClient,
            lifecycleScope = activity.lifecycleScope,
            loadingIndicatorHandler = activity.loadingIndicatorHandler,
            showLoadingIndicatorRunnable = activity.showLoadingIndicatorRunnable,
            panelStereoSingleEyeNotifier = activity.panelStereoSingleEyeNotifier,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerImageLoadingCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel
            )
        )

        activity.audioEmptyStateController = AudioEmptyStateController(
            context = activity,
            audioCoverArtView = activity.activityBinding.audioCoverArtView,
            barsView = activity.activityBinding.audioBarsView,
            videoView = activity.activityBinding.audioVideoView,
            wavesView = activity.activityBinding.audioWaveParticleView
        )
        activity.imageLoadingManager.setAudioEmptyStateController(activity.audioEmptyStateController!!)
    }

    private fun initNetworkAndTranslation() {
        activity.networkFileManager = NetworkFileManager(
            context = activity,
            smbClient = activity.smbClient,
            sftpClient = activity.sftpClient,
            ftpClient = activity.ftpClient,
            googleDriveClient = activity.googleDriveClient,
            dropboxClient = activity.dropboxClient,
            oneDriveClient = activity.oneDriveClient,
            credentialsRepository = activity.credentialsRepository,
            smbFileOperationHandler = activity.smbFileOperationHandler,
            sftpFileOperationHandler = activity.sftpFileOperationHandler,
            ftpFileOperationHandler = activity.ftpFileOperationHandler,
            cloudFileOperationHandler = activity.cloudFileOperationHandler,
            unifiedCache = activity.unifiedCache,
            callback = object : NetworkFileManager.NetworkFileCallback {
                override fun getCurrentResource(): com.sza.fastmediasorter.domain.model.MediaResource? =
                    activity.viewModel.state.value.resource
                override fun showError(message: String) {
                    activity.showError(message)
                }
            }
        )

        activity.translationManager = TranslationManager(
            context = activity,
            settingsRepository = activity.settingsRepository,
            callback = object : TranslationManager.TranslationCallback {
                override fun showError(message: String) {
                    activity.showError(message)
                }
                override fun showModelDownloadPrompt(
                    languageName: String,
                    onConfirm: () -> Unit,
                    onCancel: () -> Unit
                ) {
                    activity.runOnUiThread {
                        AlertDialog.Builder(activity)
                            .setTitle(R.string.download_translation_model_title)
                            .setMessage(activity.getString(R.string.download_translation_model_message, languageName))
                            .setPositiveButton(R.string.download) { _, _ ->
                                onConfirm()
                                android.widget.Toast.makeText(
                                    activity,
                                    activity.getString(R.string.translation_downloading_wait),
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        )
    }

    private fun initPlayerControlsAndOcr() {
        // OPTIMIZATION: Document Viewers and VideoPlayerManager use lazy initialization
        activity.playerGestureCallback = com.sza.fastmediasorter.ui.player.callbacks.PlayerGestureCallbackImpl(
            activity = activity,
            viewModel = activity.viewModel,
            binding = activity.activityBinding,
            pdfViewerManagerProvider = { activity.pdfViewerManager },
            epubViewerManagerProvider = { activity.epubViewerManager }
        )

        activity.lyricsManager = LyricsManager(
            context = activity,
            binding = activity.activityBinding,
            lifecycleScope = activity.lifecycleScope,
            searchLyricsUseCase = activity.searchLyricsUseCase,
            getTranslationSessionSettings = { activity.translationSessionSettings }
        )

        activity.gestureHelper = PlayerGestureHelper(
            context = activity,
            gestureCallback = activity.playerGestureCallback
        )

        activity.touchZoneGestureManager = TouchZoneGestureManager(
            binding = activity.activityBinding,
            viewModel = activity.viewModel,
            touchZoneDetector = activity.touchZoneDetector,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerTouchZoneCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel
            )
        )

        activity.translationButtonManager = TranslationButtonManager(
            context = activity,
            lifecycleOwner = activity,
            binding = activity.activityBinding,
            settingsRepository = activity.settingsRepository,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerTranslationButtonCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel
            )
        )

        // OPTIMIZATION: VideoPlayerManager uses lazy initialization (see PlayerViewerFactory)
        activity.playerSettingsManager = PlayerSettingsManager(
            activity = activity,
            dialogHelper = activity.dialogHelper,
            videoPlayerManagerProvider = { activity.videoPlayerManager },
            settingsRepository = activity.settingsRepository,
            // Reads live stereo mode from ViewModel so dialog always shows the current value
            getStereoMode = { activity.viewModel.stereoMode.value },
            // Propagate user stereo selection back into ViewModel
            onStereoModeChanged = { mode -> activity.viewModel.setStereoMode(mode) },
            callback = object : PlayerSettingsManager.Callback {}
        )

        // Observe stereoMode StateFlow and apply GL effects to the player whenever it changes.
        // Runs on Main dispatcher (lifecycleScope default) — safe for ExoPlayer.setVideoEffects().
        activity.lifecycleScope.launch {
            activity.viewModel.stereoMode
                .filter { it != StereoMode.AUTO }
                .collect { mode ->
                    activity.videoPlayerManager.applyStereoEffect(mode)
                }
        }

        // Observe stereoMode changes for image stereo crop (3D tab mode switch while viewing an image).
        // When the user changes the mode via the dialog, re-render the current image with the new crop.
        activity.lifecycleScope.launch {
            activity.viewModel.stereoMode.collect { mode ->
                val currentFile = activity.viewModel.state.value.currentFile ?: return@collect
                if (currentFile.type == com.sza.fastmediasorter.domain.model.MediaType.IMAGE ||
                    currentFile.type == com.sza.fastmediasorter.domain.model.MediaType.GIF) {
                    activity.imageLoadingManager.setStereoMode(mode)
                    // Re-display the current image so the new crop takes effect.
                    val path = currentFile.path
                    activity.imageLoadingManager.displayImage(path)
                }
            }
        }

        // Observe panelStereoSingleEye flag — toggle stereo crop on the currently displayed image
        // without a fresh navigation (spec_panel-stereo-single-eye §3.1.1).
        activity.lifecycleScope.launch {
            activity.settingsRepository.getSettings()
                .map { it.panelStereoSingleEye }
                .distinctUntilChanged()
                .collect { enabled ->
                    activity.imageLoadingManager.setPanelStereoSingleEyeEnabled(enabled)
                    val currentFile = activity.viewModel.state.value.currentFile ?: return@collect
                    if (currentFile.type == com.sza.fastmediasorter.domain.model.MediaType.IMAGE ||
                        currentFile.type == com.sza.fastmediasorter.domain.model.MediaType.GIF) {
                        activity.imageLoadingManager.displayImage(currentFile.path)
                    }
                }
        }

        activity.exoPlayerControlsManager = ExoPlayerControlsManager(
            binding = activity.activityBinding,
            videoPlayerManager = activity.videoPlayerManager,
            callback = object : ExoPlayerControlsManager.ExoPlayerControlsCallback {
                override fun onPreviousFile() = activity.navigationManager.navigatePreviousFromControl()
                override fun onNextFile() = activity.navigationManager.navigateNextFromControl()
                override fun showPlaybackControlDialog() = activity.dialogHelper.showPlaybackControlDialog()
            }
        )

        activity.searchControlsManager = SearchControlsManager(
            binding = activity.activityBinding,
            textViewerManagerProvider = { activity.textViewerManager },
            pdfViewerManagerProvider = { activity.pdfViewerManager },
            epubViewerManagerProvider = { activity.epubViewerManager },
            lifecycleScope = activity.lifecycleScope,
            inputMethodManager = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager,
            callback = object : SearchControlsManager.SearchControlsCallback {
                override fun getCurrentMediaFile() = activity.viewModel.state.value.currentFile
                override fun scheduleHideControls() = activity.scheduleHideControls()
                override fun onEpubTranslate() {
                    if (activity._epubViewerManager != null) activity.epubViewerManager.toggleTranslation()
                }
                override fun showTranslationSettingsDialog() =
                    activity.translationButtonManager.showTranslationSettingsDialog()
            }
        )

        activity.imageOcrManager = ImageOcrManager(
            binding = activity.activityBinding,
            lifecycleScope = activity.lifecycleScope,
            settingsRepository = activity.settingsRepository,
            translationManager = activity.translationManager,
            textViewerManagerProvider = { activity.textViewerManager },
            callback = object : ImageOcrManager.ImageOcrCallback {
                override fun showError(message: String) { activity.showError(message) }
                override fun getString(resId: Int): String = activity.getString(resId)
                override fun getString(resId: Int, vararg formatArgs: Any): String =
                    activity.getString(resId, *formatArgs)
            }
        )

        activity.googleLensButtonsManager = GoogleLensButtonsManager(
            binding = activity.activityBinding,
            onShareToGoogleLens = { activity.shareCurrentFileToGoogleLens() },
            onSharePdfPageToGoogleLens = {
                if (activity._pdfViewerManager != null) {
                    activity.pdfViewerManager.shareCurrentPageToGoogleLens()
                }
            },
            onExtractImageText = { activity.extractTextFromCurrentImage() },
            onExtractPdfText = {
                if (activity._pdfViewerManager != null) {
                    activity.pdfViewerManager.extractTextFromCurrentPage()
                }
            },
            onExtractEpubText = {
                if (activity._epubViewerManager != null) {
                    activity.epubViewerManager.extractTextFromCurrentChapter()
                }
            },
            onShowTranslationSettings = {
                if (activity.isTranslationButtonManagerInitialized) {
                    activity.translationButtonManager.showTranslationSettingsDialog()
                }
            }
        )
    }

    private fun initAudioAndMediaServices() {
        activity.castMediaManager = CastMediaManager(
            context = activity,
            lifecycleScope = activity.lifecycleScope,
            onCastStateChanged = { isCasting, deviceName ->
                activity.viewModel.updateCastState(isCasting, deviceName)
                if (isCasting) {
                    val currentFile = activity.viewModel.state.value.currentFile
                    if (currentFile != null) activity.castMediaManager.sendCurrentMedia(currentFile)
                }
            }
        )
        activity.castMediaManager.init()
        activity.commandPanelController.bindCastManager(activity.castMediaManager)

        activity.audioServiceController = AudioServiceController(activity)
        activity.nowPlayingManager = NowPlayingManager(
            activityBinding = activity.activityBinding,
            fragmentManager = activity.supportFragmentManager,
            audioServiceController = activity.audioServiceController!!
        )
        activity.sleepTimerManager = SleepTimerManager(
            vinylView = activity.activityBinding.vinylIndicator,
            sleepTimerBadge = activity.activityBinding.sleepTimerBadge,
            playerProvider = { activity.videoPlayerManager.getPlayer() }
        )
        activity.pipManager = PictureInPictureManager(
            activity = activity,
            binding = activity.activityBinding,
            getPlayer = { activity.videoPlayerManager.getPlayer() },
            onPlay = {
                val isAudio = activity.isMediaLoaderManagerInitialized &&
                        activity.mediaLoaderManager.isServiceAudioActive
                if (isAudio) activity.audioServiceController?.player?.play()
                else activity.videoPlayerManager.play()
            },
            onPause = {
                val isAudio = activity.isMediaLoaderManagerInitialized &&
                        activity.mediaLoaderManager.isServiceAudioActive
                if (isAudio) activity.audioServiceController?.player?.pause()
                else activity.videoPlayerManager.pause()
            },
            isVideoPlaying = {
                val currentFile = activity.viewModel.state.value.currentFile
                when (currentFile?.type) {
                    MediaType.VIDEO -> activity.videoPlayerManager.getPlayer()?.isPlaying == true
                    MediaType.AUDIO -> activity.audioServiceController?.player?.isPlaying == true
                    else -> false
                }
            }
        )
        activity.mediaLoaderManager = PlayerMediaLoaderManager(
            activity = activity,
            binding = activity.activityBinding,
            viewModel = activity.viewModel,
            imageLoadingManager = activity.imageLoadingManager,
            videoPlayerManager = activity.videoPlayerManager,
            textViewerManagerProvider = { activity.textViewerManager },
            exoPlayerControlsManager = activity.exoPlayerControlsManager,
            lifecycleScope = activity.lifecycleScope,
            loadingIndicatorHandler = activity.loadingIndicatorHandler,
            showLoadingIndicatorRunnable = activity.showLoadingIndicatorRunnable,
            mediaFilesCacheManager = activity.mediaFilesCacheManager,
            audioServiceController = activity.audioServiceController,
            onAudioServicePlaybackChanged = { isPlaying ->
                val isAudioFile = activity.viewModel.state.value.currentFile?.type == MediaType.AUDIO
                val servicePlayWhenReady = activity.audioServiceController?.player?.playWhenReady
                if (isAudioFile && servicePlayWhenReady != null) {
                    // Persistent audio is driven by MediaController state, not the local ExoPlayer path.
                    // Sync ViewModel pause from playWhenReady so pause/resume UI reactions
                    // (including filename overlay re-show) also work for service-backed audio.
                    activity.viewModel.setPaused(!servicePlayWhenReady)
                }
                activity.sleepTimerManager?.updateVinylState(isPlaying, isAudioFile)
                if (isAudioFile) {
                    activity.audioEmptyStateController?.onIsPlayingChanged(isPlaying)
                }
            },
            onAudioServiceReady = {
                val currentFile = activity.viewModel.state.value.currentFile
                if (currentFile?.type == MediaType.AUDIO) {
                    activity.updateAudioFormatInfo()
                    activity.imageLoadingManager.loadAudioCoverArt(currentFile)
                    activity.prefetchNextAudio()
                    activity.updateAudioSlideshowCurrentSongLabel()
                }
            },
            onAudioServicePlaybackEnded = {
                val direction = AudioPlaybackService.pendingDirection
                AudioPlaybackService.pendingDirection = AudioPlaybackService.DIRECTION_NEXT
                val wasAudio = activity.viewModel.state.value.currentFile?.type == MediaType.AUDIO
                if (activity.viewModel.state.value.isSlideShowActive) {
                    activity.viewModel.nextFile(skipDocuments = true)
                    activity.slideshowController.restartTimer()
                } else if (direction == AudioPlaybackService.DIRECTION_PREV) {
                    activity.viewModel.previousFile()
                } else {
                    activity.viewModel.nextFile()
                }
                if (wasAudio) {
                    activity.advanceAudioBackgroundPhoto()
                }
            },
            onAudioServicePlaybackError = { _ ->
                activity.handleMediaLoadErrorAndSkip()
            },
            smbClient = activity.smbClient,
            sftpClient = activity.sftpClient,
            ftpClient = activity.ftpClient,
            credentialsRepository = activity.credentialsRepository,
            unifiedCache = activity.unifiedCache,
            cloudClients = mapOf(
                "googledrive" to activity.googleDriveClient,
                "onedrive" to activity.oneDriveClient,
                "dropbox" to activity.dropboxClient
            )
        )
    }

    private fun initUiCoordinators() {
        activity.audioMetadataManager = PlayerAudioMetadataManager(activity)

        activity.dialogAndUiStateManager = PlayerDialogAndUiStateManager(
            activity = activity,
            viewModel = activity.viewModel,
            binding = activity.activityBinding,
            dialogHelper = activity.dialogHelper,
            destinationButtonsManager = activity.destinationButtonsManager,
            commandPanelController = activity.commandPanelController,
            textViewerManagerProvider = { activity.textViewerManager },
            mediaLoaderManager = activity.mediaLoaderManager,
            networkFileManager = activity.networkFileManager,
            imageLoadingManager = activity.imageLoadingManager,
            lifecycleScope = activity.lifecycleScope
        )

        activity.audioSlideshowPhotoModeManager = AudioSlideshowPhotoModeManager(
            activity = activity,
            binding = activity.activityBinding,
            viewModel = activity.viewModel,
            audioBackgroundPhotosManager = activity.audioBackgroundPhotosManager,
            backgroundMusicManager = activity.backgroundMusicManager,
            dialogAndUiStateManager = activity.dialogAndUiStateManager,
            settingsRepository = activity.settingsRepository,
            lifecycleScope = activity.lifecycleScope,
            callback = object : AudioSlideshowPhotoModeManager.Callback {
                override fun updateSlideShowButton() = activity.dialogAndUiStateManager.updateSlideShowButton()
                override fun updateSystemBarsForPlayer(showCommandPanel: Boolean) =
                    activity.updateSystemBarsForPlayer(showCommandPanel)
                override fun toggleSlideshow() = activity.navigationManager.toggleSlideshow()
                override fun updateSlideshowState() = activity.navigationManager.updateSlideshowState()
                override fun getSupportActionBar() = activity.supportActionBar
            }
        )

        // Wire audioSlideshowPhotoModeManager into dialogAndUiStateManager (created just above)
        activity.dialogAndUiStateManager.audioSlideshowPhotoModeManager =
            activity.audioSlideshowPhotoModeManager

        // Wire FilenameOverlayAutoHideManager — controls auto-hide timing for tvFileNameOverlay.
        // Use actual command-panel visibility rather than raw showCommandPanel state,
        // because audio can force the panel visible while the ViewModel flag stays false.
        activity.dialogAndUiStateManager.filenameOverlayManager = FilenameOverlayAutoHideManager(
            overlayView = activity.activityBinding.tvFileNameOverlay,
            isFullscreen = { !activity.activityBinding.topCommandPanel.isVisible }
        )

        // Wire zoom interaction signal from PhotoView → overlay manager.
        // This lambda is called by ImageLoadingManager when a real user pinch-zoom is detected.
        activity.imageLoadingManager.onZoomInteraction = zoomInteraction@{
            val currentFile = activity.viewModel.state.value.currentFile
                ?: return@zoomInteraction
            activity.dialogAndUiStateManager.filenameOverlayManager
                ?.onZoomInteraction(currentFile.type)
        }
    }

    private fun initSetupManagers() {
        activity.controlsSetupManager = PlayerControlsSetupManager(
            activity = activity,
            binding = activity.activityBinding,
            viewModel = activity.viewModel,
            lifecycleScope = activity.lifecycleScope,
            slideshowController = activity.slideshowController,
            pdfViewerManagerProvider = { activity.pdfViewerManager },
            epubViewerManagerProvider = { activity.epubViewerManager },
            textViewerManagerProvider = { activity.textViewerManager },
            translationManager = activity.translationManager,
            translationButtonManager = activity.translationButtonManager,
            exoPlayerControlsManager = activity.exoPlayerControlsManager,
            searchControlsManager = activity.searchControlsManager,
            blackScreenOverlayManager = activity.blackScreenOverlayManager
        )

        activity.gestureSetupManager = PlayerGestureSetupManager(
            activity = activity,
            binding = activity.activityBinding,
            viewModel = activity.viewModel,
            touchZoneGestureManager = activity.touchZoneGestureManager
        )

        activity.touchZoneSetupManager = PlayerTouchZoneSetupManager(activity)
    }
}
