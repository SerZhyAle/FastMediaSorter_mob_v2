package com.sza.fastmediasorter.ui.player

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.input.GamepadInputManager
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.GamepadAction
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.PlayerDialogAndUiStateManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import com.sza.fastmediasorter.ui.player.helpers.PlayerFpsMeter
import com.sza.fastmediasorter.ui.player.helpers.PlayerNavigationManager
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
import com.sza.fastmediasorter.ui.player.commands.FullscreenCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SaveFrameCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SystemUiCommandOverride
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import com.sza.fastmediasorter.ui.player.contracts.PlayerHostCapabilities
import com.sza.fastmediasorter.ui.player.contracts.VideoPlayerHandle
import com.sza.fastmediasorter.ui.player.entry.VrTaskTransition
import com.sza.fastmediasorter.utils.UserActionLogger
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import java.util.Optional
import javax.inject.Inject

@AndroidEntryPoint
// Open: VrPlayerActivity in vr flavor extends this to add OpenXR rendering layer
open class PlayerActivity : BaseActivity<ActivityPlayerUnifiedBinding>(), PlayerHostCapabilities {
    override fun getViewBinding(): ActivityPlayerUnifiedBinding {
        return ActivityPlayerUnifiedBinding.inflate(layoutInflater)
    }

    internal val viewModel: PlayerViewModel by viewModels()

    /** Exposes the protected ViewBinding to internal callback classes in this module. */
    internal val activityBinding: ActivityPlayerUnifiedBinding get() = binding

    // Helper controllers
    internal lateinit var slideshowController: SlideshowController
    internal lateinit var gestureHelper: PlayerGestureHelper
    internal lateinit var dialogHelper: PlayerDialogHelper
    // LAZY INITIALIZATION: Video player only created when VIDEO file opened
    internal var _videoPlayerManager: VideoPlayerManager? = null
    internal val videoPlayerManager: VideoPlayerManager
        get() {
            if (_videoPlayerManager == null) {
                Timber.d("PERFORMANCE: Lazy initializing VideoPlayerManager")
                _videoPlayerManager = PlayerViewerFactory(this).createVideoPlayerManager()
            }
            return _videoPlayerManager!!
        }

    // S0021: lazy-initialised FPS meter for the flat 2D player overlay.
    internal val playerFpsMeter: PlayerFpsMeter = PlayerFpsMeter()
    private var playerFpsCollectorStarted: Boolean = false

    internal lateinit var fileOperationsHandler: FileOperationsHandler
    internal lateinit var playerFolderPickerHandler: com.sza.fastmediasorter.ui.player.helpers.PlayerFolderPickerHandler
    internal lateinit var destinationButtonsManager: DestinationButtonsManager
    internal lateinit var navigationManager: PlayerNavigationManager
    internal lateinit var commandPanelController: CommandPanelController
    internal lateinit var imageLoadingManager: ImageLoadingManager
    // Session-scoped notifier for the panel single-eye toast (spec_panel-stereo-single-eye Phase 05).
    internal val panelStereoSingleEyeNotifier =
        com.sza.fastmediasorter.ui.player.helpers.PanelStereoSingleEyeNotifier()
    internal var audioEmptyStateController: com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController? = null
    internal lateinit var mediaLoaderManager: com.sza.fastmediasorter.ui.player.helpers.PlayerMediaLoaderManager
    internal val isMediaLoaderManagerInitialized: Boolean
        get() = ::mediaLoaderManager.isInitialized
    internal var audioServiceController: com.sza.fastmediasorter.ui.player.helpers.AudioServiceController? = null
    internal var nowPlayingManager: com.sza.fastmediasorter.ui.player.helpers.NowPlayingManager? = null
    internal var sleepTimerManager: com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager? = null
    internal var pipManager: com.sza.fastmediasorter.ui.player.helpers.PictureInPictureManager? = null
    internal val safeViews by lazy { PlayerBindingSafeViews(binding) }
    internal lateinit var dialogAndUiStateManager: PlayerDialogAndUiStateManager

    internal lateinit var audioSlideshowPhotoModeManager: com.sza.fastmediasorter.ui.player.helpers.AudioSlideshowPhotoModeManager
    internal lateinit var keyboardHandler: com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler
    internal lateinit var networkFileManager: com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
    internal lateinit var observerManager: PlayerObserverManager

    // LAZY INITIALIZATION: Document viewers only created when needed
    internal var _pdfViewerManager: com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager? = null
    internal val pdfViewerManager: com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager
        get() {
            if (_pdfViewerManager == null) {
                Timber.d("PERFORMANCE: Lazy initializing PdfViewerManager")
                _pdfViewerManager = PlayerViewerFactory(this).createPdfViewerManager()
            }
            return _pdfViewerManager!!
        }

    /**
     * Check if PdfViewerManager is initialized and PDF is active.
     * Used by gesture handlers to route gestures without triggering lazy initialization.
     */
    internal fun isPdfActive(): Boolean = _pdfViewerManager?.isPdfActive() == true

    internal var _epubViewerManager: com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager? = null
    internal val epubViewerManager: com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager
        get() {
            if (_epubViewerManager == null) {
                Timber.d("PERFORMANCE: Lazy initializing EpubViewerManager")
                _epubViewerManager = PlayerViewerFactory(this).createEpubViewerManager()
            }
            return _epubViewerManager!!
        }

