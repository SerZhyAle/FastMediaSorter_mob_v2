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
    
    // Helper controllers
    internal lateinit var slideshowController: SlideshowController
    internal lateinit var gestureHelper: PlayerGestureHelper
    internal lateinit var dialogHelper: PlayerDialogHelper
    // LAZY INITIALIZATION: Video player only created when VIDEO file opened
    private var _videoPlayerManager: VideoPlayerManager? = null
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
    private var translationJob: Job? = null
    internal lateinit var commandPanelController: CommandPanelController
    internal lateinit var imageLoadingManager: ImageLoadingManager
    private var audioEmptyStateController: com.sza.fastmediasorter.ui.player.helpers.AudioEmptyStateController? = null
    private lateinit var mediaLoaderManager: com.sza.fastmediasorter.ui.player.helpers.PlayerMediaLoaderManager
    private var audioServiceController: com.sza.fastmediasorter.ui.player.helpers.AudioServiceController? = null
    private var sleepTimerManager: com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager? = null
    private var pipManager: com.sza.fastmediasorter.ui.player.helpers.PictureInPictureManager? = null
    private val safeViews by lazy { PlayerBindingSafeViews(binding) }
    private lateinit var dialogAndUiStateManager: PlayerDialogAndUiStateManager
    internal lateinit var audioSlideshowPhotoModeManager: com.sza.fastmediasorter.ui.player.helpers.AudioSlideshowPhotoModeManager
    private lateinit var keyboardHandler: com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler
    internal lateinit var networkFileManager: com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
    
    // LAZY INITIALIZATION: Document viewers only created when needed
    private var _pdfViewerManager: com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager? = null
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
    
    private var _epubViewerManager: com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager? = null
    internal val epubViewerManager: com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager
        get() {
            if (_epubViewerManager == null) {
                Timber.d("PERFORMANCE: Lazy initializing EpubViewerManager")
                _epubViewerManager = createEpubViewerManager()
            }
            return _epubViewerManager!!
        }
    
    private var _textViewerManager: com.sza.fastmediasorter.ui.player.helpers.TextViewerManager? = null
    internal val textViewerManager: com.sza.fastmediasorter.ui.player.helpers.TextViewerManager
        get() {
            if (_textViewerManager == null) {
                Timber.d("PERFORMANCE: Lazy initializing TextViewerManager")
                _textViewerManager = createTextViewerManager()
            }
            return _textViewerManager!!
        }
    private lateinit var uiStateCoordinator: com.sza.fastmediasorter.ui.player.helpers.PlayerUiStateCoordinator
    private lateinit var undoOperationManager: com.sza.fastmediasorter.ui.player.helpers.UndoOperationManager
    internal lateinit var playerSettingsManager: com.sza.fastmediasorter.ui.player.helpers.PlayerSettingsManager
    internal lateinit var cloudAuthManager: com.sza.fastmediasorter.ui.browse.managers.BrowseCloudAuthManager
    internal lateinit var translationManager: com.sza.fastmediasorter.ui.player.helpers.TranslationManager
    private lateinit var touchZoneGestureManager: com.sza.fastmediasorter.ui.player.helpers.TouchZoneGestureManager
    private lateinit var translationButtonManager: com.sza.fastmediasorter.ui.player.helpers.TranslationButtonManager
    private lateinit var exoPlayerControlsManager: com.sza.fastmediasorter.ui.player.helpers.ExoPlayerControlsManager
    private lateinit var searchControlsManager: com.sza.fastmediasorter.ui.player.helpers.SearchControlsManager
    private lateinit var lifecycleManager: com.sza.fastmediasorter.ui.player.helpers.PlayerLifecycleManager
    private lateinit var controlsSetupManager: com.sza.fastmediasorter.ui.player.helpers.PlayerControlsSetupManager
    private lateinit var gestureSetupManager: com.sza.fastmediasorter.ui.player.helpers.PlayerGestureSetupManager
    private lateinit var imageOcrManager: com.sza.fastmediasorter.ui.player.helpers.ImageOcrManager
    private lateinit var lyricsManager: com.sza.fastmediasorter.ui.player.helpers.LyricsManager
    private lateinit var googleLensButtonsManager: com.sza.fastmediasorter.ui.player.helpers.GoogleLensButtonsManager
    private lateinit var systemBarsManager: com.sza.fastmediasorter.ui.player.helpers.SystemBarsManager

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
    private val deletePermissionLauncher = registerForActivityResult(
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
    
    // Track preload jobs to cancel on destroy (managed by lifecycleManager)
    private val preloadJobs = mutableListOf<Job>()
    private lateinit var gestureDetector: GestureDetector
    private val touchZoneDetector = TouchZoneDetector()
    private var useTouchZones = true // Use touch zones for images, gestures for video
    private var loadFullSizeImages = false // Load full-size images with PhotoView (3-zone mode)
    private var isFirstResume = true // Track first onResume to avoid duplicate load
    private val shownHintTypes = mutableSetOf<TouchZoneHintType>() // Track per-type hints shown in this session
    private var slideshowModeRequested = false // Auto-start slideshow when files are loaded
    private var isExplicitFullscreenMode = false // User requested fullscreen via button
    
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
    private var currentFilePath: String? = null
    
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
    private var currentSettings: AppSettings? = null
    
    // Session-scoped translation settings (reset when exiting Browse/Resource)
    // Will be initialized from AppSettings defaults in setupTranslationDefaults()
    private var translationSessionSettings = com.sza.fastmediasorter.domain.models.TranslationSessionSettings()
    
    // Gesture callback for custom PhotoView zoom handling
    private lateinit var playerGestureCallback: com.sza.fastmediasorter.ui.player.callbacks.PlayerGestureCallbackImpl

    
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

        // 1. Independent Managers / Fundamental Logic
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
            callback = object : com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler.PlayerKeyboardCallback {
                override fun onDeleteFile() {
                    deleteCurrentFile()
                }
                
                override fun onExitPlayer() {
                    finish()
                }
                
                override fun onToggleSlideshow() {
                    val wasActive = viewModel.state.value.isSlideShowActive
                    navigationManager.toggleSlideshow()
                    
                    if (!wasActive && viewModel.state.value.isSlideShowActive) {
                        showSlideshowEnabledMessage()
                    }
                    
                    updateSlideShowButton()
                    navigationManager.updateSlideshowState()
                }
                
                override fun onShowRenameDialog() {
                    showRenameDialog()
                }
                
                override fun onShowFileInfo() {
                    showFileInfo()
                }
                
                override fun onToggleCommandPanel() {
                    viewModel.toggleCommandPanel()
                }
                
                override fun onToggleCopyPanel() {
                    toggleCopyPanel()
                }
                
                override fun onToggleMovePanel() {
                    toggleMovePanel()
                }
                
                override fun onShowEditDialog() {
                    val currentFile = viewModel.state.value.currentFile
                    when (currentFile?.type) {
                        MediaType.IMAGE -> {
                            if (isAnimatedImagePath(currentFile.path)) showGifEditDialog() else showImageEditDialog()
                        }
                        MediaType.GIF -> showGifEditDialog()
                        MediaType.VIDEO, MediaType.AUDIO -> playerSettingsManager.showPlayerSettingsDialog()
                        else -> {}
                    }
                }
                
                override fun getExoPlayer(): ExoPlayer? = if (_videoPlayerManager != null) videoPlayerManager.getPlayer() else null
                
                override fun getCurrentMediaType(): MediaType? = viewModel.state.value.currentFile?.type
                
                override fun onPdfNextPage() {
                    if (_pdfViewerManager != null) pdfViewerManager.showNextPage()
                }
                
                override fun onPdfPreviousPage() {
                    if (_pdfViewerManager != null) pdfViewerManager.showPreviousPage()
                }

                override fun onPdfHome() {
                    if (_pdfViewerManager != null) pdfViewerManager.showPdfPage(0)
                }

                override fun onPdfEnd() {
                    if (_pdfViewerManager != null && pdfViewerManager.pdfPageCount > 0) {
                        pdfViewerManager.showPdfPage(pdfViewerManager.pdfPageCount - 1)
                    }
                }

                override fun onEpubNextPage() {
                    if (_epubViewerManager != null) epubViewerManager.showNextChapter()
                }

                override fun onEpubPreviousPage() {
                    if (_epubViewerManager != null) epubViewerManager.showPreviousChapter()
                }

                override fun onEpubHome() {
                    if (_epubViewerManager != null) epubViewerManager.scrollToHome()
                }

                override fun onEpubEnd() {
                    if (_epubViewerManager != null) epubViewerManager.scrollToEnd()
                }
                
                override fun onTextScrollDown() {
                    if (_textViewerManager != null) textViewerManager.scrollDown()
                }
                
                override fun onTextScrollUp() {
                    if (_textViewerManager != null) textViewerManager.scrollUp()
                }

                override fun onTextHome() {
                    if (_textViewerManager != null) textViewerManager.scrollToTop()
                }

                override fun onTextEnd() {
                    if (_textViewerManager != null) textViewerManager.scrollToBottom()
                }

                override fun onSeekForward(seconds: Int) {
                    if (_videoPlayerManager != null) videoPlayerManager.seekForward(seconds)
                }

                override fun onSeekBackward(seconds: Int) {
                    if (_videoPlayerManager != null) videoPlayerManager.seekBackward(seconds)
                }
            }
        )

        uiStateCoordinator = com.sza.fastmediasorter.ui.player.helpers.PlayerUiStateCoordinator(
            binding = binding,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.PlayerUiStateCoordinator.Callback {
                override fun isActivityAlive(): Boolean = !(isFinishing || isDestroyed)

                override fun getCurrentSettings(): AppSettings? = currentSettings
                override fun setCurrentSettings(settings: AppSettings?) {
                    currentSettings = settings
                }

                override fun getCurrentFilePath(): String? = currentFilePath
                override fun setCurrentFilePath(path: String?) {
                    currentFilePath = path
                }

                override fun isImageVisible(): Boolean {
                    val imageViewVisible = binding.imageView.isVisible
                    val photoSurfaceVisible =
                        (binding.photoDualSurfaceContainer?.isVisible == true) &&
                            (binding.photoView.isVisible || (binding.photoViewSurfaceB?.isVisible == true))
                    return imageViewVisible || photoSurfaceVisible
                }

                override fun hasImageDrawable(): Boolean {
                    if (binding.imageView.isVisible && binding.imageView.drawable != null) {
                        return true
                    }

                    if (binding.photoDualSurfaceContainer?.isVisible == true) {
                        if (binding.photoView.isVisible && binding.photoView.drawable != null) {
                            return true
                        }
                        if (binding.photoViewSurfaceB?.isVisible == true && binding.photoViewSurfaceB?.drawable != null) {
                            return true
                        }
                    }

                    return false
                }

                override fun isSlideshowModeRequested(): Boolean = slideshowModeRequested
                override fun clearSlideshowModeRequested() {
                    slideshowModeRequested = false
                }

                override fun hasShownHintType(type: TouchZoneHintType): Boolean = type in shownHintTypes
                override fun markHintTypeShown(type: TouchZoneHintType) {
                    shownHintTypes.add(type)
                }

                override fun getUseTouchZones(): Boolean = useTouchZones

                override fun displayImage(path: String) {
                    stopVideoPlayback()
                    this@PlayerActivity.displayImage(path)
                }
                override fun playVideo(path: String) = this@PlayerActivity.playVideo(path)
                override fun displayText(file: MediaFile) {
                    stopVideoPlayback()
                    this@PlayerActivity.displayText(file)
                }
                override fun displayPdf(file: MediaFile) {
                    stopVideoPlayback()
                    // Disable touch zones for PDF - use PDF navigation controls instead
                    safeViews.touchZonesOverlay.isVisible = false
                    safeViews.touchZones3Overlay.isVisible = false
                    if (_pdfViewerManager != null) pdfViewerManager.displayPdf(file)
                }
                override fun displayEpub(file: MediaFile) {
                    stopVideoPlayback()
                    // Disable touch zones for EPUB - use EPUB navigation controls instead
                    safeViews.touchZonesOverlay.isVisible = false
                    safeViews.touchZones3Overlay.isVisible = false
                    if (_epubViewerManager != null) epubViewerManager.displayEpub(file)
                }

                override fun adjustTouchZonesForVideo(isVideo: Boolean) =
                    this@PlayerActivity.adjustTouchZonesForVideo(isVideo)

                override fun updatePanelVisibility(showCommandPanel: Boolean) =
                    this@PlayerActivity.updatePanelVisibility(showCommandPanel)

                override fun updateCommandAvailability(state: PlayerViewModel.PlayerState) =
                    this@PlayerActivity.updateCommandAvailability(state)

                override fun updatePlayPauseButton() = this@PlayerActivity.updatePlayPauseButton()
                override fun updateSlideShowButton() = this@PlayerActivity.updateSlideShowButton()
                override fun updateVolumeButtonsVisibility() = this@PlayerActivity.updateVolumeButtonsVisibility()

                override fun showTouchZoneHintOverlay(type: TouchZoneHintType) = this@PlayerActivity.showTouchZoneHintOverlay(type)
                override fun showSlideshowEnabledMessage() = this@PlayerActivity.showSlideshowEnabledMessage()

                override fun toggleSlideShow() = viewModel.toggleSlideShow()
                override fun startSlideshow(intervalSeconds: Int) = slideshowController.startSlideshow(intervalSeconds)
                override fun getLatestState(): PlayerViewModel.PlayerState = viewModel.state.value
                override fun forceStateUpdate() = viewModel.forceStateUpdate()
                override fun enterAudioSlideshowPhotoModeIfNeeded() {
                    val state = viewModel.state.value
                    val currentFile = state.currentFile
                    val isAudioWithPhotos = currentFile?.type == MediaType.AUDIO &&
                        state.enablePhotosDuringAudio &&
                        state.audioBackgroundPhotosResourceId != null
                    
                    Timber.d("PlayerActivity: enterAudioSlideshowPhotoModeIfNeeded - isAudio=${currentFile?.type == MediaType.AUDIO}, enablePhotos=${state.enablePhotosDuringAudio}, resourceId=${state.audioBackgroundPhotosResourceId}, slideshowActive=${state.isSlideShowActive}, alreadyInMode=${audioSlideshowPhotoModeManager.isActive}")
                    
                    if (isAudioWithPhotos && state.isSlideShowActive && !audioSlideshowPhotoModeManager.isActive) {
                        Timber.i("PlayerActivity: ✓ Auto-entering audio slideshow photo mode")
                        backgroundMusicManager.isAudioSlideshowPhotoMode = true
                        audioSlideshowPhotoModeManager.enter()
                    } else {
                        Timber.d("PlayerActivity: ✗ Not entering audio slideshow photo mode (conditions not met)")
                    }
                }

                override fun updateTouchZonesHelpButtonVisibility(visible: Boolean) {
                    binding.btnTouchZonesHelp?.isVisible = visible
                }
            }
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

        // 2. Basic Dependencies
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
            }
        )
        
        dialogHelper.setAuthCallback { provider ->
            when (provider.lowercase()) {
                "dropbox" -> cloudAuthManager.launchDropboxSignIn()
                "google drive", "google_drive" -> cloudAuthManager.launchGoogleSignIn()
                "onedrive" -> cloudAuthManager.launchOneDriveSignIn()
                else -> Timber.w("Unknown provider for auth request: $provider")
            }
        }

        // Initialize FileOperationsHandler for file operations
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
        
        // Initialize CommandPanelController
        commandPanelController = CommandPanelController(
            binding = binding,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : CommandPanelController.CommandPanelCallback {
                override fun onBackClicked() {
                    finish()
                }
                
                override fun onPreviousClicked() {
                    navigationManager.navigatePreviousFromButton()
                }
                
                override fun onNextClicked() {
                    navigationManager.navigateNextFromButton()
                }
                
                override fun onRenameClicked() {
                    showRenameDialog()
                }
                
                override fun onDeleteClicked() {
                    deleteCurrentFile()
                }
                
                override fun onShareClicked() {
                    shareCurrentFile()
                }
                
                override fun onEditClicked() {
                    val currentFile = viewModel.state.value.currentFile
                    when (currentFile?.type) {
                        MediaType.VIDEO, MediaType.AUDIO -> playerSettingsManager.showPlayerSettingsDialog()
                        MediaType.IMAGE -> {
                            if (isAnimatedImagePath(currentFile.path)) showGifEditDialog() else showImageEditDialog()
                        }
                        MediaType.GIF -> showGifEditDialog()
                        MediaType.PDF -> showPdfEditDialog()
                        else -> {}
                    }
                }
                
                override fun onUndoClicked() {
                    viewModel.undoLastOperation()
                }
                
                override fun onFullscreenClicked() {
                    // Transition FROM command panel mode TO fullscreen mode
                    // (no return logic needed - fullscreen has no buttons!)
                    isExplicitFullscreenMode = true
                    if (viewModel.state.value.showCommandPanel) {
                        viewModel.toggleCommandPanel()
                    }
                    // CRITICAL: Pass false directly, not viewModel.state.value.showCommandPanel
                    // because toggleCommandPanel() is async and state hasn't updated yet
                    updateSystemBarsForPlayer(false)
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
                        backgroundMusicManager.isAudioSlideshowPhotoMode = true
                    }
                    
                    navigationManager.toggleSlideshow()
                    
                    val isNowActive = viewModel.state.value.isSlideShowActive
                    
                    if (!wasActive && isNowActive) {
                        showSlideshowEnabledMessage()
                        if (isAudioWithPhotos) {
                            audioSlideshowPhotoModeManager.enter()
                        }
                    } else if (wasActive && !isNowActive) {
                        if (audioSlideshowPhotoModeManager.isActive) {
                            audioSlideshowPhotoModeManager.exit()
                        }
                    }
                    
                    updateSlideShowButton()
                    navigationManager.updateSlideshowState()
                    updateSystemBarsForPlayer(viewModel.state.value.showCommandPanel)
                }
                
                override fun onCopyPanelHeaderClicked() {
                    toggleCopyPanel()
                }
                
                override fun onMovePanelHeaderClicked() {
                    toggleMovePanel()
                }
                
                override fun onInfoClicked() {
                    showFileInfo()
                }
                
                override fun onLyricsClicked() {
                    searchAndShowLyrics()
                }
                
                override fun onFavoriteClicked() {
                    viewModel.toggleFavorite()
                }
                
                override fun onSearchClicked() {
                    if (::searchControlsManager.isInitialized) {
                        searchControlsManager.showSearchPanel()
                    }
                }
                
                override fun onTranslateClicked() {
                    val currentFile = viewModel.state.value.currentFile ?: return
                    when (currentFile.type) {
                        MediaType.PDF -> if (_pdfViewerManager != null) pdfViewerManager.toggleTranslation()
                        MediaType.TEXT -> binding.btnTranslateTextCmd.performClick()
                        MediaType.EPUB -> {
                            if (_epubViewerManager != null) epubViewerManager.toggleTranslation()
                        }
                        MediaType.IMAGE, MediaType.GIF -> binding.btnTranslateImageCmd.performClick()
                        else -> {}
                    }
                }
                
                override fun onOcrClicked() {
                    val currentFile = viewModel.state.value.currentFile ?: return
                    when (currentFile.type) {
                        MediaType.PDF -> if (_pdfViewerManager != null) pdfViewerManager.extractTextFromCurrentPage()
                        MediaType.EPUB -> {
                            if (_epubViewerManager != null) epubViewerManager.extractTextFromCurrentChapter()
                        }
                        MediaType.IMAGE, MediaType.GIF -> extractTextFromCurrentImage()
                        else -> {}
                    }
                }
                
                override fun onGoogleLensClicked() {
                    val currentFile = viewModel.state.value.currentFile ?: return
                    when (currentFile.type) {
                        MediaType.PDF -> if (_pdfViewerManager != null) pdfViewerManager.shareCurrentPageToGoogleLens()
                        MediaType.IMAGE, MediaType.GIF -> shareCurrentFileToGoogleLens()
                        else -> {}
                    }
                }
                
                override fun onCopyTextClicked() {
                    binding.btnCopyTextCmd.performClick()
                }
                
                override fun onEditTextClicked() {
                    binding.btnEditTextCmd.performClick()
                }
                
                override fun onOcrSettingsClicked() {
                    // Long-press on OCR shows translation settings (same as translate button)
                    Timber.d("OCR settings requested - showing translation settings dialog")
                    if (::translationButtonManager.isInitialized) {
                        translationButtonManager.showTranslationSettingsDialog()
                    }
                }
                
                override fun onTranslationSettingsClicked() {
                    if (::translationButtonManager.isInitialized) {
                        translationButtonManager.showTranslationSettingsDialog()
                    }
                }

                override fun onSleepTimerClicked() {
                    showSleepTimerDialog()
                }

                override fun onReopenEncodingClicked() {
                    showEncodingDialog()
                }

                override fun onToggleMarkdownClicked() {
                    textViewerManager.toggleMarkdownRendering()
                }

                override fun onReaderSettingsClicked() {
                    showReaderSettingsDialog()
                }

                override fun onReadAloudClicked() {
                    textViewerManager.toggleReadAloud()
                }

                override fun onPdfScrollModeClicked() {
                    pdfViewerManager.toggleScrollMode()
                }

                override fun onPdfColorModeClicked() {
                    pdfViewerManager.toggleColorMode()
                }

                override fun onPdfThumbnailsClicked() {
                    pdfViewerManager.showThumbnailNavigation()
                }

                override fun onEpubReaderSettingsClicked() {
                    epubViewerManager.showReaderSettingsDialog()
                }

                override fun onEpubSearchAllClicked() {
                    epubViewerManager.showCrossChapterSearch()
                }
            }
        )
        // Initialize orientation on startup
        commandPanelController.updateOrientation(resources.configuration)

        imageLoadingManager = ImageLoadingManager(
            binding = binding,
            settingsRepository = settingsRepository,
            searchAudioCoverUseCase = searchAudioCoverUseCase,
            lifecycleScope = lifecycleScope,
            loadingIndicatorHandler = loadingIndicatorHandler,
            showLoadingIndicatorRunnable = showLoadingIndicatorRunnable,
            preloadJobs = preloadJobs,
            callback = object : ImageLoadingManager.ImageLoadingCallback {
                override fun isFinishing(): Boolean = this@PlayerActivity.isFinishing
                
                override fun isDestroyed(): Boolean = this@PlayerActivity.isDestroyed
                
                override fun releasePlayer() {
                    this@PlayerActivity.releasePlayer()
                }
                
                override fun showError(message: String, exception: Throwable?) {
                    this@PlayerActivity.showError(message, exception)
                }
                
                override fun showToast(message: String) {
                    Toast.makeText(this@PlayerActivity, message, Toast.LENGTH_SHORT).show()
                }
                
                override fun getWindowManager(): android.view.WindowManager {
                    return this@PlayerActivity.windowManager
                }
                
                override fun onAudioMetadataLoaded(metadata: com.sza.fastmediasorter.domain.model.AudioMetadata) {
                    this@PlayerActivity.onAudioMetadataLoaded(metadata)
                }
                
                override fun updateSlideShow() {
                    this@PlayerActivity.updateSlideShow()
                }
                
                override fun getAdjacentFiles(): List<MediaFile> = viewModel.getAdjacentFiles()
                
                override fun getCurrentFile(): MediaFile? = viewModel.state.value.currentFile
                
                override fun getCurrentResource(): MediaResource? = viewModel.state.value.resource
                
                override fun getExoPlayer(): ExoPlayer? = if (_videoPlayerManager != null) videoPlayerManager.getPlayer() else null
                
                override fun getString(resId: Int): String = this@PlayerActivity.getString(resId)
                
                override fun isShowingCommandPanel(): Boolean = viewModel.state.value.showCommandPanel
                
                override fun isSlideshowActive(): Boolean = viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused

                override fun setAnimatedBadgeVisible(visible: Boolean) {
                    binding.tvAnimatedBadge.isVisible = visible
                }
            }
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

        // 3. Network & Document Helpers
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

        // OPTIMIZATION: Document Viewers (PDF, EPUB, Text) and VideoPlayerManager use lazy initialization
        // They are created only when files of those types are opened (see createXxxManager() methods)
        // This saves ~2.5 seconds on IMAGE file opening

        // 5. Intermediate UI Helpers
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
            callback = object : com.sza.fastmediasorter.ui.player.helpers.TouchZoneGestureManager.TouchZoneCallback {
                override fun isOverlayBlocking(): Boolean = this@PlayerActivity.isOverlayBlocking()
                override fun getTouchZonesEnabled(): Boolean = useTouchZones
                override fun getLoadFullSizeImages(): Boolean = loadFullSizeImages
                override fun onBack() {
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
                override fun onCopy() = showCopyDialog()
                override fun onRename() = showRenameDialog()
                override fun onPrevious() = navigationManager.navigatePreviousFromControl()
                override fun onMove() = showMoveDialog()
                override fun onNext() = navigationManager.navigateNextFromControl()
                override fun onSwitchToCommandPanel() {
                    // Show command panel (exit fullscreen mode)
                    if (!viewModel.state.value.showCommandPanel) {
                        viewModel.toggleCommandPanel()
                    }
                }
                override fun onToggleSlideshow() {
                    viewModel.toggleSlideShow()
                    updateSlideShowButton()
                }
                override fun onDelete() = deleteCurrentFile()
                override fun onPauseResume() {
                    val player = binding.playerView.player
                    if (player != null) {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                }
                override fun onSeekToStart() {
                    binding.playerView.player?.seekTo(0)
                }
                override fun onSeekToEnd() {
                    val player = binding.playerView.player
                    player?.let { it.seekTo(it.duration) }
                }
                override fun onZoomIn() {
                    val currentScale = binding.photoView.scale
                    val newScale = (currentScale * 2f).coerceAtMost(8f)
                    binding.photoView.setScale(newScale, true)
                }
                override fun onZoomOut() {
                    val currentScale = binding.photoView.scale
                    val newScale = (currentScale / 2f).coerceAtLeast(1f)
                    binding.photoView.setScale(newScale, true)
                }
                override fun onPageUp() {
                    // Document page up - handled by specific viewers
                    when (viewModel.state.value.currentFile?.type) {
                        com.sza.fastmediasorter.domain.model.MediaType.PDF -> if (_pdfViewerManager != null) pdfViewerManager.showPreviousPage()
                        com.sza.fastmediasorter.domain.model.MediaType.EPUB -> if (_epubViewerManager != null) epubViewerManager.onPreviousPageRequest()
                        com.sza.fastmediasorter.domain.model.MediaType.TEXT -> if (_textViewerManager != null) textViewerManager.scrollUp()
                        else -> {}
                    }
                }
                override fun onPageDown() {
                    // Document page down - handled by specific viewers
                    when (viewModel.state.value.currentFile?.type) {
                        com.sza.fastmediasorter.domain.model.MediaType.PDF -> if (_pdfViewerManager != null) pdfViewerManager.showNextPage()
                        com.sza.fastmediasorter.domain.model.MediaType.EPUB -> if (_epubViewerManager != null) epubViewerManager.onNextPageRequest()
                        com.sza.fastmediasorter.domain.model.MediaType.TEXT -> if (_textViewerManager != null) textViewerManager.scrollDown()
                        else -> {}
                    }
                }
                override fun showSlideshowEnabledMessage() = this@PlayerActivity.showSlideshowEnabledMessage()
                override fun updateSlideShowButton() = this@PlayerActivity.updateSlideShowButton()
                override fun updateSlideShow() = navigationManager.updateSlideshowState()
                override fun setPhotoViewZoom(scale: Float) = playerGestureCallback.setPhotoViewZoom(scale)
            }
        )

        translationButtonManager = com.sza.fastmediasorter.ui.player.helpers.TranslationButtonManager(
            context = this,
            lifecycleOwner = this,
            binding = binding,
            settingsRepository = settingsRepository,
            callback = object : com.sza.fastmediasorter.ui.player.helpers.TranslationButtonManager.TranslationButtonCallback {
                override fun getTranslationSessionSettings() = translationSessionSettings
                override fun setTranslationSessionSettings(settings: com.sza.fastmediasorter.domain.models.TranslationSessionSettings) {
                    translationSessionSettings = settings
                }
                override fun getCurrentFileType() = viewModel.state.value.currentFile?.type
                override fun translateCurrentImage() = this@PlayerActivity.translateCurrentImage()
                override fun updateTextViewerTranslationButtonIcon(sourceLang: String, targetLang: String) {
                    if (_textViewerManager != null) {
                        textViewerManager.updateTranslationButtonIcon(sourceLang, targetLang)
                    }
                }
                override fun applyTextViewerFontSettings(settings: com.sza.fastmediasorter.domain.models.TranslationSessionSettings) {
                    if (_textViewerManager != null) {
                        textViewerManager.applyFontSettings(settings)
                    }
                }
                override fun applyTranslationManagerFontSettings(settings: com.sza.fastmediasorter.domain.models.TranslationSessionSettings) {
                    if (::translationManager.isInitialized) {
                        translationManager.applyFontSettings(settings)
                    }
                }
                override fun applyEpubFontSettings(settings: com.sza.fastmediasorter.domain.models.TranslationSessionSettings) {
                    if (_epubViewerManager != null) {
                        epubViewerManager.applyFontSettings(settings)
                    }
                }
                override fun forceTranslatePdf() {
                    if (_pdfViewerManager != null) pdfViewerManager.forceTranslate()
                }
                override fun forceTranslateText() {
                    if (_textViewerManager != null) textViewerManager.forceTranslate()
                }
                override fun forceTranslateEpub() {
                    if (_epubViewerManager != null) epubViewerManager.forceTranslate()
                }
            }
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
                override fun showAudioTrackDialog() = this@PlayerActivity.showAudioTrackDialog()
                override fun showSubtitleTrackDialog() = this@PlayerActivity.showSubtitleTrackDialog()
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

        // 7. Primary Coordinators (Dependencies on almost everything!)
        audioServiceController = com.sza.fastmediasorter.ui.player.helpers.AudioServiceController(this)
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
            }
        )
        
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

        // 8. Setup Managers
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
                if (::mediaLoaderManager.isInitialized && mediaLoaderManager.isServiceAudioActive) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@PlayerActivity)
                        .setTitle(R.string.background_audio_exit_title)
                        .setMessage(R.string.background_audio_exit_message)
                        .setNegativeButton(R.string.background_audio_exit_stop) { _, _ ->
                            audioServiceController?.player?.stop()
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                        .setPositiveButton(R.string.background_audio_exit_continue) { _, _ ->
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                        .show()
                    return
                }

                // Default back behavior
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    override fun observeData() {
        observeViewModel()
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
    
    private fun showRenameDialog() {
        dialogAndUiStateManager.showRenameDialog()
    }

    private fun showEncodingDialog() {
        val manager = textViewerManager
        val charsets = manager.getSupportedCharsets()
        val currentCharset = manager.getCurrentCharsetName()
        val labels = charsets.map { (name, charset) ->
            if (charset.name() == currentCharset) "✓ $name" else name
        }.toTypedArray()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_encoding)
            .setItems(labels) { _, which ->
                val selectedCharset = charsets[which].second
                manager.reopenWithEncoding(selectedCharset)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showReaderSettingsDialog() {
        val themes = com.sza.fastmediasorter.ui.player.helpers.TextReaderTheme.entries
        val themeLabels = arrayOf(
            getString(R.string.reader_theme_light),
            getString(R.string.reader_theme_dark),
            getString(R.string.reader_theme_sepia)
        )
        val currentTheme = textViewerManager.getCurrentTheme()
        val currentIndex = themes.indexOf(currentTheme).coerceAtLeast(0)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reader_settings)
            .setSingleChoiceItems(themeLabels, currentIndex) { dialog, which ->
                textViewerManager.applyReaderTheme(themes[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSleepTimerDialog() {
        val manager = sleepTimerManager ?: return
        val options = com.sza.fastmediasorter.ui.player.helpers.SleepTimerManager.SLEEP_TIMER_OPTIONS
        val labels = options.map { minutes ->
            if (minutes >= 60) {
                getString(R.string.sleep_timer_hours, minutes / 60, minutes % 60)
            } else {
                getString(R.string.sleep_timer_minutes, minutes)
            }
        }.toTypedArray()

        val items = if (manager.isSleepTimerActive) {
            arrayOf(getString(R.string.sleep_timer_off)) + labels
        } else {
            labels
        }

        val indexOffset = if (manager.isSleepTimerActive) 1 else 0

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sleep_timer_title)
            .setItems(items) { _, which ->
                if (manager.isSleepTimerActive && which == 0) {
                    manager.cancelSleepTimer()
                    Timber.d("PlayerActivity: sleep timer cancelled by user")
                } else {
                    val selectedMinutes = options[which - indexOffset]
                    manager.startSleepTimer(selectedMinutes)
                    Timber.d("PlayerActivity: sleep timer set for $selectedMinutes min")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAudioTrackDialog() {
        val tracks = videoPlayerManager.getAvailableAudioTracks()
        if (tracks.isEmpty()) return

        val labels = tracks.map { it.label }.toTypedArray()
        val selectedIndex = tracks.indexOfFirst { it.isSelected }.coerceAtLeast(0)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_audio_track)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                val track = tracks[which]
                videoPlayerManager.selectAudioTrack(track.groupIndex, track.trackIndex)
                Timber.d("PlayerActivity: selected audio track: ${track.label}")
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSubtitleTrackDialog() {
        val tracks = videoPlayerManager.getAvailableSubtitleTracks()

        // Build items: "Off" + available tracks
        val labels = mutableListOf(getString(R.string.subtitle_off))
        labels.addAll(tracks.map { it.label })

        val selectedIndex = if (tracks.any { it.isSelected }) {
            tracks.indexOfFirst { it.isSelected } + 1 // +1 for "Off" at index 0
        } else {
            0 // "Off" selected
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.select_subtitle_track)
            .setSingleChoiceItems(labels.toTypedArray(), selectedIndex) { dialog, which ->
                if (which == 0) {
                    // First valid group index or 0
                    val groupIndex = tracks.firstOrNull()?.groupIndex ?: 0
                    videoPlayerManager.selectSubtitleTrack(groupIndex, -1)
                    Timber.d("PlayerActivity: subtitles turned off")
                } else {
                    val track = tracks[which - 1]
                    videoPlayerManager.selectSubtitleTrack(track.groupIndex, track.trackIndex)
                    Timber.d("PlayerActivity: selected subtitle track: ${track.label}")
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
    private fun showTouchZoneHintOverlay(type: TouchZoneHintType) {
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
    private fun adjustTouchZonesForVideo(isVideo: Boolean) {
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
    private fun updatePanelVisibility(showCommandPanel: Boolean) {
        dialogAndUiStateManager.updatePanelVisibility(showCommandPanel)
        // Update document fullscreen exit button visibility
        controlsSetupManager.updateDocumentFullscreenExitButtonVisibility()

        updateSystemBarsForPlayer(showCommandPanel)
    }

    private fun updateSystemBarsForPlayer(showCommandPanel: Boolean) {
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

        val shouldHideSystemBars = hideSystemUiEnabled && (
            isExplicitFullscreenMode ||
            viewModel.state.value.isSlideShowActive ||
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
    private fun toggleCopyPanel() {
        dialogAndUiStateManager.toggleCopyPanel()
    }
    
    /**
     * Toggle Move to panel collapsed/expanded state
     */
    private fun toggleMovePanel() {
        dialogAndUiStateManager.toggleMovePanel()
    }
    


    /**
     * Update command availability based on settings and file permissions
     */
    private fun updateCommandAvailability(state: PlayerViewModel.PlayerState) {
        commandPanelController.updateCommandAvailability(state)
    }

    private fun displayText(mediaFile: MediaFile) {
        mediaLoaderManager.displayText(mediaFile)
    }
    
    private fun displayImage(path: String) {
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

    private fun playVideo(path: String) {
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
    private fun updateVolumeButtonsVisibility() {
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
    
    private fun showImageEditDialog() {
        dialogAndUiStateManager.showImageEditDialog()
    }
    
    private fun showGifEditDialog() {
        dialogAndUiStateManager.showGifEditDialog()
    }

    private fun isAnimatedImagePath(path: String): Boolean {
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

    private fun handleEvent(event: PlayerViewModel.PlayerEvent) {
        when (event) {
            is PlayerViewModel.PlayerEvent.ShowError -> {
                showError(event.message)
            }
            is PlayerViewModel.PlayerEvent.ShowMessage -> {
                Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
            }
            is PlayerViewModel.PlayerEvent.FileModified -> {
                // Track deleted/moved file
                lifecycleManager.trackModifiedFile(event.filePath)
            }
            is PlayerViewModel.PlayerEvent.ShowUndoSnackbar -> {
                undoOperationManager.showUndoSnackbar(event.operation)
            }
            is PlayerViewModel.PlayerEvent.CloudAuthRequired -> {
                showCloudAuthenticationError(event.provider)
            }
            is PlayerViewModel.PlayerEvent.ShowMissingFileDialog -> {
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.file_not_found_title)
                    .setMessage(getString(R.string.file_not_found_message, event.fileName))
                    .setPositiveButton(R.string.refresh_resource) { _, _ ->
                        viewModel.handleMissingFileRefresh(event.filePath)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        finish()
                    }
                    .show()
            }
            // Removed: LoadingProgress event handler (dialog not needed)
            PlayerViewModel.PlayerEvent.FinishActivity -> {
                finish()
            }
        }
    }
    
    /**
     * Show error message respecting showDetailedErrors setting
     * If showDetailedErrors=true: shows ErrorDialog with copyable text and detailed info
     * If showDetailedErrors=false: shows Toast (short notification)
     */
    internal fun showError(message: String, throwable: Throwable? = null) {
        // Check if activity is finishing to prevent WindowLeaked exception
        if (isFinishing || isDestroyed) {
            Timber.w("showError: Activity is finishing/destroyed, skipping error dialog")
            return
        }
        
        // Handle Android 10+ RecoverableSecurityException for delete operations
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && 
            throwable is android.app.RecoverableSecurityException) {
            try {
                Timber.i("showError: Launching delete permission request for user")
                val intentSender = throwable.userAction.actionIntent.intentSender
                deletePermissionLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                )
                return
            } catch (e: Exception) {
                Timber.e(e, "showError: Failed to launch delete permission request")
                // Fall through to show error message
            }
        }
        
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            // Don't show blocking dialogs if slideshow is active
            val isSlideshowActive = viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused
            
            if (settings.showDetailedErrors && !isSlideshowActive) {
                // Double-check before showing dialog
                if (isFinishing || isDestroyed) {
                    Timber.w("showError: Activity finished during settings load, skipping dialog")
                    return@launch
                }
                
                if (throwable != null) {
                    // Use ErrorDialog with full stack trace
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                        context = this@PlayerActivity,
                        title = getString(R.string.error),
                        message = message,
                        details = throwable.stackTraceToString()
                    )
                } else {
                    // Use ErrorDialog without details
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                        context = this@PlayerActivity,
                        title = getString(R.string.error),
                        message = message
                    )
                }
            } else {
                // Non-fatal network errors: debug-only toast, always logged
                com.sza.fastmediasorter.util.ToastThrottler.showNetworkError(
                    this@PlayerActivity, message
                )
            }
        }
    }

    /**
     * Show specialized dialog for Cloud authentication errors
     */
    private fun showCloudAuthenticationError(providerName: String? = null) {
        dialogHelper.showCloudAuthError(providerName) {
            // Trigger authentication based on provider
            when (providerName?.lowercase()) {
                "dropbox" -> cloudAuthManager.launchDropboxSignIn()
                "google drive", "google_drive" -> cloudAuthManager.launchGoogleSignIn()
                "onedrive" -> cloudAuthManager.launchOneDriveSignIn()
                else -> {
                    // If provider is unknown, maybe show a chooser or default to something?
                    // For now, just log warning
                    Timber.w("Unknown provider for auth request: $providerName")
                }
            }
        }
    }
    
    /**
     * Show error dialog for unsupported format with option to open in external player
     */
    internal fun showUnsupportedFormatError(message: String, filePath: String, isLocalFile: Boolean) {
        if (isFinishing || isDestroyed) {
            Timber.w("showUnsupportedFormatError: Activity is finishing/destroyed, skipping dialog")
            return
        }
        
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            val isSlideshowActive = viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused
            
            if (settings.showDetailedErrors && !isSlideshowActive) {
                if (isFinishing || isDestroyed) {
                    Timber.w("showUnsupportedFormatError: Activity finished during settings load, skipping dialog")
                    return@launch
                }
                
                if (isLocalFile) {
                    // Local file - offer to open in external player
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                        context = this@PlayerActivity,
                        title = getString(R.string.error),
                        message = message,
                        actionButtonText = getString(R.string.open_in_external_player),
                        onActionClick = { openInExternalPlayer(filePath) }
                    )
                } else {
                    // Network file - suggest copying to local resource
                    val networkMessage = "$message\n\n" +
                        getString(R.string.unsupported_format_network_hint)
                    com.sza.fastmediasorter.ui.dialog.ErrorDialog.show(
                        context = this@PlayerActivity,
                        title = getString(R.string.error),
                        message = networkMessage
                    )
                }
            } else {
                // Show toast
                val toastMessage = if (isLocalFile) {
                    getString(R.string.unsupported_format_use_external_player)
                } else {
                    getString(R.string.unsupported_format_copy_to_local)
                }
                Toast.makeText(this@PlayerActivity, toastMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Open file in external player using ACTION_VIEW intent
     */
    private fun openInExternalPlayer(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show()
                return
            }
            
            // Use FileProvider for secure file access
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            // Determine MIME type
            val mimeType = when (file.extension.lowercase()) {
                "flv" -> "video/x-flv"
                "avi" -> "video/x-msvideo"
                "mid", "midi" -> "audio/midi"
                "wmv" -> "video/x-ms-wmv"
                "rm", "rmvb" -> "video/vnd.rn-realvideo"
                "vob" -> "video/dvd"
                "ogv" -> "video/ogg"
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "mp3" -> "audio/mpeg"
                "aac" -> "audio/aac"
                else -> "*/*"
            }
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            // Check if any app can handle this intent
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(Intent.createChooser(intent, getString(R.string.open_with)))
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.no_app_to_handle_format),
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to open file in external player")
            Toast.makeText(
                this,
                getString(R.string.error_opening_external_player, e.message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

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

    private fun releasePlayer() {
        // Deprecated: delegated to VideoPlayerManager
        if (_videoPlayerManager != null) {
            videoPlayerManager.releasePlayer()
        }
    }

    /**
     * Stop video playback and release resources.
     * Called when switching to non-media files (EPUB, PDF, Text, Image)
     */
    private fun stopVideoPlayback() {
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
        viewModel.togglePause()
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
    
    private fun shareCurrentFile() {
        // Delegate to FileOperationsHandler
        fileOperationsHandler.performShare()
    }
    
    /**
     * Extract bitmap from any drawable type.
     * Handles BitmapDrawable, GifDrawable, TransitionDrawable (from Glide crossfade),
     * and any other drawable by rendering it to a bitmap canvas.
     */
    private fun extractBitmapFromDrawable(drawable: android.graphics.drawable.Drawable?, source: String): android.graphics.Bitmap? {
        if (drawable == null) {
            Timber.d("extractBitmapFromDrawable: drawable is null ($source)")
            return null
        }
        
        return when (drawable) {
            is android.graphics.drawable.BitmapDrawable -> {
                Timber.d("extractBitmapFromDrawable: BitmapDrawable ($source)")
                drawable.bitmap
            }
            is com.bumptech.glide.load.resource.gif.GifDrawable -> {
                Timber.d("extractBitmapFromDrawable: GifDrawable - extracting current frame ($source)")
                val width = drawable.intrinsicWidth
                val height = drawable.intrinsicHeight
                if (width <= 0 || height <= 0) return null
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap
            }
            is android.graphics.drawable.TransitionDrawable -> {
                // Glide crossfade wraps the actual drawable in TransitionDrawable
                // The last layer (index numberOfLayers-1) is the loaded image
                Timber.d("extractBitmapFromDrawable: TransitionDrawable with ${drawable.numberOfLayers} layers ($source)")
                val lastLayer = drawable.getDrawable(drawable.numberOfLayers - 1)
                extractBitmapFromDrawable(lastLayer, "$source/transitionLayer")
            }
            else -> {
                // Fallback: render any drawable to bitmap
                Timber.d("extractBitmapFromDrawable: fallback rendering ${drawable.javaClass.simpleName} ($source)")
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: return null
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: return null
                val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap
            }
        }
    }

    /**
     * Stop active translation and hide overlays.
     * Cancels any running translation job.
     */
    private fun stopTranslation() {
        translationJob?.cancel()
        safeViews.translationOverlay.isVisible = false
        safeViews.translationOverlayBackground.isVisible = false // Also hide background
        binding.translationLensOverlay.isVisible = false
        binding.translationLensOverlay.clear() // Clear blocks
        
        // Remove matrix change listener from PhotoView
        if (binding.photoView.isVisible) {
            binding.photoView.setOnMatrixChangeListener(null)
        }
        
        safeViews.btnTranslateImage.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()) // White
        binding.btnTranslateImageCmd.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()) // White
        safeViews.btnTranslationFontDecrease?.visibility = android.view.View.GONE
        safeViews.btnTranslationFontIncrease?.visibility = android.view.View.GONE
        Timber.d("Translation stopped and overlays hidden")
    }
    
    /**
     * Translate current image using OCR + Translation
     */
    internal fun translateCurrentImage() {
        val currentFile = viewModel.state.value.currentFile
        if (currentFile?.type != MediaType.IMAGE && currentFile?.type != MediaType.GIF) {
            binding.btnTranslateImageCmd.visibility = android.view.View.GONE
            showError("Translation is only available for images")
            return
        }
        
        // Toggle translation state - check both overlay types
        val isCurrentlyVisible = safeViews.translationOverlay.isVisible || binding.translationLensOverlay.isVisible
        
        if (isCurrentlyVisible) {
            stopTranslation()
            return
        }
        
        // Cancel any existing job just in case
        translationJob?.cancel()
        
        // Get bitmap from current image view (including GIF first frame)
        // Check all possible image surfaces: photoView, photoViewSurfaceB (dual surface), imageView
        val bitmap = when {
            binding.photoView.isVisible -> {
                extractBitmapFromDrawable(binding.photoView.drawable, "photoView")
            }
            binding.photoViewSurfaceB?.isVisible == true -> {
                extractBitmapFromDrawable(binding.photoViewSurfaceB?.drawable, "photoViewSurfaceB")
            }
            binding.imageView.isVisible -> {
                extractBitmapFromDrawable(binding.imageView.drawable, "imageView")
            }
            else -> {
                Timber.w("translateCurrentImage: No image view is visible (photoView=${binding.photoView.isVisible}, surfaceB=${binding.photoViewSurfaceB?.isVisible}, imageView=${binding.imageView.isVisible})")
                null
            }
        }
        
        if (bitmap == null) {
            showError("Could not extract image from file")
            return
        }
        
        // Turn on translation - set button to red
        safeViews.btnTranslateImage.imageTintList = android.content.res.ColorStateList.valueOf(0xFFF44336.toInt()) // Red
        binding.btnTranslateImageCmd.imageTintList = android.content.res.ColorStateList.valueOf(0xFFF44336.toInt()) // Red
        
        // Get view dimensions for overlay scaling
        val viewWidth = if (binding.photoView.isVisible) binding.photoView.width else binding.imageView.width
        val viewHeight = if (binding.photoView.isVisible) binding.photoView.height else binding.imageView.height
        
        // Get PhotoView's current display rect if available (includes zoom/pan state)
        val displayRect = if (binding.photoView.isVisible) {
            val rect = binding.photoView.displayRect
            Timber.d("TRANSLATION_DEBUG: Captured displayRect from PhotoView: $rect")
            Timber.d("TRANSLATION_DEBUG: PhotoView dimensions: ${binding.photoView.width}x${binding.photoView.height}")
            Timber.d("TRANSLATION_DEBUG: Bitmap dimensions: ${bitmap.width}x${bitmap.height}")
            rect
        } else {
            Timber.d("TRANSLATION_DEBUG: PhotoView not visible, using ImageView")
            null
        }
        
        translationJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsRepository.getSettings().first()
                val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
                val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
                val useLensStyle = settings.translationLensStyle
                
                Timber.d("TRANSLATION_DEBUG: Settings - useLensStyle=$useLensStyle, sourceLang=$sourceLang, targetLang=$targetLang")
                Timber.d("TRANSLATION_DEBUG: Bitmap size: ${bitmap.width}x${bitmap.height}")
                Timber.d("TRANSLATION_DEBUG: DisplayRect captured: $displayRect")
                
                if (useLensStyle) {
                    Timber.d("TRANSLATION_DEBUG: Taking GOOGLE LENS style path")
                    // Use Google Lens style translation with block positions
                    val lensHelper = com.sza.fastmediasorter.ui.player.helpers.GoogleLensTranslationHelper(
                        binding.translationLensOverlay,
                        translationManager
                    )
                    
                    lensHelper.translateBitmap(
                        bitmap = bitmap,
                        sourceLang = sourceLang,
                        targetLang = targetLang,
                        viewWidth = viewWidth,
                        viewHeight = viewHeight,
                        displayRect = displayRect,
                        onSuccess = { _ ->
                            // Hide the old-style overlay
                            safeViews.translationOverlay.isVisible = false
                            
                            // Setup matrix change listener for PhotoView to update overlay position
                            if (binding.photoView.isVisible) {
                                binding.photoView.setOnMatrixChangeListener { rect ->
                                    binding.translationLensOverlay.updateImageDisplayRect(rect)
                                }
                                // Initial update
                                binding.photoView.displayRect?.let { rect ->
                                    binding.translationLensOverlay.updateImageDisplayRect(rect)
                                }
                            }
                            
                            // Show font size controls when translation is active
                            safeViews.btnTranslationFontDecrease?.visibility = android.view.View.VISIBLE
                            safeViews.btnTranslationFontIncrease?.visibility = android.view.View.VISIBLE
                        },
                        onEmpty = {
                            // No text detected - reset button and show toast
                            safeViews.btnTranslateImage.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()) // White
                            binding.btnTranslateImageCmd.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()) // White
                            safeViews.btnTranslationFontDecrease?.visibility = android.view.View.GONE
                            safeViews.btnTranslationFontIncrease?.visibility = android.view.View.GONE
                            showError(getString(R.string.translation_no_text_found))
                        },
                        onError = { message ->
                            safeViews.btnTranslateImage.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()) // White
                            binding.btnTranslateImageCmd.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()) // White
                            showError("Translation failed: $message")
                        }
                    )
                } else {
                    Timber.d("TRANSLATION_DEBUG: Taking LEGACY text viewer style path")
                    // Use text viewer to display translation (same as OCR results)
                    val result = translationManager.recognizeAndTranslate(
                        bitmap = bitmap,
                        sourceLang = sourceLang,
                        targetLang = targetLang
                    )
                    
                    withContext(Dispatchers.Main) {
                        if (result != null) {
                            val (_, translated) = result
                            // Display translated text in text viewer (same as OCR)
                            if (_textViewerManager != null) {
                                textViewerManager.displayTranslatedText(translated)
                            }
                            binding.translationLensOverlay.isVisible = false // Hide lens overlay
                            
                            // Hide font size controls (text viewer has its own)
                            safeViews.btnTranslationFontDecrease?.visibility = android.view.View.GONE
                            safeViews.btnTranslationFontIncrease?.visibility = android.view.View.GONE
                        } else {
                            // No text or error
                            safeViews.btnTranslateImage.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()) // White
                            binding.btnTranslateImageCmd.imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()) // White
                            safeViews.btnTranslationFontDecrease?.visibility = android.view.View.GONE
                            safeViews.btnTranslationFontIncrease?.visibility = android.view.View.GONE
                            showError(getString(R.string.translation_no_text_found))
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                
                withContext(Dispatchers.Main) {
                    stopTranslation()
                    showError("Translation failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Extract text from current image using OCR (no translation)
     */
    private fun extractTextFromCurrentImage() {
        // Delegate OCR text extraction to ImageOcrManager
        // This consolidates bitmap extraction and OCR logic
        imageOcrManager.extractTextFromCurrentImage(viewModel.state.value.currentFile)
    }
    

    
    override fun onResume() {
        super.onResume()
        
        // Delegate to lifecycle manager
        lifecycleManager.onResume()
        audioEmptyStateController?.onResume()
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
    
    private fun shareCurrentFileToGoogleLens() {
        val currentFile = viewModel.state.value.currentFile ?: return
        
        // If it's a local file, we can share it directly
        if (!currentFile.path.contains("://")) {
            shareFileToGoogleLens(File(currentFile.path))
        } else {
            // For network files, we need to download/cache it first
            lifecycleScope.launch {
                try {
                    val file = networkFileManager.prepareFileForRead(currentFile)
                    shareFileToGoogleLens(file)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to prepare file for Google Lens")
                    Toast.makeText(this@PlayerActivity, R.string.toast_failed_to_prepare_file, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun shareFileToGoogleLens(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Add ClipData for better permission propagation (fixes SecurityException on some devices)
            intent.clipData = android.content.ClipData.newRawUri(null, uri)
            
            // Try to find Google Lens package
            val lensPackage = "com.google.ar.lens"
            val googlePackage = "com.google.android.googlequicksearchbox"
            val packageManager = packageManager
            
            // 1. Try standalone Lens app
            intent.setPackage(lensPackage)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            } 
            
            // 2. Try Google App (often handles Lens)
            intent.setPackage(googlePackage)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                return
            }
            
            // 3. Fallback to generic chooser
            intent.setPackage(null)
            startActivity(Intent.createChooser(intent, getString(R.string.enable_google_lens)))
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to share to Google Lens")
            Toast.makeText(this, R.string.toast_error_google_lens, Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Show dialog for PDF editing options
     */
    private fun showPdfEditDialog() {
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
        )
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
                    this@PlayerActivity.shareFileToGoogleLens(file)
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
            initialFilePath: String? = null
        ): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra("resourceId", resourceId)
                putExtra("initialIndex", initialIndex)
                putExtra("skipAvailabilityCheck", skipAvailabilityCheck)
                initialFilePath?.let { putExtra("initialFilePath", it) }
            }
        }
    }
}

