package com.sza.fastmediasorter.ui.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Trace
import android.view.ActionMode
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import com.google.android.material.snackbar.Snackbar
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import com.sza.fastmediasorter.core.input.GamepadInputManager
import com.sza.fastmediasorter.core.network.NetworkStateMonitor
import com.sza.fastmediasorter.core.storage.RestrictedTreeTargetPolicy
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.core.ui.SelfManagedScreenOrientation
import com.sza.fastmediasorter.core.xr.StartVrPlaybackUseCase
import com.sza.fastmediasorter.core.xr.XrDetectionFacade
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpEndpointResolver
import com.sza.fastmediasorter.data.repository.streams.FaviconAtlasStore
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationClassifier
import com.sza.fastmediasorter.data.transfer.local.LocalDestinationWriter
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.PlaybackOrderMode
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.ResumeStateRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.commands.FullscreenCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SaveFrameCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SystemUiCommandOverride
import com.sza.fastmediasorter.ui.player.contracts.PlayerActionHost
import com.sza.fastmediasorter.ui.player.contracts.PlayerHostCapabilities
import com.sza.fastmediasorter.ui.player.contracts.VideoPlayerHandle
import com.sza.fastmediasorter.ui.player.helpers.DocumentSelectionActionModeAugmentingCallback
import com.sza.fastmediasorter.ui.player.helpers.DocumentSelectionActionModeCallback
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import com.sza.fastmediasorter.ui.player.helpers.PlayerDialogAndUiStateManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerDisplayMode
import com.sza.fastmediasorter.ui.player.helpers.PlayerFpsMeter
import com.sza.fastmediasorter.ui.player.helpers.PlayerNavigationManager
import com.sza.fastmediasorter.ui.player.helpers.toPlaybackOrderUiState
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Optional
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity :
    BaseActivity<ActivityPlayerUnifiedBinding>(),
    PlayerHostCapabilities,
    PlayerActionHost,
    SelfManagedScreenOrientation,
    com.sza.fastmediasorter.core.share.SharePrintHost,
    com.sza.fastmediasorter.ui.player.helpers.DocumentPrintHost {
    // S0438: a player host keeps the screen on when either the global or the dependent player setting is on.
    override fun keepScreenAwakeFor(settings: AppSettings): Boolean =
        settings.preventSleep || settings.keepScreenOnPlayer

    // S0459 ADR-10: the player is the Print host for the unified «Send to..» menu. DocumentPrintManager
    // owns the async print pipeline and its own capability/error messaging, so this just dispatches.
    override fun printMediaFile(mediaFile: com.sza.fastmediasorter.domain.model.MediaFile): Boolean {
        printManager.printCurrentFile(mediaFile)
        return true
    }

    // S0613: DocumentPrintHost seam - lets DocumentPrintManager run host-agnostically across the
    // in-app player and the standalone document/text players, sharing one print pipeline.
    override val printHostActivity: AppCompatActivity get() = this
    override val printNetworkFileManager get() = networkFileManager
    override fun printOfficeDocument(): Boolean = officeDocumentViewerManager.print()
    override fun showPrintMessage(message: String) {
        Snackbar.make(activityBinding.root, message, Snackbar.LENGTH_LONG).show()
    }

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

    // LAZY INITIALIZATION: Video player only created when VIDEO file opened.
    internal var _videoPlayerManager: VideoPlayerManager? = null
    internal val videoPlayerManager: VideoPlayerManager
        get() = _videoPlayerManager ?: PlayerViewerFactory(this).createVideoPlayerManager().also { _videoPlayerManager = it }

    // S0021: lazy-initialised FPS meter for the flat 2D player overlay.
    internal val playerFpsMeter: PlayerFpsMeter = PlayerFpsMeter()
    private var playerFpsCollectorStarted: Boolean = false
    private var playerReadyTraceEmitted: Boolean = false
    internal var playerFpsCollectorStartedAccess: Boolean
        get() = playerFpsCollectorStarted
        set(value) { playerFpsCollectorStarted = value }

    // Tracks lifecycle-induced togglePause() from onPause() so onResumeWithViews() can reverse it. Prevents isPaused from leaking across background/resume cycles and breaking playWhenReady on next load.
    internal var wasToggledPausedByLifecycle = false

    internal lateinit var fileOperationsHandler: FileOperationsHandler
    internal lateinit var playerFileOperationQueue: com.sza.fastmediasorter.ui.player.fileops.PlayerFileOperationQueue
    internal val isPlayerFileOperationQueueInitialized: Boolean
        get() = try {
            playerFileOperationQueue
            true
        } catch (_: UninitializedPropertyAccessException) {
            false
        }
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

    // S0550: assigned late in PlayerManagerInitializer.initUiCoordinators(); the slideshow callback
    // can fire during init (initial displayImage -> updateSlideshowState -> stopSlideshow) before
    // this manager exists. Callers must gate UI sync on this readiness flag.
    internal val isDialogAndUiStateManagerInitialized: Boolean
        get() = ::dialogAndUiStateManager.isInitialized

    internal lateinit var audioSlideshowPhotoModeManager:
        com.sza.fastmediasorter.ui.player.helpers.AudioSlideshowPhotoModeManager
    internal lateinit var keyboardHandler: com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler
    internal lateinit var networkFileManager: com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
    internal lateinit var observerManager: PlayerObserverManager
    internal lateinit var slideshowResourceAvailabilityManager:
        com.sza.fastmediasorter.ui.player.helpers.SlideshowResourceAvailabilityManager
    internal lateinit var playerManagerInitializer: PlayerManagerInitializer
    internal var areAudioBackgroundManagersConfigured: Boolean = false

    // LAZY INITIALIZATION: Document viewers only created when needed.
    internal var _pdfViewerManager: com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager? = null
    internal val pdfViewerManager: com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager
        get() = _pdfViewerManager ?: PlayerViewerFactory(this).createPdfViewerManager().also { _pdfViewerManager = it }

    /** True when PdfViewerManager is initialized AND PDF is active; lets gesture handlers route without triggering lazy init. */
    internal fun isPdfActive(): Boolean = _pdfViewerManager?.isPdfActive() == true

    internal var _epubViewerManager: com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager? = null
    internal val epubViewerManager: com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager
        get() = _epubViewerManager ?: PlayerViewerFactory(this).createEpubViewerManager().also { _epubViewerManager = it }

    internal var _textViewerManager: com.sza.fastmediasorter.ui.player.helpers.TextViewerManager? = null
    internal val textViewerManager: com.sza.fastmediasorter.ui.player.helpers.TextViewerManager
        get() = _textViewerManager ?: PlayerViewerFactory(this).createTextViewerManager().also { _textViewerManager = it }

    // S0301 Phase 02: flavor-safe Office viewer decision provider (market = external,
    // noLegal = embedded viewer wired in Phase 03). Created once, no Activity side effects.
    internal val officeDocumentViewerProvider: com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerProvider by lazy {
        PlayerViewerFactory(this).createOfficeDocumentViewerProvider()
    }

    // S0301 Phase 03: embedded Office viewer host. Market = no-op (external handoff stays),
    // noLegal = engine-backed read-only in-app viewer. Lazily created on first internal open.
    internal var _officeDocumentViewerManager: com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerHost? = null
    internal val officeDocumentViewerManager: com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerHost
        get() = _officeDocumentViewerManager
            ?: PlayerViewerFactory(this).createOfficeDocumentViewerHost().also { _officeDocumentViewerManager = it }
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
    internal lateinit var imageTranslationManager:
        com.sza.fastmediasorter.ui.player.helpers.PlayerImageTranslationManager
    internal lateinit var shareManager: com.sza.fastmediasorter.ui.player.helpers.PlayerShareManager
    internal lateinit var printManager: com.sza.fastmediasorter.ui.player.helpers.DocumentPrintManager
    internal lateinit var eventHandler: com.sza.fastmediasorter.ui.player.helpers.PlayerEventHandler
    internal lateinit var castMediaManager: com.sza.fastmediasorter.core.cast.CastController
    internal lateinit var saveVideoFrameManager: com.sza.fastmediasorter.ui.player.helpers.SaveVideoFrameManager
    internal lateinit var imageCropManager: com.sza.fastmediasorter.ui.player.helpers.ImageCropManager
    internal lateinit var touchZoneSetupManager: com.sza.fastmediasorter.ui.player.helpers.PlayerTouchZoneSetupManager
    internal lateinit var audioMetadataManager: com.sza.fastmediasorter.ui.player.helpers.PlayerAudioMetadataManager
    internal lateinit var playerPrefetchManager: com.sza.fastmediasorter.ui.player.helpers.PlayerPrefetchManager
    internal lateinit var blackScreenOverlayManager: com.sza.fastmediasorter.ui.player.helpers.BlackScreenOverlayManager
    internal lateinit var textEditorCalculatorBridge:
        com.sza.fastmediasorter.ui.player.helpers.TextEditorCalculatorBridge
    internal var playerVrLaunchManager: com.sza.fastmediasorter.ui.player.helpers.PlayerVrLaunchManager? = null

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        val documentCallback = if (type == ActionMode.TYPE_FLOATING) {
            getDocumentSelectionActionModeCallback()
        } else {
            null
        }
        return if (documentCallback != null && callback != null) {
            super.startActionMode(DocumentSelectionActionModeAugmentingCallback(callback, documentCallback), type)
        } else {
            super.startActionMode(callback, type)
        }
    }

    private fun getDocumentSelectionActionModeCallback(): DocumentSelectionActionModeCallback? {
        val officeCallback = _officeDocumentViewerManager
            ?.takeIf { it.isActive }
            ?.getSelectionActionModeCallback()
        if (officeCallback != null) return officeCallback

        return _epubViewerManager
            ?.takeIf { activityBinding.epubWebView.isVisible }
            ?.getSelectionActionModeCallback()
    }

    // S0200 Phase 04c: googleSignInLauncher removed - Credential Manager replaces the
    // activity-result handshake. Drive sign-in goes through GoogleIdentityRepository.signInPrimary.

    // Android 11+ batch delete permission (createDeleteRequest)
    internal val batchDeletePermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val queuedOperation = lifecycleManager.consumePendingBatchDeleteOperation()
        if (queuedOperation != null && isPlayerFileOperationQueueInitialized) {
            playerFileOperationQueue.resumeAfterPermission(
                granted = result.resultCode == Activity.RESULT_OK,
                op = queuedOperation,
            )
        } else {
            // Do NOT pass currentFile here - the player may have already advanced to the next file via optimistic navigation before the dialog was shown. The correct source path was stored in lifecycleManager.storePendingBatchDeleteFilePath() at the moment the dialog was launched.
            lifecycleManager.handleBatchDeleteResult(result.resultCode)
        }
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

    internal val textEditorCalculatorLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (::textEditorCalculatorBridge.isInitialized) {
            textEditorCalculatorBridge.handleResult(result)
        }
    }

    internal val hideControlsHandler = Handler(Looper.getMainLooper())
    internal val loadingIndicatorHandler = Handler(Looper.getMainLooper())

    /**
     * S0704: single visibility owner of the shared `R.id.progressBar`. Every former direct writer
     * (reactive driver, image cycle, ExoPlayer/OCR/translation/PDF/text/EPUB) now requests show/hide
     * by source through this coordinator instead of racing on `progressBar.isVisible`. Created lazily
     * on first manager wiring, before any media manager needs it.
     */
    internal val loadingIndicatorCoordinator by lazy {
        com.sza.fastmediasorter.ui.player.helpers.PlayerLoadingIndicatorCoordinator(
            progressBar = binding.progressBar,
            handler = loadingIndicatorHandler,
            isDestroyed = { isDestroyed || isFinishing },
        )
    }

    private lateinit var gestureDetector: GestureDetector
    internal val touchZoneDetector = TouchZoneDetector()
    internal var useTouchZones = true // Use touch zones for images, gestures for video
    internal var loadFullSizeImages = false // Load full-size images with PhotoView (3-zone mode)
    internal var nineZoneGridEnabled = true // S0620: false -> fullscreen uses the 3-zone fallback layout
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
    private var playbackOrderContextKey: String? = null

    // Current settings cached for overlay visibility
    internal var currentSettings: AppSettings? = null

    // S0162: screen rotation manager + accelerometer availability (lazy: deferred until first use)
    internal val screenRotationManager = com.sza.fastmediasorter.ui.player.helpers.ScreenRotationManager()
    internal val hasAccelerometer: Boolean by lazy { screenRotationManager.isAccelerometerPresent(this) }

    // S0667: single decision point mapping device orientation to fullscreen/command-panel mode.
    private val orientationModeManager = com.sza.fastmediasorter.ui.player.helpers.PlayerOrientationModeManager()

    // Session-scoped translation settings (reset when exiting Browse/Resource)
    internal var translationSessionSettings = com.sza.fastmediasorter.domain.models.TranslationSessionSettings()

    // Gesture callback for custom PhotoView zoom handling
    internal lateinit var playerGestureCallback: com.sza.fastmediasorter.ui.player.callbacks.PlayerGestureCallbackImpl

    // Injected dependencies for network playback
    @Inject internal lateinit var smbClientLazy: Lazy<SmbClient>

    @Inject internal lateinit var sftpClientLazy: Lazy<SftpClient>

    @Inject internal lateinit var ftpClientLazy: Lazy<FtpClient>

    @Inject internal lateinit var googleDriveClientLazy: Lazy<GoogleDriveRestClient>

    // S1379: the sanctioned reader of the compile-time background-audio axis; the player helpers get
    // the resolved value from here instead of reading the build flag themselves (CLAUDE.md Rule 14).
    @Inject lateinit var capabilityAvailability: com.sza.fastmediasorter.core.capability.CapabilityAvailability

    @Inject lateinit var networkStateMonitor: NetworkStateMonitor

    // S1006: reachable-endpoint resolver forwarded to VideoPlayerManager for multi-path SFTP playback.
    @Inject lateinit var endpointResolver: SftpEndpointResolver

    @Inject lateinit var xrDetectionFacade: XrDetectionFacade

    @Inject lateinit var castControllerFactory: com.sza.fastmediasorter.core.cast.CastControllerFactory

    @Inject lateinit var startVrPlaybackUseCase: StartVrPlaybackUseCase

    @Inject lateinit var vrLaunchPayloadHolder: com.sza.fastmediasorter.core.xr.VrLaunchPayloadHolder

    @Inject internal lateinit var dropboxClientLazy: Lazy<com.sza.fastmediasorter.data.cloud.DropboxClient>

    @Inject internal lateinit var oneDriveClientLazy: Lazy<com.sza.fastmediasorter.data.cloud.OneDriveRestClient>

    @Inject internal lateinit var fullscreenCommandOverride: Optional<FullscreenCommandOverride>

    @Inject internal lateinit var restrictedTreeTargetPolicy: RestrictedTreeTargetPolicy

    @Inject internal lateinit var saveFrameCommandOverride: Optional<SaveFrameCommandOverride>

    @Inject internal lateinit var systemUiCommandOverride: Optional<SystemUiCommandOverride>

    // S0459: unified «Send to..» menu manager; accessed by PlayerCommandPanelCallbackImpl.
    @Inject internal lateinit var sendToMenuManager: com.sza.fastmediasorter.ui.share.SendToMenuManager

    @Inject internal lateinit var credentialsRepositoryLazy: Lazy<NetworkCredentialsRepository>

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var resourceRepository: com.sza.fastmediasorter.domain.repository.ResourceRepository

    @Inject lateinit var playbackPositionRepository:
        com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository

    // S1382: forwarded into NowPlayingManager so the background-playback bar can resolve a stream's
    // own favicon from the atlas sidecar.
    @Inject lateinit var faviconAtlasStore: FaviconAtlasStore

    // S0473: usage-statistics sink, forwarded into the manually-constructed player managers
    // (video/image/pdf) via PlayerViewerFactory and PlayerManagerInitializer.
    @Inject lateinit var statsSink: com.sza.fastmediasorter.domain.stats.StatsSink

    // S1144 (ADR-6): per-channel track preference, forwarded into VideoPlayerManager the same way.
    @Inject lateinit var streamTrackPreferenceUseCase:
        com.sza.fastmediasorter.domain.usecase.streams.StreamTrackPreferenceUseCase

    // S0207 Phase 01: passed to VideoPlayerManager via PlayerViewerFactory for PRE_PLAY / AFTER_STATE_READY probes.
    @Inject lateinit var memoryProbe: com.sza.fastmediasorter.core.memory.MemoryProbe

    @Inject lateinit var memoryProfileCoordinator: com.sza.fastmediasorter.core.memory.MemoryProfileCoordinator

    // S0213 Pillar A: passed to VideoPlayerManager and PlayerMediaLoaderManager for decoder-error cooldown.
    @Inject lateinit var recentDecoderFailureTracker: com.sza.fastmediasorter.core.playback.RecentDecoderFailureTracker

    // S0391: source-availability gate; threaded into VideoPlayerManager and PlayerMediaLoaderManager.
    @Inject lateinit var remoteSourceGate: com.sza.fastmediasorter.core.capability.RemoteSourceAvailabilityGate

    // S0565: flavor-gated stream-protocol support; threaded into VideoPlayerManager for RTSP isolation.
    @Inject lateinit var streamProtocolSupport: com.sza.fastmediasorter.domain.player.StreamProtocolSupport

    @Inject lateinit var streamFrameIngestor: com.sza.fastmediasorter.domain.streams.StreamFrameIngestor

    // S0436: flavor-resolved capability layer; passed down to the player-manager cascade in place of BuildConfig.SUPPORT_* reads.
    @Inject lateinit var mediaCapabilities: com.sza.fastmediasorter.core.capability.MediaCapabilities

    // S0213 Pillar C: source of FAIL-verdict events; collected by observeData() into a one-shot snackbar.
    @Inject lateinit var memoryDegradationSignal: com.sza.fastmediasorter.core.memory.MemoryDegradationSignal

    /** Per-Activity guard so the degradation snackbar is shown at most once per player session. */
    private var memoryAlertShownInSession: Boolean = false
    internal var memoryAlertShownInSessionAccess: Boolean
        get() = memoryAlertShownInSession
        set(value) { memoryAlertShownInSession = value }

    @Inject lateinit var rotateImageUseCase: com.sza.fastmediasorter.domain.usecase.RotateImageUseCase

    @Inject lateinit var searchAudioCoverUseCase: com.sza.fastmediasorter.domain.usecase.SearchAudioCoverUseCase

    @Inject lateinit var deliveredAudioVisualizationSource:
        com.sza.fastmediasorter.data.delivery.DeliveredAudioVisualizationSource

    @Inject lateinit var flipImageUseCase: com.sza.fastmediasorter.domain.usecase.FlipImageUseCase

    @Inject lateinit var networkImageEditUseCase: com.sza.fastmediasorter.domain.usecase.NetworkImageEditUseCase

    @Inject lateinit var applyImageFilterUseCase: com.sza.fastmediasorter.domain.usecase.ApplyImageFilterUseCase

    @Inject lateinit var adjustImageUseCase: com.sza.fastmediasorter.domain.usecase.AdjustImageUseCase

    @Inject lateinit var extractGifFramesUseCase: com.sza.fastmediasorter.domain.usecase.ExtractGifFramesUseCase

    @Inject lateinit var saveGifFirstFrameUseCase: com.sza.fastmediasorter.domain.usecase.SaveGifFirstFrameUseCase

    @Inject lateinit var changeGifSpeedUseCase: com.sza.fastmediasorter.domain.usecase.ChangeGifSpeedUseCase

    @Inject lateinit var downloadNetworkFileUseCase: com.sza.fastmediasorter.domain.usecase.DownloadNetworkFileUseCase

    @Inject lateinit var localDestinationClassifier: LocalDestinationClassifier

    @Inject lateinit var localDestinationWriter: LocalDestinationWriter

    @Inject lateinit var searchLyricsUseCase: com.sza.fastmediasorter.domain.usecase.SearchLyricsUseCase

    @Inject internal lateinit var unifiedCacheLazy: Lazy<com.sza.fastmediasorter.core.cache.UnifiedFileCache>

    @Inject lateinit var mediaFilesCacheManager: MediaFilesCacheManager

    @Inject lateinit var gamepadInputManager: GamepadInputManager

    @Inject internal lateinit var smbFileOperationHandlerLazy:
        Lazy<com.sza.fastmediasorter.data.network.SmbFileOperationHandler>

    @Inject internal lateinit var sftpFileOperationHandlerLazy:
        Lazy<com.sza.fastmediasorter.data.network.SftpFileOperationHandler>

    @Inject internal lateinit var ftpFileOperationHandlerLazy:
        Lazy<com.sza.fastmediasorter.data.network.FtpFileOperationHandler>

    @Inject internal lateinit var cloudFileOperationHandlerLazy:
        Lazy<com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler>

    // S0242 Phase 02: passed to PlayerLifecycleManager so successful file ops are journaled
    // for the Browse Reconciler instead of returned via an Intent extra.
    @Inject lateinit var mutationJournal: com.sza.fastmediasorter.domain.mutation.MutationJournal

    @Inject lateinit var pathNormalizer: com.sza.fastmediasorter.domain.path.PathNormalizer

    @Inject lateinit var fileOperationUseCase: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase

    @Inject lateinit var imageClipboardWriter: com.sza.fastmediasorter.core.clipboard.ImageClipboardWriter

    @Inject lateinit var saveFallbackNotifier: com.sza.fastmediasorter.core.save.SaveFallbackNotifier

    @Inject
    @com.sza.fastmediasorter.core.di.ApplicationScope
    lateinit var fileOpsAppScope: kotlinx.coroutines.CoroutineScope

    @Inject internal lateinit var backgroundMusicManagerLazy:
        Lazy<com.sza.fastmediasorter.ui.player.helpers.BackgroundMusicManager>

    @Inject internal lateinit var audioBackgroundPhotosManagerLazy:
        Lazy<com.sza.fastmediasorter.ui.player.helpers.AudioBackgroundPhotosManager>

    @Inject lateinit var audioMetadataCacheRepository:
        com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository

    @Inject lateinit var okHttpClient: okhttp3.OkHttpClient

    @Inject lateinit var keyBindingManager: com.sza.fastmediasorter.core.input.KeyBindingManager

    internal val smbClient: SmbClient get() = smbClientLazy.get()
    internal val sftpClient: SftpClient get() = sftpClientLazy.get()
    internal val ftpClient: FtpClient get() = ftpClientLazy.get()
    internal val googleDriveClient: GoogleDriveRestClient get() = googleDriveClientLazy.get()
    internal val dropboxClient: com.sza.fastmediasorter.data.cloud.DropboxClient get() = dropboxClientLazy.get()
    internal val oneDriveClient: com.sza.fastmediasorter.data.cloud.OneDriveRestClient get() = oneDriveClientLazy.get()
    internal val credentialsRepository: NetworkCredentialsRepository get() = credentialsRepositoryLazy.get()
    internal val unifiedCache: com.sza.fastmediasorter.core.cache.UnifiedFileCache get() = unifiedCacheLazy.get()
    internal val smbFileOperationHandler: com.sza.fastmediasorter.data.network.SmbFileOperationHandler get() = smbFileOperationHandlerLazy.get()
    internal val sftpFileOperationHandler: com.sza.fastmediasorter.data.network.SftpFileOperationHandler get() = sftpFileOperationHandlerLazy.get()
    internal val ftpFileOperationHandler: com.sza.fastmediasorter.data.network.FtpFileOperationHandler get() = ftpFileOperationHandlerLazy.get()
    internal val cloudFileOperationHandler: com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler get() = cloudFileOperationHandlerLazy.get()
    internal val backgroundMusicManager: com.sza.fastmediasorter.ui.player.helpers.BackgroundMusicManager get() = backgroundMusicManagerLazy.get()
    internal val audioBackgroundPhotosManager: com.sza.fastmediasorter.ui.player.helpers.AudioBackgroundPhotosManager get() = audioBackgroundPhotosManagerLazy.get()

    internal val hideControlsRunnable = Runnable {
        if (!isDestroyed && !isFinishing && !viewModel.state.value.isPaused) viewModel.toggleControls()
    }
    private lateinit var imageTouchGestureDetector: GestureDetector

    // S0028: per-window resume state isolation
    internal lateinit var windowId: String

    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply the LargePlayerControls theme overlay (when "Compact elements" is OFF) BEFORE super.onCreate → setContentView. DefaultTimeBar reads bar/touch/scrubber sizes from XML once at inflate; switching layouts requires app restart.
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
            lifecycle = lifecycle,
            mutationJournal = mutationJournal,
            pathNormalizer = pathNormalizer,
        )
        lifecycleManager.onCreate(savedInstanceState)
        slideshowModeRequested = lifecycleManager.isSlideshowModeRequested()
        if (!slideshowModeRequested && viewModel.resumeSlideshowEnabled) {
            slideshowModeRequested = true
        }
        initializeManagers()
        // S0159: pre-activate draw overlay when launched from Browse overflow ⋮ menu
        if (intent.getBooleanExtra(EXTRA_ACTIVATE_DRAW_MODE, false)) {
            window.decorView.post {
                if (isDrawOverlayManagerReady) {
                    syncDrawOverlayCurrentFile()
                    imageDrawOverlayManager.enterDrawMode()
                }
            }
        }
        // S0189: signal textViewerManager to enter edit mode once text content finishes loading
        if (intent.getBooleanExtra(EXTRA_TEXT_EDIT_MODE_ON_OPEN, false)) {
            textViewerManager.setAutoOpenEditMode(true)
        }
        // S0162: pass accelerometer capability once; ViewModel launches settings collector internally
        viewModel.initRotationCapability(hasAccelerometer)
        setupToolbar()
        setupControls()
        translationButtonManager.setupTranslationDefaults()
        translationButtonManager.setupTranslationButtonIcons()
        setupGoogleLensButtons()
        setupCommandPanelControls()
        setupTouchZones()
        binding.root.post {
            updateSystemBarsForPlayer(viewModel.state.value.showCommandPanel)
            initEnterFullscreenOnLaunch(savedInstanceState)
        }
    }

    /**
     * S0694/S0820: certain launches enter fullscreen immediately - a live video stream (S0694) or,
     * when the "open video in fullscreen" setting is on, an ordinary video file opened from Browse
     * with no per-resource command-panel override (S0820). Both producers resolve the decision
     * before building the intent (the media type/stream-ness is not reliably known here at first
     * layout) and pass the final result via [EXTRA_ENTER_FULLSCREEN] - trust it directly. Runs only
     * on a fresh launch - a process-death recreation must not re-force fullscreen after the user has
     * manually exited it. enterFullscreenMode() is idempotent.
     */
    private fun initEnterFullscreenOnLaunch(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        if (!intent.getBooleanExtra(EXTRA_ENTER_FULLSCREEN, false)) return
        viewModel.enterFullscreenMode()
        updateSystemBarsForPlayer(viewModel.state.value.showCommandPanel)
    }

    /** Initialize all helper managers - delegates to PlayerManagerInitializer. */
    private fun initializeManagers() {
        playerManagerInitializer = PlayerManagerInitializer(this)
        playerManagerInitializer.initialize()
    }

    internal fun shouldUseAudioBackgroundManagers(state: PlayerViewModel.PlayerState): Boolean {
        return state.currentFile?.type == MediaType.AUDIO ||
            state.isSlideShowActive ||
            state.enableSlideshowBackgroundMusic ||
            state.enablePhotosDuringAudio ||
            state.slideshowMusicResourceId != null ||
            state.audioBackgroundPhotosResourceId != null ||
            (::audioSlideshowPhotoModeManager.isInitialized && audioSlideshowPhotoModeManager.isActive)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::windowId.isInitialized) outState.putString(EXTRA_WINDOW_ID, windowId)
    }

    // S0293: forward multi-window / desktop-mode transitions so the player command panel
    // recomputes "Open in new window" visibility on its next render.
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (::commandPanelController.isInitialized) commandPanelController.notifyMultiWindowModeChanged()
    }

    /** S0532: lets keyboard callbacks query move availability without risking a lateinit access before setup. */
    internal fun isCommandPanelControllerReady(): Boolean = ::commandPanelController.isInitialized

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::commandPanelController.isInitialized) commandPanelController.notifyMultiWindowModeChanged()
        commandPanelController.updateOrientation(newConfig)
        // Hardware keyboard attach/detach arrives as a configuration change - re-evaluate slot number badges.
        if (::destinationButtonsManager.isInitialized) destinationButtonsManager.refreshSlotBadges()
        binding.topCommandPanel.post { binding.topCommandPanel.requestApplyInsets() }
        val mediaType = viewModel.state.value.currentFile?.type
        val isVisualMedia = mediaType == MediaType.VIDEO ||
            mediaType == MediaType.IMAGE ||
            mediaType == MediaType.GIF
        val targetMode = orientationModeManager.resolve(
            isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE,
            followsDevice = screenRotationManager.followsDevice(),
            isVisualMedia = isVisualMedia
        )
        when (targetMode) {
            PlayerDisplayMode.FULLSCREEN -> viewModel.enterFullscreenMode()
            PlayerDisplayMode.COMMAND_PANEL -> viewModel.enterCommandPanelMode()
            null -> Unit
        }
        binding.root.post {
            val currentFile = viewModel.state.value.currentFile ?: return@post
            if (currentFile.type != MediaType.IMAGE && currentFile.type != MediaType.GIF) return@post
            if (::imageLoadingManager.isInitialized) imageLoadingManager.reEvaluateScaleTypeOnRotation()
            binding.imageView.setImageDrawable(null)
            binding.photoView.setImageDrawable(null)
            lifecycleScope.launch(Dispatchers.Main) { displayImage(currentFile.path) }
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

    override fun observeData() = lifecycleBridge.observeData()

    internal fun exitPlayerWithAudioCheck(withTransition: Boolean = false) {
        emitTraceSection(FMS_PLAYER_BACK_NAVIGATION)
        lifecycleManager.exitPlayerWithAudioCheck(withTransition)
    }

    internal fun emitPlayerReadyTraceIfNeeded() {
        if (playerReadyTraceEmitted) return
        playerReadyTraceEmitted = true
        emitTraceSection(FMS_PLAYER_READY)
    }

    private fun emitTraceSection(sectionName: String) {
        Trace.beginSection(sectionName)
        Trace.endSection()
    }

    private fun setupGestureDetector() = gestureSetupManager.setupGestureDetector()

    private fun setupToolbar() = controlsSetupManager.setupToolbar()

    private fun setupControls() {
        controlsSetupManager.setupAllControls()
        controlsSetupManager.setupDocumentFullscreenExitButton()
    }

    private fun setupCommandPanelControls() {
        commandPanelController.setupCommandPanelControls()
        exoPlayerControlsManager.updatePlaybackOrderButtonState()
    }

    private fun setupTouchZones() = touchZoneSetupManager.setupLegacyTouchZoneListeners()

    internal fun showTouchZoneHintOverlay(type: TouchZoneHintType) =
        touchZoneSetupManager.showHintOverlay(type)

    fun showTouchZonesHelpOverlay() {
        val hintType = uiStateCoordinator.getCurrentHintType(viewModel.state.value, nineZoneGridEnabled)
            ?: TouchZoneHintType.FULLSCREEN_9ZONE
        touchZoneSetupManager.showHintOverlay(hintType)
    }

    internal fun adjustTouchZonesForVideo(isVideo: Boolean) =
        mediaLoaderManager.adjustTouchZonesForVideo(isVideo, useTouchZones)

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
        val isPaused = if (isAnimatedContent) {
            mediaLoaderManager.isAnimatedPlaybackPaused()
        } else {
            viewModel.state.value.isPaused
        }
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
        // S0819: in non-touch mode (D-pad/gamepad) the controls overlay is the only thing the
        // travelling focus frame can land on, so never auto-hide it there - the frame would be
        // left pointing at nothing. Touch mode keeps the original auto-hide behaviour.
        if (!binding.root.isInTouchMode) return
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

    internal fun saveCurrentFrame() = saveVideoFrameManager.saveCurrentFrame()

    // ── S0106: Image crop & compress (delegate) ─────────────────────────────

    internal lateinit var cropDelegate: com.sza.fastmediasorter.ui.player.helpers.PlayerCropDelegate

    internal lateinit var immersiveModeManager: com.sza.fastmediasorter.ui.player.helpers.PlayerImmersiveModeManager

    internal fun enterImageCropMode(mode: com.sza.fastmediasorter.ui.player.helpers.ImageCropManager.CropMode) =
        cropDelegate.enterCropMode(mode)

    internal fun startCompressedCopy() = cropDelegate.startCompressedCopy()

    // ── End S0106 ────────────────────────────────────────────────────────────

    // ── S0107: Draw overlay (delegated) ────────────────────────────────────────────────
    lateinit var playerDrawingSaveHelper: com.sza.fastmediasorter.ui.player.helpers.PlayerDrawingSaveHelper
    lateinit var imageDrawOverlayManager: com.sza.fastmediasorter.ui.player.helpers.ImageDrawOverlayManager

    /** True once imageDrawOverlayManager has been constructed (Phase 02 init). */
    internal val isDrawOverlayManagerReady: Boolean get() = ::imageDrawOverlayManager.isInitialized

    @Inject lateinit var mergeDrawOverlayUseCase: com.sza.fastmediasorter.domain.usecase.MergeDrawOverlayUseCase

    // S0189: registry for deferred new-note creations (consulted by TextViewerManager on load/cancel)
    @Inject lateinit var textNoteStagingRegistry: com.sza.fastmediasorter.data.local.staging.LocalStagingRegistry

    // S0192 Phase 05 - Google Keep export, invoked from the draw editor overflow menu.
    @Inject lateinit var drawKeepExportHelper: com.sza.fastmediasorter.ui.player.helpers.DrawKeepExportHelper

    internal fun syncDrawOverlayCurrentFile() = playerDrawingSaveHelper.syncDrawOverlayCurrentFile()

    internal fun setupDrawOverlayActionCallbacks() = playerDrawingSaveHelper.setupDrawOverlayActionCallbacks()

    internal fun setupDrawOverlayInPlaceSaveCallback() = playerDrawingSaveHelper.setupDrawOverlayInPlaceSaveCallback()

    internal fun setupDrawOverlaySaveCallback() = playerDrawingSaveHelper.setupDrawOverlaySaveCallback()

    internal fun setupEditModeCallbacks() {
        val cb: (com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode) -> Unit = { mode ->
            viewModel.setImageEditMode(mode)
        }
        imageDrawOverlayManager.editModeCallback = cb
        imageCropManager.editModeCallback = cb
        viewModel.setImageEditMode(com.sza.fastmediasorter.ui.player.state.PlayerImageEditMode.NONE)
    }

    // ── End S0107 ─────────────────────────────────────────────────────────

    fun toggleBlackScreenOverlay() {
        if (blackScreenOverlayManager.isVisible) {
            blackScreenOverlayManager.hide()
        } else {
            blackScreenOverlayManager.show()
        }
    }

    // S0184: open a duplicate Player in a new window slot while keeping the source player alive.
    internal fun tearOffPlayer() {
        val filePath = currentFilePath ?: return
        val state = viewModel.state.value
        val newWindowId = java.util.UUID.randomUUID().toString()
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("resourceId", state.resourceId)
            putExtra("initialIndex", state.currentIndex)
            putExtra("initialFilePath", filePath)
            putExtra(EXTRA_WINDOW_ID, newWindowId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        startActivity(intent)
    }

    internal fun handleDeleteSuccess(deletedFilePath: String) = lifecycleManager.handleDeleteSuccess(deletedFilePath)

    private fun handleEvent(event: PlayerViewModel.PlayerEvent) {
        if (event is PlayerViewModel.PlayerEvent.StopPlayback) {
            _videoPlayerManager?.getPlayer()?.pause()
            audioServiceController?.player?.pause()
            Toast.makeText(this, R.string.playback_order_stopped, Toast.LENGTH_SHORT).show()
            return
        }
        eventHandler.handleEvent(event)
    }

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

    internal fun onManualSlideshowNavigation() {
        if (::slideshowResourceAvailabilityManager.isInitialized) {
            slideshowResourceAvailabilityManager.onManualNavigation()
        }
    }

    internal fun stopSlideshowDueToResourceIssue(message: String) {
        if (!viewModel.state.value.isSlideShowActive || !::navigationManager.isInitialized) return

        viewModel.setSlideShowActive(false)
        navigationManager.updateSlideshowState()
        if (::audioSlideshowPhotoModeManager.isInitialized && audioSlideshowPhotoModeManager.isActive) {
            audioSlideshowPhotoModeManager.exit()
        }
        if (::dialogAndUiStateManager.isInitialized) {
            dialogAndUiStateManager.updateSlideShowButton()
        }
        updateSystemBarsForPlayer(viewModel.state.value.showCommandPanel)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    internal fun populateDestinationButtons() = destinationButtonsManager.populateDestinationButtons()

    /** S0531: number operation slots when driven by non-touch input (TV remote / D-pad / hardware keyboard). */
    internal fun shouldNumberOperationSlots(): Boolean = isNonTouchInputActive()

    /** S0162: Called by PlayerEventHandler when RotationSensorToggled fires. followSystem=false is guaranteed by the guard in PlayerViewModel.toggleRotationSensor(). */
    internal fun onRotationSensorToggled(sensorEnabled: Boolean) {
        screenRotationManager.apply(
            this,
            followSystem = false,
            sensorEnabled = sensorEnabled,
            hasAccelerometer = hasAccelerometer
        )
        commandPanelController.updateRotationToggleIcon(sensorEnabled)
    }

    private fun performCopyOperation(destination: MediaResource) = fileOperationsHandler.performCopy(destination)

    private fun performMoveOperation(destination: MediaResource) = fileOperationsHandler.performMove(destination)

    private fun showAudioFileInfo(file: MediaFile?) = mediaLoaderManager.showAudioFileInfo(file)

    internal fun updateAudioFormatInfo() = imageLoadingManager.updateAudioFormatInfo()

    internal fun updateTrackButtonsVisibility() {
        applyPlaybackOrderModeToActivePlayer(viewModel.state.value.playbackOrderMode)
        exoPlayerControlsManager.updateTrackButtonsVisibility(
            viewModel.state.value.currentFile?.type == MediaType.VIDEO ||
                viewModel.state.value.currentFile?.type == MediaType.AUDIO
        )
        exoPlayerControlsManager.updatePlaybackOrderButtonState()
        // S0641: re-trim the on-player controls when a live video stream loads (covers the case where
        // the controller is already visible, so no visibility-change callback fires).
        exoPlayerControlsManager.applyStreamControlProfile()
    }

    internal fun prefetchNextAudio() {
        if (::mediaLoaderManager.isInitialized) mediaLoaderManager.prefetchNextAudio()
    }

    internal fun handleMediaLoadErrorAndSkip() {
        Toast.makeText(this, getString(R.string.error_loading_media), Toast.LENGTH_SHORT).show()
        // In slideshow mode the player must continue without interruption. If isPaused=true reached this point (e.g., a background/resume lifecycle race before the toggle-reversal in onResumeWithViews() completes), force-unpause so the next file gets playWhenReady=true.
        if (viewModel.state.value.isSlideShowActive && viewModel.state.value.isPaused) {
            viewModel.setPaused(false)
        }
        navigationManager.skipAfterLoadError()
    }

    internal fun releasePlayer() {
        if (_videoPlayerManager != null) videoPlayerManager.releasePlayer()
    }

    internal fun stopVideoPlayback() = lifecycleManager.stopVideoPlayback()

    /**
     * S0289 §2.2 / S0819: initial focus target on a non-touch open.
     *
     * `btnPlayPause` lives inside `controlsOverlay`, which is `gone` by default and only shown when
     * `PlayerState.showControls` is true and the current media does not use touch zones. Focusing a
     * `gone` view is a silent no-op, so before returning the button we make the overlay visible on a
     * non-touch open (unless the media is touch-zone driven, e.g. images, where the overlay is
     * intentionally suppressed and there is nothing to force-show). Visibility is set both on the
     * `PlayerState` (so a following `updateUI` keeps it, and S0819's non-touch auto-hide guard leaves
     * it up) and synchronously on the view (so the immediate `requestFocus()` in BaseActivity finds a
     * focusable target on the very first pass).
     */
    override fun getInitialFocusView(): View? {
        if (!binding.root.isInTouchMode && !useTouchZones) {
            if (!viewModel.state.value.showControls) viewModel.toggleControls()
            binding.controlsOverlay.isVisible = true
        }
        return binding.btnPlayPause
    }

    private val lifecycleBridge by lazy { PlayerActivityLifecycleBridge(this) }

    override fun onPause() {
        super.onPause()
        if (isInPictureInPictureMode) return
        lifecycleBridge.onPause()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        pipManager?.onUserLeaveHint(currentSettings?.enablePictureInPicture == true)
    }

    // Single-arg override kept for legacy minSdk 23: on API 24-25 the framework calls only this
    // signature (two-arg added in API 26), so migrating away would drop PiP handling on those devices.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        pipManager?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    internal fun stopTranslation() = imageTranslationManager.stopTranslation()

    internal fun translateCurrentImage() = imageTranslationManager.translateCurrentImage()

    internal fun extractTextFromCurrentImage() =
        imageOcrManager.extractTextFromCurrentImage(viewModel.state.value.currentFile)

    override fun onResumeWithViews() = lifecycleBridge.onResumeWithViews()

    /** S0021: reconcile FPS overlay visibility against settings + current file type. Open: only visible when `playerShowFps` is on AND current file is VIDEO. */
    internal fun updatePlayerFpsOverlay() {
        val show = viewModel.settings.value.playerShowFps &&
            viewModel.state.value.currentFile?.type == MediaType.VIDEO
        binding.tvPlayerFpsOverlay.isVisible = show
        if (show) playerFpsMeter.start() else playerFpsMeter.stop()
    }

    private val inputDispatcher by lazy { PlayerInputDispatcher(this) }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean =
        inputDispatcher.onKeyDown(keyCode, event) { kc, ev -> super.onKeyDown(kc, ev) }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        inputDispatcher.dispatchKeyEvent(event) { super.dispatchKeyEvent(it) }

    // Transport owns directional keys via PlayerInputDispatcher (seek / volume / chapter); the base
    // container-focus guard must not steal them onto a control. S0506.
    override fun shouldGuardContainerFocus(): Boolean = false

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        inputDispatcher.dispatchGenericMotionEvent(event) { super.dispatchGenericMotionEvent(it) }

    // Transport routes stick/trigger motion through PlayerInputDispatcher (seek / volume); the
    // shared gamepad navigation layer must not also move focus here. S0508.
    override fun shouldHandleGamepadNavigation(): Boolean = false

    fun advanceAudioBackgroundPhoto() = audioSlideshowPhotoModeManager.advancePhoto()

    internal fun updateAudioSlideshowCurrentSongLabel() = audioSlideshowPhotoModeManager.updateCurrentSongLabel()

    override fun onDestroy() {
        if (::slideshowResourceAvailabilityManager.isInitialized) {
            slideshowResourceAvailabilityManager.cleanup()
        }
        if (::eventHandler.isInitialized) eventHandler.onDestroy()
        if (isPlayerFileOperationQueueInitialized) playerFileOperationQueue.shutdown()
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
    fun onAudioMetadataLoaded(
        metadata: com.sza.fastmediasorter.domain.model.AudioMetadata,
        originatingPath: String
    ) {
        val current = viewModel.state.value.currentFile
        if (current == null || current.path != originatingPath) {
            return
        }
        audioMetadataManager.onMetadataLoaded(metadata, current)
    }

    // ── PlayerActionHost (S0393) ──────────────────────────────────────────────
    // Adapter over the existing binding/VM so type-specific delegates consume the seam, not this
    // Activity. Behaviour-preserving: these members mirror what the in-app delegates already read.

    override val hostActivity: AppCompatActivity get() = this
    override val hostScope: LifecycleCoroutineScope get() = lifecycleScope
    override val actionCurrentFile: MediaFile? get() = viewModel.state.value.currentFile
    override val actionCurrentResource: MediaResource? get() = viewModel.state.value.resource
    override val overlayMountTarget: ViewGroup get() = activityBinding.mediaContentArea
    override val imagePinchTarget: View get() = activityBinding.photoView

    // Only the visible photoView carries a live zoom/pan matrix; when the non-zoomable imageView is the
    // active surface, photoView may still hold a stale drawable, so report an empty rect to fall back to
    // the legacy full-view crop mapping.
    override fun imageDisplayRect(): RectF =
        if (activityBinding.photoView.isVisible) (activityBinding.photoView.displayRect ?: RectF()) else RectF()

    // The crop overlay maps its selection through imageDisplayRect(), so the zoomable photoView keeps its
    // current zoom/pan (WYSIWYG). Only the non-zoomable imageView is normalized (its crop uses the legacy
    // full-view mapping).
    override fun prepareImageSurfacesForCrop() {
        activityBinding.imageView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
    }
    override val displayedBitmap: Bitmap? get() = viewModel.currentDisplayedBitmap

    override fun reloadCurrentImageInPlace() {
        if (isMediaLoaderManagerInitialized) mediaLoaderManager.reloadCurrentImage()
    }

    override fun onFileSavedInFolder(savedPath: String) {
        val fileName = savedPath.substringAfterLast('/')
        val currentParent = viewModel.state.value.currentFile?.path?.substringBeforeLast('/') ?: ""
        val savedParent = savedPath.substringBeforeLast('/')
        if (currentParent.isNotEmpty() && currentParent == savedParent) {
            viewModel.reloadFiles()
            lifecycleScope.launch {
                val files = withTimeoutOrNull(10_000L) {
                    viewModel.state.map { it.files }.distinctUntilChanged()
                        .first { fs -> fs.any { it.path == savedPath } }
                }
                val idx = files?.indexOfFirst { it.path == savedPath } ?: -1
                if (idx >= 0) {
                    viewModel.jumpToIndex(idx, manual = true)
                } else {
                    Toast.makeText(
                        this@PlayerActivity,
                        getString(R.string.crop_file_created, fileName),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } else {
            Toast.makeText(
                this@PlayerActivity,
                getString(R.string.crop_file_created, fileName),
                Toast.LENGTH_LONG
            ).show()
        }
    }

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

    // S0763: gate the dialog's "3D" section on the 3D/VR master toggle (hot settings StateFlow).
    override val is3dVrEnabled: Boolean get() = !viewModel.settings.value.disable3dVr

    override val activeSourceIsStream: Boolean
        get() = _videoPlayerManager?.activeSourceIsStream == true

    override val activeSourceIsLive: Boolean
        get() = _videoPlayerManager?.isActiveSourceLive() == true

    override val supportsColorAdjustmentForActiveSource: Boolean
        get() = !activeSourceIsStream

    override fun setStereoMode(mode: StereoMode) {
        viewModel.setStereoMode(mode)
    }
    override fun rememberStereoModeForCurrentFile(mode: StereoMode) = viewModel.rememberStereoModeForCurrentFile(mode)

    override val videoPlayerHandle: VideoPlayerHandle get() = playerActivityVideoHandle

    private val playerActivityVideoHandle: VideoPlayerHandle by lazy {
        PlayerActivityVideoHandle(
            videoPlayerManagerProvider = { _videoPlayerManager },
            audioPlayerProvider = { audioServiceController?.player },
            isAudioServiceActive = { isAudioServiceActive },
        )
    }

    override val isAudioServiceActive: Boolean
        get() = isMediaLoaderManagerInitialized && mediaLoaderManager.isServiceAudioActive

    override fun showMessage(message: String) = viewModel.showMessage(message)

    // Handled internally: PlayerDeleteUndoCoordinator advances the file list or emits FinishActivity.
    override fun requestFinishAfterDelete() = Unit

    // S0995: apply the session frame rotation to whichever surface is live. The video handle re-composes
    // the effect chain (no-op without an active video); the image manager rotates the visible image view.
    // Both paths are safe to call regardless of the current media type.
    internal fun applyContentRotation(angleDegrees: Int) {
        videoPlayerHandle.setContentRotationDegrees(angleDegrees)
        if (::imageLoadingManager.isInitialized) imageLoadingManager.applyRotation(angleDegrees)
    }

    private fun applyPlaybackOrderModeToActivePlayer(mode: PlaybackOrderMode) {
        when (viewModel.state.value.currentFile?.type) {
            MediaType.AUDIO -> audioServiceController?.applyPlaybackOrderMode(mode)
            MediaType.VIDEO -> {
                val exoRepeatMode = if (mode == PlaybackOrderMode.REPEAT_ONE) {
                    Player.REPEAT_MODE_ONE
                } else {
                    Player.REPEAT_MODE_OFF
                }
                _videoPlayerManager?.getPlayer()?.repeatMode = exoRepeatMode
            }
            else -> {}
        }
    }

    internal fun syncPlaybackOrderForCurrentResource(state: PlayerViewModel.PlayerState): Boolean {
        val resourceId = state.resource?.id ?: return false
        val mediaType = state.currentFile?.type ?: return false
        val contextKey = "$resourceId:${PlaybackControlPreferences.modeScopeFor(mediaType)}"
        if (playbackOrderContextKey == contextKey) {
            return false
        }

        playbackOrderContextKey = contextKey
        val prefs = getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, MODE_PRIVATE)
        val overrideValue = intent.getStringExtra(PlaybackControlPreferences.EXTRA_PLAYBACK_ORDER_OVERRIDE)
        val overrideMode = overrideValue?.let(PlaybackOrderMode::fromPrefsString)
        val resolvedMode = overrideMode ?: PlaybackControlPreferences.loadMode(prefs, resourceId, mediaType)

        if (overrideMode != null) {
            PlaybackControlPreferences.saveMode(prefs, resourceId, mediaType, overrideMode)
            intent.removeExtra(PlaybackControlPreferences.EXTRA_PLAYBACK_ORDER_OVERRIDE)
        }

        applyPlaybackOrderModeToActivePlayer(resolvedMode)
        exoPlayerControlsManager.updatePlaybackOrderButtonState()
        if (state.playbackOrderMode == resolvedMode) {
            return false
        }

        viewModel.setPlaybackOrderMode(resolvedMode)
        return true
    }

    internal fun onPlaybackOrderClicked() {
        val newMode = viewModel.cyclePlaybackOrderMode()
        val currentState = viewModel.state.value
        val resourceId = currentState.resource?.id
        val mediaType = currentState.currentFile?.type
        val prefs = getSharedPreferences(PlaybackControlPreferences.PREFS_NAME, MODE_PRIVATE)
        if (resourceId != null) {
            PlaybackControlPreferences.saveMode(prefs, resourceId, mediaType, newMode)
        } else {
            prefs.edit()
                .putString(PlaybackControlPreferences.globalKeyFor(mediaType), newMode.toPrefsString())
                .apply()
        }
        applyPlaybackOrderModeToActivePlayer(newMode)
        exoPlayerControlsManager.updatePlaybackOrderButtonState()
        val label = getString(newMode.toPlaybackOrderUiState().labelResId)
        Toast.makeText(this, getString(R.string.playback_order_mode_set, label), Toast.LENGTH_SHORT).show()
    }

    internal fun onStreamFrameIngested(url: String) {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(EXTRA_STREAM_THUMBNAIL_URL, url),
        )
    }

    companion object {
        private const val VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS = 15000L
        private const val FMS_PLAYER_READY = "FMS_PLAYER_READY"
        private const val FMS_PLAYER_BACK_NAVIGATION = "FMS_PLAYER_BACK_NAVIGATION"

        // S0242 Phase 02: the legacy "modified files" intent extra was removed - the Browse Reconciler reads the MutationJournal independently on its onResume. S0028: per-window resume state isolation
        const val EXTRA_WINDOW_ID = "extra_window_id"

        // S0159: pre-activate draw overlay mode when launched from Browse overflow menu
        const val EXTRA_ACTIVATE_DRAW_MODE = "activate_draw_mode"

        // S0189: open text file directly in edit mode (create-text-note flow)
        const val EXTRA_TEXT_EDIT_MODE_ON_OPEN = "s0189_edit_mode_on_open"

        // S0026: detected stereo mode hint. Browse fills this when opening media; the flat player primes PlayerStereoModeCoordinator with this value before applying user-settings, so the route decision sees the actual file format instead of the default MONO.
        const val EXTRA_DETECTED_STEREO_MODE = "extra_detected_stereo_mode"

        // S0289: last-played resource id propagated back to MainActivity on player exit so focus restores to the corresponding list row (not just the launch resource). Future extension: MainActivity may consume this via onActivityResult if the launch path is migrated from startActivity to ActivityResultLauncher.
        const val EXTRA_LAST_PLAYED_RESOURCE_ID = "s0289_last_played_resource_id"

        // S0694: open a live video stream straight into fullscreen. Set by the Streams launch path;
        // honoured once on first layout (see initEnterFullscreenOnLaunch).
        const val EXTRA_ENTER_FULLSCREEN = "extra_enter_fullscreen"
        const val EXTRA_STREAM_THUMBNAIL_URL = "extra_stream_thumbnail_url"

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
            windowId: String? = null,
            enterFullscreen: Boolean = false,
        ): Intent {
            // S0241 Phase 03: VR runtime detached from main player entry-point - every flavor now
            // routes to the flat PlayerActivity directly.
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra("resourceId", resourceId)
                putExtra("initialIndex", initialIndex)
                putExtra("skipAvailabilityCheck", skipAvailabilityCheck)
                initialFilePath?.let { putExtra("initialFilePath", it) }
                isPlaying?.let { putExtra("resumeIsPlaying", it) }
                if (isSlideshowEnabled) putExtra("resumeSlideshowEnabled", true)
                if (shuffleOnStart) putExtra("shuffleOnStart", true)
                detectedStereoMode?.let { putExtra(EXTRA_DETECTED_STEREO_MODE, it.name) }
                windowId?.let { putExtra(EXTRA_WINDOW_ID, it) }
                if (enterFullscreen) putExtra(EXTRA_ENTER_FULLSCREEN, true)
            }
        }

        /** Alias for [createIntent] kept so call-sites that explicitly want the flat-panel 2D entry-point remain searchable. Historically (S0019/S0038) bypassed `BuildConfig.PLAYER_ACTIVITY_CLASS` to escape a VR clone-window regression; after S0241 Phase 03 the per-flavor override is gone. */
        fun createPanelIntent(
            context: Context,
            resourceId: Long,
            initialIndex: Int = 0,
            skipAvailabilityCheck: Boolean = false,
            initialFilePath: String? = null,
            isPlaying: Boolean? = null,
            isSlideshowEnabled: Boolean = false,
            shuffleOnStart: Boolean = false,
            detectedStereoMode: StereoMode? = null,
            windowId: String? = null,
            enterFullscreen: Boolean = false,
        ): Intent = createIntent(
            context, resourceId, initialIndex, skipAvailabilityCheck, initialFilePath,
            isPlaying, isSlideshowEnabled, shuffleOnStart, detectedStereoMode, windowId,
            enterFullscreen,
        )
    }
}