    internal var _textViewerManager: com.sza.fastmediasorter.ui.player.helpers.TextViewerManager? = null
    internal val textViewerManager: com.sza.fastmediasorter.ui.player.helpers.TextViewerManager
        get() {
            if (_textViewerManager == null) {
                Timber.d("PERFORMANCE: Lazy initializing TextViewerManager")
                _textViewerManager = PlayerViewerFactory(this).createTextViewerManager()
            }
            return _textViewerManager!!
        }
    internal lateinit var uiStateCoordinator: com.sza.fastmediasorter.ui.player.helpers.PlayerUiStateCoordinator
    internal lateinit var undoOperationManager: com.sza.fastmediasorter.ui.player.helpers.UndoOperationManager
    internal lateinit var playerSettingsManager: com.sza.fastmediasorter.ui.player.helpers.PlayerSettingsManager
    internal lateinit var cloudAuthManager: com.sza.fastmediasorter.ui.browse.managers.BrowseCloudAuthManager
    internal lateinit var translationManager: com.sza.fastmediasorter.ui.player.helpers.TranslationManager
    internal lateinit var touchZoneGestureManager: com.sza.fastmediasorter.ui.player.helpers.TouchZoneGestureManager
    internal lateinit var translationButtonManager: com.sza.fastmediasorter.ui.player.helpers.TranslationButtonManager
    internal val isTranslationButtonManagerInitialized: Boolean
        get() = ::translationButtonManager.isInitialized
    internal lateinit var exoPlayerControlsManager: com.sza.fastmediasorter.ui.player.helpers.ExoPlayerControlsManager
    internal lateinit var searchControlsManager: com.sza.fastmediasorter.ui.player.helpers.SearchControlsManager
    internal lateinit var lifecycleManager: com.sza.fastmediasorter.ui.player.helpers.PlayerLifecycleManager
    internal lateinit var controlsSetupManager: com.sza.fastmediasorter.ui.player.helpers.PlayerControlsSetupManager
    internal lateinit var gestureSetupManager: com.sza.fastmediasorter.ui.player.helpers.PlayerGestureSetupManager
    internal lateinit var imageOcrManager: com.sza.fastmediasorter.ui.player.helpers.ImageOcrManager
    internal lateinit var lyricsManager: com.sza.fastmediasorter.ui.player.helpers.LyricsManager
    internal lateinit var googleLensButtonsManager: com.sza.fastmediasorter.ui.player.helpers.GoogleLensButtonsManager
    internal lateinit var systemBarsManager: com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager
    internal lateinit var imageTranslationManager: com.sza.fastmediasorter.ui.player.helpers.PlayerImageTranslationManager
    internal lateinit var shareManager: com.sza.fastmediasorter.ui.player.helpers.PlayerShareManager
    internal lateinit var printManager: com.sza.fastmediasorter.ui.player.helpers.DocumentPrintManager
    internal lateinit var eventHandler: com.sza.fastmediasorter.ui.player.helpers.PlayerEventHandler
    internal lateinit var castMediaManager: com.sza.fastmediasorter.ui.player.helpers.CastMediaManager
    internal lateinit var saveVideoFrameManager: com.sza.fastmediasorter.ui.player.helpers.SaveVideoFrameManager
    internal lateinit var touchZoneSetupManager: com.sza.fastmediasorter.ui.player.helpers.PlayerTouchZoneSetupManager
    internal lateinit var audioMetadataManager: com.sza.fastmediasorter.ui.player.helpers.PlayerAudioMetadataManager
    internal lateinit var playerPrefetchManager: com.sza.fastmediasorter.ui.player.helpers.PlayerPrefetchManager
    internal lateinit var blackScreenOverlayManager: com.sza.fastmediasorter.ui.player.helpers.BlackScreenOverlayManager

