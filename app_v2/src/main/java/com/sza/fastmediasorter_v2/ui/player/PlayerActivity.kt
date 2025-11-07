package com.sza.fastmediasorter_v2.ui.player

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
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
import coil.load
import com.sza.fastmediasorter_v2.R
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.ui.dialog.CopyToDialog
import com.sza.fastmediasorter_v2.ui.dialog.MoveToDialog
import com.sza.fastmediasorter_v2.ui.dialog.RenameDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class PlayerActivity : BaseActivity<ActivityPlayerUnifiedBinding>() {
    override fun getViewBinding(): ActivityPlayerUnifiedBinding {
        return ActivityPlayerUnifiedBinding.inflate(layoutInflater)
    }

    private val viewModel: PlayerViewModel by viewModels()
    private var exoPlayer: ExoPlayer? = null
    private val slideShowHandler = Handler(Looper.getMainLooper())
    private val hideControlsHandler = Handler(Looper.getMainLooper())
    private lateinit var gestureDetector: GestureDetector
    private val touchZoneDetector = TouchZoneDetector()
    private var useTouchZones = true // Use touch zones for images, gestures for video

    private val slideShowRunnable = object : Runnable {
        override fun run() {
            if (viewModel.state.value.isSlideShowActive && !viewModel.state.value.isPaused) {
                viewModel.nextFile()
                slideShowHandler.postDelayed(this, viewModel.state.value.slideShowInterval)
            }
        }
    }

    private val hideControlsRunnable = Runnable {
        if (!viewModel.state.value.isPaused) {
            viewModel.toggleControls()
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
    }

    override fun observeData() {
        observeViewModel()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // For static images, use touch zones instead of toggling controls
                val currentFile = viewModel.state.value.currentFile
                if (currentFile?.type == MediaType.IMAGE && useTouchZones) {
                    handleTouchZone(e.x, e.y)
                } else {
                    // For video and audio, toggle controls on single tap
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
                // Fling still works for quick swipe navigation
                if (e1 != null && Math.abs(velocityX) > Math.abs(velocityY)) {
                    if (velocityX > 0) {
                        viewModel.previousFile()
                    } else {
                        viewModel.nextFile()
                    }
                    return true
                }
                return false
            }
        })

        binding.root.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
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
        
        // For video, limit touch zones to upper 75% to leave space for ExoPlayer controls
        val effectiveHeight = if (currentFile?.type == MediaType.VIDEO || currentFile?.type == MediaType.AUDIO) {
            (screenHeight * 0.75f).toInt()
        } else {
            screenHeight
        }
        
        // If touch is below effective height (in bottom 25% for video), ignore
        if (y > effectiveHeight) {
            return
        }
        
        val zone = touchZoneDetector.detectZone(x, y, screenWidth, effectiveHeight)
        
        when (zone) {
            TouchZone.BACK -> {
                finish()
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
                viewModel.nextFile()
            }
            TouchZone.COMMAND_PANEL -> {
                viewModel.toggleCommandPanel()
            }
            TouchZone.DELETE -> {
                deleteCurrentFile()
            }
            TouchZone.SLIDESHOW -> {
                viewModel.toggleSlideShow()
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
        
        CopyToDialog(
            context = this,
            sourceFiles = listOf(File(currentFile.path)),
            sourceFolderName = "Current folder", // TODO: Get actual resource name
            currentResourceId = resourceId,
            fileOperationUseCase = viewModel.fileOperationUseCase,
            getDestinationsUseCase = viewModel.getDestinationsUseCase,
            overwriteFiles = false, // TODO: Get from settings
            onComplete = {
                // Refresh current view or go to next file based on settings
                // TODO: Check settings for goToNextAfterCopy
            }
        ).show()
    }
    
    private fun showMoveDialog() {
        val currentFile = viewModel.state.value.currentFile ?: return
        val resourceId = intent.getLongExtra("resourceId", -1)
        
        MoveToDialog(
            context = this,
            sourceFiles = listOf(File(currentFile.path)),
            sourceFolderName = "Current folder", // TODO: Get actual resource name
            currentResourceId = resourceId,
            fileOperationUseCase = viewModel.fileOperationUseCase,
            getDestinationsUseCase = viewModel.getDestinationsUseCase,
            overwriteFiles = false, // TODO: Get from settings
            onComplete = {
                // Go to next file after move
                viewModel.nextFile()
            }
        ).show()
    }
    
    private fun showRenameDialog() {
        val currentFile = viewModel.state.value.currentFile ?: return
        
        RenameDialog(
            context = this,
            files = listOf(File(currentFile.path)),
            sourceFolderName = "Current folder", // TODO: Get actual resource name
            fileOperationUseCase = viewModel.fileOperationUseCase,
            onComplete = {
                // Refresh current file display
                // TODO: Reload file list to reflect new name
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
            viewModel.toggleSlideShow()
            updateSlideShowButton()
            updateSlideShow()
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

        binding.btnSlideshowCmd.setOnClickListener {
            viewModel.toggleSlideShow()
            updateSlideShowButton()
            updateSlideShow()
        }
    }

    private fun setupTouchZones() {
        binding.touchZonePrevious.setOnClickListener {
            viewModel.previousFile()
        }

        binding.touchZoneNext.setOnClickListener {
            viewModel.nextFile()
        }
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
        state.currentFile?.let { file ->
            binding.toolbar.title = "${state.currentIndex + 1}/${state.files.size} - ${file.name}"
            binding.btnPrevious.isEnabled = state.hasPrevious
            binding.btnNext.isEnabled = state.hasNext
            binding.btnPreviousCmd.isEnabled = state.hasPrevious
            binding.btnNextCmd.isEnabled = state.hasNext

            val isVideo = file.type == MediaType.VIDEO || file.type == MediaType.AUDIO
            
            when (file.type) {
                MediaType.IMAGE, MediaType.GIF -> displayImage(file.path)
                MediaType.VIDEO, MediaType.AUDIO -> playVideo(file.path)
            }
            
            // Adjust touch zones for video
            adjustTouchZonesForVideo(isVideo)
        }

        // Update visibility based on showCommandPanel flag
        updatePanelVisibility(state.showCommandPanel)

        // Controls overlay is only visible in fullscreen mode and when showControls is true
        binding.controlsOverlay.isVisible = !state.showCommandPanel && state.showControls
        updatePlayPauseButton()
        updateSlideShowButton()
    }

    /**
     * Update panel visibility based on mode
     */
    private fun updatePanelVisibility(showCommandPanel: Boolean) {
        if (showCommandPanel) {
            // Command panel mode
            binding.topCommandPanel.isVisible = true
            binding.touchZonesOverlay.isVisible = true
            binding.copyToPanel.isVisible = true
            binding.moveToPanel.isVisible = true
            binding.controlsOverlay.isVisible = false
            
            // Populate destination buttons
            populateDestinationButtons()
        } else {
            // Fullscreen mode
            binding.topCommandPanel.isVisible = false
            binding.touchZonesOverlay.isVisible = false
            binding.copyToPanel.isVisible = false
            binding.moveToPanel.isVisible = false
            // controlsOverlay visibility is controlled in updateUI based on showControls
        }
    }

    private fun displayImage(path: String) {
        releasePlayer()
        binding.playerView.isVisible = false
        binding.imageView.isVisible = true

        binding.imageView.load(File(path))

        updateSlideShow()
    }

    private fun playVideo(path: String) {
        binding.imageView.isVisible = false
        binding.playerView.isVisible = true

        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(this).build().also {
                binding.playerView.player = it
            }
        }

        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(File(path).toURI().toString()))
            prepare()
            playWhenReady = !viewModel.state.value.isPaused
        }

        slideShowHandler.removeCallbacks(slideShowRunnable)
    }

    private fun updateSlideShow() {
        slideShowHandler.removeCallbacks(slideShowRunnable)
        if (viewModel.state.value.isSlideShowActive &&
            viewModel.state.value.currentFile?.type == MediaType.IMAGE &&
            !viewModel.state.value.isPaused
        ) {
            slideShowHandler.postDelayed(
                slideShowRunnable,
                viewModel.state.value.slideShowInterval
            )
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
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
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
     * Populate destination buttons dynamically based on destinations from DB
     */
    private fun populateDestinationButtons() {
        val resourceId = intent.getLongExtra("resourceId", -1)
        
        lifecycleScope.launch {
            try {
                val destinations = viewModel.getDestinationsUseCase().first()
                    .filter { it.id != resourceId } // Exclude current resource
                
                // Clear existing buttons
                binding.copyToButtonsGrid.removeAllViews()
                binding.moveToButtonsGrid.removeAllViews()
                
                // Calculate grid dimensions (max 5 columns, up to 2 rows)
                val buttonCount = destinations.size.coerceAtMost(10)
                
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
            text = destination.name
            textSize = 10f
            setTextColor(android.graphics.Color.WHITE)
            
            // Set button color from destination
            setBackgroundColor(destination.destinationColor)
            
            // Calculate button size based on count
            val buttonCount = binding.copyToButtonsGrid.childCount + 1
            val columnsCount = if (buttonCount <= 5) buttonCount else 5
            val buttonWidth = (resources.displayMetrics.widthPixels / columnsCount) - 8
            
            layoutParams = android.widget.GridLayout.LayoutParams().apply {
                width = buttonWidth
                height = 80
                setMargins(4, 4, 4, 4)
            }
            
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
            try {
                val operation = com.sza.fastmediasorter_v2.domain.usecase.FileOperation.Copy(
                    sources = listOf(File(currentFile.path)),
                    destination = File(destination.path),
                    overwrite = false // TODO: Get from settings
                )
                val result = viewModel.fileOperationUseCase.execute(operation)
                
                when (result) {
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.Success -> {
                        Toast.makeText(this@PlayerActivity, "File copied to ${destination.name}", Toast.LENGTH_SHORT).show()
                        // TODO: Check settings for goToNextAfterCopy
                    }
                    else -> {
                        Toast.makeText(this@PlayerActivity, "Copy failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@PlayerActivity, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performMoveOperation(destination: com.sza.fastmediasorter_v2.domain.model.MediaResource) {
        val currentFile = viewModel.state.value.currentFile ?: return
        
        lifecycleScope.launch {
            try {
                val operation = com.sza.fastmediasorter_v2.domain.usecase.FileOperation.Move(
                    sources = listOf(File(currentFile.path)),
                    destination = File(destination.path),
                    overwrite = false // TODO: Get from settings
                )
                val result = viewModel.fileOperationUseCase.execute(operation)
                
                when (result) {
                    is com.sza.fastmediasorter_v2.domain.usecase.FileOperationResult.Success -> {
                        Toast.makeText(this@PlayerActivity, "File moved to ${destination.name}", Toast.LENGTH_SHORT).show()
                        viewModel.nextFile() // Go to next file after move
                    }
                    else -> {
                        Toast.makeText(this@PlayerActivity, "Move failed", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@PlayerActivity, "Move failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun releasePlayer() {
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
        viewModel.togglePause()
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        slideShowHandler.removeCallbacks(slideShowRunnable)
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
    }

    companion object {
        fun createIntent(context: Context, resourceId: Long, initialIndex: Int = 0): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra("resourceId", resourceId)
                putExtra("initialIndex", initialIndex)
            }
        }
    }
}
