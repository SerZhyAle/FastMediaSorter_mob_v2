package com.sza.fastmediasorter.ui.player

import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.mutation.Mutation
import com.sza.fastmediasorter.ui.browse.managers.BrowseCloudAuthManager
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperation
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperationEvent
import com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperationQueue
import com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController
import com.sza.fastmediasorter.ui.player.helpers.AudioServiceController
import com.sza.fastmediasorter.ui.player.helpers.AudioSlideshowPhotoModeManager
import com.sza.fastmediasorter.ui.player.helpers.BlackScreenOverlayManager
import com.sza.fastmediasorter.ui.player.helpers.DocumentPrintManager
import com.sza.fastmediasorter.ui.player.helpers.ExoPlayerControlsManager
import com.sza.fastmediasorter.ui.player.helpers.FilenameOverlayAutoHideManager
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
import com.sza.fastmediasorter.ui.player.helpers.PlayerGestureSetupManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerImageTranslationManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs
import com.sza.fastmediasorter.ui.player.helpers.PlayerMediaLoaderManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerNavigationManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerPrefetchManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerSettingsManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerShareManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerTouchZoneSetupManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerVrLaunchManager
import com.sza.fastmediasorter.ui.player.helpers.SaveVideoFrameManager
import com.sza.fastmediasorter.ui.player.helpers.SearchControlsManager
import com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager
import com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager
import com.sza.fastmediasorter.ui.player.helpers.TouchZoneGestureManager
import com.sza.fastmediasorter.ui.player.helpers.TranslationButtonManager
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.ui.player.helpers.UndoOperationManager
import com.sza.fastmediasorter.ui.player.helpers.seekBackward
import com.sza.fastmediasorter.ui.player.helpers.seekForward
import com.sza.fastmediasorter.util.showBoundTo
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.UUID

/** Consolidates all manager initialization logic extracted from PlayerActivity. Called once from PlayerActivity.initializeManagers() to reduce activity size by ~650 lines. Each private init*() method corresponds to a former private method in PlayerActivity. */
internal class PlayerManagerInitializer(private val activity: PlayerActivity) {

    /**
     * S1549 (owner ruling 2026-08-16, strategic §6 item 5, variant A): initialization is split in
     * two halves. The screen-level half runs exactly once per screen - managers with no view
     * references, managers whose construction registers a standing collector/observer/listener,
     * and stateful holders that get a rebind seam instead of a re-construction. The binding-bound
     * half is re-runnable: managers that merely capture the binding (or views resolved from it)
     * and register nothing but view listeners, which die with the discarded tree. After a
     * re-inflate the activity re-runs only the binding-bound half - re-running the screen-level
     * half would double every registration it contains.
     */
    fun initialize() {
        constructScreenLevelOnce()
        constructBindingBoundManagers()
    }

    private var screenLevelConstructed = false

    private fun constructScreenLevelOnce() {
        if (screenLevelConstructed) return
        screenLevelConstructed = true
        initScreenLevelCoreCoordination()
        initDialogHelper()
        initFileOps()
        initCommandPanel()
        initAudioEmptyState()
        initNetworkAndTranslation()
        initScreenLevelControlsAndSettings()
        initAudioAndMediaServices()
        initScreenLevelUiCoordinators()
        initPrefetchManager()
    }

    /**
     * Re-runnable half of [initialize] - called again by the host after a re-inflate swapped the
     * binding (S1549). Every manager created here is re-created against the current binding.
     */
    fun constructBindingBoundManagers() {
        initBackgroundMedia()
        initBindingBoundCore()
        initImageLoading()
        initBindingBoundControlsAndOcr()
        initMediaLoaderOnce()
        initBindingBoundMediaServices()
        initUiCoordinators()
        initSetupManagers()
    }

    // Helper must live at class scope because rename callbacks use it before initFileOps()
    // declares its local helpers.
    private fun buildRenamedPath(oldPath: String, newName: String): String {
        val lastSlashIndex = oldPath.lastIndexOf('/')
        return if (lastSlashIndex >= 0) {
            oldPath.substring(0, lastSlashIndex + 1) + newName
        } else {
            newName
        }
    }