    internal val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (::cloudAuthManager.isInitialized) {
            cloudAuthManager.handleGoogleSignInResult(result.data)
        }
    }

    // Android 11+ batch delete permission (createDeleteRequest)
    internal val batchDeletePermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        lifecycleManager.handleBatchDeleteResult(
            result.resultCode,
            viewModel.state.value.currentFile?.path
        )
    }

    internal val folderPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (::playerFolderPickerHandler.isInitialized) playerFolderPickerHandler.onFolderPicked(uri)
    }

    // Android 10 single-file delete permission (RecoverableSecurityException)
    internal val deletePermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        lifecycleManager.handleDeletePermissionResult(result.resultCode)
    }

    internal val hideControlsHandler = Handler(Looper.getMainLooper())
    internal val loadingIndicatorHandler = Handler(Looper.getMainLooper())

    private lateinit var gestureDetector: GestureDetector
    internal val touchZoneDetector = TouchZoneDetector()
    internal var useTouchZones = true // Use touch zones for images, gestures for video
    internal var loadFullSizeImages = false // Load full-size images with PhotoView (3-zone mode)
    internal val shownHintTypes = mutableSetOf<TouchZoneHintType>() // Track per-type hints shown in this session
    internal var slideshowModeRequested = false // Auto-start slideshow when files are loaded
    internal var isExplicitFullscreenMode = false // User requested fullscreen via button

    // Retry logic for network stream errors
    private var playbackRetryCount = 0
    private val maxPlaybackRetries = 3
    private var lastPlaybackPosition = 0L
    internal val retryHandler = Handler(Looper.getMainLooper())
    internal var retryRunnable: Runnable? = null

    // Track current file path to avoid reloading when only metadata changes (e.g., isFavorite)
    internal var currentFilePath: String? = null

    // Current settings cached for overlay visibility
    internal var currentSettings: AppSettings? = null

    // Session-scoped translation settings (reset when exiting Browse/Resource)
    internal var translationSessionSettings = com.sza.fastmediasorter.domain.models.TranslationSessionSettings()

    // Gesture callback for custom PhotoView zoom handling
    internal lateinit var playerGestureCallback: com.sza.fastmediasorter.ui.player.callbacks.PlayerGestureCallbackImpl

    // Injected dependencies for network playback
    @Inject
    lateinit var smbClient: SmbClient

    @Inject
    lateinit var sftpClient: SftpClient

    @Inject
    lateinit var ftpClient: FtpClient

    @Inject
    lateinit var googleDriveClient: GoogleDriveRestClient

    @Inject
    lateinit var dropboxClient: com.sza.fastmediasorter.data.cloud.DropboxClient

    @Inject
    lateinit var oneDriveClient: com.sza.fastmediasorter.data.cloud.OneDriveRestClient

    @Inject
    internal lateinit var fullscreenCommandOverride: Optional<FullscreenCommandOverride>

    @Inject
    internal lateinit var saveFrameCommandOverride: Optional<SaveFrameCommandOverride>

    @Inject
    internal lateinit var systemUiCommandOverride: Optional<SystemUiCommandOverride>

    @Inject
    lateinit var credentialsRepository: NetworkCredentialsRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var playbackPositionRepository: com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository

    @Inject
    lateinit var rotateImageUseCase: com.sza.fastmediasorter.domain.usecase.RotateImageUseCase

    @Inject
    lateinit var searchAudioCoverUseCase: com.sza.fastmediasorter.domain.usecase.SearchAudioCoverUseCase

    @Inject
    lateinit var flipImageUseCase: com.sza.fastmediasorter.domain.usecase.FlipImageUseCase

    @Inject
    lateinit var networkImageEditUseCase: com.sza.fastmediasorter.domain.usecase.NetworkImageEditUseCase

    @Inject
    lateinit var applyImageFilterUseCase: com.sza.fastmediasorter.domain.usecase.ApplyImageFilterUseCase

    @Inject
    lateinit var adjustImageUseCase: com.sza.fastmediasorter.domain.usecase.AdjustImageUseCase

    @Inject
    lateinit var extractGifFramesUseCase: com.sza.fastmediasorter.domain.usecase.ExtractGifFramesUseCase

    @Inject
    lateinit var saveGifFirstFrameUseCase: com.sza.fastmediasorter.domain.usecase.SaveGifFirstFrameUseCase

    @Inject
    lateinit var changeGifSpeedUseCase: com.sza.fastmediasorter.domain.usecase.ChangeGifSpeedUseCase

    @Inject
    lateinit var downloadNetworkFileUseCase: com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase

    @Inject
    lateinit var searchLyricsUseCase: com.sza.fastmediasorter.domain.usecase.SearchLyricsUseCase

    @Inject
    lateinit var unifiedCache: com.sza.fastmediasorter.core.cache.UnifiedFileCache

    @Inject
    lateinit var mediaFilesCacheManager: MediaFilesCacheManager

    @Inject
    lateinit var gamepadInputManager: GamepadInputManager

    @Inject
    lateinit var smbFileOperationHandler: com.sza.fastmediasorter.data.network.SmbFileOperationHandler

    @Inject
    lateinit var sftpFileOperationHandler: com.sza.fastmediasorter.data.network.SftpFileOperationHandler

    @Inject
    lateinit var ftpFileOperationHandler: com.sza.fastmediasorter.data.network.FtpFileOperationHandler

    @Inject
    lateinit var cloudFileOperationHandler: com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler

    @Inject
    lateinit var fileOperationUseCase: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase

    @Inject
    @com.sza.fastmediasorter.core.di.ApplicationScope
    lateinit var fileOpsAppScope: kotlinx.coroutines.CoroutineScope

    @Inject
    lateinit var backgroundMusicManager: com.sza.fastmediasorter.ui.player.helpers.BackgroundMusicManager

    @Inject
    lateinit var audioBackgroundPhotosManager: com.sza.fastmediasorter.ui.player.helpers.AudioBackgroundPhotosManager

    @Inject
    lateinit var audioMetadataCacheRepository: com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository

    @Inject
    lateinit var okHttpClient: okhttp3.OkHttpClient

    @Inject
    lateinit var keyBindingManager: com.sza.fastmediasorter.core.input.KeyBindingManager

    internal val hideControlsRunnable = Runnable {
        if (!isDestroyed && !isFinishing && !viewModel.state.value.isPaused) viewModel.toggleControls()
    }
    internal val showLoadingIndicatorRunnable = Runnable {
        if (!isDestroyed && !isFinishing) binding.progressBar.isVisible = true
    }
    private lateinit var imageTouchGestureDetector: GestureDetector

    // S0028: per-window resume state isolation
    internal lateinit var windowId: String

    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the LargePlayerControls theme overlay (when "Compact elements" is OFF)
        // BEFORE super.onCreate → setContentView. DefaultTimeBar reads bar/touch/scrubber
        // sizes from XML once at inflate; switching layouts requires app restart.
        com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs.applyControlsThemeOverlay(this)
        // PlayerActivity uses Edge-to-Edge ALWAYS to allow precise manual insets control.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        // S0028: resolve windowId before any use-case call
        windowId = savedInstanceState?.getString(EXTRA_WINDOW_ID)
            ?: intent.getStringExtra(EXTRA_WINDOW_ID)
            ?: ResumeStateRepository.WINDOW_ID_MAIN
        super.onCreate(savedInstanceState)
        lifecycleManager = com.sza.fastmediasorter.ui.player.helpers.PlayerLifecycleManager(
            activity = this,
            viewModel = viewModel,
            lifecycle = lifecycle
        )
        lifecycleManager.onCreate(savedInstanceState)
        slideshowModeRequested = lifecycleManager.isSlideshowModeRequested()
        if (!slideshowModeRequested && viewModel.resumeSlideshowEnabled) {
            slideshowModeRequested = true
            Timber.d("PlayerActivity: Resume slideshow requested via resumeSlideshowEnabled")
        }
        initializeManagers()
        setupToolbar()
        setupControls()
        translationButtonManager.setupTranslationDefaults()
        translationButtonManager.setupTranslationButtonIcons()
        setupGoogleLensButtons()
        setupCommandPanelControls()
        setupTouchZones()
        binding.root.post {
            updateSystemBarsForPlayer(viewModel.state.value.showCommandPanel)
        }
    }

    /** Initialize all helper managers — delegates to PlayerManagerInitializer. */
    private fun initializeManagers() {
        PlayerManagerInitializer(this).initialize()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::windowId.isInitialized) outState.putString(EXTRA_WINDOW_ID, windowId)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        commandPanelController.updateOrientation(newConfig)
        binding.topCommandPanel.post {
            binding.topCommandPanel.requestApplyInsets()
        }
        binding.root.post {
            val currentFile = viewModel.state.value.currentFile
            if (currentFile != null && (currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF)) {
                if (::imageLoadingManager.isInitialized) imageLoadingManager.reEvaluateScaleTypeOnRotation()
                binding.imageView.setImageDrawable(null)
                binding.photoView.setImageDrawable(null)
                lifecycleScope.launch(Dispatchers.Main) { displayImage(currentFile.path) }
            }
        }
    }

    override fun setupViews() {
        setupGestureDetector()
        setupToolbar()
        setupControls()
        setupCommandPanelControls()
        setupTouchZones()
        lifecycleManager.setupBackPressHandler()
    }

    override fun observeData() {
        observerManager = PlayerObserverManager(this, settingsRepository)
        observerManager.startObserving()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state
                    .map { it.currentFile?.type }
                    .distinctUntilChanged()
                    .collect { type ->
                        val isAudioOrVideo = type == MediaType.AUDIO || type == MediaType.VIDEO
                        blackScreenOverlayManager.onFileTypeChanged(isAudioOrVideo)
                    }
            }
        }
    }

    // Open so VrPlayerActivity can route exit through VrTaskTransition instead of a plain finish(),
    // keeping gamepad B / keyboard Escape paths consistent in VR immersive mode.
    internal open fun exitPlayerWithAudioCheck(withTransition: Boolean = false) =
        lifecycleManager.exitPlayerWithAudioCheck(withTransition)

    private fun setupGestureDetector() = gestureSetupManager.setupGestureDetector()

    private fun setupToolbar() = controlsSetupManager.setupToolbar()

    private fun setupControls() {
        controlsSetupManager.setupAllControls()
        controlsSetupManager.setupDocumentFullscreenExitButton()
    }

    private fun setupCommandPanelControls() = commandPanelController.setupCommandPanelControls()

    private fun setupTouchZones() = touchZoneSetupManager.setupLegacyTouchZoneListeners()

    internal fun showTouchZoneHintOverlay(type: TouchZoneHintType) =
        touchZoneSetupManager.showHintOverlay(type)

    fun showTouchZonesHelpOverlay() {
        val hintType = uiStateCoordinator.getCurrentHintType(viewModel.state.value)
            ?: TouchZoneHintType.FULLSCREEN_9ZONE
        touchZoneSetupManager.showHintOverlay(hintType)
    }

    internal fun adjustTouchZonesForVideo(isVideo: Boolean) =
        mediaLoaderManager.adjustTouchZonesForVideo(isVideo, useTouchZones)

    private fun updateUI(state: PlayerViewModel.PlayerState) = observerManager.updateUI(state)

    internal fun updatePanelVisibility(showCommandPanel: Boolean) {
        dialogAndUiStateManager.updatePanelVisibility(showCommandPanel)
        controlsSetupManager.updateDocumentFullscreenExitButtonVisibility()
        updateSystemBarsForPlayer(showCommandPanel)
    }

    internal fun updateSystemBarsForPlayer(showCommandPanel: Boolean) {
        isExplicitFullscreenMode = systemBarsManager.updateForPlayerState(
            showCommandPanel = showCommandPanel,
            isExplicitFullscreenMode = isExplicitFullscreenMode,
            settings = currentSettings,
            isDocumentFullscreen = _pdfViewerManager?.isInFullscreenMode() == true ||
                _epubViewerManager?.isInFullscreenMode() == true,
            isMediaSlideshow = viewModel.state.value.isPhotoSlideshowActive,
            isAudioSlideshowPhotoMode = audioSlideshowPhotoModeManager.isActive,
            topCommandPanel = binding.topCommandPanel
        )
    }

    /** Let flavor-specific code replace the fullscreen button behavior (e.g., in XR sessions). */
    internal fun tryHandleFullscreenCommandOverride(): Boolean =
        fullscreenCommandOverride.orElse(null)?.execute(this, viewModel) == true

    /** VR binds a dedicated Save Frame override for OpenXR rendering. */
    internal fun tryHandleSaveFrameCommandOverride(): Boolean =
        saveFrameCommandOverride.orElse(null)?.execute(this) == true

    /** VR maps controller input to this override to show/hide the headset overlay. */
    internal fun tryHandleSystemUiCommandOverride(): Boolean =
        systemUiCommandOverride.orElse(null)?.execute(this) == true

    internal fun updateCommandAvailability(state: PlayerViewModel.PlayerState) =
        commandPanelController.updateCommandAvailability(state)

    internal fun displayText(mediaFile: MediaFile) = mediaLoaderManager.displayText(mediaFile)

    internal fun displayImage(path: String) = mediaLoaderManager.displayImage(path)

    internal fun playVideo(path: String) = mediaLoaderManager.playVideo(path)

    internal fun updateSlideShow() = navigationManager.updateSlideshowState()

    internal fun updatePlayPauseButton() {
        val isAnimatedContent = ::mediaLoaderManager.isInitialized && mediaLoaderManager.isCurrentAnimatedContent()
        val isPaused = if (isAnimatedContent) mediaLoaderManager.isAnimatedPlaybackPaused()
                       else viewModel.state.value.isPaused
        binding.btnPlayPause.text = if (isPaused) "▶" else "⏸"
        if (_videoPlayerManager != null) {
            videoPlayerManager.getPlayer()?.playWhenReady = !viewModel.state.value.isPaused
        }
    }

    internal fun isCurrentAnimatedContent(): Boolean =
        ::mediaLoaderManager.isInitialized && mediaLoaderManager.isCurrentAnimatedContent()

    internal fun toggleAnimatedPlayback(): Boolean? {
        if (!::mediaLoaderManager.isInitialized) return null
        return mediaLoaderManager.toggleAnimatedPlayback()
    }

    internal fun clearUiOverlayForAnimatedPause() = dialogAndUiStateManager.clearUiOverlayForAnimatedPause()

    internal fun clearImageMemoryCache() = lifecycleManager.clearImageMemoryCache()

    internal fun setSlideshowKeepAwake(enabled: Boolean) = lifecycleManager.setSlideshowKeepAwake(enabled)

    internal fun adjustVolume(delta: Float) {
        if (_videoPlayerManager == null) return
        val player = videoPlayerManager.getPlayer() ?: return
        val newVolume = (player.volume + delta).coerceIn(0f, 1f)
        player.volume = newVolume
        Toast.makeText(this, getString(R.string.volume_level, (newVolume * 100).toInt()), Toast.LENGTH_SHORT).show()
    }

    internal fun scheduleHideControls() {
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        val isAudioFile = viewModel.state.value.currentFile?.type == MediaType.AUDIO
        if (viewModel.state.value.showControls && !viewModel.state.value.isPaused && !isAudioFile) {
            hideControlsHandler.postDelayed(hideControlsRunnable, VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS)
        }
    }

    private fun updateAudioTouchZonesVisibility() = dialogAndUiStateManager.updateAudioTouchZonesVisibility()

    internal fun showFileInfo() = dialogAndUiStateManager.showFileInfo()

    internal fun isAnimatedImagePath(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".gif") || lowerPath.endsWith(".webp") || lowerPath.endsWith(".apng")
    }

    internal fun searchAndShowLyrics() {
        val currentFile = viewModel.state.value.currentFile
        val metadata = audioMetadataManager.getCachedMetadataFor(currentFile?.path)
        lyricsManager.searchAndShowLyrics(currentFile, metadata?.trackName, metadata?.artistName)
    }

    internal fun searchInYoutubeMusic() = audioMetadataManager.searchInYoutubeMusic()

    internal fun castCurrentMedia() {
        castMediaManager.showCastDialog(this)
        val currentFile = viewModel.state.value.currentFile ?: return
        if (castMediaManager.isCasting) castMediaManager.sendCurrentMedia(currentFile)
    }

    internal fun hideLyricsViewer() = lyricsManager.hideLyricsViewer()

    internal open fun saveCurrentFrame() = saveVideoFrameManager.saveCurrentFrame()

    fun toggleBlackScreenOverlay() {
        if (blackScreenOverlayManager.isVisible) blackScreenOverlayManager.hide()
        else blackScreenOverlayManager.show()
    }

    /**
     * Called when user taps the immersive toggle button. Overridden in VrPlayerActivity.
     *
     * WHY this base implementation exists: in the VR flavor, PlayerActivity may serve as
     * a panel-mode fallback for non-stereo content. If the user taps "3DVR" they want to
     * enter immersive XR mode. We relaunch using VrTaskTransition so the HorizonOS task
     * affinity rules are satisfied (separate VR task). Class is resolved via
     * BuildConfig.PLAYER_ACTIVITY_CLASS to avoid a layering dependency on VrPlayerActivity.
     */
    internal open fun handle3dVrToggleClicked() {
        if (!BuildConfig.SUPPORT_VR_PLAYER) return
        try {
            val vrClass = Class.forName(BuildConfig.PLAYER_ACTIVITY_CLASS)
            val vrIntent = Intent(intent).apply {
                setClass(this@PlayerActivity, vrClass)
                // VrPlayerActivity reads this flag to bypass stereo-detection and force
                // immersive XR route. Key must stay in sync with VrPlayerActivity.EXTRA_FORCE_IMMERSIVE.
                putExtra("com.sza.fastmediasorter.EXTRA_FORCE_IMMERSIVE", true)
            }
            VrTaskTransition.enterImmersive(this, vrIntent)
        } catch (e: ClassNotFoundException) {
            Timber.w("handle3dVrToggleClicked: VrPlayerActivity not found in this build (%s)", e.message)
        }
    }

    /**
     * S0019: explicit «launch immersive on the current file» entry point used by the
     * «Apply and 3D» combo button in [PlaybackControlDialogFragment]. Thin wrapper over
     * [handle3dVrToggleClicked] that adds a Timber.i marker with a caller-supplied reason
     * so the dialog → immersive transition is greppable in logs.
     */
    internal fun launchImmersiveOnCurrentFile(reason: String) {
        Timber.i("PlayerActivity: launchImmersiveOnCurrentFile reason=%s", reason)
        handle3dVrToggleClicked()
    }

    // S0028: tear off current Player to a new window slot; current player finishes (returns to Browse)
    internal fun tearOffPlayer() {
        val filePath = currentFilePath ?: return
        val newWindowId = java.util.UUID.randomUUID().toString()
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("initialFilePath", filePath)
            putExtra(EXTRA_WINDOW_ID, newWindowId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        startActivity(intent)
        finish()
    }

    internal fun handleDeleteSuccess(deletedFilePath: String) = lifecycleManager.handleDeleteSuccess(deletedFilePath)

    private fun handleEvent(event: PlayerViewModel.PlayerEvent) = eventHandler.handleEvent(event)

    internal fun showError(message: String, throwable: Throwable? = null) =
        eventHandler.showError(message, throwable)

    internal fun showFileNotFound(fileName: String) = eventHandler.showFileNotFound(fileName)

    private fun showCloudAuthenticationError(providerName: String? = null) =
        eventHandler.showCloudAuthenticationError(providerName)

    internal fun showUnsupportedFormatError(message: String, filePath: String, isLocalFile: Boolean) =
        eventHandler.showUnsupportedFormatError(message, filePath, isLocalFile)

    internal fun showSlideshowEnabledMessage() {
        val intervalSeconds = viewModel.state.value.slideShowInterval / 1000
        Toast.makeText(this, getString(R.string.slideshow_enabled_message, intervalSeconds), Toast.LENGTH_SHORT).show()
    }

    internal fun populateDestinationButtons() = destinationButtonsManager.populateDestinationButtons()

    private fun performCopyOperation(destination: MediaResource) = fileOperationsHandler.performCopy(destination)

    private fun performMoveOperation(destination: MediaResource) = fileOperationsHandler.performMove(destination)

    private fun showAudioFileInfo(file: MediaFile?) = mediaLoaderManager.showAudioFileInfo(file)

    internal fun updateAudioFormatInfo() = imageLoadingManager.updateAudioFormatInfo()

    internal fun updateTrackButtonsVisibility() {
        exoPlayerControlsManager.updateTrackButtonsVisibility(
            viewModel.state.value.currentFile?.type == MediaType.VIDEO ||
                viewModel.state.value.currentFile?.type == MediaType.AUDIO
        )
    }

    internal fun prefetchNextAudio() {
        if (::mediaLoaderManager.isInitialized) mediaLoaderManager.prefetchNextAudio()
    }

    internal fun handleMediaLoadErrorAndSkip() {
        Toast.makeText(this, getString(R.string.error_loading_media), Toast.LENGTH_SHORT).show()
        navigationManager.navigateNextFromControl()
    }

    internal fun releasePlayer() {
        if (_videoPlayerManager != null) videoPlayerManager.releasePlayer()
    }

    internal fun stopVideoPlayback() = lifecycleManager.stopVideoPlayback()

    override fun onPause() {
        super.onPause()
        if (isInPictureInPictureMode) return
        lifecycleManager.onPause()
        val serviceAudioActiveOnPause = ::mediaLoaderManager.isInitialized && mediaLoaderManager.isServiceAudioActive
        if (serviceAudioActiveOnPause) {
            binding.playerView.player = null
        } else {
            viewModel.togglePause()
        }
        audioEmptyStateController?.onPause()
        lifecycleManager.saveCurrentPlaybackPosition()
        // S0021: stop the FPS meter when the activity loses foreground; the overlay
        // hides via updatePlayerFpsOverlay() once visibility is reconciled.
        playerFpsMeter.stop()
        binding.tvPlayerFpsOverlay.isVisible = false
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pipManager?.onUserLeaveHint(currentSettings?.enablePictureInPicture == true)
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        pipManager?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    internal fun shareCurrentFile() = fileOperationsHandler.performShare()

    internal fun stopTranslation() = imageTranslationManager.stopTranslation()

    internal fun translateCurrentImage() = imageTranslationManager.translateCurrentImage()

    internal fun extractTextFromCurrentImage() =
        imageOcrManager.extractTextFromCurrentImage(viewModel.state.value.currentFile)

    override fun onResumeWithViews() {
        lifecycleManager.onResume()
        audioEmptyStateController?.onResume()
        nowPlayingManager?.onStart(
            viewModel.state.value.currentFile?.type,
            viewModel.state.value.showNowPlayingPanel
        )
        if (::mediaLoaderManager.isInitialized) mediaLoaderManager.reattachServicePlayerToView()
        // S0021: start collecting FPS once per Activity lifetime; reconcile visibility.
        if (!playerFpsCollectorStarted) {
            playerFpsCollectorStarted = true
            lifecycleScope.launch {
                playerFpsMeter.fps.collect { fps ->
                    binding.tvPlayerFpsOverlay.text = "$fps fps"
                }
            }
            lifecycleScope.launch {
                viewModel.settings.collect { updatePlayerFpsOverlay() }
            }
        }
        updatePlayerFpsOverlay()
    }

    /**
     * S0021: reconcile FPS overlay visibility against settings + current file type.
     * Open: only visible when `playerShowFps` is on AND current file is VIDEO.
     */
    internal open fun updatePlayerFpsOverlay() {
        val show = viewModel.settings.value.playerShowFps &&
            viewModel.state.value.currentFile?.type == MediaType.VIDEO
        binding.tvPlayerFpsOverlay.isVisible = show
        if (show) playerFpsMeter.start() else playerFpsMeter.stop()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        UserActionLogger.logKey(keyCode, event?.action ?: KeyEvent.ACTION_DOWN, KeyEvent.keyCodeToString(keyCode), "PlayerActivity")
        if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE)
            && pdfViewerManager.isInFullscreenMode()) {
            pdfViewerManager.exitFullscreenMode()
            return true
        }
        return keyboardHandler.handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (blackScreenOverlayManager.isVisible) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_STOP -> super.dispatchKeyEvent(event)
                else -> {
                    blackScreenOverlayManager.hide()
                    super.dispatchKeyEvent(event)
                }
            }
        }
        // Gamepad buttons are intercepted before onKeyDown to keep input routing in one place.
        // BT keyboard / remote keys fall through to super → onKeyDown → keyboardHandler.
        val action = gamepadInputManager.handleKeyEvent(event, GamepadInputManager.Surface.PLAYER)
        if (action is GamepadAction.PlayerAction && routePlayerGamepadAction(action)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        if (event != null) {
            val action = gamepadInputManager.handleMotionEvent(event, GamepadInputManager.Surface.PLAYER)
            if (action is GamepadAction.PlayerAction && routePlayerGamepadAction(action)) return true
        }
        return keyboardHandler.handlePointerEvent(window.decorView, event) || super.dispatchGenericMotionEvent(event)
    }

    /** Routes a [GamepadAction.PlayerAction] to the same callbacks used by keyboard input. */
    private fun routePlayerGamepadAction(action: GamepadAction.PlayerAction): Boolean {
        val callback = keyboardHandler.callback
        when (action) {
            is GamepadAction.PlayerAction.PlayPause -> viewModel.togglePause()
            is GamepadAction.PlayerAction.Next -> callback.onNextFile()
            is GamepadAction.PlayerAction.Prev -> callback.onPreviousFile()
            is GamepadAction.PlayerAction.Seek -> {
                val seconds = (action.ms / 1000L).toInt()
                if (action.ms >= 0L) callback.onSeekForward(seconds) else callback.onSeekBackward(-seconds)
            }
            is GamepadAction.PlayerAction.Volume -> callback.onChangeVolume(if (action.up) +1 else -1)
            is GamepadAction.PlayerAction.ToggleHud -> callback.onToggleCommandPanel()
            is GamepadAction.PlayerAction.ToggleHints -> callback.onShowHelp()
            is GamepadAction.PlayerAction.Exit -> callback.onExitPlayer()
        }
        return true
    }

    fun advanceAudioBackgroundPhoto() = audioSlideshowPhotoModeManager.advancePhoto()

    internal fun updateAudioSlideshowCurrentSongLabel() = audioSlideshowPhotoModeManager.updateCurrentSongLabel()

    override fun onDestroy() {
        if (::eventHandler.isInitialized) eventHandler.onDestroy()
        lifecycleManager.onDestroy()
        super.onDestroy()
    }

    private fun setupGoogleLensButtons() = googleLensButtonsManager.setupButtons()

    internal fun shareCurrentFileToGoogleLens() = shareManager.shareCurrentFileToGoogleLens()

    internal fun isOverlayBlocking(): Boolean =
        safeViews.translationOverlay.isVisible ||
        binding.translationLensOverlay.isVisible ||
        safeViews.textViewerContainer.isVisible ||
        safeViews.lyricsViewerContainer.isVisible

    internal fun getTouchZonesEnabled(): Boolean = useTouchZones

    internal fun isInAudioSlideshowPhotoMode(): Boolean = audioSlideshowPhotoModeManager.isActive

    /** Called by ImageLoadingManager when audio metadata is loaded from the online source. */
    fun onAudioMetadataLoaded(metadata: com.sza.fastmediasorter.domain.model.AudioMetadata) =
        audioMetadataManager.onMetadataLoaded(metadata, viewModel.state.value.currentFile)

    // ── PlayerHostCapabilities ────────────────────────────────────────────────

    override val supportsListNavigation: Boolean = true
    override val supportsSlideshow: Boolean = true
    override val supportsPersistentAudio: Boolean = true
    override val supportsCast: Boolean = true
    override val supportsDeleteUndo: Boolean = true
    override val supportsCommandPanelFolding: Boolean = true

    override val currentMediaFile: StateFlow<MediaFile?> by lazy {
        viewModel.state.map { it.currentFile }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, viewModel.state.value.currentFile)
    }

    override val currentMediaType: StateFlow<MediaType?> by lazy {
        viewModel.state.map { it.currentFile?.type }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, viewModel.state.value.currentFile?.type)
    }

    override val stereoMode: StateFlow<StereoMode> get() = viewModel.stereoMode
    override val detectedStereoMode: StateFlow<StereoMode> get() = viewModel.detectedStereoMode

    override fun setStereoMode(mode: StereoMode) = viewModel.setStereoMode(mode)
    override fun rememberStereoModeIfEnabled(mode: StereoMode) = viewModel.rememberStereoModeIfEnabled(mode)

    override val videoPlayerHandle: VideoPlayerHandle get() = playerActivityVideoHandle

    private val playerActivityVideoHandle: VideoPlayerHandle by lazy { PlayerActivityVideoHandle() }

    override val isAudioServiceActive: Boolean
        get() = isMediaLoaderManagerInitialized && mediaLoaderManager.isServiceAudioActive

    override fun showMessage(message: String) = viewModel.showMessage(message)

    // Handled internally: PlayerDeleteUndoCoordinator advances the file list or emits FinishActivity.
    override fun requestFinishAfterDelete() = Unit

    private inner class PlayerActivityVideoHandle : VideoPlayerHandle {
        override fun getAvailableAudioTracks(): List<VideoTrackSelectionManager.TrackInfo> =
            _videoPlayerManager?.getAvailableAudioTracks()
                ?.map { VideoTrackSelectionManager.TrackInfo(it.groupIndex, it.trackIndex, it.label, it.isSelected) }
                ?: emptyList()

        override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) =
            _videoPlayerManager?.selectAudioTrack(groupIndex, trackIndex) ?: Unit

        override fun getAvailableSubtitleTracks(): List<VideoTrackSelectionManager.TrackInfo> =
            _videoPlayerManager?.getAvailableSubtitleTracks()
                ?.map { VideoTrackSelectionManager.TrackInfo(it.groupIndex, it.trackIndex, it.label, it.isSelected) }
                ?: emptyList()

        override fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) =
            _videoPlayerManager?.selectSubtitleTrack(groupIndex, trackIndex) ?: Unit

        override fun getHueAdjustmentDegrees(): Float =
            _videoPlayerManager?.getHueAdjustmentDegrees() ?: 0f

        override fun setHueAdjustmentDegrees(degrees: Float) {
            _videoPlayerManager?.setHueAdjustmentDegrees(degrees)
        }

        override fun getBrightnessProgress(): Int =
            _videoPlayerManager?.getBrightnessProgress() ?: 50

        override fun setBrightnessProgress(progress: Int) {
            _videoPlayerManager?.setBrightnessProgress(progress)
        }

        override fun getBrightnessPercentOffset(): Int =
            _videoPlayerManager?.getBrightnessPercentOffset() ?: 0

        override fun getPlaybackSpeed(): Float =
            if (isAudioServiceActive) {
                audioServiceController?.player?.playbackParameters?.speed ?: 1.0f
            } else {
                _videoPlayerManager?.getPlayer()?.playbackParameters?.speed ?: 1.0f
            }

        override fun setPlaybackSpeed(speed: Float) {
            if (isAudioServiceActive) {
                audioServiceController?.player?.setPlaybackSpeed(speed)
            } else {
                _videoPlayerManager?.setPlaybackSpeed(speed)
            }
        }
    }

    companion object {
        private const val VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS = 15000L
        const val EXTRA_MODIFIED_FILES = "modified_files"
        // S0028: per-window resume state isolation
        const val EXTRA_WINDOW_ID = "extra_window_id"

        // S0026: detected stereo mode hint. Browse fills this when launching VR; VrPlayerActivity
        // primes PlayerStereoModeCoordinator with this value before applying user-settings, so the
        // route decision sees the actual file format instead of the default MONO.
        const val EXTRA_DETECTED_STEREO_MODE = "extra_detected_stereo_mode"

        fun createIntent(
            context: Context,
            resourceId: Long,
            initialIndex: Int = 0,
            skipAvailabilityCheck: Boolean = false,
            initialFilePath: String? = null,
            isPlaying: Boolean? = null,
            isSlideshowEnabled: Boolean = false,
            shuffleOnStart: Boolean = false,
            detectedStereoMode: StereoMode? = null,
        ): Intent {
            // PLAYER_ACTIVITY_CLASS is set per-flavor in build.gradle.kts.
            // VR flavor routes to VrPlayerActivity (OpenXR host); all other flavors use PlayerActivity.
            val playerClass: Class<*> = try {
                Class.forName(BuildConfig.PLAYER_ACTIVITY_CLASS)
            } catch (e: ClassNotFoundException) {
                Timber.w("PLAYER_ACTIVITY_CLASS not found: ${BuildConfig.PLAYER_ACTIVITY_CLASS}, falling back to PlayerActivity")
                PlayerActivity::class.java
            }
            return Intent(context, playerClass).apply {
                putExtra("resourceId", resourceId)
                putExtra("initialIndex", initialIndex)
                putExtra("skipAvailabilityCheck", skipAvailabilityCheck)
                initialFilePath?.let { putExtra("initialFilePath", it) }
                isPlaying?.let { putExtra("resumeIsPlaying", it) }
                if (isSlideshowEnabled) putExtra("resumeSlideshowEnabled", true)
                if (shuffleOnStart) putExtra("shuffleOnStart", true)
                detectedStereoMode?.let { putExtra(EXTRA_DETECTED_STEREO_MODE, it.name) }
            }
        }

        /**
         * S0019 / S0038: build an intent that targets the **2D** panel [PlayerActivity]
         * directly, bypassing the per-flavor [BuildConfig.PLAYER_ACTIVITY_CLASS] override.
         * Used by VR-flavor user-driven «exit to flat player» path so the user lands on
         * the actual 2D player rather than a panel-mode VrPlayerActivity that immediately
         * relaunches via STANDARD_PANEL_FALLBACK (the clone-window regression).
         */
        fun createPanelIntent(
            context: Context,
            resourceId: Long,
            initialIndex: Int = 0,
            skipAvailabilityCheck: Boolean = false,
            initialFilePath: String? = null,
            isPlaying: Boolean? = null,
            isSlideshowEnabled: Boolean = false,
            detectedStereoMode: StereoMode? = null,
        ): Intent = Intent(context, PlayerActivity::class.java).apply {
            putExtra("resourceId", resourceId)
            putExtra("initialIndex", initialIndex)
            putExtra("skipAvailabilityCheck", skipAvailabilityCheck)
            initialFilePath?.let { putExtra("initialFilePath", it) }
            isPlaying?.let { putExtra("resumeIsPlaying", it) }
            if (isSlideshowEnabled) putExtra("resumeSlideshowEnabled", true)
            detectedStereoMode?.let { putExtra(EXTRA_DETECTED_STEREO_MODE, it.name) }
        }
    }
}
