package com.sza.fastmediasorter.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.signature.ObjectKey
import com.github.chrisbanes.photoview.PhotoView
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.FileOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.datasource.CloudDataSourceFactory
import com.sza.fastmediasorter.data.network.glide.NetworkFileData
import com.sza.fastmediasorter.data.network.datasource.SmbDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.SftpDataSourceFactory
import com.sza.fastmediasorter.data.network.datasource.FtpDataSourceFactory
import com.sza.fastmediasorter.data.network.ConnectionThrottleManager
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.ResourceType
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.model.UndoOperation
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository

import com.sza.fastmediasorter.ui.dialog.RenameDialog
import com.sza.fastmediasorter.ui.player.helpers.PlayerDialogAndUiStateManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import com.sza.fastmediasorter.ui.player.helpers.PlayerMediaLoaderManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerNavigationManager
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.ui.player.helpers.WindowMetricsCompat
import com.sza.fastmediasorter.ui.player.model.TouchZoneHintType
import com.sza.fastmediasorter.ui.player.commands.FullscreenCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SaveFrameCommandOverride
import com.sza.fastmediasorter.ui.player.commands.SystemUiCommandOverride
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.utils.UserActionLogger
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import java.io.File
import java.util.Optional
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
// Open: VrPlayerActivity in vr flavor extends this to add OpenXR rendering layer
open class PlayerActivity : BaseActivity<ActivityPlayerUnifiedBinding>() {
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
    
    internal lateinit var fileOperationsHandler: FileOperationsHandler
    internal lateinit var destinationButtonsManager: DestinationButtonsManager
    internal lateinit var navigationManager: PlayerNavigationManager
    internal lateinit var commandPanelController: CommandPanelController
    internal lateinit var imageLoadingManager: ImageLoadingManager
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