    /**
     * S0242 Phase 02: convert a queue-succeeded [PlayerFileOperation] into the matching
     * [Mutation] variant and append it to the journal.
     *
     * Variant selection:
     * - [PlayerFileOperation.Delete] → [Mutation.Delete].
     * - [PlayerFileOperation.MoveToResource] → [Mutation.Move] across resources.
     * - [PlayerFileOperation.MoveToPath] → [Mutation.Move] within the same resource (src == dst).
     * - [PlayerFileOperation.Rename] → [Mutation.Rename] (same resource, file name only).
     *
     * Skipped silently when no resource is bound - without a resource id the entry is unroutable.
     */
    private fun recordQueuedOperationMutation(op: PlayerFileOperation) {
        val lifecycleManager = activity.lifecycleManager
        val resourceId = lifecycleManager.currentResourceId() ?: return
        val resourceType = lifecycleManager.currentResourceType() ?: return
        val normalizer = lifecycleManager.pathNormalizer()
        val opId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        val mutation: Mutation = when (op) {
            is PlayerFileOperation.Delete -> Mutation.Delete(
                resourceId = resourceId,
                canonicalPath = normalizer.canonical(op.sourcePath, resourceType),
                opId = opId,
                timestampMs = timestamp,
            )
            is PlayerFileOperation.MoveToResource -> {
                val destPath = joinPath(op.destination.path, op.sourceName)
                val dstResourceId = op.destination.id
                val dstResourceType = op.destination.type
                Mutation.Move(
                    resourceId = resourceId,
                    srcResourceId = resourceId,
                    dstResourceId = dstResourceId,
                    oldCanonicalPath = normalizer.canonical(op.sourcePath, resourceType),
                    newCanonicalPath = normalizer.canonical(destPath, dstResourceType),
                    opId = opId,
                    timestampMs = timestamp,
                )
            }
            is PlayerFileOperation.MoveToPath -> {
                val destPath = joinPath(op.destinationPath, op.sourceName)
                Mutation.Move(
                    resourceId = resourceId,
                    srcResourceId = resourceId,
                    dstResourceId = resourceId,
                    oldCanonicalPath = normalizer.canonical(op.sourcePath, resourceType),
                    newCanonicalPath = normalizer.canonical(destPath, resourceType),
                    opId = opId,
                    timestampMs = timestamp,
                )
            }
            is PlayerFileOperation.Rename -> {
                val newPath = buildRenamedPath(op.sourcePath, op.newName)
                Mutation.Rename(
                    resourceId = resourceId,
                    oldCanonicalPath = normalizer.canonical(op.sourcePath, resourceType),
                    newCanonicalPath = normalizer.canonical(newPath, resourceType),
                    opId = opId,
                    timestampMs = timestamp,
                )
            }
        }
        lifecycleManager.recordMutation(mutation)
    }

    /** Join a directory path and a basename with exactly one `/`. */
    private fun joinPath(dir: String, name: String): String {
        val trimmed = dir.trimEnd('/')
        return "$trimmed/$name"
    }

    private fun initPrefetchManager() {
        activity.playerPrefetchManager = PlayerPrefetchManager(activity)
        activity.playerPrefetchManager.setup()
    }

