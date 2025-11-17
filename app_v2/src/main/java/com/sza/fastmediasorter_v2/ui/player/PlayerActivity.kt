package com.sza.fastmediasorter_v2.ui.player

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import coil.imageLoader
import coil.load
import coil.request.ImageRequest
import coil.size.Size
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.data.network.SmbClient
import com.sza.fastmediasorter_v2.data.remote.sftp.SftpClient
import com.sza.fastmediasorter_v2.data.remote.ftp.FtpClient
import com.sza.fastmediasorter_v2.data.network.coil.NetworkFileData
import com.sza.fastmediasorter_v2.data.network.datasource.SmbDataSourceFactory
import com.sza.fastmediasorter_v2.data.network.datasource.SftpDataSourceFactory
import com.sza.fastmediasorter_v2.data.network.datasource.FtpDataSourceFactory
import com.sza.fastmediasorter_v2.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter_v2.domain.model.MediaFile
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.domain.model.ResourceType
import com.sza.fastmediasorter_v2.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter_v2.domain.repository.SettingsRepository
import com.sza.fastmediasorter_v2.ui.dialog.CopyToDialog
import com.sza.fastmediasorter_v2.ui.dialog.MoveToDialog
import com.sza.fastmediasorter_v2.ui.dialog.RenameDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class PlayerActivity : BaseActivity<ActivityPlayerUnifiedBinding>() {
    override fun getViewBinding(): ActivityPlayerUnifiedBinding {
        return ActivityPlayerUnifiedBinding.inflate(layoutInflater)
    }

    private val viewModel: PlayerViewModel by viewModels()
    private var exoPlayer: ExoPlayer? = null
    private val slideShowHandler = Handler(Looper.getMainLooper())
    private val hideControlsHandler = Handler(Looper.getMainLooper())
    private val loadingIndicatorHandler = Handler(Looper.getMainLooper())
    private val countdownHandler = Handler(Looper.getMainLooper())
    
    // Track preload jobs to cancel on destroy
    private val preloadJobs = mutableListOf<Job>()
    private lateinit var gestureDetector: GestureDetector
    private val touchZoneDetector = TouchZoneDetector()
    private var useTouchZones = true // Use touch zones for images, gestures for video
    private var countdownSeconds = 3 // Current countdown value
    private var isFirstResume = true // Track first onResume to avoid duplicate load
    private var hasShownFirstRunHint = false // Track if first-run hint has been shown in this session

    // Injected dependencies for network playback
    @Inject
    lateinit var smbClient: SmbClient
    
    @Inject
    lateinit var sftpClient: SftpClient
    
    @Inject
    lateinit var ftpClient: FtpClient
    
    @Inject
    lateinit var credentialsRepository: NetworkCredentialsRepository
    
    @Inject
    lateinit var settingsRepository: SettingsRepository
    
    @Inject
    lateinit var rotateImageUseCase: com.sza.fastmediasorter_v2.domain.usecase.RotateImageUseCase
    
    @Inject
    lateinit var flipImageUseCase: com.sza.fastmediasorter_v2.domain.usecase.FlipImageUseCase
    
    @Inject
    lateinit var networkImageEditUseCase: com.sza.fastmediasorter_v2.domain.usecase.NetworkImageEditUseCase

    private val slideShowRunnable = object : Runnable {
        override fun run() {
            if (viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused) {
                val currentFile = viewModel.state.value.currentFile
                val isMediaPlaying = currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO
                
                // If playToEndInSlideshow is enabled and media is playing, don't auto-advance
                // (will be handled by exoPlayerListener.STATE_ENDED)
                if (viewModel.state.value.playToEndInSlideshow && isMediaPlaying) {
                    Timber.d("PlayerActivity.slideShowRunnable: Skipping auto-advance - waiting for media to end")
                    // Schedule next check in case something goes wrong
                    slideShowHandler.postDelayed(this, viewModel.state.value.slideShowInterval)
                } else {
                    // For images or when playToEnd is disabled - normal auto-advance
                    viewModel.nextFile()
                    slideShowHandler.postDelayed(this, viewModel.state.value.slideShowInterval)
                }
            }
        }
    }

    private val hideControlsRunnable = Runnable {
        if (!viewModel.state.value.isPaused) {
            viewModel.toggleControls()
        }
    }

    private val showLoadingIndicatorRunnable = Runnable {
        binding.progressBar.isVisible = true
    }

    private val countdownRunnable = object : Runnable {
        override fun run() {
            if (viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused) {
                if (countdownSeconds > 0) {
                    binding.tvCountdown.text = "$countdownSeconds.."
                    binding.tvCountdown.isVisible = true
                    countdownSeconds--
                    countdownHandler.postDelayed(this, 1000)
                } else {
                    binding.tvCountdown.isVisible = false
                }
            }
        }
    }

    /**
     * ExoPlayer listener for video/audio playback events
     * Handles: STATE_READY (hide loading indicator), STATE_ENDED (auto-advance in slideshow)
     */
    private val exoPlayerListener = object : androidx.media3.common.Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val stateName = when (playbackState) {
                androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                androidx.media3.common.Player.STATE_READY -> "READY"
                androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                else -> "UNKNOWN($playbackState)"
            }
            Timber.d("PlayerActivity.exoPlayerListener: onPlaybackStateChanged - state=$stateName")
            
            // Check if activity is being destroyed to avoid accessing binding
            if (isDestroyed || isFinishing) {
                Timber.w("PlayerActivity.exoPlayerListener: Activity is being destroyed, ignoring state change")
                return
            }
            
            when (playbackState) {
                androidx.media3.common.Player.STATE_READY -> {
                    // Video is ready to play - cancel and hide loading indicator
                    Timber.i("PlayerActivity.exoPlayerListener: Video READY - hiding loading indicator")
                    loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                    binding.progressBar.isVisible = false
                    
                    // Update audio info with format details if this is an audio file
                    if (viewModel.state.value.currentFile?.type == MediaType.AUDIO) {
                        updateAudioFormatInfo()
                    }
                }
                androidx.media3.common.Player.STATE_ENDED -> {
                    Timber.d("PlayerActivity.exoPlayerListener: Playback ENDED")
                    // Video/audio finished playing
                    if (viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused) {
                        // Auto-advance to next file in slideshow mode (respects playToEndInSlideshow setting)
                        Timber.d("PlayerActivity.exoPlayerListener: Slideshow active - advancing to next file")
                        viewModel.nextFile()
                        // Restart slideshow timer for the next file
                        updateSlideShow()
                    }
                }
                androidx.media3.common.Player.STATE_BUFFERING -> {
                    Timber.d("PlayerActivity.exoPlayerListener: Video BUFFERING")
                }
            }
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Timber.e("PlayerActivity.exoPlayerListener: onPlayerError - errorCode=${error.errorCode}, message=${error.message}")
            Timber.e("PlayerActivity.exoPlayerListener: Error cause: ${error.cause}")
            Timber.e("PlayerActivity.exoPlayerListener: Stack trace: ${error.stackTraceToString()}")
            
            // Check if activity is being destroyed to avoid accessing binding after onDestroy
            if (isDestroyed || isFinishing) {
                Timber.w("PlayerActivity.exoPlayerListener: Activity is being destroyed, ignoring error")
                return
            }
            
            // Cancel and hide loading indicator on error
            loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
            binding.progressBar.isVisible = false
            
            showError("Playback error: ${error.message}", error.cause)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun setupViews() {
        setupGestureDetector()
        setupToolbar()
        setupControls()
        setupCommandPanelControls()
        setupTouchZones()
        
        // Setup audio info overlay toggle (tap to hide)
        binding.audioInfoOverlay.setOnClickListener {
            binding.audioInfoOverlay.isVisible = false
        }
    }

    override fun observeData() {
        observeViewModel()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val currentFile = viewModel.state.value.currentFile
                val isInFullscreenMode = !viewModel.state.value.showCommandPanel
                
                Timber.d("PlayerActivity.onSingleTapConfirmed: tap at (${e.x}, ${e.y}), fullscreen=$isInFullscreenMode, useTouchZones=$useTouchZones, fileType=${currentFile?.type}")
                
                // In fullscreen mode, use touch zones for both images and videos
                if (isInFullscreenMode && useTouchZones) {
                    Timber.d("PlayerActivity.onSingleTapConfirmed: Calling handleTouchZone()")
                    handleTouchZone(e.x, e.y)
                } else if (currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO) {
                    // For video/audio in command panel mode, toggle controls
                    Timber.d("PlayerActivity.onSingleTapConfirmed: Video in command panel mode - toggling controls")
                    viewModel.toggleControls()
                    scheduleHideControls()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Double tap still works for quick navigation (fallback)
                val screenWidth = binding.root.width
                if (e.x < screenWidth / 3) {
                    viewModel.previousFile()
                } else if (e.x > screenWidth * 2 / 3) {
                    viewModel.nextFile()
                } else {
                    viewModel.togglePause()
                    if (viewModel.state.value.currentFile?.type == MediaType.IMAGE) {
                        updateSlideShow()
                    }
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val absX = Math.abs(velocityX)
                val absY = Math.abs(velocityY)
                
                // Determine if gesture is more horizontal or vertical
                if (absX > absY) {
                    // Horizontal fling - navigate between files
                    if (velocityX > 0) {
                        viewModel.previousFile()
                    } else {
                        viewModel.nextFile()
                    }
                    return true
                } else {
                    // Vertical fling - file operations
                    if (velocityY < 0) {
                        // Swipe UP -> Copy
                        showCopyDialog()
                    } else {
                        // Swipe DOWN -> Move
                        showMoveDialog()
                    }
                    return true
                }
            }
        })

        binding.root.setOnTouchListener { _, event ->
            // For video in fullscreen, allow touches in bottom 25% to pass through to PlayerView
            val currentFile = viewModel.state.value.currentFile
            val isInFullscreenMode = !viewModel.state.value.showCommandPanel
            val isVideo = currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO
            
            if (isVideo && isInFullscreenMode && event.action == MotionEvent.ACTION_DOWN) {
                val screenHeight = binding.root.height
                val effectiveHeight = (screenHeight * 0.75f).toInt()
                
                // If touch is in bottom 25%, don't consume the event - let it pass to PlayerView
                if (event.y > effectiveHeight) {
                    return@setOnTouchListener false
                }
            }
            
            // Let gesture detector handle the event
            // For images and upper area of video, consume the event (return true)
            gestureDetector.onTouchEvent(event)
            true
        }
        
        // Set touch listener on PlayerView to intercept touches before PlayerView handles them
        binding.playerView.setOnTouchListener { _, event ->
            val currentFile = viewModel.state.value.currentFile
            val isInFullscreenMode = !viewModel.state.value.showCommandPanel
            val isVideo = currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO
            
            Timber.d("PlayerActivity.playerView.onTouch: event=${event.action}, fullscreen=$isInFullscreenMode, video=$isVideo, useTouchZones=$useTouchZones")
            
            // In fullscreen mode with touch zones enabled, let our gesture detector handle it
            if (isInFullscreenMode && useTouchZones) {
                Timber.d("PlayerActivity.playerView.onTouch: Delegating to gesture detector")
                gestureDetector.onTouchEvent(event)
                return@setOnTouchListener true // Consume event to prevent PlayerView from handling it
            }
            
            // Otherwise, let PlayerView handle its own touches (controls)
            false
        }
    }
    
    /**
     * Handle touch zones for static images (3x3 grid)
     * For video: only upper 75% of screen is touch-sensitive
     */
    private fun handleTouchZone(x: Float, y: Float) {
        val currentFile = viewModel.state.value.currentFile
        val screenWidth = binding.root.width
        val screenHeight = binding.root.height
        
        Timber.d("PlayerActivity.handleTouchZone: x=$x, y=$y, screenSize=${screenWidth}x${screenHeight}, fileType=${currentFile?.type}")
        
        // For video/audio, limit touch zones to upper portion to leave space for ExoPlayer controls
        // Audio: 66% (upper two thirds), Video: 75% (upper three quarters)
        val effectiveHeight = when (currentFile?.type) {
            MediaType.AUDIO -> (screenHeight * 0.66f).toInt() // Upper 66% (2/3) for audio
            MediaType.VIDEO -> (screenHeight * 0.75f).toInt() // Upper 75% for video
            else -> screenHeight
        }
        
        Timber.d("PlayerActivity.handleTouchZone: effectiveHeight=$effectiveHeight (66% for audio, 75% for video)")
        
        // If touch is below effective height, ignore
        if (y > effectiveHeight) {
            Timber.d("PlayerActivity.handleTouchZone: Touch in lower area - ignored")
            return
        }
        
        val zone = touchZoneDetector.detectZone(x, y, screenWidth, effectiveHeight)
        Timber.d("PlayerActivity.handleTouchZone: Detected zone=$zone")
        
        // Stop slideshow on any touch zone except NEXT and SLIDESHOW (toggle handled separately)
        if (zone != TouchZone.NEXT && zone != TouchZone.SLIDESHOW && zone != TouchZone.NONE) {
            if (viewModel.state.value.isSlideShowActive) {
                viewModel.toggleSlideShow()
                updateSlideShow()
            }
        }
        
        when (zone) {
            TouchZone.BACK -> {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
            TouchZone.COPY -> {
                showCopyDialog()
            }
            TouchZone.RENAME -> {
                showRenameDialog()
            }
            TouchZone.PREVIOUS -> {
                viewModel.previousFile()
            }
            TouchZone.MOVE -> {
                showMoveDialog()
            }
            TouchZone.NEXT -> {
                // Reset slideshow timer but keep slideshow running
                if (viewModel.state.value.isSlideShowActive) {
                    updateSlideShow()
                }
                viewModel.nextFile()
            }
            TouchZone.COMMAND_PANEL -> {
                viewModel.toggleCommandPanel()
            }
            TouchZone.DELETE -> {
                deleteCurrentFile()
            }
            TouchZone.SLIDESHOW -> {
                val wasActive = viewModel.state.value.isSlideShowActive
                viewModel.toggleSlideShow()
                
                // Show popup when enabling slideshow
                if (!wasActive && viewModel.state.value.isSlideShowActive) {
                    showSlideshowEnabledMessage()
                }
                
                updateSlideShowButton()
                updateSlideShow()
            }
            TouchZone.NONE -> {
                // No action
            }
        }
    }
    
    private fun showCopyDialog() {
        val currentFile = viewModel.state.value.currentFile ?: return
        val resourceId = intent.getLongExtra("resourceId", -1)
        
        // For network paths (SMB/SFTP), create File with URI-compatible scheme
        val sourceFile = if (currentFile.path.startsWith("smb://") || currentFile.path.startsWith("sftp://")) {
            // Use custom File with network path that preserves the scheme
            object : File(currentFile.path) {
                override fun getAbsolutePath(): String = currentFile.path
                override fun getPath(): String = currentFile.path
            }
        } else {
            File(currentFile.path)
        }
        
        Timber.d("PlayerActivity.showCopyDialog: currentFile.path=${currentFile.path}")
        Timber.d("PlayerActivity.showCopyDialog: sourceFile.path=${sourceFile.path}")
        Timber.d("PlayerActivity.showCopyDialog: sourceFile.absolutePath=${sourceFile.absolutePath}")
        
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            val resource = viewModel.state.value.resource
            
            CopyToDialog(
                context = this@PlayerActivity,
                sourceFiles = listOf(sourceFile),
                sourceFolderName = resource?.name ?: "Current folder",
                currentResourceId = resourceId,
                fileOperationUseCase = viewModel.fileOperationUseCase,
                getDestinationsUseCase = viewModel.getDestinationsUseCase,
                overwriteFiles = settings.overwriteOnCopy,
                onComplete = { undoOperation ->
                    // Save undo operation if enabled
                    if (settings.enableUndo && undoOperation != null) {
                        viewModel.saveUndoOperation(undoOperation)
                    }
                    // Go to next file if setting enabled
                    if (settings.goToNextAfterCopy) {
                        viewModel.nextFile()
                    }
                }
            ).show()
        }
    }
    
    private fun showMoveDialog() {
        val currentFile = viewModel.state.value.currentFile ?: return
        val resourceId = intent.getLongExtra("resourceId", -1)
        
        // For network paths (SMB/SFTP), create File with URI-compatible scheme
        val sourceFile = if (currentFile.path.startsWith("smb://") || currentFile.path.startsWith("sftp://")) {
            object : File(currentFile.path) {
                override fun getAbsolutePath(): String = currentFile.path
                override fun getPath(): String = currentFile.path
            }
        } else {
            File(currentFile.path)
        }
        
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            val resource = viewModel.state.value.resource
            
            MoveToDialog(
                context = this@PlayerActivity,
                sourceFiles = listOf(sourceFile),
                sourceFolderName = resource?.name ?: "Current folder",
                currentResourceId = resourceId,
                fileOperationUseCase = viewModel.fileOperationUseCase,
                getDestinationsUseCase = viewModel.getDestinationsUseCase,
                overwriteFiles = settings.overwriteOnMove,
                onComplete = { undoOperation ->
                    // Save undo operation if enabled
                    if (settings.enableUndo && undoOperation != null) {
                        viewModel.saveUndoOperation(undoOperation)
                    }
                    // Go to next file after move (current file was moved)
                    viewModel.nextFile()
                }
            ).show()
        }
    }
    
    private fun showRenameDialog() {
        val currentFile = viewModel.state.value.currentFile ?: return
        val resource = viewModel.state.value.resource
        
        RenameDialog(
            context = this,
            lifecycleOwner = this,
            files = listOf(File(currentFile.path)),
            sourceFolderName = resource?.name ?: "Current folder",
            fileOperationUseCase = viewModel.fileOperationUseCase,
            onComplete = {
                // File list will be reloaded in BrowseActivity.onResume()
                // No need to refresh here
            }
        ).show()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupControls() {
        binding.btnPrevious.setOnClickListener {
            viewModel.previousFile()
            scheduleHideControls()
        }

        binding.btnNext.setOnClickListener {
            viewModel.nextFile()
            scheduleHideControls()
        }

        binding.btnPlayPause.setOnClickListener {
            viewModel.togglePause()
            updatePlayPauseButton()
            if (viewModel.state.value.currentFile?.type == MediaType.IMAGE) {
                updateSlideShow()
            }
            scheduleHideControls()
        }

        binding.btnSlideShow.setOnClickListener {
            val wasActive = viewModel.state.value.isSlideShowActive
            viewModel.toggleSlideShow()
            
            // Show popup when enabling slideshow
            if (!wasActive && viewModel.state.value.isSlideShowActive) {
                showSlideshowEnabledMessage()
            }
            
            updateSlideShowButton()
            updateSlideShow()
            scheduleHideControls()
        }

        binding.btnInfoCmd.setOnClickListener {
            showFileInfo()
            scheduleHideControls()
        }

        binding.btnDelete.setOnClickListener {
            deleteCurrentFile()
            scheduleHideControls()
        }
    }

    private fun setupCommandPanelControls() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnPreviousCmd.setOnClickListener {
            viewModel.previousFile()
        }

        binding.btnNextCmd.setOnClickListener {
            viewModel.nextFile()
        }

        binding.btnRenameCmd.setOnClickListener {
            showRenameDialog()
        }

        binding.btnDeleteCmd.setOnClickListener {
            deleteCurrentFile()
        }
        
        binding.btnEditCmd.setOnClickListener {
            showImageEditDialog()
        }
        
        binding.btnUndoCmd.setOnClickListener {
            viewModel.undoLastOperation()
        }

        binding.btnFullscreenCmd.setOnClickListener {
            // Toggle to fullscreen mode (hide command panel)
            if (viewModel.state.value.showCommandPanel) {
                viewModel.toggleCommandPanel()
            }
        }

        binding.btnSlideshowCmd.setOnClickListener {
            val wasActive = viewModel.state.value.isSlideShowActive
            viewModel.toggleSlideShow()
            
            // Show popup when enabling slideshow
            if (!wasActive && viewModel.state.value.isSlideShowActive) {
                showSlideshowEnabledMessage()
            }
            
            updateSlideShowButton()
            updateSlideShow()
        }
        
        // Setup collapsible Copy to panel
        binding.copyToPanelHeader.setOnClickListener {
            toggleCopyPanel()
        }
        
        // Setup collapsible Move to panel
        binding.moveToPanelHeader.setOnClickListener {
            toggleMovePanel()
        }
    }

    private fun setupTouchZones() {
        binding.touchZonePrevious.setOnClickListener {
            viewModel.previousFile()
        }

        binding.touchZoneNext.setOnClickListener {
            viewModel.nextFile()
        }
        
        // First-run hint overlay removed (migrated to new settings system)
    }
    
    /**
     * Show first-run hint overlay with touch zones guide
     * Dismisses on tap or after 5 seconds timeout
     */
    private fun showFirstRunHintOverlay() {
        // Make overlay visible with semi-transparent background
        binding.audioTouchZonesOverlay.isVisible = true
        binding.audioTouchZonesOverlay.alpha = 0.9f
        
        // Dismiss on any tap
        binding.audioTouchZonesOverlay.setOnClickListener {
            dismissFirstRunHintOverlay()
        }
        
        // Auto-dismiss after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            dismissFirstRunHintOverlay()
        }, 5000)
    }
    
    /**
     * Dismiss first-run hint overlay (deprecated)
     */
    private fun dismissFirstRunHintOverlay() {
        binding.audioTouchZonesOverlay.isVisible = false
        binding.audioTouchZonesOverlay.setOnClickListener(null)
    }
    
    /**
     * Adjust touch zones height based on media type
     * For video in command panel mode: only upper 50% to leave space for ExoPlayer controls
     */
    private fun adjustTouchZonesForVideo(isVideo: Boolean) {
        if (!viewModel.state.value.showCommandPanel) {
            // In fullscreen mode, touch zones are handled differently (via handleTouchZone)
            return
        }
        
        // In command panel mode, adjust overlay height
        val overlay = binding.touchZonesOverlay
        val layoutParams = overlay.layoutParams
        
        if (isVideo) {
            // For video: touch zones only upper 50% of media area
            layoutParams.height = 0 // Will be calculated dynamically
            overlay.layoutParams = layoutParams
            
            // Post to ensure layout is ready
            overlay.post {
                val mediaAreaHeight = binding.mediaContentArea.height
                val touchZoneHeight = (mediaAreaHeight * 0.5f).toInt()
                
                val newParams = overlay.layoutParams
                newParams.height = touchZoneHeight
                overlay.layoutParams = newParams
            }
        } else {
            // For images: touch zones cover full media area
            layoutParams.height = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            overlay.layoutParams = layoutParams
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        updateUI(state)
                    }
                }

                launch {
                    viewModel.loading.collect { isLoading ->
                        binding.progressBar.isVisible = isLoading
                    }
                }

                launch {
                    viewModel.events.collect { event ->
                        handleEvent(event)
                    }
                }
            }
        }
    }

    private fun updateUI(state: PlayerViewModel.PlayerState) {
        Timber.d("PlayerActivity.updateUI: START - currentFile=${state.currentFile?.name}, type=${state.currentFile?.type}")
        
        // Skip UI updates until files are loaded (avoids 3 redundant calls with null data)
        if (state.files.isEmpty()) {
            Timber.d("PlayerActivity.updateUI: Files not loaded yet, skipping UI update")
            return
        }
        
        // Show first-run hint if enabled and not shown yet (only in fullscreen mode without command panel)
        if (!hasShownFirstRunHint && state.currentFile != null && !state.showCommandPanel) {
            lifecycleScope.launch {
                val settings = settingsRepository.getSettings().first()
                val isFirstRun = settingsRepository.isPlayerFirstRun()
                
                if (settings.showPlayerHintOnFirstRun && isFirstRun) {
                    Timber.d("PlayerActivity.updateUI: Showing first-run hint overlay (fullscreen mode)")
                    // Delay hint to allow UI to settle
                    delay(500)
                    showFirstRunHintOverlay()
                    settingsRepository.setPlayerFirstRun(false)
                    hasShownFirstRunHint = true
                }
            }
        }
        
        state.currentFile?.let { file ->
            binding.toolbar.title = "${state.currentIndex + 1}/${state.files.size} - ${file.name}"
            binding.btnPrevious.isEnabled = state.hasPrevious
            binding.btnNext.isEnabled = state.hasNext
            binding.btnPreviousCmd.isEnabled = state.hasPrevious
            binding.btnNextCmd.isEnabled = state.hasNext

            // Check if file is actually a GIF by extension (in case type is wrong in DB)
            val isGif = file.name.lowercase().endsWith(".gif")
            
            Timber.d("PlayerActivity.updateUI: file.type=${file.type}, isGif=$isGif, fileName=${file.name}")
            
            when {
                isGif || file.type == MediaType.IMAGE || file.type == MediaType.GIF -> {
                    Timber.d("PlayerActivity.updateUI: Calling displayImage() for ${if (isGif) "GIF" else "IMAGE"}")
                    displayImage(file.path)
                }
                file.type == MediaType.VIDEO || file.type == MediaType.AUDIO -> {
                    Timber.d("PlayerActivity.updateUI: Calling playVideo()")
                    playVideo(file.path)
                }
                else -> {
                    // Unknown type or null - try to determine by extension
                    val ext = file.name.lowercase().substringAfterLast('.', "")
                    val imageExts = listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heif", "heic")
                    if (ext in imageExts) {
                        Timber.w("PlayerActivity.updateUI: Unknown type ${file.type}, but extension suggests image - using displayImage()")
                        displayImage(file.path)
                    } else {
                        Timber.w("PlayerActivity.updateUI: Unknown type ${file.type} for ${file.name} - attempting playVideo()")
                        playVideo(file.path)
                    }
                }
            }
            
            // Adjust touch zones (not for images/GIFs)
            val isVideo = !isGif && (file.type == MediaType.VIDEO || file.type == MediaType.AUDIO)
            adjustTouchZonesForVideo(isVideo)
        }

        // Update visibility based on showCommandPanel flag
        updatePanelVisibility(state.showCommandPanel)
        
        // Update command availability based on settings and permissions
        updateCommandAvailability(state)

        // Controls overlay is only visible in fullscreen mode and when showControls is true
        binding.controlsOverlay.isVisible = !state.showCommandPanel && state.showControls
        updatePlayPauseButton()
        updateSlideShowButton()
        
        // Note: updateAudioTouchZonesVisibility() is called inside updatePanelVisibility() to avoid duplicate calls
    }

    /**
     * Update panel visibility based on mode
     */
    private fun updatePanelVisibility(showCommandPanel: Boolean) {
        if (showCommandPanel) {
            // Command panel mode
            binding.topCommandPanel.isVisible = true
            binding.touchZonesOverlay.isVisible = true
            // Copy/Move panel visibility is controlled by updateCommandAvailability()
            binding.controlsOverlay.isVisible = false
            
            // Populate destination buttons (handles state restoration internally)
            populateDestinationButtons()

            // Apply small controls setting if enabled
            val state = viewModel.state.value
            if (state.showSmallControls) {
                // Reduce button heights to 50%
                val buttons = listOf(
                    binding.btnBack,
                    binding.btnPreviousCmd,
                    binding.btnNextCmd,
                    binding.btnRenameCmd,
                    binding.btnDeleteCmd,
                    binding.btnUndoCmd,
                    binding.btnSlideshowCmd
                )
                buttons.forEach { button ->
                    val params = button.layoutParams
                    params.height = (params.height * 0.5f).toInt()
                    button.layoutParams = params
                }
            }
        } else {
            // Fullscreen mode
            binding.topCommandPanel.isVisible = false
            binding.touchZonesOverlay.isVisible = false
            binding.copyToPanel.isVisible = false
            binding.moveToPanel.isVisible = false
            // controlsOverlay visibility is controlled in updateUI based on showControls
        }
        
        // Update audio touch zones overlay whenever panel visibility changes
        updateAudioTouchZonesVisibility()
    }
    
    /**
     * Toggle Copy to panel collapsed/expanded state
     */
    private fun toggleCopyPanel() {
        lifecycleScope.launch {
            val currentSettings = settingsRepository.getSettings().first()
            val newCollapsedState = !currentSettings.copyPanelCollapsed
            
            // Save new state
            settingsRepository.updateSettings(currentSettings.copy(copyPanelCollapsed = newCollapsedState))
            
            // Update UI
            updateCopyPanelVisibility(newCollapsedState)
        }
    }
    
    /**
     * Toggle Move to panel collapsed/expanded state
     */
    private fun toggleMovePanel() {
        lifecycleScope.launch {
            val currentSettings = settingsRepository.getSettings().first()
            val newCollapsedState = !currentSettings.movePanelCollapsed
            
            // Save new state
            settingsRepository.updateSettings(currentSettings.copy(movePanelCollapsed = newCollapsedState))
            
            // Update UI
            updateMovePanelVisibility(newCollapsedState)
        }
    }
    
    /**
     * Update Copy to panel buttons visibility and indicator
     */
    private fun updateCopyPanelVisibility(collapsed: Boolean) {
        binding.copyToButtonsGrid.isVisible = !collapsed
        binding.copyToPanelIndicator.text = if (collapsed) "▶" else "▼"
    }
    
    /**
     * Update Move to panel buttons visibility and indicator
     */
    private fun updateMovePanelVisibility(collapsed: Boolean) {
        binding.moveToButtonsGrid.isVisible = !collapsed
        binding.moveToPanelIndicator.text = if (collapsed) "▶" else "▼"
    }

    /**
     * Update command availability based on settings and file permissions
     */
    private fun updateCommandAvailability(state: PlayerViewModel.PlayerState) {
        val currentFile = state.currentFile ?: return
        val file = File(currentFile.path)
        
        // Check file permissions
        val canWrite = file.canWrite()
        val canRead = file.canRead()
        
        // Rename: requires write permission and allowRename setting
        binding.btnRenameCmd.isEnabled = canWrite && canRead && state.allowRename
        
        // Delete: requires write permission on parent directory and allowDelete setting
        val parentDir = file.parentFile
        val canDeleteFile = parentDir?.canWrite() == true && canRead
        binding.btnDeleteCmd.isEnabled = canDeleteFile && state.allowDelete
        
        // Edit: visible only for images with write permission
        binding.btnEditCmd.isVisible = state.showCommandPanel && 
                                        currentFile.type == MediaType.IMAGE && 
                                        canWrite && 
                                        canRead
        
        // Undo: visible only when there is a pending undo operation
        binding.btnUndoCmd.isVisible = state.showCommandPanel && state.lastOperation != null
        
        // Copy/Move panels visibility based on settings
        binding.copyToPanel.isVisible = state.showCommandPanel && state.enableCopying
        binding.moveToPanel.isVisible = state.showCommandPanel && state.enableMoving
    }

    private fun displayImage(path: String) {
        releasePlayer()
        binding.playerView.isVisible = false
        binding.imageView.isVisible = true
        
        // Hide touch zones overlay for images
        binding.audioTouchZonesOverlay.isVisible = false
        
        // Hide audio info overlay for images
        binding.audioInfoOverlay.isVisible = false

        // Schedule loading indicator to show after 1 second
        loadingIndicatorHandler.postDelayed(showLoadingIndicatorRunnable, 1000)

        val currentFile = viewModel.state.value.currentFile
        val resource = viewModel.state.value.resource
        
        // Check if this is a network resource
        if (currentFile != null && resource != null && 
            (resource.type == ResourceType.SMB || resource.type == ResourceType.SFTP || resource.type == ResourceType.FTP)) {
            
            Timber.d("PlayerActivity: Loading network image: $path from ${resource.type}")
            
            // Use NetworkFileData for Coil to load via NetworkFileFetcher
            // path is already in format: /shareName/path/to/file.jpg
            val networkData = NetworkFileData(path = path, credentialsId = resource.credentialsId)
            
            // Get image size setting (full resolution or limited to 1920px)
            lifecycleScope.launch {
                val settings = settingsRepository.getSettings().first()
                val imageSize = if (settings.loadFullSizeImages) {
                    // Load full resolution for zoom support
                    Size.ORIGINAL
                } else {
                    // Load 1920px for faster loading
                    Size(1920, 1920)
                }
            
                val request = ImageRequest.Builder(this@PlayerActivity)
                    .data(networkData)
                    .size(imageSize)
                    .target(binding.imageView)
                    .memoryCacheKey(path) // Use path as consistent cache key
                    .diskCacheKey(path)
                    .listener(
                        onStart = {
                            // Loading started - indicator will show after 1 second if still loading
                        },
                        onSuccess = { _, _ ->
                            // Cancel and hide loading indicator
                            loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                            binding.progressBar.isVisible = false
                            
                            Timber.d("PlayerActivity: Network image loaded successfully")
                            // Preload next image in background (if it's an image)
                            preloadNextImageIfNeeded()
                        },
                        onError = { _, result ->
                            // Cancel and hide loading indicator
                            loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                            binding.progressBar.isVisible = false
                            
                            Timber.e(result.throwable, "PlayerActivity: Failed to load network image")
                            showError("Failed to load image: ${result.throwable.message}", result.throwable)
                        }
                    )
                    .build()
                
                imageLoader.enqueue(request)
            }
        } else {
            // Local file - support both file:// paths and content:// URIs
            val data = if (path.startsWith("content://")) {
                android.net.Uri.parse(path)
            } else {
                File(path)
            }
            binding.imageView.load(data) {
                listener(
                    onStart = {
                        // Loading started - indicator will show after 1 second if still loading
                    },
                    onSuccess = { _, _ ->
                        // Cancel and hide loading indicator
                        loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                        binding.progressBar.isVisible = false
                        
                        // Preload next image for local files too
                        preloadNextImageIfNeeded()
                    },
                    onError = { _, _ ->
                        // Cancel and hide loading indicator
                        loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                        binding.progressBar.isVisible = false
                    }
                )
            }
        }

        updateSlideShow()
    }

    /**
     * Preload adjacent images (previous + next) in background for faster navigation.
     * Only preloads IMAGE and GIF files.
     * Supports circular navigation.
     */
    private fun preloadNextImageIfNeeded() {
        val adjacentFiles = viewModel.getAdjacentFiles()
        if (adjacentFiles.isEmpty()) return
        
        val resource = viewModel.state.value.resource ?: return
        
        // Preload each adjacent file
        adjacentFiles.forEach { file ->
            // Check if this is a network resource
            if (resource.type == ResourceType.SMB || resource.type == ResourceType.SFTP || resource.type == ResourceType.FTP) {
                Timber.d("PlayerActivity: Preloading network image: ${file.path}")
                
                val job = lifecycleScope.launch {
                    val settings = settingsRepository.getSettings().first()
                    val imageSize = if (settings.loadFullSizeImages) {
                        Size.ORIGINAL
                    } else {
                        Size(1920, 1920)
                    }
                
                    val networkData = NetworkFileData(path = file.path, credentialsId = resource.credentialsId)
                    val preloadRequest = ImageRequest.Builder(this@PlayerActivity)
                        .data(networkData)
                        .size(imageSize) // Use setting-based size
                        .memoryCacheKey(file.path) // Use path as consistent cache key
                        .diskCacheKey(file.path)
                        .listener(
                            onSuccess = { _, _ ->
                                Timber.d("PlayerActivity: Image preloaded successfully: ${file.name}")
                            },
                            onError = { _, result ->
                                Timber.w(result.throwable, "PlayerActivity: Failed to preload image: ${file.name}")
                            }
                        )
                        .build()
                    
                    imageLoader.enqueue(preloadRequest)
                }
                preloadJobs.add(job)
            } else {
                // Preload local file
                Timber.d("PlayerActivity: Preloading local image: ${file.path}")
                val preloadRequest = ImageRequest.Builder(this)
                    .data(File(file.path))
                    .memoryCacheKey(file.path) // Consistent cache key for local files
                    .listener(
                        onSuccess = { _, _ ->
                            Timber.d("PlayerActivity: Local image preloaded: ${file.name}")
                        }
                    )
                    .build()
                
                imageLoader.enqueue(preloadRequest)
            }
        }
    }

    private fun playVideo(path: String) {
        Timber.d("PlayerActivity.playVideo: START - path=$path")
        
        // Double-check: never try to play image files with ExoPlayer
        val lowerPath = path.lowercase()
        val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heif", ".heic")
        if (imageExtensions.any { lowerPath.endsWith(it) }) {
            Timber.w("PlayerActivity.playVideo: Detected image file (${lowerPath.substringAfterLast('.')}), redirecting to displayImage()")
            displayImage(path)
            return
        }
        
        binding.imageView.isVisible = false
        binding.playerView.isVisible = true

        val currentFile = viewModel.state.value.currentFile
        val resource = viewModel.state.value.resource
        
        // Configure PlayerView based on media type
        val isAudioFile = currentFile?.type == MediaType.AUDIO
        if (isAudioFile) {
            // For audio: always show controls, never hide
            binding.playerView.controllerShowTimeoutMs = 0 // 0 means never hide
            Timber.d("PlayerActivity.playVideo: Audio file detected - controls will always be visible")
            
            // Show touch zones overlay for audio in fullscreen mode
            updateAudioTouchZonesVisibility()
            
            // Show audio file info
            showAudioFileInfo(currentFile)
        } else {
            // For video: auto-hide controls after 3 seconds
            binding.playerView.controllerShowTimeoutMs = 3000
            Timber.d("PlayerActivity.playVideo: Video file detected - controls will auto-hide after 3s")
            
            // Hide touch zones overlay for video
            binding.audioTouchZonesOverlay.isVisible = false
            
            // Hide audio info overlay for video
            binding.audioInfoOverlay.isVisible = false
        }

        // Schedule loading indicator to show after 1 second
        loadingIndicatorHandler.postDelayed(showLoadingIndicatorRunnable, 1000)
        
        Timber.d("PlayerActivity.playVideo: currentFile=$currentFile, resource=${resource?.name} (${resource?.type})")
        
        // Check if this is a network resource
        if (currentFile != null && resource != null &&
            (resource.type == ResourceType.SMB || resource.type == ResourceType.SFTP || resource.type == ResourceType.FTP)) {
            
            Timber.d("PlayerActivity.playVideo: Network resource detected - type=${resource.type}, credentialsId=${resource.credentialsId}")
            
            lifecycleScope.launch {
                try {
                    val credentialsId = resource.credentialsId
                    if (credentialsId == null) {
                        Timber.e("PlayerActivity.playVideo: ERROR - No credentials found for resource")
                        showError("No credentials found for resource")
                        return@launch
                    }
                    
                    // Get credentials from database
                    Timber.d("PlayerActivity.playVideo: Fetching credentials for credentialsId=$credentialsId")
                    val credentials = credentialsRepository.getByCredentialId(credentialsId)
                    if (credentials == null) {
                        Timber.e("PlayerActivity.playVideo: ERROR - Credentials not found in database")
                        showError("Credentials not found")
                        return@launch
                    }
                    
                    Timber.d("PlayerActivity.playVideo: Credentials loaded - server=${credentials.server}, share=${credentials.shareName}, user=${credentials.username}")
                    
                    // Release old player
                    releasePlayer()
                    
                    when (resource.type) {
                        ResourceType.SMB -> {
                            Timber.d("PlayerActivity.playVideo: Initializing SMB playback")
                            Timber.d("PlayerActivity.playVideo: Creating SMB connection info")
                            val connectionInfo = SmbClient.SmbConnectionInfo(
                                server = credentials.server,
                                shareName = credentials.shareName ?: "",
                                username = credentials.username,
                                password = credentials.password,
                                domain = credentials.domain,
                                port = credentials.port
                            )
                            
                            Timber.d("PlayerActivity.playVideo: Creating SmbDataSourceFactory")
                            // Create ExoPlayer with SmbDataSourceFactory
                            val dataSourceFactory = SmbDataSourceFactory(smbClient, connectionInfo)
                            
                            Timber.d("PlayerActivity.playVideo: Building ExoPlayer")
                            exoPlayer = ExoPlayer.Builder(this@PlayerActivity)
                                .setMediaSourceFactory(
                                    DefaultMediaSourceFactory(dataSourceFactory as androidx.media3.datasource.DataSource.Factory)
                                )
                                .build()
                            
                            Timber.d("PlayerActivity.playVideo: Adding ExoPlayer listener")
                            // Add listener for playback events (ready, ended)
                            exoPlayer?.addListener(exoPlayerListener)
                            
                            Timber.d("PlayerActivity.playVideo: Setting player to PlayerView")
                            binding.playerView.player = exoPlayer
                            
                            // Extract relative path from full SMB URI
                            // Path comes in format: smb://192.168.1.100:445/shareName/relativePath/file.mp4
                            // We need to extract: relativePath/file.mp4 (everything after shareName)
                            val relativePath = if (path.startsWith("smb://")) {
                                val uri = Uri.parse(path)
                                val fullPath = uri.path ?: ""
                                // Path is /shareName/relativePath/file.mp4
                                // Remove leading slash and share name
                                val pathWithoutLeadingSlash = fullPath.removePrefix("/")
                                val sharePrefix = "${credentials.shareName}/"
                                if (pathWithoutLeadingSlash.startsWith(sharePrefix)) {
                                    pathWithoutLeadingSlash.substring(sharePrefix.length)
                                } else {
                                    // Fallback: just remove share name if present
                                    pathWithoutLeadingSlash.removePrefix(credentials.shareName ?: "")
                                }
                            } else {
                                // Already a relative path
                                path.removePrefix("/")
                            }
                            
                            // Construct SMB URI with share name
                            // relativePath should NOT start with /
                            val cleanRelativePath = relativePath.removePrefix("/")
                            val smbUri = Uri.parse("smb://${credentials.server}/${credentials.shareName}/$cleanRelativePath")
                            Timber.d("PlayerActivity.playVideo: Constructed SMB URI: $smbUri")
                            Timber.d("PlayerActivity.playVideo: Details - originalPath=$path, extractedRelativePath=$cleanRelativePath")
                            
                            val mediaItem = MediaItem.fromUri(smbUri)
                            Timber.d("PlayerActivity.playVideo: Created MediaItem, calling setMediaItem()")
                            exoPlayer?.setMediaItem(mediaItem)
                            
                            Timber.d("PlayerActivity.playVideo: Calling prepare()")
                            exoPlayer?.prepare()
                            
                            val isPaused = viewModel.state.value.isPaused
                            Timber.d("PlayerActivity.playVideo: Setting playWhenReady=${!isPaused}")
                            exoPlayer?.playWhenReady = !isPaused
                            
                            Timber.i("PlayerActivity.playVideo: SMB video setup COMPLETE - URI=$smbUri, playWhenReady=${!isPaused}")
                        }
                        
                        ResourceType.SFTP -> {
                            // Create ExoPlayer with SftpDataSourceFactory
                            val dataSourceFactory = SftpDataSourceFactory(
                                sftpClient,
                                credentials.server,
                                credentials.port,
                                credentials.username,
                                credentials.password
                            )
                            
                            exoPlayer = ExoPlayer.Builder(this@PlayerActivity)
                                .setMediaSourceFactory(
                                    DefaultMediaSourceFactory(dataSourceFactory as androidx.media3.datasource.DataSource.Factory)
                                )
                                .build()
                            
                            // Add listener for playback events (ready, ended)
                            exoPlayer?.addListener(exoPlayerListener)
                            
                            binding.playerView.player = exoPlayer
                            
                            // Construct SFTP URI
                            val sftpUri = Uri.parse("sftp://${credentials.server}:${credentials.port}$path")
                            val mediaItem = MediaItem.fromUri(sftpUri)
                            exoPlayer?.setMediaItem(mediaItem)
                            exoPlayer?.prepare()
                            exoPlayer?.playWhenReady = !viewModel.state.value.isPaused
                            
                            Timber.d("PlayerActivity: SFTP video prepared: $sftpUri")
                        }
                        
                        ResourceType.FTP -> {
                            Timber.d("PlayerActivity.playVideo: Initializing FTP playback")
                            // Create ExoPlayer with FtpDataSourceFactory
                            val dataSourceFactory = FtpDataSourceFactory(
                                ftpClient,
                                credentials.server,
                                credentials.port,
                                credentials.username,
                                credentials.password
                            )
                            
                            exoPlayer = ExoPlayer.Builder(this@PlayerActivity)
                                .setMediaSourceFactory(
                                    DefaultMediaSourceFactory(dataSourceFactory as androidx.media3.datasource.DataSource.Factory)
                                )
                                .build()
                            
                            // Add listener for playback events (ready, ended)
                            exoPlayer?.addListener(exoPlayerListener)
                            
                            binding.playerView.player = exoPlayer
                            
                            // Construct FTP URI - path already contains full URI
                            val ftpUri = Uri.parse(path)
                            Timber.d("PlayerActivity.playVideo: FTP URI: $ftpUri")
                            
                            val mediaItem = MediaItem.fromUri(ftpUri)
                            exoPlayer?.setMediaItem(mediaItem)
                            exoPlayer?.prepare()
                            exoPlayer?.playWhenReady = !viewModel.state.value.isPaused
                            
                            Timber.i("PlayerActivity.playVideo: FTP video setup COMPLETE - URI=$ftpUri, playWhenReady=${!viewModel.state.value.isPaused}")
                        }
                        
                        else -> {
                            // Shouldn't happen but fallback to local
                            playLocalVideo(path)
                        }
                    }
                } catch (e: Exception) {
                    // Cancel and hide loading indicator on error
                    loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
                    binding.progressBar.isVisible = false
                    
                    Timber.e(e, "PlayerActivity.playVideo: ERROR - Failed to play network video: ${e.message}")
                    Timber.e("PlayerActivity.playVideo: Stack trace: ${e.stackTraceToString()}")
                    showError("Failed to play video: ${e.message}", e)
                }
            }
        } else {
            // Local file
            Timber.d("PlayerActivity.playVideo: Playing local file")
            playLocalVideo(path)
        }

        slideShowHandler.removeCallbacks(slideShowRunnable)
        Timber.d("PlayerActivity.playVideo: END")
    }
    
    private fun playLocalVideo(path: String) {
        Timber.d("PlayerActivity.playLocalVideo: START - path=$path")
        if (exoPlayer == null) {
            Timber.d("PlayerActivity.playLocalVideo: Creating new ExoPlayer")
            exoPlayer = ExoPlayer.Builder(this).build().also {
                binding.playerView.player = it
                
                // Add listener for playback events (ready, ended)
                it.addListener(exoPlayerListener)
            }
        }

        Timber.d("PlayerActivity.playLocalVideo: Setting media item and preparing")
        exoPlayer?.apply {
            // Support both file:// paths and content:// URIs
            val uri = if (path.startsWith("content://")) {
                path
            } else {
                File(path).toURI().toString()
            }
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = !viewModel.state.value.isPaused
        }
        Timber.d("PlayerActivity.playLocalVideo: END - playWhenReady=${!viewModel.state.value.isPaused}")
    }

    private fun updateSlideShow() {
        slideShowHandler.removeCallbacks(slideShowRunnable)
        countdownHandler.removeCallbacks(countdownRunnable)
        binding.tvCountdown.isVisible = false
        
        if (viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused) {
            val currentFile = viewModel.state.value.currentFile
            val isMedia = currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO
            val isImage = currentFile?.type == MediaType.IMAGE
            
            // Start slideshow timer only if:
            // - It's an image OR
            // - It's media but playToEnd is disabled
            val shouldStartTimer = isImage || (isMedia && !viewModel.state.value.playToEndInSlideshow)
            
            if (shouldStartTimer) {
                val interval = viewModel.state.value.slideShowInterval
                slideShowHandler.postDelayed(slideShowRunnable, interval)
                
                // Start countdown 3 seconds before file change
                if (interval > 3000) {
                    countdownSeconds = 3
                    countdownHandler.postDelayed(countdownRunnable, interval - 3000)
                }
                Timber.d("PlayerActivity.updateSlideShow: Timer started - interval=$interval ms, playToEnd=${viewModel.state.value.playToEndInSlideshow}")
            } else {
                Timber.d("PlayerActivity.updateSlideShow: Timer NOT started - waiting for media to end (playToEnd=true)")
            }
        }
    }

    private fun updatePlayPauseButton() {
        binding.btnPlayPause.text = if (viewModel.state.value.isPaused) "▶" else "⏸"
        exoPlayer?.playWhenReady = !viewModel.state.value.isPaused
    }

    private fun updateSlideShowButton() {
        binding.btnSlideShow.alpha = if (viewModel.state.value.isSlideShowActive) 1.0f else 0.5f
    }

    private fun scheduleHideControls() {
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        if (viewModel.state.value.showControls && !viewModel.state.value.isPaused) {
            hideControlsHandler.postDelayed(hideControlsRunnable, 3000)
        }
    }

    /**
     * Update audio touch zones overlay visibility based on:
     * - Current file is audio
     * - Fullscreen mode (not showing command panel or controls overlay)
     * - Touch zones are enabled
     */
    private fun updateAudioTouchZonesVisibility() {
        val state = viewModel.state.value
        val currentFile = state.currentFile
        val isAudioFile = currentFile?.type == MediaType.AUDIO
        val isInFullscreenMode = !state.showCommandPanel && !state.showControls
        
        val shouldShow = isAudioFile && isInFullscreenMode && useTouchZones
        
        binding.audioTouchZonesOverlay.isVisible = shouldShow
        
        // Adjust overlay height to cover only upper 66% (2/3) for audio
        if (shouldShow) {
            binding.audioTouchZonesOverlay.post {
                val screenHeight = binding.root.height
                val effectiveHeight = (screenHeight * 0.66f).toInt()
                
                val params = binding.audioTouchZonesOverlay.layoutParams
                params.height = effectiveHeight
                binding.audioTouchZonesOverlay.layoutParams = params
                
                Timber.d("PlayerActivity.updateAudioTouchZonesVisibility: Overlay shown - height=$effectiveHeight (66% of $screenHeight)")
            }
        } else {
            Timber.d("PlayerActivity.updateAudioTouchZonesVisibility: Overlay hidden - audio=$isAudioFile, fullscreen=$isInFullscreenMode, touchZones=$useTouchZones")
        }
    }

    private fun showFileInfo() {
        val currentFile = viewModel.state.value.currentFile
        if (currentFile == null) {
            Toast.makeText(this, "No file information available", Toast.LENGTH_SHORT).show()
            return
        }

        // Show file information dialog
        val dialog = com.sza.fastmediasorter_v2.ui.dialog.FileInfoDialog(this, currentFile)
        dialog.show()
    }
    
    private fun showImageEditDialog() {
        val currentFile = viewModel.state.value.currentFile
        if (currentFile == null) {
            Toast.makeText(this, "No file to edit", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (currentFile.type != MediaType.IMAGE) {
            Toast.makeText(this, "Edit is only available for images", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show image edit dialog
        val dialog = com.sza.fastmediasorter_v2.ui.dialog.ImageEditDialog(
            context = this,
            imagePath = currentFile.path,
            rotateImageUseCase = rotateImageUseCase,
            flipImageUseCase = flipImageUseCase,
            networkImageEditUseCase = networkImageEditUseCase,
            onEditComplete = {
                // TODO: Reload current file after edit is implemented
                Toast.makeText(this, "Image edit completed", Toast.LENGTH_SHORT).show()
            }
        )
        dialog.show()
    }

    private fun deleteCurrentFile() {
        val currentFile = viewModel.state.value.currentFile
        if (currentFile == null) {
            Toast.makeText(this, "No file to delete", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Show confirmation dialog
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_confirmation)
            .setMessage(getString(R.string.delete_file_confirmation, currentFile.name, ""))
            .setPositiveButton(R.string.delete) { _, _ ->
                // Call ViewModel to delete file
                viewModel.deleteCurrentFile()
                // Result is handled through PlayerEvent.ShowMessage/ShowError/FinishActivity
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun handleEvent(event: PlayerViewModel.PlayerEvent) {
        when (event) {
            is PlayerViewModel.PlayerEvent.ShowError -> {
                showError(event.message)
            }
            is PlayerViewModel.PlayerEvent.ShowMessage -> {
                Toast.makeText(this, event.message, Toast.LENGTH_SHORT).show()
            }
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
    private fun showError(message: String, throwable: Throwable? = null) {
        // Check if activity is finishing to prevent WindowLeaked exception
        if (isFinishing || isDestroyed) {
            Timber.w("showError: Activity is finishing/destroyed, skipping error dialog")
            return
        }
        
        lifecycleScope.launch {
            val settings = viewModel.getSettings()
            if (settings.showDetailedErrors) {
                // Double-check before showing dialog
                if (isFinishing || isDestroyed) {
                    Timber.w("showError: Activity finished during settings load, skipping dialog")
                    return@launch
                }
                
                if (throwable != null) {
                    // Use ErrorDialog with full stack trace
                    com.sza.fastmediasorter_v2.ui.dialog.ErrorDialog.show(
                        context = this@PlayerActivity,
                        title = getString(R.string.error),
                        message = message,
                        details = throwable.stackTraceToString()
                    )
                } else {
                    // Use ErrorDialog without details
                    com.sza.fastmediasorter_v2.ui.dialog.ErrorDialog.show(
                        context = this@PlayerActivity,
                        title = getString(R.string.error),
                        message = message
                    )
                }
            } else {
                Toast.makeText(this@PlayerActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Show popup message when slideshow is enabled
     */
    private fun showSlideshowEnabledMessage() {
        val intervalSeconds = viewModel.state.value.slideShowInterval / 1000
        val message = "Slideshow enabled with $intervalSeconds seconds interval"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Populate destination buttons dynamically based on destinations from DB
     */
    private fun populateDestinationButtons() {
        val resourceId = intent.getLongExtra("resourceId", -1)
        
        lifecycleScope.launch {
            try {
                val destinations = viewModel.getDestinationsUseCase().first()
                    .filter { it.id != resourceId } // Exclude current resource
                
                // Save CURRENT UI state BEFORE clearing buttons (not from settings)
                // This prevents state loss during button rebuild
                val copyCollapsed = !binding.copyToButtonsGrid.isVisible
                val moveCollapsed = !binding.moveToButtonsGrid.isVisible
                
                // Clear existing buttons
                binding.copyToButtonsGrid.removeAllViews()
                binding.moveToButtonsGrid.removeAllViews()
                
                destinations.take(10).forEachIndexed { index, destination ->
                    // Create copy button
                    createDestinationButton(destination, index, true).let { btn ->
                        binding.copyToButtonsGrid.addView(btn)
                    }
                    
                    // Create move button
                    createDestinationButton(destination, index, false).let { btn ->
                        binding.moveToButtonsGrid.addView(btn)
                    }
                }
                
                // Restore collapsed state AFTER adding buttons
                updateCopyPanelVisibility(copyCollapsed)
                updateMovePanelVisibility(moveCollapsed)
            } catch (e: Exception) {
                Toast.makeText(this@PlayerActivity, "Failed to load destinations", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createDestinationButton(
        destination: com.sza.fastmediasorter_v2.domain.model.MediaResource,
        index: Int,
        isCopy: Boolean
    ): com.google.android.material.button.MaterialButton {
        return com.google.android.material.button.MaterialButton(this).apply {
            // Short name - take first 8 characters or first word
            val shortName = when {
                destination.name.length <= 10 -> destination.name
                destination.name.contains(" ") -> destination.name.substringBefore(" ").take(10)
                else -> destination.name.take(8) + ".."
            }
            text = shortName
            
            // Larger, readable text
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            
            // Set button color from destination
            setBackgroundColor(destination.destinationColor)
            
            // Square buttons with fixed size
            val buttonSize = 120 // Square 120x120 dp
            val density = resources.displayMetrics.density
            val buttonSizePx = (buttonSize * density).toInt()
            
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = buttonSizePx
                height = buttonSizePx
                setMargins(8, 8, 8, 8)
            }
            
            // Center text and allow wrapping
            gravity = android.view.Gravity.CENTER
            maxLines = 2
            
            setOnClickListener {
                if (isCopy) {
                    performCopyOperation(destination)
                } else {
                    performMoveOperation(destination)
                }
            }
        }
    }

    private fun performCopyOperation(destination: com.sza.fastmediasorter_v2.domain.model.MediaResource) {
        val currentFile = viewModel.state.value.currentFile ?: return
        
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            
            try {
                val operation = com.sza.fastmediasorter_v2.domain.usecase.FileOperation.Copy(
                    sources = listOf(File(currentFile.path)),
                    destination = File(destination.path),
                    overwrite = settings.overwriteOnCopy
                )
                val result = viewModel.fileOperationUseCase.execute(operation)
                
                when (result) {
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.Success -> {
                        Toast.makeText(this@PlayerActivity, "File copied to ${destination.name}", Toast.LENGTH_SHORT).show()
                        
                        // Go to next file if setting enabled
                        if (settings.goToNextAfterCopy) {
                            viewModel.nextFile()
                        }
                    }
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.PartialSuccess -> {
                        val message = buildString {
                            append("Copied ${result.processedCount} files, but ${result.failedCount} failed.")
                            if (result.errors.isNotEmpty()) {
                                append("\n\nFirst error:\n${result.errors.first()}")
                            }
                        }
                        showError(message)
                    }
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.Failure -> {
                        showError("Copy failed: ${result.error}")
                    }
                }
            } catch (e: Exception) {
                showError("Copy failed: ${e.message}", e)
            }
        }
    }

    private fun performMoveOperation(destination: com.sza.fastmediasorter_v2.domain.model.MediaResource) {
        val currentFile = viewModel.state.value.currentFile ?: return
        
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            
            try {
                val operation = com.sza.fastmediasorter_v2.domain.usecase.FileOperation.Move(
                    sources = listOf(File(currentFile.path)),
                    destination = File(destination.path),
                    overwrite = settings.overwriteOnMove
                )
                val result = viewModel.fileOperationUseCase.execute(operation)
                
                when (result) {
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.Success -> {
                        Toast.makeText(this@PlayerActivity, "File moved to ${destination.name}", Toast.LENGTH_SHORT).show()
                        
                        // Go to next file after move (current file was moved)
                        viewModel.nextFile()
                    }
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.PartialSuccess -> {
                        val message = buildString {
                            append("Moved ${result.processedCount} files, but ${result.failedCount} failed.")
                            if (result.errors.isNotEmpty()) {
                                append("\n\nFirst error:\n${result.errors.first()}")
                            }
                        }
                        showError(message)
                    }
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.Failure -> {
                        showError("Move failed: ${result.error}")
                    }
                }
            } catch (e: Exception) {
                showError("Move failed: ${e.message}", e)
            }
        }
    }

    private fun showAudioFileInfo(file: MediaFile?) {
        if (file == null) return
        
        binding.audioInfoOverlay.isVisible = true
        
        // Display file name
        binding.audioFileName.text = file.name
        
        // Get file info asynchronously (size, duration, format)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get file size only for local files
                val fileSize = if (!file.path.startsWith("smb://") && !file.path.startsWith("sftp://")) {
                    try {
                        File(file.path).length()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to get local file size")
                        -1L
                    }
                } else {
                    // For network files, size will be shown as N/A
                    -1L
                }
                
                val fileSizeStr = if (fileSize > 0) {
                    when {
                        fileSize >= 1024 * 1024 -> "%.1f MB".format(fileSize / (1024.0 * 1024.0))
                        fileSize >= 1024 -> "%.1f KB".format(fileSize / 1024.0)
                        else -> "$fileSize bytes"
                    }
                } else "N/A"
                
                withContext(Dispatchers.Main) {
                    binding.audioFileInfo.text = buildString {
                        append("Size: $fileSizeStr")
                        file.duration?.let { if (it > 0) append("\nDuration: ${formatDuration(it)}") }
                        append("\nLoading format info...")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get audio file info")
                withContext(Dispatchers.Main) {
                    binding.audioFileInfo.text = "File information unavailable"
                }
            }
        }
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
    
    private fun updateAudioFormatInfo() {
        val currentFile = viewModel.state.value.currentFile ?: return
        
        val formatInfo = exoPlayer?.currentTracks?.groups?.firstOrNull { group ->
            group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO
        }?.let { audioGroup ->
            val format = audioGroup.getTrackFormat(0)
            buildString {
                format.sampleMimeType?.let { 
                    append(it.substringAfter("audio/").uppercase())
                }
                format.sampleRate?.let { 
                    if (isNotEmpty()) append(" • ")
                    append("${it / 1000} kHz")
                }
                format.channelCount?.let {
                    if (isNotEmpty()) append(" • ")
                    append(when (it) {
                        1 -> "Mono"
                        2 -> "Stereo"
                        else -> "$it channels"
                    })
                }
                format.bitrate?.let {
                    if (it > 0) {
                        if (isNotEmpty()) append(" • ")
                        append("${it / 1000} kbps")
                    }
                }
            }
        }
        
        if (!formatInfo.isNullOrEmpty()) {
            // Update only the format line, preserve size and duration
            val currentText = binding.audioFileInfo.text.toString()
            val lines = currentText.split("\n").toMutableList()
            
            // Replace or add format info line
            if (lines.size >= 3) {
                lines[2] = formatInfo
            } else {
                lines.add(formatInfo)
            }
            
            binding.audioFileInfo.text = lines.joinToString("\n")
        }
    }

    private fun releasePlayer() {
        exoPlayer?.let { player ->
            // Remove listener before releasing to avoid callbacks during/after release
            player.removeListener(exoPlayerListener)
            player.release()
        }
        exoPlayer = null
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
        viewModel.togglePause()
    }
    
    override fun onResume() {
        super.onResume()
        
        if (isFirstResume) {
            Timber.d("PlayerActivity.onResume: First resume, skipping reload (files already loaded in ViewModel.init{})")
            isFirstResume = false
        } else {
            // Reload files when returning from background
            // This ensures deleted/renamed files from external apps are reflected
            Timber.d("PlayerActivity.onResume: Reloading files")
            viewModel.reloadFiles()
        }
        
        // Clear expired undo operations (5 minutes timeout)
        viewModel.clearExpiredUndoOperation()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        slideShowHandler.removeCallbacks(slideShowRunnable)
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        loadingIndicatorHandler.removeCallbacks(showLoadingIndicatorRunnable)
        countdownHandler.removeCallbacks(countdownRunnable)
        
        // Cancel all preload jobs to prevent memory leaks
        preloadJobs.forEach { it.cancel() }
        preloadJobs.clear()
    }

    companion object {
        fun createIntent(context: Context, resourceId: Long, initialIndex: Int = 0, skipAvailabilityCheck: Boolean = false): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra("resourceId", resourceId)
                putExtra("initialIndex", initialIndex)
                putExtra("skipAvailabilityCheck", skipAvailabilityCheck)
            }
        }
    }
}