    internal val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (::cloudAuthManager.isInitialized) {
            cloudAuthManager.handleGoogleSignInResult(result.data)
        }
    }
    
    // For handling Android 11+ batch delete permission requests (createDeleteRequest)
    internal val batchDeletePermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Permission granted, delete was successful (MediaStore handles it)
            timber.log.Timber.i("PlayerActivity: Batch delete permission granted, file deleted successfully")
            val currentFile = viewModel.state.value.currentFile
            if (currentFile != null) {
                handleDeleteSuccess(currentFile.path)
            }
        } else {
            timber.log.Timber.w("PlayerActivity: Batch delete permission denied by user")
            Toast.makeText(this, getString(R.string.error_delete_failed, "Permission denied"), Toast.LENGTH_SHORT).show()
        }
    }
    
    // For handling Android 10 single-file delete permission requests (RecoverableSecurityException)
    internal val deletePermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Permission granted, retry delete operation
            timber.log.Timber.d("PlayerActivity: Delete permission granted, retrying delete")
            fileOperationsHandler.performDelete()
        } else {
            timber.log.Timber.w("PlayerActivity: Delete permission denied by user")
            Toast.makeText(this, getString(R.string.error_delete_failed, "Permission denied"), Toast.LENGTH_SHORT).show()
        }
    }
    
    internal val hideControlsHandler = Handler(Looper.getMainLooper())
    internal val loadingIndicatorHandler = Handler(Looper.getMainLooper())
    
    // Track preload jobs to cancel on destroy (managed by lifecycleManager); keyed by file path for smart cancellation
    internal val preloadJobs = mutableMapOf<String, Job>()
    private lateinit var gestureDetector: GestureDetector
    internal val touchZoneDetector = TouchZoneDetector()
    internal var useTouchZones = true // Use touch zones for images, gestures for video
    internal var loadFullSizeImages = false // Load full-size images with PhotoView (3-zone mode)
    private var isFirstResume = true // Track first onResume to avoid duplicate load
    internal val shownHintTypes = mutableSetOf<TouchZoneHintType>() // Track per-type hints shown in this session
    internal var slideshowModeRequested = false // Auto-start slideshow when files are loaded
    internal var isExplicitFullscreenMode = false // User requested fullscreen via button
    
    // Retry logic for network stream errors
    private var playbackRetryCount = 0
    private val maxPlaybackRetries = 3
    private var lastPlaybackPosition = 0L
    internal val retryHandler = Handler(Looper.getMainLooper())
    internal var retryRunnable: Runnable? = null
    
    // Track active resource key for connection throttling
    private var activeResourceKey: String? = null
    
    // Track deleted/moved files to notify BrowseActivity
    private val modifiedFiles = mutableSetOf<String>()
    
    // Track current file path to avoid reloading when only metadata changes (e.g., isFavorite)
    internal var currentFilePath: String? = null
    
    // Cached AudioMetadata from online cover search (iTunes), keyed by file path
    private var cachedAudioMetadataPath: String? = null
    private var cachedAudioMetadata: com.sza.fastmediasorter.domain.model.AudioMetadata? = null

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
    
    // Current settings cached for overlay visibility
    internal var currentSettings: AppSettings? = null

    // Session-scoped translation settings (reset when exiting Browse/Resource)
    // Will be initialized from AppSettings defaults in setupTranslationDefaults()
    internal var translationSessionSettings = com.sza.fastmediasorter.domain.models.TranslationSessionSettings()

    // Gesture callback for custom PhotoView zoom handling
    internal lateinit var playerGestureCallback: com.sza.fastmediasorter.ui.player.callbacks.PlayerGestureCallbackImpl

    
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
    lateinit var backgroundMusicManager: com.sza.fastmediasorter.ui.player.helpers.BackgroundMusicManager

    @Inject
    lateinit var audioBackgroundPhotosManager: com.sza.fastmediasorter.ui.player.helpers.AudioBackgroundPhotosManager

    @Inject
    lateinit var audioMetadataCacheRepository: com.sza.fastmediasorter.data.repository.AudioMetadataCacheRepository

    @Inject
    lateinit var okHttpClient: okhttp3.OkHttpClient

    internal val hideControlsRunnable = Runnable {
        // Lifecycle check to prevent operations after destroy
        if (!isDestroyed && !isFinishing && !viewModel.state.value.isPaused) {
            viewModel.toggleControls()
        }
    }

    internal val showLoadingIndicatorRunnable = Runnable {
        // Lifecycle check to prevent operations after destroy
        if (!isDestroyed && !isFinishing) {
            binding.progressBar.isVisible = true
        }
    }
    
    /**
     * ExoPlayer listener for video/audio playback events
     * Handles: STATE_READY (hide loading indicator), STATE_ENDED (auto-advance in slideshow)
     * 
     * NOTE: ExoPlayer may log non-critical warnings internally (e.g., AudioSink discontinuity, 
     * ExoPlayer initialization timing). These are handled internally by ExoPlayer and don't 
     * affect playback. They appear as ERROR in logcat but are actually recoverable warnings.
     * Example: "Audio sink error... UnexpectedDiscontinuityException" - playback continues normally.
     */

    // Touch zone gesture detector managed by TouchZoneGestureManager
    private lateinit var imageTouchGestureDetector: GestureDetector

    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // PlayerActivity uses Edge-to-Edge ALWAYS to allow precise manual insets control.
        // We handle padding manually via OnApplyWindowInsetsListener in setupSystemBars().
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        
        super.onCreate(savedInstanceState)
        
        // Initialize lifecycle manager first
        lifecycleManager = com.sza.fastmediasorter.ui.player.helpers.PlayerLifecycleManager(
            activity = this,
            viewModel = viewModel,
            lifecycle = lifecycle
        )
        
        // Delegate onCreate to lifecycle manager
        lifecycleManager.onCreate(savedInstanceState)
        
        // Get slideshow mode flag from lifecycle manager
        slideshowModeRequested = lifecycleManager.isSlideshowModeRequested()
        
        // Resume slideshow if coming from resume playback with slideshow active
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
        
        // Set initial system bars state based on command panel visibility
        // Wait for layout to ensure managers are ready
        binding.root.post {
            val showCommandPanel = viewModel.state.value.showCommandPanel
            updateSystemBarsForPlayer(showCommandPanel)
        }
    }
    
    /**
     * Initialize all helper managers and controllers.
     * Delegates to PlayerManagerInitializer to keep this file small.
     */
    private fun initializeManagers() {
        PlayerManagerInitializer(this).initialize()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        
        // Update command panel orientation (adaptive layout)
        commandPanelController.updateOrientation(newConfig)
        
        // CRITICAL: Request insets reapplication after orientation change
        // This ensures topCommandPanel gets correct padding in landscape/portrait
        binding.topCommandPanel.post {
            binding.topCommandPanel.requestApplyInsets()
            Timber.d("TopCommandPanel: Requested apply insets after orientation change")
        }
        
        // Wait for layout to settle with new dimensions, then update touch zones
        binding.root.post {
            // Touch zones depend on screen dimensions - recalculate after rotation
            val currentFile = viewModel.state.value.currentFile
            
            // For images, reload with new dimensions (in coroutine to avoid blocking main thread)
            if (currentFile != null && (currentFile.type == MediaType.IMAGE || currentFile.type == MediaType.GIF)) {
                // Re-evaluate scale type immediately for currently displayed image
                // This provides instant visual feedback before the image reload completes
                if (::imageLoadingManager.isInitialized) {
                    imageLoadingManager.reEvaluateScaleTypeOnRotation()
                }
                
                // Clear current image from ImageViews to free memory before reloading
                binding.imageView.setImageDrawable(null)
                binding.photoView.setImageDrawable(null)
                
                // Load image asynchronously to prevent frame drops (was causing 128+ skipped frames)
                lifecycleScope.launch(Dispatchers.Main) {
                    displayImage(currentFile.path)
                }
            }
            // Video player handles rotation automatically, no need to reload
            // Touch zones will be recalculated automatically on next touch (using new binding.root.width/height)
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
    }

    /**
     * Exit the player, showing a dialog if background audio is playing.
     * "Stop" → stops the service and finishes. "Keep Playing" → finishes without stopping service.
     * @param withTransition whether to apply slide-out transition on exit
     */
    internal fun exitPlayerWithAudioCheck(withTransition: Boolean = false) =
        lifecycleManager.exitPlayerWithAudioCheck(withTransition)

    private fun setupGestureDetector() {
        Timber.d("TOUCH_DEBUG: PlayerActivity.setupGestureDetector() CALLED - delegating to gestureSetupManager")
        // Delegate all gesture detector and touch listener setup to PlayerGestureSetupManager
        // This consolidates 190 lines of complex touch zone logic
        gestureSetupManager.setupGestureDetector()
        Timber.d("TOUCH_DEBUG: PlayerActivity.setupGestureDetector() COMPLETED")
    }
    

    private fun setupToolbar() = controlsSetupManager.setupToolbar()

    private fun setupControls() {
        // Delegate all button setup to PlayerControlsSetupManager
        // This consolidates 273 lines of setOnClickListener boilerplate
        controlsSetupManager.setupAllControls()
        controlsSetupManager.setupDocumentFullscreenExitButton()
    }
    
    private fun setupCommandPanelControls() {
        commandPanelController.setupCommandPanelControls()
    }

    private fun setupTouchZones() {
        // DEPRECATED: View-based touch zone overlays (touchZonesOverlay, touchZones3Overlay)
        // are now permanently hidden. All touch zone detection is handled by TouchZoneGestureManager:
        // - Command panel mode: 3-zone (20% left, 60% center, 20% right) via handleCommandPanelTouchZones()
        // - Fullscreen mode: 9-zone grid via handleTouchZone()
        //
        // These click listeners are preserved for backward compatibility but will never fire
        // since the overlay Views are always hidden (isVisible = false).
        
        // Legacy 2-zone mode listeners (deprecated)
        safeViews.touchZonePrevious.setOnClickListener {
            navigationManager.navigatePreviousFromTouchZone()
        }

        safeViews.touchZoneNext.setOnClickListener {
            navigationManager.navigateNextFromTouchZone()
        }
        
        // Legacy 3-zone mode listeners (deprecated)
        safeViews.touchZone3Previous.setOnClickListener {
            navigationManager.navigatePreviousFromTouchZone()
        }
        
        safeViews.touchZone3Next.setOnClickListener {
            navigationManager.navigateNextFromTouchZone()
        }
        
        // Center gesture zone - no click handler (PhotoView handles pinch/rotate)
        // This is now fully handled by TouchZoneGestureManager gesture detection
    }
    
    /**
     * Show context-aware touch zone hint overlay based on the current mode.
     * - FULLSCREEN_9ZONE: shows the 9-zone grid overlay (audioTouchZonesOverlay)
     * - COMMAND_PANEL_3ZONE: shows text-based overlay describing 3-zone layout
     * - MEDIA_BOTTOM_RESERVED: shows text-based overlay describing reserved bottom area
     * Dismisses on first tap.
     */
    internal fun showTouchZoneHintOverlay(type: TouchZoneHintType) {
        when (type) {
            TouchZoneHintType.FULLSCREEN_9ZONE -> {
                safeViews.audioTouchZonesOverlay.isVisible = true
                safeViews.audioTouchZonesOverlay.alpha = 1.0f
                safeViews.audioTouchZonesOverlay.setOnClickListener {
                    safeViews.audioTouchZonesOverlay.isVisible = false
                    safeViews.audioTouchZonesOverlay.setOnClickListener(null)
                }
            }
            TouchZoneHintType.COMMAND_PANEL_3ZONE -> {
                safeViews.tvFirstRunHintText.setText(R.string.hint_touch_zone_3zone)
                safeViews.firstRunHintOverlay.isVisible = true
                safeViews.firstRunHintOverlay.setOnClickListener {
                    safeViews.firstRunHintOverlay.isVisible = false
                    safeViews.firstRunHintOverlay.setOnClickListener(null)
                }
            }
            TouchZoneHintType.MEDIA_BOTTOM_RESERVED -> {
                safeViews.tvFirstRunHintText.setText(R.string.hint_touch_zone_media)
                safeViews.firstRunHintOverlay.isVisible = true
                safeViews.firstRunHintOverlay.setOnClickListener {
                    safeViews.firstRunHintOverlay.isVisible = false
                    safeViews.firstRunHintOverlay.setOnClickListener(null)
                }
            }
        }
    }

    /**
     * Re-display touch zones overlay when user taps the [?] help button.
     * Shows context-appropriate overlay based on current player mode.
     * Dismissed on tap.
     */
    fun showTouchZonesHelpOverlay() {
        val state = viewModel.state.value
        val hintType = uiStateCoordinator.getCurrentHintType(state)
            ?: TouchZoneHintType.FULLSCREEN_9ZONE
        showTouchZoneHintOverlay(hintType)
    }

    /**
     * Adjust touch zones visibility based on media type
     * For video/audio in command panel mode: touch zones DISABLED (ExoPlayer controls used)
     * For images: touch zones ENABLED for Previous/Next navigation
     */
    internal fun adjustTouchZonesForVideo(isVideo: Boolean) {
        mediaLoaderManager.adjustTouchZonesForVideo(isVideo, useTouchZones)
    }

    private fun updateUI(state: PlayerViewModel.PlayerState) {
        observerManager.updateUI(state)
    }

    /**
     * Update panel visibility based on mode
     */
    internal fun updatePanelVisibility(showCommandPanel: Boolean) {
        dialogAndUiStateManager.updatePanelVisibility(showCommandPanel)
        // Update document fullscreen exit button visibility
        controlsSetupManager.updateDocumentFullscreenExitButtonVisibility()

        updateSystemBarsForPlayer(showCommandPanel)
    }

    internal fun updateSystemBarsForPlayer(showCommandPanel: Boolean) {
        if (showCommandPanel) {
            isExplicitFullscreenMode = false
        }

        // Check if user wants to hide system UI in fullscreen mode (default: true)
        val hideSystemUiEnabled = currentSettings?.hideSystemUiInFullscreen != false

        val isDocumentFullscreen = _pdfViewerManager?.isInFullscreenMode() == true ||
            _epubViewerManager?.isInFullscreenMode() == true
        if (isDocumentFullscreen && hideSystemUiEnabled) {
            systemBarsManager.enterFullscreenMode()
            return
        } else if (isDocumentFullscreen) {
            // Document fullscreen but system UI hiding disabled - keep bars visible
            systemBarsManager.exitFullscreenMode()
            return
        }

        val isMediaSlideshow = viewModel.state.value.isPhotoSlideshowActive

        val shouldHideSystemBars = hideSystemUiEnabled && (
            isExplicitFullscreenMode ||
            isMediaSlideshow ||
            audioSlideshowPhotoModeManager.isActive
        )

        if (shouldHideSystemBars) {
            systemBarsManager.enterFullscreenMode()
            // CRITICAL: Remove top padding from topCommandPanel when entering fullscreen
            // This prevents extra space at top when status bar is hidden
            binding.topCommandPanel.setPadding(
                binding.topCommandPanel.paddingLeft,
                0, // No top padding in fullscreen
                binding.topCommandPanel.paddingRight,
                binding.topCommandPanel.paddingBottom
            )
        } else {
            systemBarsManager.exitFullscreenMode()
            // CRITICAL: Force reapply insets to topCommandPanel after exiting fullscreen
            // This ensures command panel moves below status bar instead of overlapping it
            binding.topCommandPanel.post {
                binding.topCommandPanel.requestApplyInsets()
                Timber.d("TopCommandPanel: Forced insets reapply after exitFullscreen")
            }
        }
    }

    /**
     * Let flavor-specific code replace the fullscreen button behavior when Android system bars
     * are no longer the primary UX surface, such as inside an active XR session.
     */
    internal fun tryHandleFullscreenCommandOverride(): Boolean {
        return fullscreenCommandOverride.orElse(null)?.execute(this, viewModel) == true
    }

    /**
     * VR binds a dedicated Save Frame override so command routing no longer depends on hidden
     * phone-only surfaces when the player is rendered by OpenXR.
     */
    internal fun tryHandleSaveFrameCommandOverride(): Boolean {
        return saveFrameCommandOverride.orElse(null)?.execute(this) == true
    }

    /**
     * Shared player has no separate system-UI toggle command, but VR maps controller/menu input
     * to this override so it can show or hide the headset overlay instead of Android system bars.
     */
    internal fun tryHandleSystemUiCommandOverride(): Boolean {
        return systemUiCommandOverride.orElse(null)?.execute(this) == true
    }




    /**
     * Update command availability based on settings and file permissions
     */
    internal fun updateCommandAvailability(state: PlayerViewModel.PlayerState) {
        commandPanelController.updateCommandAvailability(state)
    }

    internal fun displayText(mediaFile: MediaFile) {
        mediaLoaderManager.displayText(mediaFile)
    }

    internal fun displayImage(path: String) {
        mediaLoaderManager.displayImage(path)
    }

    /**
     * Preload adjacent images (previous + next) in background for faster navigation.
     * Only preloads IMAGE and GIF files.
     * Supports circular navigation.
     */
    private fun preloadNextImageIfNeeded() {
        mediaLoaderManager.preloadNextImageIfNeeded()
    }

    internal fun playVideo(path: String) {
        mediaLoaderManager.playVideo(path)
    }
    
    private fun playLocalVideo(path: String) {
        mediaLoaderManager.playLocalVideo(path)
    }

    internal fun updateSlideShow() {
        navigationManager.updateSlideshowState()
    }

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

    internal fun isCurrentAnimatedContent(): Boolean {
        return ::mediaLoaderManager.isInitialized && mediaLoaderManager.isCurrentAnimatedContent()
    }

    internal fun toggleAnimatedPlayback(): Boolean? {
        if (!::mediaLoaderManager.isInitialized) return null
        return mediaLoaderManager.toggleAnimatedPlayback()
    }

    internal fun clearUiOverlayForAnimatedPause() {
        if (viewModel.state.value.showCommandPanel) {
            viewModel.enterFullscreenMode()
        }

        if (viewModel.state.value.showControls) {
            viewModel.toggleControls()
        }

        hideControlsHandler.removeCallbacks(hideControlsRunnable)
    }

    /**
     * Clear Glide memory cache to prevent OOM during long slideshows.
     * Called periodically by SlideshowController every 5 slides.
     */
    internal fun clearImageMemoryCache() {
        if (isFinishing || isDestroyed) {
            return
        }
        
        if (::imageLoadingManager.isInitialized) {
            imageLoadingManager.clearMemoryCache()
        }
    }
    
    internal fun setSlideshowKeepAwake(enabled: Boolean) =
        lifecycleManager.setSlideshowKeepAwake(enabled)

    /**
     * Update background music track name display (called by BackgroundMusicManager)
     * Shows track name in bottom-left corner during slideshow with music
     * @param trackName track name without extension, or null to hide
     */
    private fun updateBackgroundMusicTrackDisplay(trackName: String?) =
        dialogAndUiStateManager.updateBackgroundMusicTrackDisplay(trackName)

    /**
     * Adjust player volume by delta (e.g., +0.2 or -0.2)
     * Volume is clamped between 0.0 and 1.0
     */
    internal fun adjustVolume(delta: Float) {
        if (_videoPlayerManager == null) return
        val player = videoPlayerManager.getPlayer() ?: return
        val currentVolume = player.volume
        val newVolume = (currentVolume + delta).coerceIn(0f, 1f)
        player.volume = newVolume
        
        val percentage = (newVolume * 100).toInt()
        Timber.d("Volume adjusted: ${(currentVolume * 100).toInt()}% -> $percentage%")
        Toast.makeText(this, getString(R.string.volume_level, percentage), Toast.LENGTH_SHORT).show()
    }

    internal fun scheduleHideControls() {
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        // Don't auto-hide controls for audio files
        val isAudioFile = viewModel.state.value.currentFile?.type == MediaType.AUDIO
        if (viewModel.state.value.showControls && !viewModel.state.value.isPaused && !isAudioFile) {
            hideControlsHandler.postDelayed(hideControlsRunnable, VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS)
        }
    }

    /**
     * Update audio touch zones overlay visibility based on:
     * - Current file is audio
     * - Fullscreen mode (not showing command panel or controls overlay)
     * - Touch zones are enabled
     */
    private fun updateAudioTouchZonesVisibility() {
        dialogAndUiStateManager.updateAudioTouchZonesVisibility()
    }

    /**
     * Show quick translation settings dialog on long press of translate button
     */
    
    /**
     * Apply font settings to translation overlay view (Google Lens style)
     */

    internal fun showFileInfo() {
        val currentFile = viewModel.state.value.currentFile
        if (currentFile == null) {
            Toast.makeText(this, getString(R.string.file_info_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        // Enrich with live ExoPlayer data — MediaFile from network listing has no duration/dimensions.
        // ExoPlayer already parsed the container while streaming, so use its values directly.
        val player = _videoPlayerManager?.getPlayer()
        val exoDuration = player?.duration?.takeIf { it > 0 }
        val exoWidth = player?.videoSize?.width?.takeIf { it > 0 }
        val exoHeight = player?.videoSize?.height?.takeIf { it > 0 }
        val enrichedFile = if (exoDuration != null || exoWidth != null) {
            currentFile.copy(
                duration = exoDuration ?: currentFile.duration,
                width = exoWidth ?: currentFile.width,
                height = exoHeight ?: currentFile.height
            )
        } else {
            currentFile
        }
        Timber.d("showFileInfo: exoDuration=$exoDuration, exoSize=${exoWidth}x${exoHeight}")
        dialogHelper.showFileInfo(enrichedFile)
    }
    
    internal fun isAnimatedImagePath(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".gif") || lowerPath.endsWith(".webp") || lowerPath.endsWith(".apng")
    }


    
    /**
     * Search and display song lyrics for audio files.
     */
    internal fun searchAndShowLyrics() {
        val currentFile = viewModel.state.value.currentFile
        // Use cached metadata only if it belongs to the current file
        val metadata = if (currentFile != null && cachedAudioMetadataPath == currentFile.path) {
            cachedAudioMetadata
        } else {
            null
        }
        lyricsManager.searchAndShowLyrics(
            currentFile = currentFile,
            resolvedTitle = metadata?.trackName,
            resolvedArtist = metadata?.artistName
        )
    }
    
    /**
     * Open YouTube Music with a search query for the current audio track.
     * Uses cached iTunes metadata (artist + title) when available; falls back to filename.
     * Opens in the YouTube Music app if installed, otherwise in the browser.
     */
    internal fun searchInYoutubeMusic() {
        val currentFile = viewModel.state.value.currentFile ?: return
        val metadata = if (cachedAudioMetadataPath == currentFile.path) cachedAudioMetadata else null
        val artist = metadata?.artistName?.takeIf { it.isNotBlank() }
        val title = metadata?.trackName?.takeIf { it.isNotBlank() }
        val query = when {
            artist != null && title != null -> "$artist $title"
            title != null -> title
            artist != null -> artist
            else -> currentFile.name.substringBeforeLast(".")
        }
        Timber.d("PlayerActivity: searchInYoutubeMusic query='$query'")
        val uri = android.net.Uri.parse("https://music.youtube.com/search?q=${android.net.Uri.encode(query)}")
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            Timber.w(e, "PlayerActivity: no app can handle YouTube Music search")
            Toast.makeText(this, getString(R.string.search_in_youtube_music_no_app), Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens Chromecast device picker and casts the current media file.
     * Delegates all logic to CastMediaManager.
     */
    internal fun castCurrentMedia() {
        castMediaManager.showCastDialog(this)
        val currentFile = viewModel.state.value.currentFile ?: return
        if (castMediaManager.isCasting) {
            castMediaManager.sendCurrentMedia(currentFile)
        }
    }

    /**
     * Hide lyrics viewer overlay.
     */
    internal fun hideLyricsViewer() {
        lyricsManager.hideLyricsViewer()
    }
    
    /**
     * Reload current image after edit operation (rotation/flip/filter/adjustments).
     * Clears both memory and disk cache, updates MediaFilesCache, and reloads.
     */
    private fun reloadCurrentImage() {
        mediaLoaderManager.reloadCurrentImage()
    }

    /**
     * Save the currently visible frame using the activity-specific rendering path.
     * VR overrides this so Save Frame targets the OpenXR swapchain instead of TextureView.
     */
    internal open fun saveCurrentFrame() {
        saveVideoFrameManager.saveCurrentFrame()
    }
    
    /**
     * Handle successful file deletion (called from batch delete permission launcher or callback)
     */
    internal fun handleDeleteSuccess(deletedFilePath: String) {
        // Track deleted file
        lifecycleManager.trackModifiedFile(deletedFilePath)
        
        // Remove from cache
        viewModel.state.value.resource?.let { resource ->
            MediaFilesCacheManager.removeFile(resource.id, deletedFilePath)
        }
        
        // Remove from ViewModel list and navigate
        val hasRemainingFiles = viewModel.removeDeletedFile(deletedFilePath)
        if (!hasRemainingFiles) {
            finish()
        }
    }

    private fun handleEvent(event: PlayerViewModel.PlayerEvent) = eventHandler.handleEvent(event)

    internal fun showError(message: String, throwable: Throwable? = null) =
        eventHandler.showError(message, throwable)

    internal fun showFileNotFound(fileName: String) =
        eventHandler.showFileNotFound(fileName)

    private fun showCloudAuthenticationError(providerName: String? = null) =
        eventHandler.showCloudAuthenticationError(providerName)

    internal fun showUnsupportedFormatError(message: String, filePath: String, isLocalFile: Boolean) =
        eventHandler.showUnsupportedFormatError(message, filePath, isLocalFile)
    
    /**
     * Show popup message when slideshow is enabled
     */
    internal fun showSlideshowEnabledMessage() {
        val intervalSeconds = viewModel.state.value.slideShowInterval / 1000
        val message = getString(R.string.slideshow_enabled_message, intervalSeconds)
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Populate destination buttons dynamically based on destinations from DB
     */
    internal fun populateDestinationButtons() {
        Timber.d("PlayerActivity: populateDestinationButtons() CALLED")
        destinationButtonsManager.populateDestinationButtons()
    }

    private fun performCopyOperation(destination: com.sza.fastmediasorter.domain.model.MediaResource) {
        // Delegate to FileOperationsHandler
        fileOperationsHandler.performCopy(destination)
    }

    private fun performMoveOperation(destination: com.sza.fastmediasorter.domain.model.MediaResource) {
        // Delegate to FileOperationsHandler
        fileOperationsHandler.performMove(destination)
    }

    private fun showAudioFileInfo(file: MediaFile?) {
        mediaLoaderManager.showAudioFileInfo(file)
    }
    
    private fun formatDuration(millis: Long?): String {
        if (millis == null || millis <= 0) return "N/A"
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
        } else {
            "%d:%02d".format(minutes, seconds % 60)
        }
    }
    
    internal fun updateAudioFormatInfo() {
        imageLoadingManager.updateAudioFormatInfo()
    }

    internal fun updateTrackButtonsVisibility() {
        exoPlayerControlsManager.updateTrackButtonsVisibility(
            viewModel.state.value.currentFile?.type == com.sza.fastmediasorter.domain.model.MediaType.VIDEO ||
                viewModel.state.value.currentFile?.type == com.sza.fastmediasorter.domain.model.MediaType.AUDIO
        )
    }

    internal fun prefetchNextAudio() {
        if (::mediaLoaderManager.isInitialized) {
            mediaLoaderManager.prefetchNextAudio()
        }
    }

    internal fun handleMediaLoadErrorAndSkip() {
        Timber.w("PlayerActivity: media load error, skipping to next file")
        android.widget.Toast.makeText(this, getString(com.sza.fastmediasorter.R.string.error_loading_media), android.widget.Toast.LENGTH_SHORT).show()
        navigationManager.navigateNextFromControl()
    }

    internal fun releasePlayer() {
        // Deprecated: delegated to VideoPlayerManager
        if (_videoPlayerManager != null) {
            videoPlayerManager.releasePlayer()
        }
    }

    /**
     * Stop video playback and release resources.
     * Called when switching to non-media files (EPUB, PDF, Text, Image)
     */
    internal fun stopVideoPlayback() {
        Timber.d("PlayerActivity: stopVideoPlayback() CALLED")
        if (_videoPlayerManager != null) {
            Timber.d("PlayerActivity: videoPlayerManager is initialized, calling pause() and releasePlayer()")
            // Save position is handled by pause() (which calls saveCurrentPosition internally in VideoPlayerManager)
            videoPlayerManager.pause()
            videoPlayerManager.releasePlayer()
        } else {
             Timber.d("PlayerActivity: videoPlayerManager NOT initialized")
        }
    }

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
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val enablePip = currentSettings?.enablePictureInPicture == true
        pipManager?.onUserLeaveHint(enablePip)
    }

    @Suppress("DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        pipManager?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }
    
    // Removed: showLoadingDialog/dismissLoadingDialog/updateLoadingProgress
    // File list loading is fast (cached), no dialog needed
    // Image loading uses ProgressBar (showLoadingIndicatorRunnable with 2s delay)
    
    internal fun shareCurrentFile() {
        // Delegate to FileOperationsHandler
        fileOperationsHandler.performShare()
    }
    
    internal fun stopTranslation() = imageTranslationManager.stopTranslation()

    internal fun translateCurrentImage() = imageTranslationManager.translateCurrentImage()
    
    /**
     * Extract text from current image using OCR (no translation)
     */
    internal fun extractTextFromCurrentImage() {
        // Delegate OCR text extraction to ImageOcrManager
        // This consolidates bitmap extraction and OCR logic
        imageOcrManager.extractTextFromCurrentImage(viewModel.state.value.currentFile)
    }
    

    
    override fun onResume() {
        super.onResume()

        // Delegate to lifecycle manager
        lifecycleManager.onResume()
        audioEmptyStateController?.onResume()
        nowPlayingManager?.onStart(
            viewModel.state.value.currentFile?.type,
            viewModel.state.value.showNowPlayingPanel
        )

        // Re-attach PlayerView to the service MediaController if audio was playing in background.
        // This restores UI control visibility (seek bar, play/pause button) after the Activity
        // returns from background, without having interrupted service playback.
        if (::mediaLoaderManager.isInitialized) mediaLoaderManager.reattachServicePlayerToView()
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        UserActionLogger.logKey(keyCode, event?.action ?: KeyEvent.ACTION_DOWN, KeyEvent.keyCodeToString(keyCode), "PlayerActivity")
        // Handle Back/Escape for PDF fullscreen mode first
        if ((keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE) 
            && pdfViewerManager.isInFullscreenMode()) {
            pdfViewerManager.exitFullscreenMode()
            return true
        }
        // Delegate keyboard handling to PlayerKeyboardHandler
        return keyboardHandler.handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }
    
    override fun dispatchGenericMotionEvent(event: MotionEvent?): Boolean {
        // Delegate mouse scroll handling to PlayerKeyboardHandler for PDF/TXT
        return keyboardHandler.handleGenericMotionEvent(event) || super.dispatchGenericMotionEvent(event)
    }

    /**
     * Advance to next photo for audio background photos feature
     * Called by PlayerNavigationManager on slideshow timer tick
     */
    fun advanceAudioBackgroundPhoto() = audioSlideshowPhotoModeManager.advancePhoto()

    /**
     * Update current song label in audio slideshow photo mode.
     * Called by PlayerNavigationManager on track change.
     */
    internal fun updateAudioSlideshowCurrentSongLabel() = audioSlideshowPhotoModeManager.updateCurrentSongLabel()

    override fun onDestroy() {
        // Dismiss any open error dialogs before destroying the Activity window
        // to prevent WindowLeaked errors (dialog shown just before onDestroy).
        if (::eventHandler.isInitialized) eventHandler.onDestroy()
        lifecycleManager.onDestroy()
        super.onDestroy()
    }
    
    private fun setupGoogleLensButtons() {
        // Delegate to GoogleLensButtonsManager
        googleLensButtonsManager.setupButtons()
    }
    
    internal fun shareCurrentFileToGoogleLens() = shareManager.shareCurrentFileToGoogleLens()
    
    /**
     * Check if translation/OCR overlays are blocking PDF/EPUB touch zones
     */
    internal fun isOverlayBlocking(): Boolean {
        return safeViews.translationOverlay.isVisible ||
               binding.translationLensOverlay.isVisible ||
               safeViews.textViewerContainer.isVisible ||  // OCR result window
               safeViews.lyricsViewerContainer.isVisible   // Lyrics viewer window
    }
    
    /**
     * Get whether touch zones are enabled.
     * Used by PlayerGestureSetupManager to determine touch zone behavior.
     */
    internal fun getTouchZonesEnabled(): Boolean {
        return useTouchZones
    }
    
    /**
     * Get whether audio slideshow photo mode is active.
     * Used by PlayerGestureSetupManager to allow tap-to-exit in this mode.
     */
    internal fun isInAudioSlideshowPhotoMode(): Boolean {
        return audioSlideshowPhotoModeManager.isActive
    }
    
    /**
     * Callback from ImageLoadingManager when audio metadata is loaded from online source
     */
    fun onAudioMetadataLoaded(metadata: com.sza.fastmediasorter.domain.model.AudioMetadata) {
        // Cache metadata for lyrics search (keyed by current file path)
        val currentFile = viewModel.state.value.currentFile
        if (currentFile != null) {
            cachedAudioMetadataPath = currentFile.path
            cachedAudioMetadata = metadata
            Timber.d("Cached AudioMetadata for lyrics: artist='${metadata.artistName}', title='${metadata.trackName}' (file: ${currentFile.name})")
        }
        
        runOnUiThread {
            // Build single-line "Artist – Album (Year) – Title" (en-dash separated)
            val albumPart = when {
                metadata.albumName != null && metadata.releaseYear != null -> "${metadata.albumName} (${metadata.releaseYear})"
                metadata.albumName != null -> metadata.albumName
                else -> null
            }
            val parts = listOfNotNull(
                metadata.artistName?.takeIf { it.isNotBlank() },
                albumPart,
                metadata.trackName?.takeIf { it.isNotBlank() }
            )
            val metadataLine = parts.joinToString(" \u2013 ")

            if (metadataLine.isNotBlank()) {
                safeViews.audioMetadata.text = metadataLine
                safeViews.audioMetadata.visibility = View.VISIBLE
                safeViews.audioFileName.visibility = View.GONE
                Timber.d("Audio metadata displayed: $metadataLine")

                // Update song label in audio slideshow photo mode if active
                if (audioSlideshowPhotoModeManager.isActive) {
                    audioSlideshowPhotoModeManager.updateCurrentSongLabel()
                }
            }
            // else: keep existing embedded metadata visible, do nothing
        }
    }

    companion object {
        private const val SMALL_CONTROLS_SCALE = 0.5f
        private const val VIDEO_CONTROLS_AUTO_HIDE_DELAY_MS = 15000L // 15 seconds
        const val EXTRA_MODIFIED_FILES = "modified_files"

        fun createIntent(
            context: Context,
            resourceId: Long,
            initialIndex: Int = 0,
            skipAvailabilityCheck: Boolean = false,
            initialFilePath: String? = null,
            isPlaying: Boolean? = null,
            isSlideshowEnabled: Boolean = false,
            shuffleOnStart: Boolean = false
        ): Intent {
            // PLAYER_ACTIVITY_CLASS is set per-flavor in build.gradle.kts.
            // VR flavor routes here to VrPlayerActivity (OpenXR host); all other
            // flavors resolve to PlayerActivity. Using Class.forName avoids a direct
            // compile-time dependency on VrPlayerActivity from the main source set.
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
            }
        }
    }
}