    fun ensureAudioBackgroundManagersConfigured() {
        if (activity.areAudioBackgroundManagersConfigured) {
            return
        }

        activity.backgroundMusicManager.initialize()
        activity.backgroundMusicManager.setOnTrackChangedListener { trackName ->
            activity.runOnUiThread {
                runCatching { activity.dialogAndUiStateManager }
                    .getOrNull()
                    ?.updateBackgroundMusicTrackDisplay(trackName)
            }
        }
        activity.backgroundMusicManager.setOnMusicErrorListener { errorMessage ->
            activity.runOnUiThread {
                Toast.makeText(activity, errorMessage, Toast.LENGTH_SHORT).show()
            }
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
        activity.areAudioBackgroundManagersConfigured = true
    }

    private fun initBackgroundMedia() {
        activity.safeViews.tvBackgroundMusicTrack.setOnClickListener {
            ensureAudioBackgroundManagersConfigured()
            activity.backgroundMusicManager.skipToNextRandomTrack()
        }
    }

    private fun initScreenLevelCoreCoordination() {
        activity.cloudAuthManager = BrowseCloudAuthManager(
            context = activity,
            coroutineScope = activity.lifecycleScope,
            googleDriveClient = activity.googleDriveClientLazy,
            dropboxClient = activity.dropboxClientLazy,
            oneDriveClient = activity.oneDriveClientLazy,
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
        activity.slideshowResourceAvailabilityManager =
            com.sza.fastmediasorter.ui.player.helpers.SlideshowResourceAvailabilityManager(
                activity = activity,
                networkStateMonitor = activity.networkStateMonitor,
                lifecycleScope = activity.lifecycleScope
            )

        activity.keyboardHandler = com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler(
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerKeyboardCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel
            ),
            keyBindingManager = activity.keyBindingManager,
        )

        activity.systemBarsManager = SystemBarsManager(activity = activity)
        activity.blackScreenOverlayManager = BlackScreenOverlayManager(
            activityRef = java.lang.ref.WeakReference(activity),
            systemBarsManager = activity.systemBarsManager
        )
        activity.imageTranslationManager = PlayerImageTranslationManager(activity = activity)
        activity.shareManager = PlayerShareManager(activity = activity)
        activity.printManager = DocumentPrintManager(host = activity, mediaCapabilities = activity.mediaCapabilities)
        activity.saveVideoFrameManager = SaveVideoFrameManager(
            activity = activity,
            fileOperationUseCase = activity.playerHostFactory.fileOperation,
            imageClipboardWriter = activity.imageClipboardWriter,
            localDestinationClassifier = activity.localDestinationClassifier,
            localDestinationWriter = activity.localDestinationWriter
        )
        activity.imageCropManager = com.sza.fastmediasorter.ui.player.helpers.ImageCropManager(
            context = activity,
            lifecycleScope = activity.lifecycleScope,
            fileOperationUseCase = activity.playerHostFactory.fileOperation
        )
        activity.cropDelegate = com.sza.fastmediasorter.ui.player.helpers.PlayerCropDelegate(
            host = activity,
            imageCropManager = activity.imageCropManager,
        )
        // S0107: Draw overlay manager; S0162: pass rotation manager for ADR-4 exit restore
        // S0192 Phase 05: add Keep export dependency + base-bitmap provider
        activity.imageDrawOverlayManager = com.sza.fastmediasorter.ui.player.helpers.ImageDrawOverlayManager(
            activity = activity,
            imageContainer = activity.activityBinding.photoDualSurfaceContainer!!,
            screenRotationManager = activity.screenRotationManager,
            hasAccelerometer = activity.hasAccelerometer,
            keepExportHelper = activity.drawKeepExportHelper
        )
        activity.imageDrawOverlayManager.baseBitmapProvider = { activity.viewModel.currentDisplayedBitmap }
        activity.imageDrawOverlayManager.bindToolbar(activity.activityBinding.drawOverlayToolbarStub.root)
        activity.playerDrawingSaveHelper = com.sza.fastmediasorter.ui.player.helpers.PlayerDrawingSaveHelper(
            activity,
            activity.imageEditFactory.mergeDrawOverlay,
        )
        activity.setupDrawOverlaySaveCallback()
        activity.setupDrawOverlayActionCallbacks()
        activity.setupDrawOverlayInPlaceSaveCallback()
        activity.playerDrawingSaveHelper.setupDrawCropCallback()
        activity.setupEditModeCallbacks()
        activity.eventHandler = PlayerEventHandler(activity = activity)
        activity.textEditorCalculatorBridge = com.sza.fastmediasorter.ui.player.helpers.TextEditorCalculatorBridge(
            context = activity,
            launcher = activity.textEditorCalculatorLauncher,
            textViewerManagerProvider = { activity._textViewerManager },
        )
    }

    /** Binding-bound half: coordinators that only capture the binding and register nothing standing. */
    private fun initBindingBoundCore() {
        activity.uiStateCoordinator = com.sza.fastmediasorter.ui.player.helpers.PlayerUiStateCoordinator(
            binding = activity.activityBinding,
            settingsRepository = activity.playerHostFactory.settingsRepository,
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

        activity.immersiveModeManager = com.sza.fastmediasorter.ui.player.helpers.PlayerImmersiveModeManager(
            activity = activity,
            safeViews = com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews(activity.activityBinding)
        )
    }

    private fun initDialogHelper() {
        activity.dialogHelper = PlayerDialogHelper(
            activity = activity,
            viewModel = activity.viewModel,
            settingsRepository = activity.playerHostFactory.settingsRepository,
            smbClient = activity.smbClientLazy,
            sftpClient = activity.sftpClientLazy,
            ftpClient = activity.ftpClientLazy,
            credentialsRepository = activity.playerHostFactory.credentialsRepository,
            unifiedCache = activity.unifiedCacheLazy,
            rotateImageUseCase = activity.imageEditFactory.rotateImage,
            flipImageUseCase = activity.imageEditFactory.flipImage,
            networkImageEditUseCase = activity.imageEditFactory.networkImageEdit,
            applyImageFilterUseCase = activity.imageEditFactory.applyImageFilter,
            adjustImageUseCase = activity.imageEditFactory.adjustImage,
            extractGifFramesUseCase = activity.imageEditFactory.extractGifFrames,
            saveGifFirstFrameUseCase = activity.imageEditFactory.saveGifFirstFrame,
            changeGifSpeedUseCase = activity.imageEditFactory.changeGifSpeed,
            downloadNetworkFileUseCase = activity.playerHostFactory.downloadNetworkFile,
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
                override fun onBeforeRenameDialog(oldPath: String) {
                    if (oldPath != activity.viewModel.state.value.currentFile?.path) return
                    activity.stopVideoPlayback()
                    activity.viewModel.state.value.resource?.let { resource ->
                        MediaFilesCacheManager.removeFile(resource.id, oldPath)
                    }
                }

                override fun onRenameRequested(oldPath: String, newName: String) {
                    if (oldPath != activity.viewModel.state.value.currentFile?.path) return

                    val currentFile = activity.viewModel.state.value.currentFile ?: return
                    val currentResource = activity.viewModel.state.value.resource
                    val operation = PlayerFileOperation.rename(currentFile, currentResource, newName)
                    val newPath = buildRenamedPath(oldPath, newName)

                    val found = activity.viewModel.updateRenamedFilePath(oldPath, newPath)
                    if (!found) {
                        activity.viewModel.reloadAfterRename()
                    }

                    activity.playerFileOperationQueue.enqueue(operation)
                }

                override fun onRenameComplete(oldPath: String, newPath: String) {
                    val found = activity.viewModel.updateRenamedFilePath(oldPath, newPath)
                    if (!found) {
                        activity.viewModel.reloadAfterRename()
                    }
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
        PlayerFileOpsInitializer(
            activity = activity,
            recordQueuedOperationMutation = ::recordQueuedOperationMutation,
            buildRenamedPath = ::buildRenamedPath,
        ).install()
    }

    /** Screen-level half: the panel instance survives a re-inflate (bindCastManager's collector). */
    private fun initCommandPanel() {
        val bigButtonsMode = PlayerLayoutModePrefs.isBigButtonsMode(activity)
        activity.commandPanelController = CommandPanelController(
            binding = activity.activityBinding,
            settingsRepository = activity.playerHostFactory.settingsRepository,
            coroutineScope = activity.lifecycleScope,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerCommandPanelCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel
            ),
            mediaCapabilities = activity.mediaCapabilities,
            bigButtonsMode = bigButtonsMode,
            allowVrLaunch = { activity.playerVrLaunchManager?.isOverflowEntryVisible() == true },
        )
        activity.commandPanelController.updateOrientation(activity.resources.configuration)
    }

    /** Screen-level half: keeps its playback/visualization state and lifecycle registration. */
    private fun initAudioEmptyState() {
        activity.audioEmptyStateController = AudioEmptyStateController(
            context = activity,
            audioCoverArtView = activity.activityBinding.audioCoverArtView,
            barsView = activity.activityBinding.audioBarsView,
            videoView = activity.activityBinding.audioVideoView,
            wavesView = activity.activityBinding.audioWaveParticleView,
            deliveredSource = activity.deliveredAudioVisualizationSource,
            lifecycle = activity.lifecycle
        )
    }

    /** Binding-bound half: image loading registers nothing standing at construction. */
    private fun initImageLoading() {
        activity.imageLoadingManager = ImageLoadingManager(
            binding = activity.activityBinding,
            settingsRepository = activity.playerHostFactory.settingsRepository,
            searchAudioCoverUseCase = activity.playerHostFactory.searchAudioCover,
            audioMetadataCacheRepository = activity.playerHostFactory.audioMetadataCacheRepository,
            okHttpClient = activity.okHttpClient,
            lifecycleScope = activity.lifecycleScope,
            loadingIndicatorCoordinator = activity.loadingIndicatorCoordinator,
            panelStereoSingleEyeNotifier = activity.panelStereoSingleEyeNotifier,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerImageLoadingCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel,
                statsSink = activity.statsSink
            )
        )

        activity.imageLoadingManager.setAudioEmptyStateController(activity.audioEmptyStateController!!)
    }

    private fun initNetworkAndTranslation() {
        activity.networkFileManager = NetworkFileManager(
            context = activity,
            smbClient = activity.smbClientLazy,
            sftpClient = activity.sftpClientLazy,
            ftpClient = activity.ftpClientLazy,
            googleDriveClient = activity.googleDriveClientLazy,
            dropboxClient = activity.dropboxClientLazy,
            oneDriveClient = activity.oneDriveClientLazy,
            credentialsRepository = activity.playerHostFactory.credentialsRepository,
            smbFileOperationHandler = activity.smbFileOperationHandlerLazy,
            sftpFileOperationHandler = activity.sftpFileOperationHandlerLazy,
            ftpFileOperationHandler = activity.ftpFileOperationHandlerLazy,
            cloudFileOperationHandler = activity.cloudFileOperationHandlerLazy,
            unifiedCache = activity.unifiedCacheLazy,
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
            settingsRepository = activity.playerHostFactory.settingsRepository,
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
                        MaterialAlertDialogBuilder(activity)
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
                            .showBoundTo(activity)
                    }
                }
            }
        )
    }

    private fun initScreenLevelControlsAndSettings() {
        // OPTIMIZATION: Document Viewers and VideoPlayerManager use lazy initialization
        activity.lyricsManager = LyricsManager(
            context = activity,
            binding = activity.activityBinding,
            lifecycleScope = activity.lifecycleScope,
            settingsRepository = activity.playerHostFactory.settingsRepository,
            searchLyricsUseCase = activity.playerHostFactory.searchLyrics,
            getTranslationSessionSettings = { activity.translationSessionSettings }
        )

        activity.translationButtonManager = TranslationButtonManager(
            context = activity,
            lifecycleOwner = activity,
            binding = activity.activityBinding,
            settingsRepository = activity.playerHostFactory.settingsRepository,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerTranslationButtonCallbackImpl(
                activity = activity,
                viewModel = activity.viewModel
            )
        )

        // OPTIMIZATION: VideoPlayerManager uses lazy initialization (see PlayerViewerFactory)
        activity.playerSettingsManager = PlayerSettingsManager(
            activity = activity,
            videoPlayerManagerProvider = { activity.videoPlayerManager },
            settingsRepository = activity.playerHostFactory.settingsRepository,
        )

        // Observe stereoMode StateFlow and apply GL effects to the player whenever it changes.
        // Runs on Main dispatcher (lifecycleScope default) - safe for ExoPlayer.setVideoEffects().
        // S0895: repeatOnLifecycle(STARTED) - was a bare collect that kept applying GL effects
        // while the Activity was stopped (baselined in the unsafe-collect neuroslop gate).
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activity.viewModel.stereoMode
                    .filter { it != StereoMode.AUTO }
                    .collect { mode ->
                        activity.videoPlayerManager.applyStereoEffect(mode)
                    }
            }
        }

        // Observe stereoMode changes for image stereo crop (3D tab mode switch while viewing an image).
        // When the user changes the mode via the dialog, re-render the current image with the new crop.
        // S0895: repeatOnLifecycle(STARTED) - was a bare collect that kept re-rendering the image
        // while the Activity was stopped.
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
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
        }

        // Observe panelStereoSingleEye flag - toggle stereo crop on the currently displayed image
        // without a fresh navigation (spec_panel-stereo-single-eye §3.1.1).
        // S0895: repeatOnLifecycle(STARTED) - same unsafe-collect fix as the two collectors above.
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activity.playerHostFactory.settingsRepository.getSettings()
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
        }
    }

    /** Binding-bound half: control/gesture/search helpers that only capture the binding. */
    private fun initBindingBoundControlsAndOcr() {
        activity.playerGestureCallback = com.sza.fastmediasorter.ui.player.callbacks.PlayerGestureCallbackImpl(
            activity = activity,
            viewModel = activity.viewModel,
            binding = activity.activityBinding,
            pdfViewerManagerProvider = { activity.pdfViewerManager },
            epubViewerManagerProvider = { activity.epubViewerManager }
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

        activity.exoPlayerControlsManager = ExoPlayerControlsManager(
            binding = activity.activityBinding,
            videoPlayerManager = activity.videoPlayerManager,
            callback = object : ExoPlayerControlsManager.ExoPlayerControlsCallback {
                override fun onPreviousFile() = activity.navigationManager.navigatePreviousFromControl()
                override fun onNextFile() = activity.navigationManager.navigateNextFromControl()
                override fun onPlaybackOrderClicked() = activity.onPlaybackOrderClicked()
                override fun getPlaybackOrderMode() = activity.viewModel.state.value.playbackOrderMode
                override fun showPlaybackControlDialog() = activity.dialogHelper.showPlaybackControlDialog()
                override fun onSeekForward(seconds: Int) {
                    if (activity.isAudioServiceActive) {
                        val p = activity.audioServiceController?.player ?: return
                        p.seekTo((p.currentPosition + seconds * 1000L).coerceAtMost(
                            p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                        ))
                    } else {
                        activity.videoPlayerManager.seekForward(seconds)
                    }
                }
                override fun onSeekBackward(seconds: Int) {
                    if (activity.isAudioServiceActive) {
                        val p = activity.audioServiceController?.player ?: return
                        p.seekTo((p.currentPosition - seconds * 1000L).coerceAtLeast(0))
                    } else {
                        activity.videoPlayerManager.seekBackward(seconds)
                    }
                }
                override fun isLiveVideoStream() = activity.viewModel.state.value.isLiveVideoStream
                // S1114: delegate the transport-row VR entry to the existing VR launch manager.
                override fun onVrLaunchClicked() {
                    activity.playerVrLaunchManager?.launchFromControlsRow()
                }
                override fun isVrEntryAvailable(): Boolean =
                    activity.playerVrLaunchManager?.isOverflowEntryVisible() == true
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
            settingsRepository = activity.playerHostFactory.settingsRepository,
            translationManager = activity.translationManager,
            textViewerManagerProvider = { activity.textViewerManager },
            loadingIndicatorCoordinator = activity.loadingIndicatorCoordinator,
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
        // S0403: Cast is behind the CastController seam. The per-flavor CastControllerFactory
        // (castEnabled = GMS impl, castDisabled = no-op) builds it from the Activity-scoped args.
        activity.castMediaManager = activity.castControllerFactory.create(
            lifecycleScope = activity.lifecycleScope,
            networkFileManager = activity.networkFileManager,
            onCastStateChanged = { isCasting, deviceName ->
                activity.viewModel.updateCastState(isCasting, deviceName)
                if (isCasting) {
                    val currentFile = activity.viewModel.state.value.currentFile
                    // S1558: backing field, not the lazy getter - see PlayerActivity.castCurrentMedia.
                    if (currentFile != null) {
                        activity.castMediaManager.sendCurrentMedia(
                            currentFile,
                            activity._videoPlayerManager?.currentPanelStereoCrop,
                        )
                    }
                }
            }
        )
        activity.castMediaManager.init()
        activity.commandPanelController.bindCastManager(activity.castMediaManager)

        activity.audioServiceController = AudioServiceController(activity)
        activity.nowPlayingManager = NowPlayingManager(
            activityBinding = activity.activityBinding,
            fragmentManager = activity.supportFragmentManager,
            audioServiceController = activity.audioServiceController!!,
            persistentAudioCompiledIn = activity.capabilityAvailability.isPersistentAudioPlaybackAvailable(),
            faviconAtlasStore = activity.faviconAtlasStore,
            scope = activity.lifecycleScope
        )
    }

    /**
     * S1817: constructed HERE, in the binding-bound half, and no longer in [initAudioAndMediaServices]
     * where it used to sit. The constructor reads `imageLoadingManager` and `exoPlayerControlsManager`,
     * both created earlier in THIS half, so a screen-level construction read the first of them before
     * its only assignment and killed every PlayerActivity open with an
     * UninitializedPropertyAccessException.
     *
     * Still once per screen, despite living in the re-runnable half: this manager owns the audio
     * service connection and the prefetch jobs, and it carries the S1549 [PlayerMediaLoaderManager.rebind]
     * seam precisely so a re-inflate re-points it at the new views instead of re-creating it. The guard
     * is what keeps both halves of that contract true, since a re-inflate re-runs this function.
     */
    private fun initMediaLoaderOnce() {
        if (activity.isMediaLoaderManagerInitialized) return
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
            mediaFilesCacheManager = activity.mediaFilesCacheManager,
            audioServiceController = activity.audioServiceController,
            onAudioServicePlaybackChanged = { isPlaying ->
                val isAudioFile = activity.viewModel.state.value.currentFile?.type == MediaType.AUDIO
                val servicePlayWhenReady = activity.audioServiceController?.player?.playWhenReady
                if (isAudioFile && servicePlayWhenReady != null) {
                    // Persistent audio is driven by MediaController state, not the local ExoPlayer path. Sync ViewModel pause from playWhenReady so pause/resume UI reactions (including filename overlay re-show) also work for service-backed audio.
                    activity.viewModel.setPaused(!servicePlayWhenReady)
                }
                activity.sleepTimerManager?.updateVinylState(isPlaying, isAudioFile)
                if (isAudioFile) {
                    activity.audioEmptyStateController?.onIsPlayingChanged(isPlaying)
                }
            },
            onAudioServiceReady = {
                activity.slideshowResourceAvailabilityManager.onPlaybackReady()
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
                if (activity.viewModel.state.value.isSlideShowActive &&
                    activity.slideshowResourceAvailabilityManager.handlePlaybackEnded()) {
                    return@PlayerMediaLoaderManager
                }
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
            onAudioServicePlaybackError = { error ->
                if (!activity.slideshowResourceAvailabilityManager.handlePlaybackError(error)) {
                    activity.handleMediaLoadErrorAndSkip()
                }
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
            ),
            playbackPositionRepository = activity.playerHostFactory.playbackPositionRepository,
            // S0213 Pillar A: cooldown gate at playVideo entry - short-circuits decoder-error replays.
            decoderFailureTracker = activity.recentDecoderFailureTracker,
            // S0391: source-availability gate for the Favorites mixed-source playback path.
            remoteSourceGate = activity.remoteSourceGate,
        )
    }

    /** Binding-bound half: badge/PiP helpers that only capture views and register nothing standing. */
    private fun initBindingBoundMediaServices() {
        activity.sleepTimerManager = SleepTimerManager(
            vinylView = activity.activityBinding.vinylIndicator,
            sleepTimerBadge = activity.activityBinding.sleepTimerBadge,
            playerProvider = { activity.videoPlayerManager.getPlayer() }
        )
        activity.pipManager = PictureInPictureManager(
            activity = activity,
            playerView = activity.activityBinding.playerView,
            chromeToHide = listOf(activity.activityBinding.toolbar, activity.activityBinding.topCommandPanel),
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
    }

    /** Screen-level half: no views held; `bind()` registers standing collectors. */
    private fun initScreenLevelUiCoordinators() {
        activity.audioMetadataManager = PlayerAudioMetadataManager(activity)

        activity.playerVrLaunchManager = PlayerVrLaunchManager(
            activity = activity,
            viewModel = activity.viewModel,
            settingsRepository = activity.playerHostFactory.settingsRepository,
            detectionFacade = activity.xrDetectionFacade,
            startVrPlaybackUseCase = activity.playerHostFactory.startVrPlayback,
            payloadHolder = activity.vrLaunchPayloadHolder,
        ).also { it.bind() }
    }

    private fun initUiCoordinators() {
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
            audioBackgroundPhotosManagerProvider = {
                ensureAudioBackgroundManagersConfigured()
                activity.audioBackgroundPhotosManager
            },
            backgroundMusicManagerProvider = {
                ensureAudioBackgroundManagersConfigured()
                activity.backgroundMusicManager
            },
            dialogAndUiStateManager = activity.dialogAndUiStateManager,
            settingsRepository = activity.playerHostFactory.settingsRepository,
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

        // Wire FilenameOverlayAutoHideManager - controls auto-hide timing for tvFileNameOverlay. Use actual command-panel visibility rather than raw showCommandPanel state, because audio can force the panel visible while the ViewModel flag stays false.
        activity.dialogAndUiStateManager.filenameOverlayManager = FilenameOverlayAutoHideManager(
            overlayView = activity.activityBinding.tvFileNameOverlay,
            isFullscreen = { !activity.activityBinding.topCommandPanel.isVisible },
            companionViews = {
                if (!activity.activityBinding.topCommandPanel.isVisible) {
                    activity.playerVrLaunchManager?.overlayViewsForAutoHide().orEmpty()
                } else {
                    emptyList()
                }
            },
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
            blackScreenOverlayManager = activity.blackScreenOverlayManager,
            bigButtonsMode = PlayerLayoutModePrefs.isBigButtonsMode(activity)
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
