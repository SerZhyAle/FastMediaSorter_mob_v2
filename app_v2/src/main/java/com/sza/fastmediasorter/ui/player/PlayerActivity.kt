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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.sza.fastmediasorter.utils.UserActionLogger
import com.sza.fastmediasorter.core.cache.MediaFilesCacheManager
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt

@AndroidEntryPoint
class PlayerActivity : BaseActivity<ActivityPlayerUnifiedBinding>() {
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
                _videoPlayerManager = createVideoPlayerManager()
            }
            return _videoPlayerManager!!
        }
    
    internal lateinit var fileOperationsHandler: FileOperationsHandler
    internal lateinit var destinationButtonsManager: DestinationButtonsManager
    internal lateinit var navigationManager: PlayerNavigationManager
    internal lateinit var commandPanelController: CommandPanelController
    internal lateinit var imageLoadingManager: ImageLoadingManager
    private var audioEmptyStateController: com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController? = null
    private lateinit var mediaLoaderManager: com.sza.fastmediasorter.ui.player.helpers.PlayerMediaLoaderManager
    internal var audioServiceController: com.sza.fastmediasorter.ui.player.helpers.AudioServiceController? = null
    internal var nowPlayingManager: com.sza.fastmediasorter.ui.player.helpers.NowPlayingManager? = null
    internal var sleepTimerManager: com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager? = null
    private var pipManager: com.sza.fastmediasorter.ui.player.helpers.PictureInPictureManager? = null
    internal val safeViews by lazy { PlayerBindingSafeViews(binding) }
    private lateinit var dialogAndUiStateManager: PlayerDialogAndUiStateManager

    internal lateinit var audioSlideshowPhotoModeManager: com.sza.fastmediasorter.ui.player.helpers.AudioSlideshowPhotoModeManager
    private lateinit var keyboardHandler: com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler
    internal lateinit var networkFileManager: com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
    
    // LAZY INITIALIZATION: Document viewers only created when needed
    internal var _pdfViewerManager: com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager? = null
    internal val pdfViewerManager: com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager
        get() {
            if (_pdfViewerManager == null) {
                Timber.d("PERFORMANCE: Lazy initializing PdfViewerManager")
                _pdfViewerManager = createPdfViewerManager()
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
                _epubViewerManager = createEpubViewerManager()
            }
            return _epubViewerManager!!
        }
    
    internal var _textViewerManager: com.sza.fastmediasorter.ui.player.helpers.TextViewerManager? = null
    internal val textViewerManager: com.sza.fastmediasorter.ui.player.helpers.TextViewerManager
        get() {
            if (_textViewerManager == null) {
                Timber.d("PERFORMANCE: Lazy initializing TextViewerManager")
                _textViewerManager = createTextViewerManager()
            }
            return _textViewerManager!!
        }
    private lateinit var uiStateCoordinator: com.sza.fastmediasorter.ui.player.helpers.PlayerUiStateCoordinator
    internal lateinit var undoOperationManager: com.sza.fastmediasorter.ui.player.helpers.UndoOperationManager
    internal lateinit var playerSettingsManager: com.sza.fastmediasorter.ui.player.helpers.PlayerSettingsManager
    internal lateinit var cloudAuthManager: com.sza.fastmediasorter.ui.browse.managers.BrowseCloudAuthManager
    internal lateinit var translationManager: com.sza.fastmediasorter.ui.player.helpers.TranslationManager
    private lateinit var touchZoneGestureManager: com.sza.fastmediasorter.ui.player.helpers.TouchZoneGestureManager
    internal lateinit var translationButtonManager: com.sza.fastmediasorter.ui.player.helpers.TranslationButtonManager
    private lateinit var exoPlayerControlsManager: com.sza.fastmediasorter.ui.player.helpers.ExoPlayerControlsManager
    internal lateinit var searchControlsManager: com.sza.fastmediasorter.ui.player.helpers.SearchControlsManager
    internal lateinit var lifecycleManager: com.sza.fastmediasorter.ui.player.helpers.PlayerLifecycleManager
    private lateinit var controlsSetupManager: com.sza.fastmediasorter.ui.player.helpers.PlayerControlsSetupManager
    private lateinit var gestureSetupManager: com.sza.fastmediasorter.ui.player.helpers.PlayerGestureSetupManager
    private lateinit var imageOcrManager: com.sza.fastmediasorter.ui.player.helpers.ImageOcrManager
    private lateinit var lyricsManager: com.sza.fastmediasorter.ui.player.helpers.LyricsManager
    private lateinit var googleLensButtonsManager: com.sza.fastmediasorter.ui.player.helpers.GoogleLensButtonsManager
    private lateinit var systemBarsManager: com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager
    internal lateinit var imageTranslationManager: com.sza.fastmediasorter.ui.player.helpers.PlayerImageTranslationManager
    internal lateinit var shareManager: com.sza.fastmediasorter.ui.player.helpers.PlayerShareManager
    internal lateinit var printManager: com.sza.fastmediasorter.ui.player.helpers.DocumentPrintManager
    internal lateinit var eventHandler: com.sza.fastmediasorter.ui.player.helpers.PlayerEventHandler
    internal lateinit var castMediaManager: com.sza.fastmediasorter.ui.player.helpers.CastMediaManager

    private val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (::cloudAuthManager.isInitialized) {
            cloudAuthManager.handleGoogleSignInResult(result.data)
        }
    }
    
    // For handling Android 11+ batch delete permission requests (createDeleteRequest)
    private val batchDeletePermissionLauncher = registerForActivityResult(
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
    private val preloadJobs = mutableMapOf<String, Job>()
    private lateinit var gestureDetector: GestureDetector
    private val touchZoneDetector = TouchZoneDetector()
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
    
    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        // Handle mouse wheel scroll
        if (event.action == android.view.MotionEvent.ACTION_SCROLL) {
            val verticalScroll = event.getAxisValue(android.view.MotionEvent.AXIS_VSCROLL)
            if (verticalScroll != 0f) {
                val currentType = viewModel.state.value.currentFile?.type
                
                // For documents, scroll within the document
                when (currentType) {
                    MediaType.PDF -> {
                        if (_pdfViewerManager != null) {
                            if (verticalScroll > 0) {
                                pdfViewerManager.showPreviousPage()
                            } else {
                                pdfViewerManager.showNextPage()
                            }
                            return true
                        }
                    }
                    MediaType.TEXT -> {
                        if (_textViewerManager != null) {
                            textViewerManager.handleMouseWheelScroll(verticalScroll)
                            return true
                        }
                    }
                    MediaType.EPUB -> {
                        if (_epubViewerManager != null) {
                            if (verticalScroll > 0) {
                                epubViewerManager.showPreviousChapter()
                            } else {
                                epubViewerManager.showNextChapter()
                            }
                            return true
                        }
                    }
                    else -> {
                        // For other media types (images, videos), navigate between files
                        navigationManager.handleMouseWheelScroll(verticalScroll)
                        return true
                    }
                }
            }
        }
        return super.onGenericMotionEvent(event)
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
        observeViewModel()
        
        // Set initial system bars state based on command panel visibility
        // Wait for layout to ensure managers are ready
        binding.root.post {
            val showCommandPanel = viewModel.state.value.showCommandPanel
            updateSystemBarsForPlayer(showCommandPanel)
        }
    }
    
    /**
     * Initialize all helper managers and controllers.
     * Centralized initialization to keep onCreate clean and organized.
     */
    private fun initializeManagers() {
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
    }

    private fun initBackgroundMedia() {
        // Initialize Background Music Manager
        backgroundMusicManager.initialize()
        
        // Set listener for background music track name display
        backgroundMusicManager.setOnTrackChangedListener { trackName ->
            runOnUiThread {
                updateBackgroundMusicTrackDisplay(trackName)
            }
        }
        
        // Set listener for background music errors
        backgroundMusicManager.setOnMusicErrorListener { errorMessage ->
            runOnUiThread {
                Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
            }
        }
        
        // Click on track name to skip to another random track
        safeViews.tvBackgroundMusicTrack.setOnClickListener {
            Timber.d("BackgroundMusic: User clicked track name - skipping to next random")
            backgroundMusicManager.skipToNextRandomTrack()
        }

        // Initialize Audio Background Photos Manager
        audioBackgroundPhotosManager.initialize()
        audioBackgroundPhotosManager.setOnPhotoChangedListener { photo ->
            if (photo != null && audioSlideshowPhotoModeManager.isActive) {
                audioSlideshowPhotoModeManager.loadBackgroundPhoto(photo)
                audioSlideshowPhotoModeManager.updatePhotoLabel(photo)
            } else if (photo == null) {
                // Clear ImageView when feature is deactivated
                binding.imageView.setImageDrawable(null)
                if (audioSlideshowPhotoModeManager.isActive) {
                    audioSlideshowPhotoModeManager.updatePhotoLabel(null)
                    audioSlideshowPhotoModeManager.exit()
                }
            }
        }
        audioBackgroundPhotosManager.setOnErrorListener { errorMessage ->
            Toast.makeText(this@PlayerActivity, errorMessage, Toast.LENGTH_SHORT).show()
        }
    }

    private fun initCoreCoordination() {
        cloudAuthManager = com.sza.fastmediasorter.ui.browse.managers.BrowseCloudAuthManager(
            context = this,
            coroutineScope = lifecycleScope,
            googleDriveClient = googleDriveClient,
            dropboxClient = dropboxClient,
            oneDriveClient = oneDriveClient,
            googleSignInLauncher = googleSignInLauncher,
            callbacks = object : com.sza.fastmediasorter.ui.browse.managers.BrowseCloudAuthManager.CloudAuthCallbacks {
                override fun onAuthenticationSuccess() {
                    // Authentication successful
                }
                override fun onAuthenticationFailure() {
                    // Handled by manager
                }
            }
        )
        
        // Initialize Navigation Manager (includes SlideshowController)
        navigationManager = PlayerNavigationManager(
            activity = this,
            viewModel = viewModel,
            lifecycle = lifecycle
        )
        slideshowController = navigationManager.getSlideshowController()

        keyboardHandler = com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler(
            viewModel = viewModel,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerKeyboardCallbackImpl(
                activity = this,
                viewModel = viewModel
            )
        )

        uiStateCoordinator = com.sza.fastmediasorter.ui.player.helpers.PlayerUiStateCoordinator(
            binding = binding,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerUiStateCoordinatorCallbackImpl(
                activity = this,
                viewModel = viewModel
            )
        )

        undoOperationManager = com.sza.fastmediasorter.ui.player.helpers.UndoOperationManager(
            rootView = binding.root,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.UndoOperationManager.Callback {
                override fun isActivityAlive(): Boolean = !(isFinishing || isDestroyed)

                override fun getUndoActionText(): String =
                    com.sza.fastmediasorter.ui.player.helpers.UndoOperationManager.defaultUndoActionText(binding.root)

                override fun onUndoRequested() {
                    viewModel.undoLastOperation()
                }
            }
        )
        
        // Initialize SystemBarsManager for fullscreen mode
        systemBarsManager = com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager(
            activity = this
        )

        imageTranslationManager = com.sza.fastmediasorter.ui.player.helpers.PlayerImageTranslationManager(activity = this)
        shareManager = com.sza.fastmediasorter.ui.player.helpers.PlayerShareManager(activity = this)
        printManager = com.sza.fastmediasorter.ui.player.helpers.DocumentPrintManager(activity = this)
        eventHandler = com.sza.fastmediasorter.ui.player.helpers.PlayerEventHandler(activity = this)
    }

    private fun initDialogHelper() {
        dialogHelper = PlayerDialogHelper(
            activity = this,
            viewModel = viewModel,
            settingsRepository = settingsRepository,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            credentialsRepository = credentialsRepository,
            unifiedCache = unifiedCache,
            rotateImageUseCase = rotateImageUseCase,
            flipImageUseCase = flipImageUseCase,
            networkImageEditUseCase = networkImageEditUseCase,
            applyImageFilterUseCase = applyImageFilterUseCase,
            adjustImageUseCase = adjustImageUseCase,
            extractGifFramesUseCase = extractGifFramesUseCase,
            saveGifFirstFrameUseCase = saveGifFirstFrameUseCase,
            changeGifSpeedUseCase = changeGifSpeedUseCase,
            downloadNetworkFileUseCase = downloadNetworkFileUseCase,
            dialogCallback = object : PlayerDialogHelper.DialogCallback {
                override fun onImageEditComplete() {
                    // Update file size in ViewModel (triggers cache invalidation due to size change)
                    viewModel.refreshCurrentFileInfo()

                    // Reload image after edit to show changes
                    reloadCurrentImage()
                    Toast.makeText(this@PlayerActivity, getString(R.string.msg_image_edit_completed), Toast.LENGTH_SHORT).show()
                }

                override fun onGifEditComplete() {
                    // Update file size in ViewModel (GIF speed change modifies file)
                    viewModel.refreshCurrentFileInfo()

                    // Reload GIF after speed change
                    reloadCurrentImage()
                    Toast.makeText(this@PlayerActivity, R.string.gif_edit_completed, Toast.LENGTH_SHORT).show()
                }

                override fun onRenameComplete() {
                    // Reload file in player after rename
                    viewModel.reloadAfterRename()
                }
            },
            videoPlayerManagerProvider = { videoPlayerManager },
            textViewerManagerProvider = { textViewerManager },
            sleepTimerManagerProvider = { sleepTimerManager }
        )
        
        dialogHelper.setAuthCallback { provider ->
            when (provider.lowercase()) {
                "dropbox" -> cloudAuthManager.launchDropboxSignIn()
                "google drive", "google_drive" -> cloudAuthManager.launchGoogleSignIn()
                "onedrive" -> cloudAuthManager.launchOneDriveSignIn()
                else -> Timber.w("Unknown provider for auth request: $provider")
            }
        }
    }

    private fun initFileOps() {
        fileOperationsHandler = FileOperationsHandler(
            context = this,
            lifecycleScope = lifecycleScope,
            settingsRepository = settingsRepository,
            fileOperationUseCase = viewModel.fileOperationUseCase,
            callback = object : FileOperationsHandler.FileOperationCallback {
                override fun onCopySuccess(destination: MediaResource, goToNext: Boolean) {
                    if (goToNext) {
                        navigationManager.navigateNextAfterOperation("Copy success with goToNext=true")
                    }
                }
                
                override fun onMoveSuccess(destination: MediaResource, movedFilePath: String, goToNext: Boolean) {
                    // Track moved file
                    lifecycleManager.trackModifiedFile(movedFilePath)
                    
                    // Remove from cache
                    viewModel.state.value.resource?.let { resource ->
                        MediaFilesCacheManager.removeFile(resource.id, movedFilePath)
                    }
                    
                    // Remove from ViewModel list and navigate
                    val hasRemainingFiles = viewModel.removeMovedFile(movedFilePath)
                    if (!hasRemainingFiles) {
                        finish()
                    } else if (goToNext) {
                        navigationManager.navigateNextAfterOperation("Move success with goToNext=true")
                    }
                }
                
                override fun onDeleteSuccess(deletedFilePath: String) {
                    handleDeleteSuccess(deletedFilePath)
                }
                
                override fun onOperationError(message: String, throwable: Throwable?) {
                    showError(message, throwable)
                }
                
                override fun onAuthenticationRequired(provider: String, message: String) {
                    showCloudAuthenticationError(provider)
                }
                
                override fun onBatchDeletePermissionRequired(pendingIntent: android.app.PendingIntent) {
                    // Android 11+ batch delete - launch system permission dialog
                    timber.log.Timber.i("PlayerActivity: Launching batch delete permission dialog")
                    try {
                        batchDeletePermissionLauncher.launch(
                            androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                        )
                    } catch (e: Exception) {
                        timber.log.Timber.e(e, "PlayerActivity: Failed to launch batch delete permission request")
                        showError(getString(R.string.error_delete_failed, e.message))
                    }
                }
                
                override fun getCurrentFile(): MediaFile? {
                    return viewModel.state.value.currentFile
                }
                
                override fun getCurrentResource(): MediaResource? {
                    return viewModel.state.value.resource
                }
            }
        )
        
        // Initialize DestinationButtonsManager
        destinationButtonsManager = DestinationButtonsManager(
            binding = binding,
            settingsRepository = settingsRepository,
            getDestinationsUseCase = viewModel.getDestinationsUseCase,
            lifecycleScope = lifecycleScope,
            callback = object : DestinationButtonsManager.DestinationButtonsCallback {
                override fun onCopyClicked(destination: MediaResource) {
                    fileOperationsHandler.performCopy(destination)
                }
                
                override fun onMoveClicked(destination: MediaResource) {
                    Timber.d("PlayerActivity: onMoveClicked - destination=${destination.name}")
                    performMoveOperation(destination)
                }
                
                override fun getCurrentResourceId(): Long {
                    return intent.getLongExtra("resourceId", -1)
                }
                
                override fun onUpdateCommandAvailability() {
                    val state = viewModel.state.value
                    Timber.d("PlayerActivity.onUpdateCommandAvailability: showCommandPanel=${state.showCommandPanel}, enableCopying=${state.enableCopying}, enableMoving=${state.enableMoving}")
                    updateCommandAvailability(state)
                }
                
                override fun isCommandPanelVisible(): Boolean {
                    // Mirror the audio override from updatePanelVisibility:
                    // audio files always show the command panel
                    val state = viewModel.state.value
                    return state.showCommandPanel || state.currentFile?.type == MediaType.AUDIO
                }
            }
        )
        
    }

    private fun initCommandPanelAndImageLoading() {
        commandPanelController = CommandPanelController(
            binding = binding,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerCommandPanelCallbackImpl(
                activity = this,
                viewModel = viewModel
            )
        )
        // Initialize orientation on startup
        commandPanelController.updateOrientation(resources.configuration)

        imageLoadingManager = ImageLoadingManager(
            binding = binding,
            settingsRepository = settingsRepository,
            searchAudioCoverUseCase = searchAudioCoverUseCase,
            audioMetadataCacheRepository = audioMetadataCacheRepository,
            okHttpClient = okHttpClient,
            lifecycleScope = lifecycleScope,
            loadingIndicatorHandler = loadingIndicatorHandler,
            showLoadingIndicatorRunnable = showLoadingIndicatorRunnable,
            preloadJobs = preloadJobs,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerImageLoadingCallbackImpl(
                activity = this,
                viewModel = viewModel
            )
        )

        // Create and inject AudioEmptyStateController now that binding views are available
        audioEmptyStateController = com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController(
            context = this,
            audioCoverArtView = binding.audioCoverArtView,
            barsView = binding.audioBarsView,
            videoView = binding.audioVideoView,
            wavesView = binding.audioWaveParticleView
        )
        imageLoadingManager.setAudioEmptyStateController(audioEmptyStateController!!)
    }

    private fun initNetworkAndTranslation() {
        networkFileManager = com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager(
            context = this,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            googleDriveClient = googleDriveClient,
            dropboxClient = dropboxClient,
            oneDriveClient = oneDriveClient,
            credentialsRepository = credentialsRepository,
            smbFileOperationHandler = smbFileOperationHandler,
            sftpFileOperationHandler = sftpFileOperationHandler,
            ftpFileOperationHandler = ftpFileOperationHandler,
            cloudFileOperationHandler = cloudFileOperationHandler,
            unifiedCache = unifiedCache,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager.NetworkFileCallback {
                override fun getCurrentResource(): MediaResource? = viewModel.state.value.resource
                
                override fun showError(message: String) {
                    this@PlayerActivity.showError(message)
                }
            }
        )

        translationManager = com.sza.fastmediasorter.ui.player.helpers.TranslationManager(
            context = this,
            settingsRepository = settingsRepository,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.TranslationManager.TranslationCallback {
                override fun showError(message: String) {
                    this@PlayerActivity.showError(message)
                }
                
                override fun showModelDownloadPrompt(
                    languageName: String,
                    onConfirm: () -> Unit,
                    onCancel: () -> Unit
                ) {
                    runOnUiThread {
                        AlertDialog.Builder(this@PlayerActivity)
                            .setTitle("Download Translation Model")
                            .setMessage("Translation to $languageName requires downloading ~30MB model. Download now?")
                            .setPositiveButton("Download") { _, _ -> onConfirm() }
                            .setNegativeButton("Cancel") { _, _ -> onCancel() }
                            .setCancelable(false)
                            .show()
                    }
                }
            }
        )

    }

    private fun initPlayerControlsAndOcr() {
        // OPTIMIZATION: Document Viewers (PDF, EPUB, Text) and VideoPlayerManager use lazy initialization
        // They are created only when files of those types are opened (see createXxxManager() methods)
        playerGestureCallback = com.sza.fastmediasorter.ui.player.callbacks.PlayerGestureCallbackImpl(
            activity = this,
            viewModel = viewModel,
            binding = binding,
            pdfViewerManagerProvider = { pdfViewerManager },
            epubViewerManagerProvider = { epubViewerManager }
        )
        
        lyricsManager = com.sza.fastmediasorter.ui.player.helpers.LyricsManager(
            context = this,
            binding = binding,
            lifecycleScope = lifecycleScope,
            searchLyricsUseCase = searchLyricsUseCase,
            getTranslationSessionSettings = { translationSessionSettings }
        )
        
        gestureHelper = PlayerGestureHelper(
            context = this,
            gestureCallback = playerGestureCallback
        )

        touchZoneGestureManager = com.sza.fastmediasorter.ui.player.helpers.TouchZoneGestureManager(
            binding = binding,
            viewModel = viewModel,
            touchZoneDetector = touchZoneDetector,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerTouchZoneCallbackImpl(
                activity = this,
                viewModel = viewModel
            )
        )

        translationButtonManager = com.sza.fastmediasorter.ui.player.helpers.TranslationButtonManager(
            context = this,
            lifecycleOwner = this,
            binding = binding,
            settingsRepository = settingsRepository,
            callback = com.sza.fastmediasorter.ui.player.callbacks.PlayerTranslationButtonCallbackImpl(
                activity = this,
                viewModel = viewModel
            )
        )

        // OPTIMIZATION: VideoPlayerManager uses lazy initialization (see createVideoPlayerManager())
        // Only created when VIDEO/AUDIO file is opened

        playerSettingsManager = com.sza.fastmediasorter.ui.player.helpers.PlayerSettingsManager(
            activity = this,
            dialogHelper = dialogHelper,
            videoPlayerManagerProvider = { videoPlayerManager },
            settingsRepository = settingsRepository,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.PlayerSettingsManager.Callback {
                // Currently no callbacks needed
            }
        )

        exoPlayerControlsManager = com.sza.fastmediasorter.ui.player.helpers.ExoPlayerControlsManager(
            binding = binding,
            videoPlayerManager = videoPlayerManager,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.ExoPlayerControlsManager.ExoPlayerControlsCallback {
                override fun onPreviousFile() = navigationManager.navigatePreviousFromControl()
                override fun onNextFile() = navigationManager.navigateNextFromControl()
                override fun showPlaybackSpeedDialog() = playerSettingsManager.showPlaybackSpeedDialog()
                override fun showAudioTrackDialog() = dialogHelper.showAudioTrackDialog()
                override fun showSubtitleTrackDialog() = dialogHelper.showSubtitleTrackDialog()
            }
        )
        
        searchControlsManager = com.sza.fastmediasorter.ui.player.helpers.SearchControlsManager(
            binding = binding,
            textViewerManagerProvider = { textViewerManager },
            pdfViewerManagerProvider = { pdfViewerManager },
            epubViewerManagerProvider = { epubViewerManager },
            lifecycleScope = lifecycleScope,
            inputMethodManager = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.SearchControlsManager.SearchControlsCallback {
                override fun getCurrentMediaFile() = viewModel.state.value.currentFile
                override fun scheduleHideControls() = this@PlayerActivity.scheduleHideControls()
                override fun onEpubTranslate() {
                    if (_epubViewerManager != null) epubViewerManager.toggleTranslation()
                }
                override fun showTranslationSettingsDialog() = translationButtonManager.showTranslationSettingsDialog()
            }
        )

        imageOcrManager = com.sza.fastmediasorter.ui.player.helpers.ImageOcrManager(
            binding = binding,
            lifecycleScope = lifecycleScope,
            settingsRepository = settingsRepository,
            translationManager = translationManager,
            textViewerManager = textViewerManager,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.ImageOcrManager.ImageOcrCallback {
                override fun showError(message: String) {
                    this@PlayerActivity.showError(message)
                }
                override fun getString(resId: Int): String {
                    return this@PlayerActivity.getString(resId)
                }
                override fun getString(resId: Int, vararg formatArgs: Any): String {
                    return this@PlayerActivity.getString(resId, *formatArgs)
                }
            }
        )

        googleLensButtonsManager = com.sza.fastmediasorter.ui.player.helpers.GoogleLensButtonsManager(
            binding = binding,
            onShareToGoogleLens = { shareCurrentFileToGoogleLens() },
            onSharePdfPageToGoogleLens = { 
                if (_pdfViewerManager != null) {
                    pdfViewerManager.shareCurrentPageToGoogleLens()
                }
            },
            onExtractImageText = { extractTextFromCurrentImage() },
            onExtractPdfText = { 
                if (_pdfViewerManager != null) {
                    pdfViewerManager.extractTextFromCurrentPage()
                }
            },
            onExtractEpubText = { 
                if (_epubViewerManager != null) {
                    epubViewerManager.extractTextFromCurrentChapter()
                }
            },
            onShowTranslationSettings = { 
                if (::translationButtonManager.isInitialized) {
                    translationButtonManager.showTranslationSettingsDialog()
                }
            }
        )

    }

    private fun initAudioAndMediaServices() {
        castMediaManager = com.sza.fastmediasorter.ui.player.helpers.CastMediaManager(
            context = this,
            lifecycleScope = lifecycleScope,
            onCastStateChanged = { isCasting, deviceName ->
                viewModel.updateCastState(isCasting, deviceName)
                if (isCasting) {
                    val currentFile = viewModel.state.value.currentFile
                    if (currentFile != null) castMediaManager.sendCurrentMedia(currentFile)
                }
            }
        )
        castMediaManager.init()
        audioServiceController = com.sza.fastmediasorter.ui.player.helpers.AudioServiceController(this)
        nowPlayingManager = com.sza.fastmediasorter.ui.player.helpers.NowPlayingManager(
            activityBinding = binding,
            fragmentManager = supportFragmentManager,
            audioServiceController = audioServiceController!!
        )
        sleepTimerManager = com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager(
            vinylView = binding.vinylIndicator,
            sleepTimerBadge = binding.sleepTimerBadge,
            playerProvider = { videoPlayerManager.getPlayer() }
        )
        pipManager = com.sza.fastmediasorter.ui.player.helpers.PictureInPictureManager(
            activity = this,
            binding = binding,
            videoPlayerManager = videoPlayerManager,
            isVideoPlaying = {
                val currentFile = viewModel.state.value.currentFile
                currentFile?.type == MediaType.VIDEO && videoPlayerManager.getPlayer()?.isPlaying == true
            }
        )
        mediaLoaderManager = com.sza.fastmediasorter.ui.player.helpers.PlayerMediaLoaderManager(
            activity = this,
            binding = binding,
            viewModel = viewModel,
            imageLoadingManager = imageLoadingManager,
            videoPlayerManager = videoPlayerManager,
            pdfViewerManager = pdfViewerManager,
            epubViewerManager = epubViewerManager,
            textViewerManager = textViewerManager,
            exoPlayerControlsManager = exoPlayerControlsManager,
            lifecycleScope = lifecycleScope,
            loadingIndicatorHandler = loadingIndicatorHandler,
            showLoadingIndicatorRunnable = showLoadingIndicatorRunnable,
            mediaFilesCacheManager = mediaFilesCacheManager,
            audioServiceController = audioServiceController,
            onAudioServicePlaybackChanged = { isPlaying ->
                val isAudioFile = viewModel.state.value.currentFile?.type == MediaType.AUDIO
                sleepTimerManager?.updateVinylState(isPlaying, isAudioFile)
                if (isAudioFile) {
                    audioEmptyStateController?.onIsPlayingChanged(isPlaying)
                }
            },
            onAudioServiceReady = {
                val currentFile = viewModel.state.value.currentFile
                if (currentFile?.type == MediaType.AUDIO) {
                    updateAudioFormatInfo()
                    imageLoadingManager.loadAudioCoverArt(currentFile)
                    prefetchNextAudio()
                    // Refresh song label in audio slideshow photo mode (covers auto-advance case)
                    updateAudioSlideshowCurrentSongLabel()
                }
            },
            onAudioServicePlaybackEnded = {
                // Read and reset direction flag set by ForwardingPlayer for NEXT/PREV buttons
                val direction = AudioPlaybackService.pendingDirection
                AudioPlaybackService.pendingDirection = AudioPlaybackService.DIRECTION_NEXT

                val wasAudio = viewModel.state.value.currentFile?.type == MediaType.AUDIO
                if (viewModel.state.value.isSlideShowActive) {
                    viewModel.nextFile(skipDocuments = true)
                    slideshowController.restartTimer()
                } else if (direction == AudioPlaybackService.DIRECTION_PREV) {
                    viewModel.previousFile()
                } else {
                    viewModel.nextFile()
                }
                // Advance background photo on audio track auto-advance (mirrors navigateNext behaviour)
                if (wasAudio) {
                    advanceAudioBackgroundPhoto()
                }
            },
            onAudioServicePlaybackError = { _ ->
                handleMediaLoadErrorAndSkip()
            }
        )
        
    }

    private fun initUiCoordinators() {
        dialogAndUiStateManager = PlayerDialogAndUiStateManager(
            activity = this,
            viewModel = viewModel,
            binding = binding,
            dialogHelper = dialogHelper,
            destinationButtonsManager = destinationButtonsManager,
            commandPanelController = commandPanelController,
            textViewerManager = textViewerManager,
            mediaLoaderManager = mediaLoaderManager,
            networkFileManager = networkFileManager,
            imageLoadingManager = imageLoadingManager,
            lifecycleScope = lifecycleScope
        )

        audioSlideshowPhotoModeManager = com.sza.fastmediasorter.ui.player.helpers.AudioSlideshowPhotoModeManager(
            activity = this,
            binding = binding,
            viewModel = viewModel,
            audioBackgroundPhotosManager = audioBackgroundPhotosManager,
            backgroundMusicManager = backgroundMusicManager,
            dialogAndUiStateManager = dialogAndUiStateManager,
            settingsRepository = settingsRepository,
            lifecycleScope = lifecycleScope,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.AudioSlideshowPhotoModeManager.Callback {
                override fun updateSlideShowButton() = this@PlayerActivity.updateSlideShowButton()
                override fun updateSystemBarsForPlayer(showCommandPanel: Boolean) = this@PlayerActivity.updateSystemBarsForPlayer(showCommandPanel)
                override fun toggleSlideshow() = navigationManager.toggleSlideshow()
                override fun updateSlideshowState() = navigationManager.updateSlideshowState()
                override fun getSupportActionBar() = this@PlayerActivity.supportActionBar
            }
        )

        // Wire audioSlideshowPhotoModeManager into dialogAndUiStateManager (created earlier)
        dialogAndUiStateManager.audioSlideshowPhotoModeManager = audioSlideshowPhotoModeManager
    }

    private fun initSetupManagers() {
        controlsSetupManager = com.sza.fastmediasorter.ui.player.helpers.PlayerControlsSetupManager(
            activity = this,
            binding = binding,
            viewModel = viewModel,
            lifecycleScope = lifecycleScope,
            slideshowController = slideshowController,
            pdfViewerManager = pdfViewerManager,
            epubViewerManager = epubViewerManager,
            textViewerManager = textViewerManager,
            translationManager = translationManager,
            translationButtonManager = translationButtonManager,
            exoPlayerControlsManager = exoPlayerControlsManager,
            searchControlsManager = searchControlsManager
        )
        
        gestureSetupManager = com.sza.fastmediasorter.ui.player.helpers.PlayerGestureSetupManager(
            activity = this,
            binding = binding,
            viewModel = viewModel,
            touchZoneGestureManager = touchZoneGestureManager
        )
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
        setupBackPressHandler()
    }
    
    /**
     * Setup back press handler for PDF fullscreen mode
     */
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Check if PDF fullscreen mode is active
                if (_pdfViewerManager != null && pdfViewerManager.isInFullscreenMode()) {
                    pdfViewerManager.exitFullscreenMode()
                    return
                }
                // Check if EPUB fullscreen mode is active
                if (_epubViewerManager != null && epubViewerManager.isInFullscreenMode()) {
                    epubViewerManager.onExitFullscreenRequest()
                    return
                }
                
                // Check if overlays are blocking (Translation, OCR, Lyrics)
                if (isOverlayBlocking()) {
                    if (safeViews.translationOverlay.isVisible || binding.translationLensOverlay.isVisible) {
                        stopTranslation()
                        return
                    }
                    if (safeViews.textViewerContainer.isVisible) {
                        safeViews.textViewerContainer.isVisible = false
                        return
                    }
                    if (safeViews.lyricsViewerContainer.isVisible) {
                        hideLyricsViewer()
                        return
                    }
                }

                // Check if background audio service is playing — ask user to stop or keep
                exitPlayerWithAudioCheck()
            }
        })
    }

    override fun observeData() {
        observeViewModel()
    }

    /**
     * Exit the player, showing a dialog if background audio is playing.
     * "Stop" → stops the service and finishes. "Keep Playing" → finishes without stopping service.
     * @param withTransition whether to apply slide-out transition on exit
     */
    internal fun exitPlayerWithAudioCheck(withTransition: Boolean = false) {
        if (::mediaLoaderManager.isInitialized && mediaLoaderManager.isServiceAudioActive) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.background_audio_exit_title)
                .setMessage(R.string.background_audio_exit_message)
                .setNegativeButton(R.string.background_audio_exit_stop) { _, _ ->
                    audioServiceController?.player?.stop()
                    doFinish(withTransition)
                }
                .setPositiveButton(R.string.background_audio_exit_continue) { _, _ ->
                    doFinish(withTransition)
                }
                .show()
        } else {
            doFinish(withTransition)
        }
    }

    private fun doFinish(withTransition: Boolean) {
        viewModel.clearResumeState()
        finish()
        if (withTransition) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun setupGestureDetector() {
        Timber.d("TOUCH_DEBUG: PlayerActivity.setupGestureDetector() CALLED - delegating to gestureSetupManager")
        // Delegate all gesture detector and touch listener setup to PlayerGestureSetupManager
        // This consolidates 190 lines of complex touch zone logic
        gestureSetupManager.setupGestureDetector()
        Timber.d("TOUCH_DEBUG: PlayerActivity.setupGestureDetector() COMPLETED")
    }
    
    /**
     * Handle touch zones for static images (3x3 grid)
     * For video: only upper 75% of screen is touch-sensitive (lower 25% reserved for ExoPlayer controls)
     * For audio: only upper 66% of screen is touch-sensitive (lower 34% reserved for ExoPlayer controls)
     */
    internal fun showCopyDialog() {
        dialogAndUiStateManager.showCopyDialog()
    }
    
    internal fun showMoveDialog() {
        dialogAndUiStateManager.showMoveDialog()
    }
    
    internal fun showRenameDialog() {
        dialogAndUiStateManager.showRenameDialog()
    }

    internal fun showEncodingDialog() = dialogHelper.showEncodingDialog()

    internal fun showReaderSettingsDialog() = dialogHelper.showReaderSettingsDialog()

    internal fun showSleepTimerDialog() = dialogHelper.showSleepTimerDialog()

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

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state
                        .distinctUntilChangedBy { 
                            // Track showCommandPanel to trigger UI updates on fullscreen/panel mode changes
                            // Also track isFavorite to update star icon
                            Triple(
                                Triple(it.currentIndex, it.currentFile?.path, it.isSlideShowActive),
                                it.showCommandPanel,
                                it.currentFile?.isFavorite
                            )
                        }
                        .collect { state ->
                            updateUI(state)
                            backgroundMusicManager.updateState(state)
                            audioBackgroundPhotosManager.updateState(state)
                        }
                }

                launch {
                    viewModel.loading.collect { isLoading ->
                        // Don't override progressBar for PDF/EPUB - they manage it themselves
                        val currentType = viewModel.state.value.currentFile?.type
                        if (currentType != MediaType.PDF && currentType != MediaType.EPUB) {
                            binding.progressBar.isVisible = isLoading
                        }
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        handleEvent(event)
                    }
                }
                
                // Observe settings to show/hide favorite button and update touch zones mode
                launch {
                    combine(
                        settingsRepository.getSettings().distinctUntilChanged(),
                        viewModel.state.distinctUntilChangedBy { it.resource?.id }
                    ) { settings, state ->
                        // Cache settings for overlay visibility and touch zones
                        currentSettings = settings
                        loadFullSizeImages = settings.loadFullSizeImages
                        
                        // Enable/disable the dynamic background extension effect
                        imageLoadingManager.setDynamicBackgroundEnabled(settings.dynamicBackgroundExtension)
                        
                        // Setup PiP button visibility based on settings
                        pipManager?.setupPipButton(settings.enablePictureInPicture)
                        
                        // Show favorite button if:
                        // 1. enableFavorites setting is on, OR
                        // 2. Currently viewing Favorites resource (id = -100)
                        settings.enableFavorites || state.resource?.id == -100L
                    }.collect { shouldShow ->
                        binding.btnFavorite.isVisible = shouldShow
                        // Note: updateUI() is called by main state.collect above - no need to call here
                        // Removing duplicate updateUI() call to prevent excessive UI redraws
                    }
                }

                // Removed: loading dialog during file list load (not needed - single file loads fast)
                // Image loading progress handled by showLoadingIndicatorRunnable with 2s delay
            }
        }
    }

    private fun updateUI(state: PlayerViewModel.PlayerState) {
        uiStateCoordinator.updateUI(state)
        // Stop vinyl animation when switching to non-audio file
        if (state.currentFile?.type != MediaType.AUDIO) {
            sleepTimerManager?.stopVinylAnimation()
        }
        // GUARD: After state observers update views, re-enforce audio slideshow photo mode UI
        if (audioSlideshowPhotoModeManager.isActive) {
            audioSlideshowPhotoModeManager.enforceUI()
        }
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
     * Toggle Copy to panel collapsed/expanded state
     */
    internal fun toggleCopyPanel() {
        dialogAndUiStateManager.toggleCopyPanel()
    }

    /**
     * Toggle Move to panel collapsed/expanded state
     */
    internal fun toggleMovePanel() {
        dialogAndUiStateManager.toggleMovePanel()
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

    internal fun updateSlideShowButton() = dialogAndUiStateManager.updateSlideShowButton()

    /**
     * Update countdown display for slideshow (called by NavigationManager)
     */
    internal fun updateCountdownDisplay(seconds: Int) = dialogAndUiStateManager.updateCountdownDisplay(seconds)
    
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
    
    /**
     * Set screen keep-awake state for slideshow (D.8).
     * @param enabled true to force screen on, false to restore to global preventSleep setting
     */
    internal fun setSlideshowKeepAwake(enabled: Boolean) {
        if (isFinishing || isDestroyed) {
            return
        }
        
        if (enabled) {
            // Force keep screen on during slideshow
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            Timber.d("SlideshowKeepAwake: Enabled - screen will stay on during slideshow")
            
            // Show user notification (D.8)
            android.widget.Toast.makeText(
                this,
                R.string.slideshow_keep_awake,
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            // Restore to global preventSleep setting
            lifecycleScope.launch {
                val settings = settingsRepository.getSettings().first()
                if (settings.preventSleep) {
                    // Global setting is ON - keep screen on
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    // Global setting is OFF - allow screen sleep
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                Timber.d("SlideshowKeepAwake: Disabled - restored to global setting (preventSleep=${settings.preventSleep})")
            }
        }
    }

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

    /**
     * Update volume buttons visibility - show for audio and video files
     */
    internal fun updateVolumeButtonsVisibility() {
        dialogAndUiStateManager.updateVolumeButtonsVisibility()
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
        dialogHelper.showFileInfo(currentFile)
    }
    
    internal fun showImageEditDialog() {
        dialogAndUiStateManager.showImageEditDialog()
    }

    internal fun showGifEditDialog() {
        dialogAndUiStateManager.showGifEditDialog()
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
     * Show player settings dialog for video/audio files.
     * Allows configuring playback speed, repeat, subtitles and audio track.
     */
    internal fun deleteCurrentFile() {
        // Check for Read-only mode
        val resource = viewModel.state.value.resource
        if (resource?.isReadOnly == true) {
            Toast.makeText(this, getString(R.string.error_read_only), Toast.LENGTH_SHORT).show()
            return
        }
        
        val currentFile = viewModel.state.value.currentFile
        if (currentFile == null) {
            Toast.makeText(this, getString(R.string.msg_no_file_to_delete), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check Safe Mode settings
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            val shouldConfirm = settings.enableSafeMode || settings.confirmDelete
            
            Timber.d("deleteCurrentFile: shouldConfirm=$shouldConfirm (safeMode=${settings.enableSafeMode}, confirmDelete=${settings.confirmDelete})")
            
            if (shouldConfirm) {
                // Check if activity is still alive before showing dialog
                if (isFinishing || isDestroyed) {
                    Timber.w("deleteCurrentFile: Activity is finishing/destroyed, skipping confirm dialog")
                    return@launch
                }
                
                // Show confirmation dialog
                AlertDialog.Builder(this@PlayerActivity)
                    .setTitle(R.string.confirm_delete_title)
                    .setMessage(getString(R.string.confirm_delete_message, 1))
                    .setPositiveButton(R.string.delete) { _, _ ->
                        fileOperationsHandler.performDelete()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else {
                // Skip confirmation - execute immediately
                fileOperationsHandler.performDelete()
            }
        }
    }
    
    /**
     * Handle successful file deletion (called from batch delete permission launcher or callback)
     */
    private fun handleDeleteSuccess(deletedFilePath: String) {
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
        exoPlayerControlsManager.updateTrackButtonsVisibility()
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
        // Skip pause logic when entering PiP (video should keep playing)
        if (isInPictureInPictureMode) return
        lifecycleManager.onPause()

        // Detach PlayerView from the service MediaController BEFORE any pause logic.
        // TextureView loses its surface when Activity goes to background; if a MediaController
        // is still bound at that point, PlayerView automatically calls player.pause() on it,
        // which sets playWhenReady=false in AudioPlaybackService and breaks background audio.
        val serviceAudioActiveOnPause = ::mediaLoaderManager.isInitialized && mediaLoaderManager.isServiceAudioActive
        if (serviceAudioActiveOnPause) {
            binding.playerView.player = null
        } else {
            viewModel.togglePause()
        }

        audioEmptyStateController?.onPause()
        
        // Save playback position for video/audio
        saveCurrentPlaybackPosition()
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
        nowPlayingManager?.onStart()

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
        // Dismiss all tracked dialogs to prevent WindowLeaked
        if (::dialogHelper.isInitialized) {
            dialogHelper.dismissAll()
        }

        // Release background music manager
        backgroundMusicManager.release()
        
        // Release audio background photos manager
        audioBackgroundPhotosManager.release()
        
        // Release audio service controller (disconnect from AudioPlaybackService)
        audioServiceController?.release()
        audioServiceController = null
        
        // Release sleep timer manager (stop animations and timer)
        sleepTimerManager?.release()
        sleepTimerManager = null

        // Release audio empty-state controller (stop animations, clear Glide)
        audioEmptyStateController?.release()
        audioEmptyStateController = null
        
        // Release PiP manager (unregister receiver)
        pipManager?.release()
        pipManager = null

        // Release text file pager
        if (_textViewerManager != null) {
            textViewerManager.release()
        }

        // Release Cast manager (stop proxy server, cancel downloads, unregister listener)
        if (::castMediaManager.isInitialized) {
            castMediaManager.release()
        }

        // Delegate to lifecycle manager
        lifecycleManager.onDestroy()

        super.onDestroy()
    }
    
    /**
     * Save current playback position for video/audio files.
     */
    private fun saveCurrentPlaybackPosition() {
        val currentFile = viewModel.state.value.currentFile ?: return
        
        // Only save for video/audio files
        if (currentFile.type != MediaType.VIDEO && currentFile.type != MediaType.AUDIO) {
            return
        }

        // Save resume state alongside playback position
        viewModel.saveResumeState()

        val player = videoPlayerManager.getPlayer() ?: return
        val position = player.currentPosition
        val duration = player.duration
        
        // Don't save if duration is unknown or position is invalid
        if (duration <= 0 || position < 0) {
            return
        }
        
        // Save position asynchronously
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                playbackPositionRepository.savePosition(currentFile.path, position, duration)
                Timber.d("PlayerActivity: Saved playback position $position/$duration for ${currentFile.name}")
            } catch (e: Exception) {
                Timber.e(e, "PlayerActivity: Failed to save playback position for ${currentFile.name}")
            }
        }
    }
    
    private fun setupGoogleLensButtons() {
        // Delegate to GoogleLensButtonsManager
        googleLensButtonsManager.setupButtons()
    }
    
    internal fun shareCurrentFileToGoogleLens() = shareManager.shareCurrentFileToGoogleLens()
    
    /**
     * Show dialog for PDF editing options
     */
    internal fun showPdfEditDialog() {
        dialogAndUiStateManager.showPdfEditDialog()
    }

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

    // ==================== LAZY INITIALIZATION FACTORY METHODS ====================
    
    /**
     * Factory method for lazy VideoPlayerManager initialization.
     * Only called when VIDEO file is opened.
     */
    private fun createVideoPlayerManager(): VideoPlayerManager {
        return VideoPlayerManager(
            context = this,
            lifecycle = lifecycle,
            playerCallback = com.sza.fastmediasorter.ui.player.callbacks.PlayerPlaybackCallbackImpl(
                activity = this,
                viewModel = viewModel,
                binding = binding,
                loadingIndicatorHandler = loadingIndicatorHandler,
                showLoadingIndicatorRunnable = showLoadingIndicatorRunnable,
                playerSettingsManagerProvider = { playerSettingsManager },
                imageLoadingManagerProvider = { imageLoadingManager },
                slideshowController = slideshowController,
                sleepTimerManagerProvider = { sleepTimerManager },
                audioEmptyStateControllerProvider = { audioEmptyStateController }
            ),
            credentialsRepository = credentialsRepository,
            smbClient = smbClient,
            sftpClient = sftpClient,
            ftpClient = ftpClient,
            googleDriveClient = googleDriveClient,
            oneDriveClient = oneDriveClient,
            dropboxClient = dropboxClient,
            playbackPositionRepository = playbackPositionRepository
        ).also {
            it.onPositionSaved = { viewModel.saveResumeState() }
        }
    }
    
    /**
     * Factory method for lazy PdfViewerManager initialization.
     * Only called when PDF file is opened.
     */
    private fun createPdfViewerManager(): com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager {
        return com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager(
            binding = binding,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager.PdfViewerCallback {
                override fun showError(message: String) {
                    this@PlayerActivity.showError(message)
                }
                
                override fun displayOcrText(text: String) {
                    textViewerManager.displayOcrText(text)
                }
                
                override fun displayTranslatedText(text: String) {
                    textViewerManager.displayTranslatedText(text)
                }
                
                override fun shareFileToGoogleLens(file: File) {
                    shareManager.shareFileToGoogleLens(file)
                }
                
                override fun isLandscapeMode(): Boolean {
                    return resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                }
                
                override fun onEnterFullscreenMode() {
                    if (currentSettings?.hideSystemUiInFullscreen != false) {
                        systemBarsManager.enterFullscreenMode()
                    }
                    binding.toolbar.isVisible = false
                    safeViews.copyToPanel.isVisible = false
                    safeViews.moveToPanel.isVisible = false
                    safeViews.pdfControlsLayout.isVisible = false
                    safeViews.translationOverlay.isVisible = false
                    binding.translationLensOverlay.isVisible = false
                }
                
                override fun onExitFullscreenMode() {
                    systemBarsManager.exitFullscreenMode()
                    binding.toolbar.isVisible = true
                    safeViews.pdfControlsLayout.isVisible = true
                }
            },
            translationManager = translationManager,
            playbackPositionRepository = playbackPositionRepository
        )
    }
    
    /**
     * Factory method for lazy EpubViewerManager initialization.
     * Only called when EPUB file is opened.
     */
    private fun createEpubViewerManager(): com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager {
        return com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager(
            binding = binding,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager.EpubViewerCallback {
                override fun showError(message: String) {
                    this@PlayerActivity.showError(message)
                }
                
                override fun displayTranslatedText(text: String) {
                    textViewerManager.displayTranslatedText(text)
                }
                
                override fun onEnterFullscreenMode() {
                    if (currentSettings?.hideSystemUiInFullscreen != false) {
                        systemBarsManager.enterFullscreenMode()
                    }
                }
                
                override fun onExitFullscreenMode() {
                    systemBarsManager.exitFullscreenMode()
                }
            },
            playbackPositionRepository = playbackPositionRepository,
            translationManager = translationManager
        )
    }
    
    /**
     * Factory method for lazy TextViewerManager initialization.
     * Only called when TEXT file is opened.
     */
    private fun createTextViewerManager(): com.sza.fastmediasorter.ui.player.helpers.TextViewerManager {
        return com.sza.fastmediasorter.ui.player.helpers.TextViewerManager(
            context = this,
            binding = binding,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.TextViewerManager.TextViewerCallback {
                override fun showError(message: String) {
                    this@PlayerActivity.showError(message)
                }
                
                override fun showTranslationSettingsDialog() {
                    if (::translationButtonManager.isInitialized) {
                        translationButtonManager.showTranslationSettingsDialog()
                    }
                }
                
                override fun exitFullscreenMode() {
                    systemBarsManager.exitFullscreenMode()
                    viewModel.toggleCommandPanel()
                }

                override fun setTouchZonesEnabled(enabled: Boolean) {
                    safeViews.touchZonesOverlay.isVisible = enabled && useTouchZones
                }

                override fun showEncodingDialog() {
                    this@PlayerActivity.showEncodingDialog()
                }
            },
            translationManager = translationManager
        )
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
            return Intent(context, PlayerActivity::class.java).apply {
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
