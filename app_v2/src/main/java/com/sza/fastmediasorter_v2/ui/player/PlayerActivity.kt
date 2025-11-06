package com.sza.fastmediasorter_v2.ui.player

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
import com.sza.fastmediasorter_v2.core.ui.BaseActivity
import com.sza.fastmediasorter_v2.databinding.ActivityPlayerBinding
import com.sza.fastmediasorter_v2.domain.model.MediaType
import com.sza.fastmediasorter_v2.ui.dialog.CopyToDialog
import com.sza.fastmediasorter_v2.ui.dialog.MoveToDialog
import com.sza.fastmediasorter_v2.ui.dialog.RenameDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class PlayerActivity : BaseActivity<ActivityPlayerBinding>() {
    override fun getViewBinding(): ActivityPlayerBinding {
        return ActivityPlayerBinding.inflate(layoutInflater)
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
     */
    private fun handleTouchZone(x: Float, y: Float) {
        val screenWidth = binding.root.width
        val screenHeight = binding.root.height
        
        val zone = touchZoneDetector.detectZone(x, y, screenWidth, screenHeight)
        
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
                // TODO: Toggle command panel mode
                Toast.makeText(this, "Command Panel mode - not implemented yet", Toast.LENGTH_SHORT).show()
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

            when (file.type) {
                MediaType.IMAGE, MediaType.GIF -> displayImage(file.path)
                MediaType.VIDEO, MediaType.AUDIO -> playVideo(file.path)
            }
        }

        binding.controlsOverlay.isVisible = state.showControls
        updatePlayPauseButton()
        updateSlideShowButton()
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
        Toast.makeText(this, "Delete functionality coming soon", Toast.LENGTH_SHORT).show()
    }

    private fun handleEvent(event: PlayerViewModel.PlayerEvent) {
        when (event) {
            is PlayerViewModel.PlayerEvent.ShowError -> {
                Toast.makeText(this, event.message, Toast.LENGTH_LONG).show()
            }
            PlayerViewModel.PlayerEvent.FinishActivity -> {
                finish()
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
