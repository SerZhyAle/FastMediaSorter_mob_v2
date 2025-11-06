package com.sza.fastmediasorter.ui.sort

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.ConnectionConfig
import com.sza.fastmediasorter.databinding.ActivitySortBinding
import com.sza.fastmediasorter.network.LocalStorageClient
import com.sza.fastmediasorter.network.SmbClient
import com.sza.fastmediasorter.network.SmbDataSourceFactory
import com.sza.fastmediasorter.ui.ConnectionViewModel
import com.sza.fastmediasorter.ui.base.LocaleActivity
import com.sza.fastmediasorter.utils.Logger
import com.sza.fastmediasorter.utils.MediaUtils
import com.sza.fastmediasorter.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SortActivity - Main activity for sorting and organizing media files.
 *
 * This activity provides a manual sorting interface where users can:
 * - View images and videos one by one from local storage or SMB network shares
 * - Copy or move files to predefined destination folders
 * - Delete unwanted files with proper permission handling
 * - Rename files with Android 11+ scoped storage support
 * - Navigate through media with touch gestures or buttons
 * - Preload next images for smooth transitions
 * - Handle various error scenarios with detailed diagnostics
 *
 * Key features:
 * - Dual mode support: Local storage (MediaStore/SAF) and SMB network shares
 * - Video playback with ExoPlayer and custom SMB data source
 * - Comprehensive error handling and validation
 * - Android 11+ permission handling for file operations
 * - Progress indicators and user feedback
 * - Configurable sort destinations and operation preferences
 */
class SortActivity : LocaleActivity() {
    private lateinit var binding: ActivitySortBinding
    private lateinit var viewModel: ConnectionViewModel
    private lateinit var smbClient: SmbClient
    private lateinit var preferenceManager: PreferenceManager
    private var localStorageClient: LocalStorageClient? = null

    // Current connection and media state
    private var currentConfig: ConnectionConfig? = null
    private var imageFiles = mutableListOf<String>()
    private var currentIndex = 0
    private var sortDestinations = listOf<ConnectionConfig>()
    private var isLocalMode = false

    // Video playback components
    private var exoPlayer: ExoPlayer? = null
    private var isCurrentMediaVideo = false

    // Preloading optimization for smooth transitions
    private var nextImageData: ByteArray? = null
    private var nextImageIndex: Int = -1
    private var preloadJob: Job? = null

    // Error tracking and handling
    private data class MediaError(
        val fileName: String,
        val errorType: String,
        val errorMessage: String,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val errorLog = mutableListOf<MediaError>()
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 5

    // Android 11+ permission handling for file operations
    private var pendingDeleteUri: Uri? = null
    private var isDeleteFromMove: Boolean = false // Track if delete is from Move operation
    private lateinit var deletePermissionLauncher: ActivityResultLauncher<IntentSenderRequest>

    // Android 11+ permission handling for rename operations
    private var pendingRenameUri: Uri? = null
    private var pendingNewFileName: String? = null
    private lateinit var renamePermissionLauncher: ActivityResultLauncher<IntentSenderRequest>

    // Android 10+ permission handling for write operations
    private var pendingWriteFileName: String? = null
    private var pendingWriteData: ByteArray? = null
    private var pendingWriteFolderName: String? = null
    private lateinit var writePermissionLauncher: ActivityResultLauncher<IntentSenderRequest>

    /**
     * Initializes the SortActivity with all necessary components and permissions.
     *
     * This method performs the following initialization steps:
     * 1. Sets up View Binding and basic UI components
     * 2. Registers permission launchers for Android 11+ file operations
     * 3. Initializes ViewModel and SMB client
     * 4. Sets up ExoPlayer for video playback
     * 5. Configures touch areas and UI controls
     * 6. Loads the connection configuration and media files
     *
     * @param savedInstanceState Bundle containing the activity's previously saved state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySortBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        // Initialize preference manager first
        preferenceManager = PreferenceManager(this)

        // Keep screen on if enabled
        if (preferenceManager.isKeepScreenOn()) {
            window.addFlags(
                android
                    .view
                    .WindowManager
                    .LayoutParams
                    .FLAG_KEEP_SCREEN_ON,
            )
        }

        // Register delete permission launcher for Android 11+
        deletePermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    Logger.d("SortActivity", "User granted delete permission")
                    pendingDeleteUri?.let { uri ->
                        lifecycleScope.launch {
                            handleDeleteSuccess(uri)
                        }
                    }
                } else {
                    Logger.w("SortActivity", "User denied delete permission")
                    android
                        .widget
                        .Toast
                        .makeText(
                            this,
                            "Delete permission denied",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                }
                pendingDeleteUri = null
            }

        renamePermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    Logger.d("SortActivity", "User granted rename permission")
                    pendingRenameUri?.let { uri ->
                        pendingNewFileName?.let { newName ->
                            lifecycleScope.launch {
                                handleRenameSuccess(uri, newName)
                            }
                        }
                    }
                } else {
                    Logger.w("SortActivity", "User denied rename permission")
                    android
                        .widget
                        .Toast
                        .makeText(
                            this,
                            "Rename permission denied",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                }
                pendingRenameUri = null
                pendingNewFileName = null
            }

        writePermissionLauncher =
            registerForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult(),
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    Logger.d("SortActivity", "User granted write permission")
                    pendingWriteFileName?.let { fileName ->
                        pendingWriteData?.let { data ->
                            pendingWriteFolderName?.let { folderName ->
                                lifecycleScope.launch {
                                    handleWriteSuccess(fileName, data, folderName)
                                }
                            }
                        }
                    }
                } else {
                    Logger.w("SortActivity", "User denied write permission")
                    android
                        .widget
                        .Toast
                        .makeText(
                            this,
                            "Write permission denied",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                }
                pendingWriteFileName = null
                pendingWriteData = null
                pendingWriteFolderName = null
            }

        viewModel = ViewModelProvider(this)[ConnectionViewModel::class.java]

        val configId = intent.getLongExtra("configId", -1)
        if (configId == -1L) {
            finish()
            return
        }

        setupExoPlayer()
        setupTouchAreas()
        setupBackButton()
        setupImageCounter()
        setupObservers()

        lifecycleScope.launch {
            loadConnection(configId)
        }
    }

    /**
     * Initializes and configures the ExoPlayer for video playback in sort mode.
     *
     * This method sets up:
     * - ExoPlayer instance with proper configuration for sorting interface
     * - PlayerView with controls enabled and buffering indicators
     * - Video size and aspect ratio handling
     * - Playback state listeners for UI updates and error handling
     * - Support for both local and SMB video sources
     */
    private fun setupExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build()
        binding.playerView.player = exoPlayer

        // Configure player view for sorting mode - enable controls and ensure video is visible
        binding.playerView.useController = true
        binding.playerView.controllerAutoShow = true
        binding.playerView.controllerShowTimeoutMs = 3000
        binding.playerView.controllerHideOnTouch = true

        // Ensure video surface is properly configured
        binding.playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        binding.playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

        Logger.d("SortActivity", "ExoPlayer setup complete - controls enabled, buffering always shown")

        exoPlayer?.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    Logger.d("SortActivity", "Video playback state changed: $playbackState")
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            Logger.d("SortActivity", "Video buffering - showing loading layout")
                            binding.videoLoadingLayout.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            Logger.d("SortActivity", "Video ready - hiding loading, video should be visible")
                            binding.videoLoadingLayout.visibility = View.GONE

                            // Log video details
                            val videoSize = exoPlayer?.videoSize
                            Logger.d(
                                "SortActivity",
                                "Video ready: size=${videoSize?.width}x${videoSize?.height}, duration=${exoPlayer?.duration}",
                            )

                            // Ensure player view is visible and properly sized
                            binding.playerView.visibility = View.VISIBLE
                            binding.playerView.requestLayout()
                        }
                        Player.STATE_ENDED -> {
                            Logger.d("SortActivity", "Video ended - hiding loading")
                            binding.videoLoadingLayout.visibility = View.GONE

                            // Auto-advance to next file if "Play video till end" is enabled (slideshow mode)
                            if (preferenceManager.isPlayVideoTillEnd() && imageFiles.isNotEmpty()) {
                                Logger.d("SortActivity", "Video ended, auto-advancing to next file (slideshow mode)")
                                currentIndex =
                                    if (currentIndex < imageFiles.size - 1) {
                                        currentIndex + 1
                                    } else {
                                        0 // Loop to first
                                    }
                                loadMedia()
                            }
                        }
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Logger.d("SortActivity", "Video player error: ${error.message}")
                    binding.videoLoadingLayout.visibility = View.GONE

                    // Log error consistently
                    val currentMediaUrl = imageFiles.getOrNull(currentIndex) ?: "unknown"
                    handleMediaError(currentMediaUrl, "Video Playback", error.message ?: "Playback error")

                    if (preferenceManager.isShowVideoErrorDetails()) {
                        showVideoErrorDialog(error)
                    } else {
                        Toast
                            .makeText(
                                this@SortActivity,
                                "Video playback error: ${error.message}",
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    Logger.d("SortActivity", "Video size changed: ${videoSize.width}x${videoSize.height}")
                    super.onVideoSizeChanged(videoSize)
                }

                override fun onRenderedFirstFrame() {
                    Logger.d("SortActivity", "Video first frame rendered - video should now be visible")
                    super.onRenderedFirstFrame()
                }
            },
        )
    }

    /**
     * Sets up LiveData observers for ViewModel state changes.
     *
     * Currently observes sort destinations list and updates the UI
     * when destinations are loaded or changed. This ensures the
     * sort buttons are properly configured when destinations become available.
     */
    private fun setupObservers() {
        // Observe sort destinations
        viewModel.sortDestinations.observe(this) { destinations ->
            Logger.d(
                "SortActivity",
                "sortDestinations observed: ${destinations.size} items, currentConfig=${currentConfig?.id}",
            )
            sortDestinations = destinations
            if (currentConfig != null) {
                setupSortButtons()
            }
        }
    }

    /**
     * Configures touch-sensitive areas for media navigation.
     *
     * Sets up invisible touch zones that allow users to navigate
     * through media files by tapping on different screen areas:
     * - Previous area: Left side navigation
     * - Next area: Right side navigation
     *
     * These areas provide an intuitive touch interface for sorting
     * without obstructing the media view.
     */
    private fun setupTouchAreas() {
        binding.previousArea.setOnClickListener {
            Logger.d("SortActivity", "Previous area clicked")
            if (imageFiles.isEmpty()) return@setOnClickListener

            currentIndex =
                if (currentIndex > 0) {
                    currentIndex - 1
                } else {
                    imageFiles.size - 1 // Jump to last
                }
            loadMedia()
        }

        binding.nextArea.setOnClickListener {
            Logger.d("SortActivity", "Next area clicked")
            if (imageFiles.isEmpty()) return@setOnClickListener

            currentIndex =
                if (currentIndex < imageFiles.size - 1) {
                    currentIndex + 1
                } else {
                    0 // Jump to first
                }
            loadMedia()
        }
    }

    /**
     * Configures the back button and refresh functionality.
     *
     * Sets up:
     * - Back button to return to previous activity
     * - Refresh button to reload the current folder contents
     * - Proper navigation handling with file list refresh
     */
    private fun setupBackButton() {
        binding.backButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.refreshButton.setOnClickListener {
            refreshFileList()
        }
    }

    /**
     * Configures the image counter display as an interactive element.
     *
     * The counter shows current position (e.g., "5 / 20") and allows
     * users to tap it to open a jump-to-file dialog for quick navigation
     * to any position in the media list.
     */
    private fun setupImageCounter() {
        binding.imageCounterText.setOnClickListener {
            showJumpToDialog()
        }
    }

    /**
     * Displays a dialog for jumping to a specific file number.
     *
     * Shows an input dialog where users can enter a file number
     * to quickly navigate to any position in the current media list.
     * Validates input and provides user feedback for invalid entries.
     */
    private fun showJumpToDialog() {
        if (imageFiles.isEmpty()) return

        val totalFiles = imageFiles.size
        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "1 - $totalFiles"

        androidx
            .appcompat
            .app
            .AlertDialog
            .Builder(this)
            .setTitle("Jump to file")
            .setMessage("Enter file number (1 - $totalFiles):")
            .setView(input)
            .setPositiveButton("Jump") { _, _ ->
                val inputText = input.text.toString()
                if (inputText.isNotEmpty()) {
                    try {
                        val targetNumber = inputText.toInt()
                        if (targetNumber in 1..totalFiles) {
                            currentIndex = targetNumber - 1
                            loadMedia()
                            Toast
                                .makeText(
                                    this,
                                    "Jumped to file $targetNumber",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        } else {
                            Toast
                                .makeText(
                                    this,
                                    "Number must be between 1 and $totalFiles",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    } catch (e: NumberFormatException) {
                        Toast
                            .makeText(
                                this,
                                "Invalid number",
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }
            }.setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Configures the sort operation buttons based on current configuration and permissions.
     *
     * This method dynamically sets up the UI for file operations:
     * - Copy buttons: Allow copying files to destination folders
     * - Move buttons: Allow moving files to destination folders
     * - Delete button: Allow deleting files (with permission checks)
     * - Rename button: Allow renaming files (with permission checks)
     *
     * Considers various factors:
     * - Source folder write permissions
     * - User preferences for allowed operations
     * - Available destination folders
     * - Android version-specific permission handling
     * - UI layout preferences (small buttons mode)
     */
    private fun setupSortButtons() {
        if (currentConfig == null) {
            Logger.d("SortActivity", "setupSortButtons: currentConfig is null, skipping")
            return
        }

        val copyButtons =
            listOf(
                binding.sortButton0, binding.sortButton1, binding.sortButton2,
                binding.sortButton3, binding.sortButton4, binding.sortButton5,
                binding.sortButton6, binding.sortButton7, binding.sortButton8,
                binding.sortButton9,
            )

        val moveButtons =
            listOf(
                binding.moveButton0, binding.moveButton1, binding.moveButton2,
                binding.moveButton3, binding.moveButton4, binding.moveButton5,
                binding.moveButton6, binding.moveButton7, binding.moveButton8,
                binding.moveButton9,
            )

        // Check if current source folder has write permissions
        val sourceHasWritePermission =
            when (currentConfig?.type) {
                "LOCAL_CUSTOM", "LOCAL_STANDARD" -> true // Local folders always have write permission
                "SMB" -> currentConfig?.writePermission ?: true // SMB folders default to writable (user has access)
                else -> currentConfig?.writePermission ?: false
            }

        // Check if move is allowed
        val allowMove = preferenceManager.isAllowMove() && sourceHasWritePermission

        // Check if copy is allowed
        val allowCopy = preferenceManager.isAllowCopy()

        // Check if delete is allowed
        val allowDelete = preferenceManager.isAllowDelete() && sourceHasWritePermission

        // Filter out destinations that are the same as source (pre-calculation for UI logic)
        val filteredDestinations =
            sortDestinations.filter { config ->
                // Skip if it's the same as current source connection
                currentConfig?.let { currentSource ->
                    // Skip if same config ID (primary check - works for all types)
                    if (config.id == currentSource.id) {
                        return@filter false
                    }

                    // For network connections, also compare server+folder as fallback
                    if (currentSource.type == "SMB" && config.type == "SMB") {
                        val sameServer = currentSource.serverAddress == config.serverAddress
                        val sameFolder = currentSource.folderPath == config.folderPath
                        if (sameServer && sameFolder) {
                            return@filter false
                        }
                    }
                }
                true
            }

        // Check if we have destinations after filtering
        val hasDestinations = filteredDestinations.isNotEmpty()

        // Show warning only if NO destinations configured at all (not just filtered out)
        val totalDestinationsCount = sortDestinations.size
        binding.noDestinationsWarning.visibility =
            if (totalDestinationsCount == 0 && allowDelete) View.VISIBLE else View.GONE

        // Check if small buttons are enabled
        val useSmallButtons = preferenceManager.isUseSmallButtons()

        // Hide/show copy section (only if we have destinations)
        val showCopy = allowCopy && hasDestinations
        binding.copyToLabel.visibility = if (showCopy) View.VISIBLE else View.GONE
        binding.buttonsRow1.visibility = if (showCopy) View.VISIBLE else View.GONE
        // Always show second row when copy is enabled (small buttons just change text size)
        binding.buttonsRow2.visibility = if (showCopy) View.VISIBLE else View.GONE

        // Adjust label text size for small buttons mode
        if (useSmallButtons) {
            binding.copyToLabel.textSize = 10f // Smaller text for labels
        } else {
            binding.copyToLabel.textSize = 12f // Default text size
        }

        // Hide/show move section (only if we have destinations)
        val showMove = allowMove && hasDestinations
        binding.moveToLabel.visibility = if (showMove) View.VISIBLE else View.GONE
        binding.moveButtonsRow1.visibility = if (showMove) View.VISIBLE else View.GONE
        // Always show second row when move is enabled (small buttons just change text size)
        binding.moveButtonsRow2.visibility = if (showMove) View.VISIBLE else View.GONE

        // Adjust label text size for small buttons mode
        if (useSmallButtons) {
            binding.moveToLabel.textSize = 10f // Smaller text for labels
        } else {
            binding.moveToLabel.textSize = 12f // Default text size
        }

        // Hide/show delete button
        binding.deleteButton.visibility = if (allowDelete) View.VISIBLE else View.GONE

        // Setup delete button click listener
        binding.deleteButton.setOnClickListener {
            deleteCurrentImage()
        }

        // Hide/show rename button
        val allowRename = preferenceManager.isAllowRename() && sourceHasWritePermission
        binding.renameButton.visibility = if (allowRename) View.VISIBLE else View.GONE

        // Setup rename button click listener
        binding.renameButton.setOnClickListener {
            renameCurrentMedia()
        }

        // Color shades for buttons
        val colors =
            listOf(
                android.graphics.Color.parseColor("#5C6BC0"),
                // Indigo
                android.graphics.Color.parseColor("#42A5F5"),
                // Blue
                android.graphics.Color.parseColor("#26C6DA"),
                // Cyan
                android.graphics.Color.parseColor("#66BB6A"),
                // Green
                android.graphics.Color.parseColor("#9CCC65"),
                // Light Green
                android.graphics.Color.parseColor("#FFCA28"),
                // Amber
                android.graphics.Color.parseColor("#FFA726"),
                // Orange
                android.graphics.Color.parseColor("#EF5350"),
                // Red
                android.graphics.Color.parseColor("#AB47BC"),
                // Purple
                android.graphics.Color.parseColor("#EC407A"),
                // Pink
            )

        // Hide all buttons first
        copyButtons.forEach { it.visibility = View.GONE }
        moveButtons.forEach { it.visibility = View.GONE }

        // Show and configure buttons for filtered destinations
        filteredDestinations.forEachIndexed { index, config ->
            if (index < copyButtons.size) {
                val copyButton = copyButtons[index]
                copyButton.text = config.sortName
                copyButton.setBackgroundColor(colors[index])
                copyButton.setTextColor(android.graphics.Color.BLACK)

                // Adjust text size for small buttons
                if (useSmallButtons) {
                    copyButton.textSize = 10f // Smaller text for small buttons
                } else {
                    copyButton.textSize = 14f // Default text size
                }

                copyButton.visibility = View.VISIBLE
                copyButton.setOnClickListener {
                    copyToDestination(config)
                }

                val moveButton = moveButtons[index]
                moveButton.text = config.sortName
                moveButton.setBackgroundColor(colors[index])
                moveButton.setTextColor(android.graphics.Color.BLACK)

                // Adjust text size for small buttons
                if (useSmallButtons) {
                    moveButton.textSize = 10f // Smaller text for small buttons
                } else {
                    moveButton.textSize = 14f // Default text size
                }

                moveButton.visibility = View.VISIBLE
                moveButton.setOnClickListener {
                    moveToDestination(config)
                }
            }
        }

        // Adjust button heights for small buttons mode
        if (useSmallButtons) {
            // Set compact height for small buttons (36dp instead of wrap_content)
            val compactHeight = (36 * resources.displayMetrics.density).toInt()

            // Apply compact height to all copy buttons
            copyButtons.forEach { button ->
                val params = button.layoutParams
                params.height = compactHeight
                button.layoutParams = params
            }

            // Apply compact height to all move buttons
            moveButtons.forEach { button ->
                val params = button.layoutParams
                params.height = compactHeight
                button.layoutParams = params
            }

            // Apply compact height to delete and rename buttons
            val deleteParams = binding.deleteButton.layoutParams
            deleteParams.height = compactHeight
            binding.deleteButton.layoutParams = deleteParams

            val renameParams = binding.renameButton.layoutParams
            renameParams.height = compactHeight
            binding.renameButton.layoutParams = renameParams

            // Reduce vertical margins for compact layout
            // Copy section margins
            val copyLabelParams = binding.copyToLabel.layoutParams as ViewGroup.MarginLayoutParams
            copyLabelParams.bottomMargin = (3 * resources.displayMetrics.density).toInt() // 3dp instead of 6dp
            binding.copyToLabel.layoutParams = copyLabelParams

            val buttonsRow1Params = binding.buttonsRow1.layoutParams as ViewGroup.MarginLayoutParams
            buttonsRow1Params.bottomMargin = (2 * resources.displayMetrics.density).toInt() // 2dp instead of 4dp
            binding.buttonsRow1.layoutParams = buttonsRow1Params

            // Move section margins
            val moveLabelParams = binding.moveToLabel.layoutParams as ViewGroup.MarginLayoutParams
            moveLabelParams.topMargin = (6 * resources.displayMetrics.density).toInt() // 6dp instead of 12dp
            moveLabelParams.bottomMargin = (3 * resources.displayMetrics.density).toInt() // 3dp instead of 6dp
            binding.moveToLabel.layoutParams = moveLabelParams

            val moveButtonsRow1Params = binding.moveButtonsRow1.layoutParams as ViewGroup.MarginLayoutParams
            moveButtonsRow1Params.bottomMargin = (2 * resources.displayMetrics.density).toInt() // 2dp instead of 4dp
            binding.moveButtonsRow1.layoutParams = moveButtonsRow1Params

            // Delete/Rename section margin
            val deleteRenameContainer = binding.deleteButton.parent as LinearLayout
            val deleteRenameParams = deleteRenameContainer.layoutParams as ViewGroup.MarginLayoutParams
            deleteRenameParams.topMargin = (6 * resources.displayMetrics.density).toInt() // 6dp instead of 12dp
            deleteRenameContainer.layoutParams = deleteRenameParams
        }
    }

    /**
     * Initiates copying the current media file to a destination folder.
     *
     * Handles different copy scenarios:
     * - Local to Local (MediaStore/SAF operations)
     * - Local to SMB (upload to network share)
     * - SMB to SMB (server-side copy)
     * - SMB to Local (download to device)
     *
     * Shows progress indicators and provides user feedback.
     * Automatically advances to next file on successful copy if enabled.
     *
     * @param destination The target ConnectionConfig where the file should be copied
     */
    private fun copyToDestination(destination: ConnectionConfig) {
        if (imageFiles.isEmpty()) return

        val currentImageUrl = imageFiles[currentIndex]
        Logger.d(
            "SortActivity",
            "Copy to destination: ${destination.localDisplayName ?: destination.name}, type=${destination.type}",
        )

        lifecycleScope.launch(Dispatchers.IO) {
            // Show progress
            withContext(Dispatchers.Main) {
                binding.progressText.text = "Copying..."
                binding.copyProgressLayout.visibility = View.VISIBLE
            }

            try {
                val result =
                    when {
                        isLocalMode && destination.type in listOf("LOCAL_CUSTOM", "LOCAL_STANDARD") -> {
                            Logger.d("SortActivity", "Local → Local copy")
                            copyFromLocalToLocal(currentImageUrl, destination)
                        }
                        isLocalMode && destination.type == "SMB" -> {
                            Logger.d("SortActivity", "Local → SMB copy")
                            copyFromLocalToSmb(currentImageUrl, destination)
                        }
                        !isLocalMode && destination.type == "SMB" -> {
                            Logger.d("SortActivity", "SMB → SMB copy")
                            smbClient.copyFile(
                                currentImageUrl,
                                destination.serverAddress,
                                destination.folderPath,
                            )
                        }
                        !isLocalMode && destination.type in listOf("LOCAL_CUSTOM", "LOCAL_STANDARD") -> {
                            Logger.d("SortActivity", "SMB → Local copy")
                            copyFromSmbToLocal(currentImageUrl, destination)
                        }
                        else -> {
                            Logger.e(
                                "SortActivity",
                                "Unsupported copy operation: isLocal=$isLocalMode, destType=${destination.type}",
                            )
                            SmbClient.CopyResult.UnknownError("Unsupported operation")
                        }
                    }

                withContext(Dispatchers.Main) {
                    // Hide progress
                    binding.copyProgressLayout.visibility = View.GONE

                    val message =
                        when (result) {
                            is SmbClient.CopyResult.Success -> "File copied"
                            is SmbClient.CopyResult.AlreadyExists -> "File already exists"
                            is SmbClient.CopyResult.SameFolder -> "Same folder"
                            is SmbClient.CopyResult.NetworkError -> "Network error: ${result.message}"
                            is SmbClient.CopyResult.SecurityError -> "Security error: ${result.message}"
                            is SmbClient.CopyResult.UnknownError -> "Error: ${result.message}"
                        }

                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            message,
                            if (result is SmbClient.CopyResult.Success) {
                                android.widget.Toast.LENGTH_SHORT
                            } else {
                                android.widget.Toast.LENGTH_LONG
                            },
                        ).show()

                    // Jump to next image on success (if setting enabled)
                    if (result is SmbClient.CopyResult.Success && preferenceManager.isGoNextAfterCopy()) {
                        if (imageFiles.isNotEmpty()) {
                            currentIndex =
                                if (currentIndex < imageFiles.size - 1) {
                                    currentIndex + 1
                                } else {
                                    0 // Jump to first
                                }
                            loadMedia()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Hide progress
                    binding.copyProgressLayout.visibility = View.GONE

                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Unexpected error: ${e.message}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    private suspend fun copyFromLocalToLocal(
        localUri: String,
        destination: ConnectionConfig,
    ): SmbClient.CopyResult {
        return try {
            val sourceUri = Uri.parse(localUri)

            // Read source file data
            val imageBytes = localStorageClient?.downloadImage(sourceUri)
            if (imageBytes == null) {
                Logger.e("SortActivity", "Failed to read source file: $localUri")
                return SmbClient.CopyResult.UnknownError("Failed to read source file")
            }

            val fileInfo = localStorageClient?.getFileInfo(sourceUri)
            val fileName = fileInfo?.name ?: "unknown.jpg"
            Logger.d(
                "SortActivity",
                "Copying local file: $fileName (${imageBytes.size} bytes) to ${destination.localDisplayName}",
            )

            // Check if destination has a URI (user-selected folder) or is standard folder
            if (!destination.localUri.isNullOrEmpty()) {
                // User-selected folder - use SAF
                val destUri = Uri.parse(destination.localUri)
                Logger.d("SortActivity", "Destination URI: $destUri")

                val success = localStorageClient?.writeFile(destUri, fileName, imageBytes) ?: false

                if (success) {
                    Logger.d("SortActivity", "File copied successfully to local folder")
                    SmbClient.CopyResult.Success
                } else {
                    Logger.e("SortActivity", "Failed to write file to local folder")
                    SmbClient.CopyResult.UnknownError("Failed to write file")
                }
            } else {
                // Standard folder (Camera, Pictures, etc.) - use MediaStore
                Logger.d("SortActivity", "Standard folder: ${destination.localDisplayName}")
                val folderName = destination.localDisplayName ?: "Pictures"
                val success = localStorageClient?.writeFileToStandardFolder(fileName, imageBytes, folderName) ?: false

                if (success) {
                    Logger.d("SortActivity", "File copied successfully to standard folder")
                    SmbClient.CopyResult.Success
                } else {
                    Logger.e("SortActivity", "Failed to write file to standard folder")
                    SmbClient.CopyResult.UnknownError("Failed to write to standard folder")
                }
            }
        } catch (e: Exception) {
            Logger.e("SortActivity", "Exception during local copy: ${e.message}", e)
            e.printStackTrace()
            SmbClient.CopyResult.UnknownError(e.message ?: "Unknown error")
        }
    }

    private suspend fun copyFromLocalToSmb(
        localUri: String,
        destination: ConnectionConfig,
    ): SmbClient.CopyResult {
        val destClient = SmbClient()
        return try {
            val uri = Uri.parse(localUri)
            val imageBytes = localStorageClient?.downloadImage(uri)
            if (imageBytes == null) {
                Logger.e("SortActivity", "Failed to read local file: $localUri")
                return SmbClient.CopyResult.UnknownError("Failed to read local file")
            }

            val fileInfo = localStorageClient?.getFileInfo(uri)
            val fileName = fileInfo?.name ?: "unknown.jpg"
            Logger.d("SortActivity", "Copying file: $fileName (${imageBytes.size} bytes)")

            // Connect to destination SMB if needed
            val connected = destClient.connect(destination.serverAddress, destination.username, destination.password)
            if (!connected) {
                Logger.e("SortActivity", "Failed to connect to ${destination.serverAddress}")
                return SmbClient.CopyResult.NetworkError("Failed to connect to destination")
            }

            Logger.d("SortActivity", "Connected to ${destination.serverAddress}, writing to ${destination.folderPath}")

            // Write file to SMB and return result
            val result = destClient.writeFile(destination.serverAddress, destination.folderPath, fileName, imageBytes)

            when (result) {
                is SmbClient.CopyResult.Success ->
                    Logger.d("SortActivity", "File copied successfully: $fileName")
                is SmbClient.CopyResult.AlreadyExists ->
                    Logger.w("SortActivity", "File already exists: $fileName")
                is SmbClient.CopyResult.NetworkError ->
                    Logger.e("SortActivity", "Network error: ${result.message}")
                is SmbClient.CopyResult.SecurityError ->
                    Logger.e("SortActivity", "Security error: ${result.message}")
                else ->
                    Logger.e("SortActivity", "Unknown error during copy")
            }

            result
        } catch (e: Exception) {
            Logger.e("SortActivity", "Exception during copy: ${e.message}", e)
            e.printStackTrace()
            SmbClient.CopyResult.UnknownError(e.message ?: "Unknown error")
        } finally {
            // Clear credentials from memory immediately after operation
            destClient.disconnect()
        }
    }

    private suspend fun moveFromLocalToLocal(
        localUri: String,
        destination: ConnectionConfig,
    ): SmbClient.MoveResult {
        try {
            Logger.d("SortActivity", "Moving from local to local: $localUri")
            val copyResult = copyFromLocalToLocal(localUri, destination)

            if (copyResult is SmbClient.CopyResult.Success) {
                val uri = Uri.parse(localUri)

                // Try to delete with proper permission handling for Android 11+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    deleteImageWithPermission(uri, fromMove = true)
                    // For Android 11+, return pending status
                    // Actual success will be handled in the callback
                    return SmbClient.MoveResult.PendingUserConfirmation
                } else {
                    val deleted = localStorageClient?.deleteImage(uri) ?: false
                    return if (deleted) {
                        Logger.d("SortActivity", "File deleted successfully after local copy")
                        SmbClient.MoveResult.Success
                    } else {
                        Logger.w("SortActivity", "Failed to delete local file after successful local copy")
                        SmbClient.MoveResult.DeleteError("Failed to delete local file after copy")
                    }
                }
            } else {
                val errorMsg =
                    when (copyResult) {
                        is SmbClient.CopyResult.AlreadyExists -> "File already exists"
                        is SmbClient.CopyResult.UnknownError -> "Copy failed: ${copyResult.message}"
                        else -> "Copy failed"
                    }
                Logger.e("SortActivity", "Local copy failed: $errorMsg")
                return SmbClient.MoveResult.UnknownError(errorMsg)
            }
        } catch (e: Exception) {
            Logger.e("SortActivity", "Exception during local move: ${e.message}", e)
            e.printStackTrace()
            return SmbClient.MoveResult.UnknownError(e.message ?: "Unknown error")
        }
    }

    private suspend fun moveFromLocalToSmb(
        localUri: String,
        destination: ConnectionConfig,
    ): SmbClient.MoveResult {
        try {
            Logger.d("SortActivity", "Moving from local to SMB: $localUri")
            val copyResult = copyFromLocalToSmb(localUri, destination)

            if (copyResult is SmbClient.CopyResult.Success) {
                val uri = Uri.parse(localUri)

                // Try to delete with proper permission handling for Android 11+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    deleteImageWithPermission(uri, fromMove = true)
                    // For Android 11+, return pending status
                    // Actual success will be handled in the callback
                    return SmbClient.MoveResult.PendingUserConfirmation
                } else {
                    val deleted = localStorageClient?.deleteImage(uri) ?: false
                    return if (deleted) {
                        Logger.d("SortActivity", "File deleted successfully after copy")
                        SmbClient.MoveResult.Success
                    } else {
                        Logger.w("SortActivity", "Failed to delete local file after successful copy")
                        SmbClient.MoveResult.DeleteError("Failed to delete local file after copy")
                    }
                }
            } else {
                val errorMsg =
                    when (copyResult) {
                        is SmbClient.CopyResult.AlreadyExists -> "File already exists"
                        is SmbClient.CopyResult.NetworkError -> "Network: ${copyResult.message}"
                        is SmbClient.CopyResult.SecurityError -> "Security: ${copyResult.message}"
                        is SmbClient.CopyResult.UnknownError -> "Copy failed: ${copyResult.message}"
                        else -> "Copy failed"
                    }
                Logger.e("SortActivity", "Copy failed: $errorMsg")
                return SmbClient.MoveResult.UnknownError(errorMsg)
            }
        } catch (e: Exception) {
            Logger.e("SortActivity", "Exception during move: ${e.message}", e)
            e.printStackTrace()
            return SmbClient.MoveResult.UnknownError(e.message ?: "Unknown error")
        }
    }

    private suspend fun copyFromSmbToLocal(
        smbUri: String,
        destination: ConnectionConfig,
    ): SmbClient.CopyResult {
        return try {
            Logger.d("SortActivity", "Copying from SMB to local: $smbUri")

            // Download file from SMB
            val imageBytes = smbClient.downloadImage(smbUri)
            if (imageBytes == null) {
                Logger.e("SortActivity", "Failed to download SMB file: $smbUri")
                return SmbClient.CopyResult.UnknownError("Failed to download SMB file")
            }

            val fileName = smbUri.substringAfterLast('/')

            // Write to local storage
            val writeResult =
                when (destination.type) {
                    "LOCAL_STANDARD" -> {
                        val folderName = destination.name
                        writeFileToStandardFolderWithPermission(fileName, imageBytes, folderName)
                    }
                    "LOCAL_CUSTOM" -> {
                        val destUri = Uri.parse(destination.localUri)
                        localStorageClient?.writeFile(destUri, fileName, imageBytes)
                    }
                    else -> false
                }

            if (writeResult == true) {
                SmbClient.CopyResult.Success
            } else {
                SmbClient.CopyResult.UnknownError("Failed to write to local storage")
            }
        } catch (e: Exception) {
            Logger.e("SortActivity", "Exception during SMB→Local copy: ${e.message}", e)
            e.printStackTrace()
            SmbClient.CopyResult.UnknownError(e.message ?: "Unknown error")
        }
    }

    private suspend fun moveFromSmbToLocal(
        smbUri: String,
        destination: ConnectionConfig,
    ): SmbClient.MoveResult {
        return try {
            Logger.d("SortActivity", "Moving from SMB to local: $smbUri")
            val copyResult = copyFromSmbToLocal(smbUri, destination)

            if (copyResult is SmbClient.CopyResult.Success) {
                // Try to delete from SMB
                val deleteResult = smbClient.deleteFile(smbUri)
                when (deleteResult) {
                    is SmbClient.DeleteResult.Success -> {
                        Logger.d("SortActivity", "File deleted from SMB successfully after copy")
                        SmbClient.MoveResult.Success
                    }
                    else -> {
                        Logger.w("SortActivity", "Failed to delete SMB file after successful copy: $deleteResult")
                        SmbClient.MoveResult.DeleteError("Failed to delete SMB file after copy")
                    }
                }
            } else {
                val errorMsg =
                    when (copyResult) {
                        is SmbClient.CopyResult.AlreadyExists -> "File already exists"
                        is SmbClient.CopyResult.NetworkError -> "Network: ${copyResult.message}"
                        is SmbClient.CopyResult.SecurityError -> "Security: ${copyResult.message}"
                        is SmbClient.CopyResult.UnknownError -> "Copy failed: ${copyResult.message}"
                        else -> "Copy failed"
                    }
                Logger.e("SortActivity", "Copy failed: $errorMsg")
                SmbClient.MoveResult.UnknownError(errorMsg)
            }
        } catch (e: Exception) {
            Logger.e("SortActivity", "Exception during SMB→Local move: ${e.message}", e)
            e.printStackTrace()
            SmbClient.MoveResult.UnknownError(e.message ?: "Unknown error")
        }
    }

    private suspend fun deleteImageWithPermission(
        uri: Uri,
        fromMove: Boolean = false,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Logger.d("SortActivity", "Requesting delete permission for Android 11+")

                val intentSender =
                    MediaStore
                        .createDeleteRequest(
                            contentResolver,
                            listOf(uri),
                        ).intentSender

                pendingDeleteUri = uri
                isDeleteFromMove = fromMove
                val request = IntentSenderRequest.Builder(intentSender).build()

                withContext(Dispatchers.Main) {
                    deletePermissionLauncher.launch(request)
                }
            } catch (e: Exception) {
                Logger.e("SortActivity", "Error requesting delete permission", e)
                withContext(Dispatchers.Main) {
                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Cannot request delete permission: ${e.message}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    private suspend fun handleDeleteSuccess(uri: Uri) {
        Logger.d("SortActivity", "File deleted successfully: $uri")

        withContext(Dispatchers.Main) {
            // Show success message based on context
            val message = if (isDeleteFromMove) "File moved" else "File deleted"
            android
                .widget
                .Toast
                .makeText(
                    this@SortActivity,
                    message,
                    android.widget.Toast.LENGTH_SHORT,
                ).show()

            // Reset flag
            isDeleteFromMove = false

            // Remove from list and load next
            if (imageFiles.isNotEmpty()) {
                val index = imageFiles.indexOfFirst { it == uri.toString() }
                if (index != -1) {
                    imageFiles.removeAt(index)
                    if (currentIndex >= imageFiles.size) {
                        currentIndex = imageFiles.size - 1
                    }
                }

                if (imageFiles.isEmpty()) {
                    showNoFilesState()
                } else {
                    loadMedia()
                }
            }
        }
    }

    /**
     * Initiates moving the current media file to a destination folder.
     *
     * This operation combines copy and delete:
     * 1. Copies the file to the destination
     * 2. If copy succeeds, deletes the original file
     * 3. Updates the file list and advances to next media
     *
     * Handles Android 11+ permission dialogs for file deletion.
     * For pending user confirmations, the actual deletion happens
     * in the permission callback.
     *
     * @param destination The target ConnectionConfig where the file should be moved
     */
    private fun moveToDestination(destination: ConnectionConfig) {
        if (imageFiles.isEmpty()) return

        val currentImageUrl = imageFiles[currentIndex]
        Logger.d(
            "SortActivity",
            "Move to destination: ${destination.localDisplayName ?: destination.name}, type=${destination.type}",
        )

        lifecycleScope.launch(Dispatchers.IO) {
            // Show progress
            withContext(Dispatchers.Main) {
                binding.progressText.text = "Moving..."
                binding.copyProgressLayout.visibility = View.VISIBLE
            }

            try {
                val result =
                    when {
                        isLocalMode && destination.type in listOf("LOCAL_CUSTOM", "LOCAL_STANDARD") -> {
                            Logger.d("SortActivity", "Local → Local move")
                            moveFromLocalToLocal(currentImageUrl, destination)
                        }
                        isLocalMode && destination.type == "SMB" -> {
                            Logger.d("SortActivity", "Local → SMB move")
                            moveFromLocalToSmb(currentImageUrl, destination)
                        }
                        !isLocalMode && destination.type == "SMB" -> {
                            Logger.d("SortActivity", "SMB → SMB move")
                            smbClient.moveFile(
                                currentImageUrl,
                                destination.serverAddress,
                                destination.folderPath,
                            )
                        }
                        !isLocalMode && destination.type in listOf("LOCAL_CUSTOM", "LOCAL_STANDARD") -> {
                            Logger.d("SortActivity", "SMB → Local move")
                            moveFromSmbToLocal(currentImageUrl, destination)
                        }
                        else -> {
                            Logger.e(
                                "SortActivity",
                                "Unsupported move operation: isLocal=$isLocalMode, destType=${destination.type}",
                            )
                            SmbClient.MoveResult.UnknownError("Unsupported operation")
                        }
                    }

                withContext(Dispatchers.Main) {
                    // Hide progress
                    binding.copyProgressLayout.visibility = View.GONE

                    val message =
                        when (result) {
                            is SmbClient.MoveResult.Success -> "File moved"
                            is SmbClient.MoveResult.PendingUserConfirmation -> "Confirm file deletion..."
                            is SmbClient.MoveResult.AlreadyExists -> "File already exists"
                            is SmbClient.MoveResult.SameFolder -> "Same folder"
                            is SmbClient.MoveResult.NetworkError -> "Network error: ${result.message}"
                            is SmbClient.MoveResult.SecurityError -> "Security error: ${result.message}"
                            is SmbClient.MoveResult.DeleteError -> "Delete error: ${result.message}"
                            is SmbClient.MoveResult.UnknownError -> "Error: ${result.message}"
                        }

                    // Show toast only for non-pending results
                    if (result !is SmbClient.MoveResult.PendingUserConfirmation) {
                        android
                            .widget
                            .Toast
                            .makeText(
                                this@SortActivity,
                                message,
                                if (result is SmbClient.MoveResult.Success) {
                                    android.widget.Toast.LENGTH_SHORT
                                } else {
                                    android.widget.Toast.LENGTH_LONG
                                },
                            ).show()
                    }

                    // Remove from list and load next on success
                    if (result is SmbClient.MoveResult.Success) {
                        if (imageFiles.isNotEmpty()) {
                            // Remove current file from list
                            imageFiles.removeAt(currentIndex)

                            // Check if list is now empty
                            if (imageFiles.isEmpty()) {
                                showNoFilesState()
                                return@withContext
                            }

                            // Adjust index if we removed the last item
                            if (currentIndex >= imageFiles.size) {
                                currentIndex = imageFiles.size - 1
                            } // Load media at current index (which is now the next file)
                            loadMedia()
                        }
                    }
                    // For PendingUserConfirmation, the dialog will be shown automatically
                    // Actual deletion and list update happens in handleDeleteSuccess callback
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Hide progress
                    binding.copyProgressLayout.visibility = View.GONE

                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Unexpected error: ${e.message}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    /**
     * Loads and initializes the connection configuration for sorting.
     *
     * Handles different connection types:
     * - Negative IDs: Standard local folders (Camera, Downloads, etc.)
     * - Positive IDs: Custom connections from database (SMB or custom local)
     *
     * For local connections:
     * - Initializes LocalStorageClient
     * - Sets up UI with folder name
     *
     * For SMB connections:
     * - Initializes SmbClient with authentication
     * - Establishes network connection
     * - Sets up UI with server/folder information
     *
     * @param configId The database ID of the connection configuration
     */
    private suspend fun loadConnection(configId: Long) {
        Logger.d("SortActivity", "loadConnection called with configId=$configId")
        // Handle standard local folders (negative IDs)
        if (configId < 0) {
            val bucketName =
                when (configId) {
                    -1L -> "Camera"
                    -2L -> "Screenshots"
                    -3L -> "Pictures"
                    -4L -> "Download"
                    else -> {
                        withContext(Dispatchers.Main) {
                            android
                                .widget
                                .Toast
                                .makeText(
                                    this@SortActivity,
                                    "Invalid folder ID",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                            finish()
                        }
                        return
                    }
                }

            currentConfig =
                com.sza.fastmediasorter.data.ConnectionConfig(
                    id = configId,
                    name = bucketName,
                    serverAddress = "",
                    username = "",
                    password = "",
                    folderPath = "",
                    interval = 10,
                    lastUsed = System.currentTimeMillis(),
                    type = "LOCAL_STANDARD",
                    localUri = "",
                    localDisplayName = bucketName,
                )

            setupSortButtons()

            isLocalMode = true
            localStorageClient = LocalStorageClient(this@SortActivity)
            smbClient = SmbClient() // Still need for destinations

            withContext(Dispatchers.Main) {
                binding.connectionNameText.text = "Local: $bucketName"
                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "Loading local images...",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
            }

            loadImages()
            return
        }

        // Get config from database directly (for custom folders and SMB)
        val database =
            com
                .sza
                .fastmediasorter
                .data
                .AppDatabase
                .getDatabase(applicationContext)
        val config =
            withContext(Dispatchers.IO) {
                database.connectionConfigDao().getConfigById(configId)
            }

        Logger.d(
            "SortActivity",
            "Loaded config from DB: id=${config?.id}, type=${config?.type}, name=${config?.localDisplayName ?: config?.name}",
        )

        if (config == null) {
            Logger.e("SortActivity", "Config not found in DB for configId=$configId")
            withContext(Dispatchers.Main) {
                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "Connection not found",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                finish()
            }
            return
        }

        currentConfig = config
        Logger.d("SortActivity", "Set currentConfig: id=${currentConfig?.id}, type=${currentConfig?.type}")

        setupSortButtons()
        isLocalMode = config.type == "LOCAL_CUSTOM" || config.type == "LOCAL_STANDARD"

        if (isLocalMode) {
            localStorageClient = LocalStorageClient(this@SortActivity)
            smbClient = SmbClient() // Still need for destinations

            withContext(Dispatchers.Main) {
                binding.connectionNameText.text = "Local: ${config.localDisplayName}"
                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "Loading local images...",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
            }

            loadImages()
        } else {
            // SMB mode
            smbClient = SmbClient()
            localStorageClient = LocalStorageClient(this@SortActivity) // Needed for local destinations

            withContext(Dispatchers.Main) {
                binding.connectionNameText.text = "${config.name} - ${config.serverAddress}\\${config.folderPath}"
            }

            // Use default credentials if not set
            var username = config.username
            var password = config.password
            if (username.isEmpty()) {
                username = preferenceManager.getDefaultUsername()
            }
            if (password.isEmpty()) {
                password = preferenceManager.getDefaultPassword()
            }

            // Connect to SMB
            val connected =
                withContext(Dispatchers.IO) {
                    smbClient.connect(config.serverAddress, username, password)
                }

            if (!connected) {
                withContext(Dispatchers.Main) {
                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Failed to connect to ${config.serverAddress}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    finish()
                }
                return
            }

            withContext(Dispatchers.Main) {
                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "Connected. Loading images...",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
            }

            loadImages()
        }
    }

    /**
     * Refreshes the current file list and attempts to restore position.
     *
     * Useful when:
     * - New files have been added to the folder
     * - Files have been deleted externally
     * - User wants to see updated folder contents
     *
     * Preserves current file position by filename when possible,
     * falling back to first file if current file is no longer available.
     */
    private fun refreshFileList() {
        Toast.makeText(this, "Refreshing file list...", Toast.LENGTH_SHORT).show()

        // Save current file name to restore position after refresh
        val currentFileName =
            if (imageFiles.isNotEmpty() && currentIndex < imageFiles.size) {
                imageFiles[currentIndex].substringAfterLast('/')
            } else {
                null
            }

        loadImages()

        // Try to restore position to same file
        if (currentFileName != null) {
            lifecycleScope.launch {
                kotlinx.coroutines.delay(500) // Wait for loadImages to complete
                val newIndex = imageFiles.indexOfFirst { it.substringAfterLast('/') == currentFileName }
                if (newIndex >= 0) {
                    currentIndex = newIndex
                    loadMedia()
                }
            }
        }
    }

    /**
     * Loads the list of media files for the current connection.
     *
     * Retrieves files from either:
     * - Local storage using MediaStore or SAF (Storage Access Framework)
     * - SMB network shares using custom SMB client
     *
     * Features:
     * - Progress indication for large local folders
     * - Video filtering based on user preferences
     * - Size limits for video files
     * - Alphabetical sorting of results
     * - Error handling with user feedback
     */
    private fun loadImages() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                currentConfig?.let { config ->
                    val isVideoEnabled = preferenceManager.isVideoEnabled()
                    val maxVideoSizeMb = preferenceManager.getMaxVideoSizeMb()

                    val files =
                        if (isLocalMode) {
                            val localUri = if (!config.localUri.isNullOrEmpty()) Uri.parse(config.localUri) else null
                            val bucketName = config.localDisplayName?.ifEmpty { null }
                            Logger.d(
                                "SortActivity",
                                "Loading local files: bucketName='$bucketName', localUri='$localUri', isVideoEnabled=$isVideoEnabled",
                            )

                            // Show scan progress for SAF folders (user-selected folders)
                            val showProgress = localUri != null && bucketName == null
                            if (showProgress) {
                                withContext(Dispatchers.Main) {
                                    binding.scanProgressLayout.visibility = View.VISIBLE
                                    binding.scanProgressBar.progress = 0
                                    binding.scanProgressText.text = "0 / 0 files"
                                    binding.scanProgressDetail.text = "Initializing..."
                                }
                            }

                            val imageInfoList =
                                localStorageClient?.getImageFiles(
                                    folderUri = localUri,
                                    bucketName = bucketName,
                                    isVideoEnabled = isVideoEnabled,
                                    maxVideoSizeMb = maxVideoSizeMb,
                                    batchSize = 50,
                                    // Process in smaller batches for better responsiveness
                                    progressCallback =
                                        if (showProgress) {
                                            object : LocalStorageClient.ScanProgressCallback {
                                                override fun onProgress(
                                                    scannedCount: Int,
                                                    totalCount: Int,
                                                    currentBatch: Int,
                                                ) {
                                                    lifecycleScope.launch(Dispatchers.Main) {
                                                        val progressPercent =
                                                            if (totalCount > 0) {
                                                                (scannedCount * 100) / totalCount
                                                            } else {
                                                                0
                                                            }
                                                        binding.scanProgressBar.progress = progressPercent
                                                        binding.scanProgressText.text =
                                                            "$scannedCount / $totalCount files"
                                                        binding.scanProgressDetail.text =
                                                            "Processing batch $currentBatch..."
                                                    }
                                                }

                                                override fun onComplete(
                                                    totalFiles: Int,
                                                    durationMs: Long,
                                                ) {
                                                    lifecycleScope.launch(Dispatchers.Main) {
                                                        binding.scanProgressLayout.visibility = View.GONE
                                                        Logger
                                                            .d("SortActivity", "Scan completed: $totalFiles files in ${durationMs}ms")
                                                    }
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                ) ?: emptyList()

                            Logger.d("SortActivity", "Found ${imageInfoList.size} local files:")
                            imageInfoList.forEachIndexed { index, info ->
                                Logger.d(
                                    "SortActivity",
                                    "  [$index] ${info.name} (${MediaUtils.formatFileSize(info.size)})",
                                )
                            }
                            imageInfoList.map { it.uri.toString() }
                        } else {
                            val result =
                                smbClient.getImageFiles(
                                    config.serverAddress,
                                    config.folderPath,
                                    isVideoEnabled,
                                    maxVideoSizeMb,
                                )
                            if (result.errorMessage != null) {
                                withContext(Dispatchers.Main) {
                                    com.sza.fastmediasorter.utils.ErrorDialogHelper.showErrorWithCopy(
                                        this@SortActivity,
                                        "Connection Error",
                                        result.errorMessage,
                                    ) {
                                        finish()
                                    }
                                }
                                return@launch
                            }
                            result.files
                        }
                    imageFiles.clear()
                    imageFiles.addAll(files.sorted()) // ABC order

                    withContext(Dispatchers.Main) {
                        if (imageFiles.isNotEmpty()) {
                            currentIndex = 0
                            loadMedia()
                        } else {
                            android
                                .widget
                                .Toast
                                .makeText(
                                    this@SortActivity,
                                    "No media found in folder",
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Hide scan progress if it was shown
                    binding.scanProgressLayout.visibility = View.GONE

                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Error loading images: ${e.message}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    private fun showVideoErrorDialog(error: androidx.media3.common.PlaybackException) {
        val currentFile =
            if (imageFiles.isNotEmpty() && currentIndex < imageFiles.size) {
                imageFiles[currentIndex]
            } else {
                "Unknown"
            }

        val errorDetails = StringBuilder()
        errorDetails.append("=== VIDEO PLAYBACK ERROR ===\n")
        errorDetails.append(
            "Date: ${java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US,
            ).format(java.util.Date())}\n\n",
        )

        errorDetails.append("=== FILE INFO ===\n")
        errorDetails.append("File: ${currentFile.substringAfterLast('/')}\n")
        errorDetails.append("Path: $currentFile\n")
        errorDetails.append("Source: ${if (isLocalMode) "Local Storage" else "SMB Network"}\n\n")

        errorDetails.append("=== ERROR DETAILS ===\n")
        errorDetails.append("Exception: ${error.javaClass.simpleName}\n")
        errorDetails.append("Error Code: ${error.errorCode}\n")
        errorDetails.append("Message: ${error.message ?: "No message"}\n")
        errorDetails.append("Cause: ${error.cause?.javaClass?.simpleName ?: "None"}\n")
        if (error.cause != null) {
            errorDetails.append("Cause Message: ${error.cause?.message ?: "No message"}\n")
        }
        errorDetails.append("\n")

        errorDetails.append("=== PLAYBACK STATE ===\n")
        errorDetails.append("Video Enabled: ${preferenceManager.isVideoEnabled()}\n")
        errorDetails.append("Max Video Size: ${preferenceManager.getMaxVideoSizeMb()} MB\n")
        errorDetails.append("Play Video Till End: ${preferenceManager.isPlayVideoTillEnd()}\n")
        errorDetails.append("\n")

        errorDetails.append("=== SYSTEM INFO ===\n")
        errorDetails.append("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n")
        errorDetails.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
        errorDetails.append("\n")

        errorDetails.append("=== POSSIBLE CAUSES ===\n")
        when {
            error.message?.contains("codec", ignoreCase = true) == true -> {
                errorDetails.append("• Unsupported video codec\n")
                errorDetails.append("• Try converting to MP4 with H.264 codec\n")
            }
            error.message?.contains("source", ignoreCase = true) == true -> {
                errorDetails.append("• File not accessible\n")
                errorDetails.append("• Network connection issue (for SMB)\n")
                errorDetails.append("• File may be corrupted\n")
            }
            error.message?.contains("timeout", ignoreCase = true) == true -> {
                errorDetails.append("• Network timeout (for SMB)\n")
                errorDetails.append("• File too large or slow connection\n")
            }
            else -> {
                errorDetails.append("• Unsupported video format\n")
                errorDetails.append("• Corrupted video file\n")
                errorDetails.append("• Insufficient device resources\n")
            }
        }
        errorDetails.append("\n")

        errorDetails.append("=== RECOMMENDATIONS ===\n")
        errorDetails.append("1. Try playing file with another app\n")
        errorDetails.append("2. Convert to MP4 (H.264 codec)\n")
        errorDetails.append("3. Reduce video resolution\n")
        errorDetails.append("4. Check file is not corrupted\n")
        errorDetails.append("5. For SMB: ensure stable network\n")
        errorDetails.append("6. For SMB connection errors: reconnect to share\n")
        errorDetails.append("\n=== DEBUG INFO ===\n")
        errorDetails.append(
            "SMB Context Available: ${if (isLocalMode) "N/A (Local)" else smbClient.getContext() != null}\n",
        )
        errorDetails.append("Full Path: $currentFile\n")

        com.sza.fastmediasorter.ui.dialogs.DiagnosticDialog.show(
            this,
            "Video Playback Error",
            errorDetails.toString(),
            false,
        )
    }

    private fun showVideoValidationError(
        fileName: String,
        validationResult: com.sza.fastmediasorter.utils.MediaValidator.ValidationResult,
    ) {
        val errorDetails = StringBuilder()
        errorDetails.append("=== VIDEO FILE VALIDATION ERROR ===\n")
        errorDetails.append(
            "Date: ${java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US,
            ).format(java.util.Date())}\n\n",
        )

        errorDetails.append("=== FILE INFO ===\n")
        errorDetails.append("File: $fileName\n")
        errorDetails.append("Extension: .${fileName.substringAfterLast('.', "unknown")}\n")
        errorDetails.append("Source: ${if (isLocalMode) "Local Storage" else "SMB Network"}\n\n")

        errorDetails.append("=== PRE-VALIDATION RESULT ===\n")
        errorDetails.append("Status: FAILED (file rejected before playback attempt)\n")
        errorDetails.append("Error Type: ${validationResult.errorType}\n")
        errorDetails.append("Details: ${validationResult.errorDetails}\n\n")

        errorDetails.append("=== WHY THIS HAPPENED ===\n")
        errorDetails.append("This file was rejected INSTANTLY (< 100ms) by header validation.\n")
        errorDetails.append("No playback was attempted, avoiding errors and delays.\n\n")

        errorDetails.append("=== DIAGNOSIS ===\n")
        when {
            validationResult.errorType?.contains("Size mismatch", ignoreCase = true) == true -> {
                errorDetails.append("▶ FILE TRUNCATED OR CORRUPTED\n")
                errorDetails.append("  The file header declares one size, but actual file is different.\n")
                errorDetails.append("  This causes ArrayIndexOutOfBoundsException during playback.\n")
                errorDetails.append("  File was likely damaged during transfer or incomplete download.\n\n")
            }
            validationResult.errorType?.contains("Invalid", ignoreCase = true) == true -> {
                errorDetails.append("▶ INVALID FILE FORMAT\n")
                errorDetails.append("  File header doesn't match expected format signature.\n")
                errorDetails.append("  File may be renamed, corrupted, or not a valid video.\n\n")
            }
            validationResult.errorType?.contains("Truncated", ignoreCase = true) == true -> {
                errorDetails.append("▶ FILE TOO SMALL\n")
                errorDetails.append("  File doesn't have enough bytes for a valid header.\n")
                errorDetails.append("  File is incomplete or severely damaged.\n\n")
            }
        }

        errorDetails.append("=== RECOMMENDATION ===\n")
        errorDetails.append("${validationResult.recommendation}\n\n")

        errorDetails.append("=== ACTIONS TAKEN ===\n")
        errorDetails.append("✓ File validation failed\n")
        errorDetails.append("✓ No playback attempted\n")
        errorDetails.append("✓ Error logged for review\n")

        com.sza.fastmediasorter.ui.dialogs.DiagnosticDialog.show(
            this,
            "Video Validation Failed",
            errorDetails.toString(),
            false,
        )
    }

    private fun showImageValidationError(
        fileName: String,
        validationResult: com.sza.fastmediasorter.utils.MediaValidator.ValidationResult,
    ) {
        val errorDetails = StringBuilder()
        errorDetails.append("=== IMAGE FILE VALIDATION ERROR ===\n")
        errorDetails.append(
            "Date: ${java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss",
                java.util.Locale.US,
            ).format(java.util.Date())}\n\n",
        )

        errorDetails.append("=== FILE INFO ===\n")
        errorDetails.append("File: $fileName\n")
        errorDetails.append("Extension: .${fileName.substringAfterLast('.', "unknown")}\n")
        errorDetails.append("Source: ${if (isLocalMode) "Local Storage" else "SMB Network"}\n\n")

        errorDetails.append("=== PRE-VALIDATION RESULT ===\n")
        errorDetails.append("Status: FAILED (file rejected before loading)\n")
        errorDetails.append("Error Type: ${validationResult.errorType}\n")
        errorDetails.append("Details: ${validationResult.errorDetails}\n\n")

        errorDetails.append("=== WHY THIS HAPPENED ===\n")
        errorDetails.append("This file was rejected INSTANTLY (< 100ms) by header validation.\n")
        errorDetails.append("No loading attempt was made.\n\n")

        errorDetails.append("=== DIAGNOSIS ===\n")
        when {
            validationResult.errorType?.contains("Size mismatch", ignoreCase = true) == true -> {
                errorDetails.append("▶ FILE TRUNCATED OR CORRUPTED\n")
                errorDetails.append("  The file header declares one size, but actual file is different.\n")
                errorDetails.append("  File was likely damaged during transfer or incomplete download.\n\n")
            }
            validationResult.errorType?.contains("Invalid", ignoreCase = true) == true -> {
                errorDetails.append("▶ INVALID FILE FORMAT\n")
                errorDetails.append("  File header doesn't match expected format signature.\n")
                errorDetails.append("  File may be renamed, corrupted, or not a valid image.\n\n")
            }
            validationResult.errorType?.contains("Truncated", ignoreCase = true) == true -> {
                errorDetails.append("▶ FILE TOO SMALL\n")
                errorDetails.append("  File doesn't have enough bytes for a valid header.\n")
                errorDetails.append("  File is incomplete or severely damaged.\n\n")
            }
            validationResult.errorType?.contains("SOI marker", ignoreCase = true) == true -> {
                errorDetails.append("▶ INVALID JPEG SIGNATURE\n")
                errorDetails.append("  JPEG files must start with 0xFFD8 marker.\n")
                errorDetails.append("  File is not a valid JPEG or is corrupted.\n\n")
            }
            validationResult.errorType?.contains("PNG", ignoreCase = true) == true -> {
                errorDetails.append("▶ INVALID PNG SIGNATURE\n")
                errorDetails.append("  PNG files must start with specific 8-byte signature.\n")
                errorDetails.append("  File is not a valid PNG or is corrupted.\n\n")
            }
        }

        errorDetails.append("=== RECOMMENDATION ===\n")
        errorDetails.append("${validationResult.recommendation}\n\n")

        errorDetails.append("=== ACTIONS TAKEN ===\n")
        errorDetails.append("✓ File validation failed\n")
        errorDetails.append("✓ No loading attempted\n")
        errorDetails.append("✓ Fast rejection prevents decode errors\n")

        com.sza.fastmediasorter.ui.dialogs.DiagnosticDialog.show(
            this,
            "Image Validation Failed",
            errorDetails.toString(),
            false,
        )
    }

    private fun showNoFilesState() {
        // Hide all media views
        binding.imageView.visibility = View.GONE
        binding.playerView.visibility = View.GONE
        binding.videoLoadingLayout.visibility = View.GONE
        binding.copyProgressLayout.visibility = View.GONE

        // Show "No files" message
        binding.fileInfoText.text = "No files in this folder"
        binding.imageCounterText.text = "0 / 0"

        // Stop video player if running
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()

        Toast
            .makeText(
                this,
                "No more files in folder",
                Toast.LENGTH_SHORT,
            ).show()
    }

    /**
     * Loads and displays the media file at the current index.
     *
     * Determines media type (image/video) and calls appropriate loading method:
     * - Images: loadImage() with Glide for display
     * - Videos: loadVideo() with ExoPlayer for playback
     *
     * Updates UI elements:
     * - Shows/hides appropriate view components
     * - Updates file counter and information display
     * - Configures video control zones
     */
    private fun loadMedia() {
        if (imageFiles.isEmpty()) {
            showNoFilesState()
            return
        }

        // Cancel any ongoing preload
        preloadJob?.cancel()

        val mediaUrl = imageFiles[currentIndex]

        // Determine if current media is video - get actual filename for local files
        val actualFileName =
            if (isLocalMode) {
                try {
                    val uri = Uri.parse(mediaUrl)
                    localStorageClient?.getFileInfo(uri)?.name ?: mediaUrl.substringAfterLast('/')
                } catch (e: Exception) {
                    Logger.w("SortActivity", "Failed to get file info for $mediaUrl: ${e.message}")
                    mediaUrl.substringAfterLast('/')
                }
            } else {
                mediaUrl.substringAfterLast('/')
            }

        isCurrentMediaVideo = MediaUtils.isVideo(actualFileName)
        Logger.d(
            "SortActivity",
            "loadMedia() - mediaUrl: $mediaUrl, actualFileName: $actualFileName, isVideo: $isCurrentMediaVideo",
        )

        // Show/hide appropriate view
        if (isCurrentMediaVideo) {
            Logger.d("SortActivity", "▶▶▶ LOADING VIDEO: hiding imageView, showing playerView")
            binding.imageView.visibility = View.GONE
            binding.playerView.visibility = View.VISIBLE
            Logger.d("SortActivity", "  PlayerView visibility set to VISIBLE, imageView set to GONE")
            loadVideo(mediaUrl)
        } else {
            Logger.d("SortActivity", "▶▶▶ LOADING IMAGE: hiding playerView, showing imageView")
            binding.playerView.visibility = View.GONE
            binding.imageView.visibility = View.VISIBLE
            Logger.d("SortActivity", "  ImageView visibility set to VISIBLE, playerView set to GONE")
            loadImage(mediaUrl)
        }

        updateImageCounter()
        updateVideoControlZone()
    }

    private fun updateVideoControlZone() {
        // Post to ensure layout is complete
        binding.root.post {
            // Get the actual media display area
            val mediaAreaHeight = binding.fileInfoText.top

            // Set guideline at 2/3 from top (bottom 1/3 for video controls)
            val controlZoneStart = (mediaAreaHeight * 2) / 3

            val params = binding.videoControlGuideline.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.guideBegin = controlZoneStart
            binding.videoControlGuideline.layoutParams = params

            // Debug log
            Logger.d("VideoControl", "Media height: $mediaAreaHeight, Control zone starts at: $controlZoneStart")
        }
    }

    /**
     * Loads and displays an image file with validation and optimization.
     *
     * For SMB images: Performs fast pre-validation before download
     * For local images: Loads directly using URI
     *
     * Features:
     * - File validation to prevent loading corrupted images
     * - Preloaded data usage for instant display
     * - Progress indication for slow loads
     * - GIF animation support
     * - Error handling with fallback to next file
     *
     * @param imageUrl The URL/path of the image file to load
     */
    private fun loadImage(imageUrl: String) {
        if (imageFiles.isEmpty()) return

        // FAST PRE-VALIDATION for SMB images
        if (!isLocalMode) {
            lifecycleScope.launch(Dispatchers.IO) {
                val fileName = imageUrl.substringAfterLast('/')
                val extension = imageUrl.substringAfterLast('.', "").lowercase()
                Logger.d("SortActivity", "▶ Pre-validating SMB image file: $fileName")
                val validationStartTime = System.currentTimeMillis()
                val validationResult =
                    com.sza.fastmediasorter.utils.MediaValidator.validateSmbMedia(
                        imageUrl,
                        smbClient.getContext()?.let { smbClient },
                        isVideo = false,
                    )
                val validationTime = System.currentTimeMillis() - validationStartTime
                Logger.d("SortActivity", "  Validation completed in ${validationTime}ms")

                if (!validationResult.isValid) {
                    Logger.e("SortActivity", "═══════════════════════════════════════════")
                    Logger.e("SortActivity", "▶▶▶ IMAGE FILE VALIDATION FAILED ▶▶▶")
                    Logger.e("SortActivity", "  File: $fileName")
                    Logger.e("SortActivity", "  Extension: .$extension")
                    Logger.e("SortActivity", "  Error Type: ${validationResult.errorType}")
                    Logger.e("SortActivity", "  Details: ${validationResult.errorDetails}")
                    Logger.e("SortActivity", "  SKIPPING WITHOUT ATTEMPTING LOAD")
                    Logger.e("SortActivity", "═══════════════════════════════════════════")

                    withContext(Dispatchers.Main) {
                        if (preferenceManager.isShowVideoErrorDetails()) {
                            showImageValidationError(fileName, validationResult)
                        } else {
                            Toast
                                .makeText(
                                    this@SortActivity,
                                    "Image file corrupted: $fileName",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }

                        handleMediaError(
                            imageUrl,
                            "Image Validation",
                            validationResult.errorDetails ?: "File corrupted",
                        )
                    }
                    return@launch
                }

                // Continue with normal loading if validation passed
                loadImageAfterValidation(imageUrl)
            }
        } else {
            // Local mode - load directly
            loadImageAfterValidation(imageUrl)
        }
    }

    private fun loadImageAfterValidation(imageUrl: String) {
        Logger.d(
            "SortActivity",
            "loadImageAfterValidation() - imageUrl: $imageUrl, nextImageIndex: $nextImageIndex, currentIndex: $currentIndex",
        )

        // Check if we have preloaded this image
        if (nextImageIndex == currentIndex && nextImageData != null) {
            Logger.d("SortActivity", "▶▶▶ USING PRELOAD PATH: preloaded data available for index $currentIndex")
            // Use preloaded data
            val imageBytes = nextImageData
            nextImageData = null
            nextImageIndex = -1

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val fileInfo =
                        if (isLocalMode) {
                            Logger.d("SortActivity", "Getting file info for preloaded local file: $imageUrl")
                            val uri = Uri.parse(imageUrl)
                            localStorageClient?.getFileInfo(uri)?.let {
                                SmbClient.FileInfo(
                                    name = it.name,
                                    sizeKB = (it.size / 1024),
                                    modifiedDate = it.dateModified,
                                )
                            }
                        } else {
                            smbClient.getFileInfo(imageUrl)
                        }

                    withContext(Dispatchers.Main) {
                        val fileName = fileInfo?.name ?: imageUrl.substringAfterLast('/')
                        val isGif = fileName.substringAfterLast('.', "").lowercase() == "gif"

                        if (isGif) {
                            Logger.d("SortActivity", "▶▶▶ PRELOAD PATH: LOADING ANIMATED GIF")
                            // Load GIF with animation
                            Glide
                                .with(this@SortActivity)
                                .asGif()
                                .load(imageBytes)
                                .error(R.drawable.error_placeholder)
                                .listener(
                                    object : RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable> {
                                        override fun onLoadFailed(
                                            e: GlideException?,
                                            model: Any?,
                                            target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>,
                                            isFirstResource: Boolean,
                                        ): Boolean {
                                            Logger.e(
                                                "SortActivity",
                                                "PRELOAD PATH: Failed to load GIF: ${e?.message}",
                                                e,
                                            )
                                            handleMediaError(imageUrl, "GIF Load", e?.message ?: "Glide load failed")
                                            return false
                                        }

                                        override fun onResourceReady(
                                            resource: com.bumptech.glide.load.resource.gif.GifDrawable,
                                            model: Any,
                                            target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>?,
                                            dataSource: DataSource,
                                            isFirstResource: Boolean,
                                        ): Boolean {
                                            Logger.d(
                                                "SortActivity",
                                                "PRELOAD PATH: GIF loaded successfully: ${resource.intrinsicWidth}x${resource.intrinsicHeight}, ${resource.frameCount} frames",
                                            )
                                            consecutiveErrors = 0
                                            return false
                                        }
                                    },
                                ).into(binding.imageView)
                        } else {
                            Logger.d("SortActivity", "▶▶▶ PRELOAD PATH: LOADING STATIC IMAGE")
                            // Load static image
                            Glide
                                .with(this@SortActivity)
                                .asBitmap()
                                .load(imageBytes)
                                .error(R.drawable.error_placeholder)
                                .listener(
                                    object : RequestListener<android.graphics.Bitmap> {
                                        override fun onLoadFailed(
                                            e: GlideException?,
                                            model: Any?,
                                            target: Target<android.graphics.Bitmap>,
                                            isFirstResource: Boolean,
                                        ): Boolean {
                                            Logger.e(
                                                "SortActivity",
                                                "PRELOAD PATH: Failed to load image: ${e?.message}",
                                                e,
                                            )
                                            handleMediaError(imageUrl, "Image Load", e?.message ?: "Glide load failed")
                                            return false
                                        }

                                        override fun onResourceReady(
                                            resource: android.graphics.Bitmap,
                                            model: Any,
                                            target: Target<android.graphics.Bitmap>?,
                                            dataSource: DataSource,
                                            isFirstResource: Boolean,
                                        ): Boolean {
                                            Logger.d(
                                                "SortActivity",
                                                "PRELOAD PATH: Image loaded successfully: ${resource.width}x${resource.height}",
                                            )
                                            consecutiveErrors = 0
                                            return false
                                        }
                                    },
                                ).into(binding.imageView)
                        }

                        updateFileInfo(fileInfo)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // Start preloading next image
                preloadNextImage()
            }
        } else {
            Logger.d("SortActivity", "▶▶▶ USING NORMAL PATH: no preloaded data available")
            // Load normally
            lifecycleScope.launch(Dispatchers.IO) {
                var toastShown = false
                var loadingToast: Toast? = null

                // Schedule toast if loading takes >1 second
                val toastJob =
                    launch(Dispatchers.IO) {
                        delay(1000)
                        val fileInfo =
                            try {
                                if (isLocalMode) {
                                    localStorageClient?.getFileInfo(Uri.parse(imageUrl))?.let {
                                        SmbClient.FileInfo(
                                            name = it.name,
                                            sizeKB = (it.size / 1024),
                                            modifiedDate = it.dateModified,
                                        )
                                    }
                                } else {
                                    smbClient.getFileInfo(imageUrl)
                                }
                            } catch (e: Exception) {
                                null
                            }

                        withContext(Dispatchers.Main) {
                            val sizeText = fileInfo?.let { "${it.sizeKB}KB" } ?: "..."
                            loadingToast =
                                Toast.makeText(
                                    this@SortActivity,
                                    "Loading... $sizeText",
                                    Toast.LENGTH_SHORT,
                                )
                            loadingToast?.show()
                            toastShown = true
                        }
                    }

                try {
                    val imageBytes =
                        if (isLocalMode) {
                            localStorageClient?.downloadImage(Uri.parse(imageUrl))
                        } else {
                            smbClient.downloadImage(imageUrl)
                        }

                    val fileInfo =
                        if (isLocalMode) {
                            Logger.d("SortActivity", "Getting file info for local file: $imageUrl")
                            val uri = Uri.parse(imageUrl)
                            Logger.d("SortActivity", "Parsed URI: $uri")

                            val localInfo = localStorageClient?.getFileInfo(uri)
                            Logger.d(
                                "SortActivity",
                                "LocalStorageClient returned: ${if (localInfo == null) "NULL" else "name=${localInfo.name}, size=${localInfo.size}"}",
                            )

                            localInfo?.let {
                                SmbClient.FileInfo(
                                    name = it.name,
                                    sizeKB = (it.size / 1024),
                                    modifiedDate = it.dateModified,
                                )
                            }
                        } else {
                            smbClient.getFileInfo(imageUrl)
                        }

                    // Cancel toast timer if still waiting
                    toastJob.cancel()

                    withContext(Dispatchers.Main) {
                        // Hide toast if it was shown
                        if (toastShown) {
                            loadingToast?.cancel()
                        }

                        if (imageBytes != null) {
                            val fileName = fileInfo?.name ?: imageUrl.substringAfterLast('/')
                            val isGif = fileName.substringAfterLast('.', "").lowercase() == "gif"
                            Logger.d(
                                "SortActivity",
                                "▶ NORMAL PATH: Loading image: fileName='$fileName', isGif=$isGif, hasPreloadedData=${imageBytes != null}",
                            )

                            if (isGif) {
                                Logger.d("SortActivity", "▶▶▶ NORMAL PATH: LOADING ANIMATED GIF")
                                // Load GIF with animation
                                Glide
                                    .with(this@SortActivity)
                                    .asGif()
                                    .load(imageBytes)
                                    .error(R.drawable.error_placeholder)
                                    .listener(
                                        object : RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable> {
                                            override fun onLoadFailed(
                                                e: GlideException?,
                                                model: Any?,
                                                target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>,
                                                isFirstResource: Boolean,
                                            ): Boolean {
                                                Logger.e(
                                                    "SortActivity",
                                                    "NORMAL PATH: Failed to load GIF: ${e?.message}",
                                                    e,
                                                )
                                                handleMediaError(
                                                    imageUrl,
                                                    "GIF Load",
                                                    e?.message ?: "Glide load failed",
                                                )
                                                return false
                                            }

                                            override fun onResourceReady(
                                                resource: com.bumptech.glide.load.resource.gif.GifDrawable,
                                                model: Any,
                                                target: Target<com.bumptech.glide.load.resource.gif.GifDrawable>?,
                                                dataSource: DataSource,
                                                isFirstResource: Boolean,
                                            ): Boolean {
                                                Logger.d(
                                                    "SortActivity",
                                                    "NORMAL PATH: GIF loaded successfully: ${resource.intrinsicWidth}x${resource.intrinsicHeight}, ${resource.frameCount} frames",
                                                )
                                                consecutiveErrors = 0
                                                return false
                                            }
                                        },
                                    ).into(binding.imageView)
                            } else {
                                Logger.d("SortActivity", "▶▶▶ NORMAL PATH: LOADING STATIC IMAGE")
                                // Load static image
                                Glide
                                    .with(this@SortActivity)
                                    .asBitmap() // Static images only
                                    .load(imageBytes)
                                    .error(R.drawable.error_placeholder)
                                    .listener(
                                        object : RequestListener<android.graphics.Bitmap> {
                                            override fun onLoadFailed(
                                                e: GlideException?,
                                                model: Any?,
                                                target: Target<android.graphics.Bitmap>,
                                                isFirstResource: Boolean,
                                            ): Boolean {
                                                Logger.e(
                                                    "SortActivity",
                                                    "NORMAL PATH: Failed to load image: ${e?.message}",
                                                    e,
                                                )
                                                handleMediaError(
                                                    imageUrl,
                                                    "Image Load",
                                                    e?.message ?: "Glide load failed",
                                                )
                                                return false
                                            }

                                            override fun onResourceReady(
                                                resource: android.graphics.Bitmap,
                                                model: Any,
                                                target: Target<android.graphics.Bitmap>?,
                                                dataSource: DataSource,
                                                isFirstResource: Boolean,
                                            ): Boolean {
                                                Logger.d(
                                                    "SortActivity",
                                                    "NORMAL PATH: Image loaded successfully: ${resource.width}x${resource.height}",
                                                )
                                                consecutiveErrors = 0
                                                return false
                                            }
                                        },
                                    ).into(binding.imageView)
                            }
                            updateFileInfo(fileInfo)
                        } else {
                            handleMediaError(imageUrl, "Image Download", "No data received")
                        }
                    }
                } catch (e: Exception) {
                    toastJob.cancel()
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        if (toastShown) {
                            loadingToast?.cancel()
                        }
                        handleMediaError(imageUrl, "Image Load", e.message ?: "Unknown error")
                    }
                }

                // Start preloading next image
                preloadNextImage()
            }
        }
    }

    /**
     * Loads and plays a video file with comprehensive validation and error handling.
     *
     * Process:
     * 1. Fast pre-validation for SMB videos to detect corruption
     * 2. File info retrieval for UI display
     * 3. ExoPlayer setup with appropriate data source
     * 4. Playback initiation with proper state management
     *
     * Supports both local (URI-based) and SMB (custom data source) videos.
     * Provides detailed error diagnostics for troubleshooting.
     *
     * @param videoUrl The URL/path of the video file to load
     */
    private fun loadVideo(videoUrl: String) {
        Logger.d("SortActivity", "loadVideo() - Starting video playback for: $videoUrl")
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fileName = videoUrl.substringAfterLast('/')
                val extension = videoUrl.substringAfterLast('.', "").lowercase()

                // FAST PRE-VALIDATION: Check video file integrity BEFORE ExoPlayer
                if (!isLocalMode) {
                    Logger.d("SortActivity", "▶ Pre-validating SMB video file: $fileName")
                    val validationStartTime = System.currentTimeMillis()
                    val validationResult =
                        com.sza.fastmediasorter.utils.MediaValidator.validateSmbMedia(
                            videoUrl,
                            smbClient.getContext()?.let { smbClient },
                            isVideo = true,
                        )
                    val validationTime = System.currentTimeMillis() - validationStartTime
                    Logger.d("SortActivity", "  Validation completed in ${validationTime}ms")
                    Logger.d(
                        "SortActivity",
                        "  Validation result: isValid=${validationResult.isValid}, errorType=${validationResult.errorType}, errorDetails=${validationResult.errorDetails}",
                    )

                    if (!validationResult.isValid) {
                        Logger.e("SortActivity", "═══════════════════════════════════════════")
                        Logger.e("SortActivity", "▶▶▶ VIDEO FILE VALIDATION FAILED ▶▶▶")
                        Logger.e("SortActivity", "  File: $fileName")
                        Logger.e("SortActivity", "  Extension: .$extension")
                        Logger.e("SortActivity", "  Error Type: ${validationResult.errorType}")
                        Logger.e("SortActivity", "  Details: ${validationResult.errorDetails}")
                        Logger.e("SortActivity", "  Recommendation: ${validationResult.recommendation}")
                        Logger.e("SortActivity", "  SKIPPING WITHOUT ATTEMPTING PLAYBACK")
                        Logger.e("SortActivity", "═══════════════════════════════════════════")

                        withContext(Dispatchers.Main) {
                            binding.videoLoadingLayout.visibility = View.GONE

                            if (preferenceManager.isShowVideoErrorDetails()) {
                                showVideoValidationError(fileName, validationResult)
                            } else {
                                Toast
                                    .makeText(
                                        this@SortActivity,
                                        "Video file corrupted: $fileName",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }

                            handleMediaError(
                                videoUrl,
                                "Video Validation",
                                validationResult.errorDetails ?: "File corrupted",
                            )
                        }
                        return@launch
                    } else if (validationResult.errorDetails != null) {
                        Logger.w("SortActivity", "⚠ Validation warning: ${validationResult.errorDetails}")
                    }
                }

                // Get file info for display
                val fileInfo =
                    if (isLocalMode) {
                        localStorageClient?.getFileInfo(Uri.parse(videoUrl))?.let {
                            SmbClient.FileInfo(
                                name = it.name,
                                sizeKB = (it.size / 1024),
                                modifiedDate = it.dateModified,
                            )
                        }
                    } else {
                        smbClient.getFileInfo(videoUrl)
                    }

                withContext(Dispatchers.Main) {
                    try {
                        Logger.d("SortActivity", "▶▶▶ STARTING VIDEO LOAD: $videoUrl")
                        Logger.d("SortActivity", "  PlayerView visibility before: ${binding.playerView.visibility}")
                        Logger.d(
                            "SortActivity",
                            "  PlayerView size: ${binding.playerView.width}x${binding.playerView.height}",
                        )

                        // Stop current playback
                        exoPlayer?.stop()
                        exoPlayer?.clearMediaItems()

                        if (isLocalMode) {
                            Logger.d("SortActivity", "  Loading local video: $videoUrl")
                            // Local video - use URI directly
                            val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
                            exoPlayer?.setMediaItem(mediaItem)
                            exoPlayer?.prepare()
                            exoPlayer?.play()

                            Logger.d("SortActivity", "  Local video prepared and play() called")
                        } else {
                            Logger.d("SortActivity", "  Loading SMB video: $videoUrl")
                            // SMB video - need to recreate player with custom data source
                            val smbContext = smbClient.getContext()
                            if (smbContext != null) {
                                Logger.d("SortActivity", "  SMB context available, recreating player")
                                // Release old player
                                exoPlayer?.release()

                                // Create new player with SmbDataSource
                                val dataSourceFactory = SmbDataSourceFactory(smbClient)
                                exoPlayer =
                                    ExoPlayer
                                        .Builder(this@SortActivity)
                                        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory as androidx.media3.datasource.DataSource.Factory))
                                        .build()
                                binding.playerView.player = exoPlayer

                                Logger.d("SortActivity", "  New SMB player created and attached to PlayerView")

                                // Re-attach listener after recreating player
                                exoPlayer?.addListener(
                                    object : Player.Listener {
                                        override fun onPlaybackStateChanged(playbackState: Int) {
                                            Logger.d("SortActivity", "SMB Video playback state: $playbackState")
                                            when (playbackState) {
                                                Player.STATE_BUFFERING -> {
                                                    Logger.d("SortActivity", "SMB Video buffering")
                                                    binding.videoLoadingLayout.visibility = View.VISIBLE
                                                }
                                                Player.STATE_READY -> {
                                                    Logger.d("SortActivity", "SMB Video ready - should be playing now")
                                                    binding.videoLoadingLayout.visibility = View.GONE

                                                    val videoSize = exoPlayer?.videoSize
                                                    Logger.d(
                                                        "SortActivity",
                                                        "SMB Video size: ${videoSize?.width}x${videoSize?.height}",
                                                    )
                                                }
                                                Player.STATE_ENDED -> {
                                                    Logger.d("SortActivity", "SMB Video ended")
                                                    binding.videoLoadingLayout.visibility = View.GONE

                                                    // Auto-advance to next file if "Play video till end" is enabled (slideshow mode)
                                                    if (preferenceManager.isPlayVideoTillEnd() &&
                                                        imageFiles.isNotEmpty()
                                                    ) {
                                                        Logger.d(
                                                            "SortActivity",
                                                            "SMB Video ended, auto-advancing to next file (slideshow mode)",
                                                        )
                                                        currentIndex =
                                                            if (currentIndex < imageFiles.size - 1) {
                                                                currentIndex + 1
                                                            } else {
                                                                0 // Loop to first
                                                            }
                                                        loadMedia()
                                                    }
                                                }
                                            }
                                        }

                                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                                            Logger.d("SortActivity", "SMB Video player error: ${error.message}")
                                            binding.videoLoadingLayout.visibility = View.GONE
                                            handleMediaError(
                                                videoUrl,
                                                "Video Playback",
                                                error.message ?: "Playback error",
                                            )

                                            if (preferenceManager.isShowVideoErrorDetails()) {
                                                showVideoErrorDialog(error)
                                            } else {
                                                Toast
                                                    .makeText(
                                                        this@SortActivity,
                                                        "Video playback error: ${error.message}",
                                                        Toast.LENGTH_SHORT,
                                                    ).show()
                                            }
                                        }

                                        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                                            Logger.d(
                                                "SortActivity",
                                                "SMB Video size changed: ${videoSize.width}x${videoSize.height}",
                                            )
                                            super.onVideoSizeChanged(videoSize)
                                        }

                                        override fun onRenderedFirstFrame() {
                                            Logger.d("SortActivity", "SMB Video first frame rendered")
                                            super.onRenderedFirstFrame()
                                        }
                                    },
                                )

                                val mediaItem = MediaItem.fromUri(videoUrl)
                                exoPlayer?.setMediaItem(mediaItem)
                                exoPlayer?.prepare()
                                exoPlayer?.play()

                                Logger.d("SortActivity", "  SMB video prepared and play() called")
                                consecutiveErrors = 0
                            } else {
                                Logger.e("SortActivity", "  SMB context not available for video: $videoUrl")
                                handleMediaError(videoUrl, "Video Load", "SMB context not available")
                                return@withContext
                            }
                        }

                        updateFileInfo(fileInfo)
                        Logger.d("SortActivity", "▶▶▶ VIDEO LOAD COMPLETED: $videoUrl")
                    } catch (e: Exception) {
                        Logger.e("SortActivity", "Failed to load video: ${e.message}", e)
                        handleMediaError(videoUrl, "Video Load", e.message ?: "Unknown error")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    handleMediaError(videoUrl, "Video Load", e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun updateFileInfo(fileInfo: SmbClient.FileInfo?) {
        if (fileInfo == null) {
            binding.fileInfoText.text = "File info unavailable"
            return
        }

        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        val dateStr = dateFormat.format(java.util.Date(fileInfo.modifiedDate))

        binding.fileInfoText.text = "${fileInfo.name}  ${fileInfo.sizeKB} KB  $dateStr"
    }

    private fun updateImageCounter() {
        val total = imageFiles.size
        val current = currentIndex + 1
        binding.imageCounterText.text = "$current / $total"
    }

    /**
     * Preloads the next image in the background for smooth transitions.
     *
     * Downloads and caches the next image data to enable instant display
     * when user navigates forward. This optimization eliminates loading
     * delays during sorting operations.
     *
     * Silently fails if preloading encounters errors to avoid interrupting
     * the main sorting workflow.
     */
    private fun preloadNextImage() {
        if (imageFiles.isEmpty()) return

        val nextIndex =
            if (currentIndex < imageFiles.size - 1) {
                currentIndex + 1
            } else {
                0 // Loop to first
            }

        preloadJob =
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val nextImageUrl = imageFiles[nextIndex]
                    val imageBytes =
                        if (isLocalMode) {
                            localStorageClient?.downloadImage(Uri.parse(nextImageUrl))
                        } else {
                            smbClient.downloadImage(nextImageUrl)
                        }

                    if (imageBytes != null) {
                        nextImageData = imageBytes
                        nextImageIndex = nextIndex
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Silently fail preload
                }
            }
    }

    /**
     * Initiates deletion of the currently displayed media file.
     *
     * Shows confirmation dialog if user preference requires it,
     * otherwise proceeds directly to deletion. Handles both local
     * and SMB file deletion with appropriate permission management.
     */
    private fun deleteCurrentImage() {
        if (imageFiles.isEmpty()) return

        val currentImageUrl = imageFiles[currentIndex]

        // Check if confirmation is required
        if (preferenceManager.isConfirmDelete()) {
            // Show confirmation dialog
            android
                .app
                .AlertDialog
                .Builder(this)
                .setTitle("Delete Media")
                .setMessage("Are you sure you want to delete this media file?")
                .setPositiveButton("Delete") { _, _ ->
                    performDelete(currentImageUrl)
                }.setNegativeButton("Cancel", null)
                .show()
        } else {
            // Delete without confirmation
            performDelete(currentImageUrl)
        }
    }

    private fun performDelete(imageUrl: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                binding.progressText.text = "Deleting..."
                binding.copyProgressLayout.visibility = View.VISIBLE
            }

            try {
                if (isLocalMode) {
                    val uri = Uri.parse(imageUrl)

                    // Check if this is a SAF URI (user-selected folder) or MediaStore URI (standard folder)
                    val isSafUri = imageUrl.startsWith("content://com.android.externalstorage.documents/")

                    if (isSafUri) {
                        // SAF URI - use direct delete (user already granted persistent permissions)
                        Logger.d("SortActivity", "Direct delete for SAF URI: $imageUrl")
                        val deleted = localStorageClient?.deleteImage(uri) ?: false
                        handleDeleteResultLocal(deleted, imageUrl)
                    } else {
                        // MediaStore URI - use permission dialog for Android 11+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            withContext(Dispatchers.Main) {
                                binding.copyProgressLayout.visibility = View.GONE
                            }
                            deleteImageWithPermission(uri)
                            // Dialog will be shown, actual deletion in callback
                        } else {
                            // Direct delete for Android 10 and below
                            val deleted = localStorageClient?.deleteImage(uri) ?: false
                            handleDeleteResultLocal(deleted, imageUrl)
                        }
                    }
                } else {
                    // SMB delete
                    val deleteResult = smbClient.deleteFile(imageUrl)
                    handleDeleteResult(deleteResult, imageUrl)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.copyProgressLayout.visibility = View.GONE
                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Error: ${e.message}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    private suspend fun handleDeleteResultLocal(
        deleted: Boolean,
        imageUrl: String,
    ) {
        withContext(Dispatchers.Main) {
            binding.copyProgressLayout.visibility = View.GONE

            if (deleted) {
                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "File deleted",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()

                // Remove from list and load next
                imageFiles.remove(imageUrl)

                if (imageFiles.isEmpty()) {
                    showNoFilesState()
                    return@withContext
                }

                if (currentIndex >= imageFiles.size) {
                    currentIndex = imageFiles.size - 1
                }
                loadMedia()
            } else {
                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "Failed to delete file",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    private suspend fun handleDeleteResult(
        deleteResult: SmbClient.DeleteResult,
        imageUrl: String,
    ) {
        withContext(Dispatchers.Main) {
            binding.copyProgressLayout.visibility = View.GONE

            when (deleteResult) {
                is SmbClient.DeleteResult.Success -> {
                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "File deleted",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()

                    // Remove from list and load next
                    imageFiles.remove(imageUrl)

                    if (imageFiles.isEmpty()) {
                        showNoFilesState()
                        return@withContext
                    }

                    if (currentIndex >= imageFiles.size) {
                        currentIndex = imageFiles.size - 1
                    }
                    loadMedia()
                }
                is SmbClient.DeleteResult.FileNotFound -> {
                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "File not found",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
                is SmbClient.DeleteResult.SecurityError -> {
                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Access denied: ${deleteResult.message}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
                is SmbClient.DeleteResult.NetworkError -> {
                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Network error: ${deleteResult.message}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
                is SmbClient.DeleteResult.UnknownError -> {
                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Error: ${deleteResult.message}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    /**
     * Initiates renaming of the currently displayed media file.
     *
     * Shows an input dialog for the new filename and validates input.
     * Handles both local and SMB file renaming with appropriate
     * permission management for Android 11+ scoped storage.
     */
    private fun renameCurrentMedia() {
        if (imageFiles.isEmpty()) return

        val currentUrl = imageFiles[currentIndex]

        // Get real filename based on mode
        val currentFileName =
            if (isLocalMode) {
                val uri = Uri.parse(currentUrl)
                localStorageClient?.getFileInfo(uri)?.name ?: currentUrl.substringAfterLast('/')
            } else {
                currentUrl.substringAfterLast('/')
            }

        // Create input dialog
        val input = EditText(this)
        input.setText(currentFileName)
        input.selectAll()
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT

        android
            .app
            .AlertDialog
            .Builder(this)
            .setTitle("Rename Media")
            .setMessage("Enter new filename:")
            .setView(input)
            .setPositiveButton("Rename") { _, _ ->
                val newFileName = input.text.toString().trim()
                if (newFileName.isNotEmpty() && newFileName != currentFileName) {
                    performRename(currentUrl, newFileName)
                }
            }.setNegativeButton("Cancel", null)
            .show()
    }

    private fun performRename(
        oldUrl: String,
        newFileName: String,
    ) {
        // Validate filename
        val invalidChars = listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
        if (invalidChars.any { newFileName.contains(it) }) {
            android
                .widget
                .Toast
                .makeText(
                    this,
                    "Filename contains invalid characters: / \\ : * ? \" < > |",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                binding.progressText.text = "Renaming..."
                binding.copyProgressLayout.visibility = View.VISIBLE
            }

            try {
                val newUrl: String
                val success: Boolean

                if (isLocalMode) {
                    // Local storage rename
                    val oldUri = Uri.parse(oldUrl)

                    // Check if this is a SAF URI (user-selected folder) or MediaStore URI (standard folder)
                    val isSafUri = oldUrl.startsWith("content://com.android.externalstorage.documents/")

                    if (isSafUri) {
                        // SAF URI - use direct rename (user already granted persistent permissions)
                        Logger.d("SortActivity", "Direct rename for SAF URI: $oldUrl")
                        val result = localStorageClient?.renameFile(oldUri, newFileName)
                        success = result?.first ?: false
                        newUrl = result?.second ?: oldUrl
                        handleRenameResultLocal(success, newUrl, oldUrl)
                    } else {
                        // MediaStore URI - use permission dialog for Android 11+
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            withContext(Dispatchers.Main) {
                                binding.copyProgressLayout.visibility = View.GONE
                            }
                            renameWithPermission(oldUri, newFileName)
                            // Dialog will be shown, actual rename in callback
                            return@launch
                        } else {
                            // Direct rename for Android 10 and below
                            val result = localStorageClient?.renameFile(oldUri, newFileName)
                            success = result?.first ?: false
                            newUrl = result?.second ?: oldUrl
                            handleRenameResultLocal(success, newUrl, oldUrl)
                        }
                    }
                } else {
                    // SMB rename
                    val folderPath = oldUrl.substringBeforeLast('/')
                    newUrl = "$folderPath/$newFileName"

                    val renameResult = smbClient.renameFile(oldUrl, newUrl)

                    withContext(Dispatchers.Main) {
                        binding.copyProgressLayout.visibility = View.GONE

                        when (renameResult) {
                            is SmbClient.RenameResult.Success -> {
                                // Update the list with new URL
                                imageFiles[currentIndex] = newUrl

                                android
                                    .widget
                                    .Toast
                                    .makeText(
                                        this@SortActivity,
                                        "File renamed successfully",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()

                                // Reload current media with new name
                                loadMedia()
                            }
                            is SmbClient.RenameResult.SourceNotFound -> {
                                android
                                    .widget
                                    .Toast
                                    .makeText(
                                        this@SortActivity,
                                        "Source file not found",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                            }
                            is SmbClient.RenameResult.TargetExists -> {
                                android
                                    .widget
                                    .Toast
                                    .makeText(
                                        this@SortActivity,
                                        "File with this name already exists",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                            }
                            is SmbClient.RenameResult.SecurityError -> {
                                android
                                    .widget
                                    .Toast
                                    .makeText(
                                        this@SortActivity,
                                        "Access denied: ${renameResult.message}",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                            }
                            is SmbClient.RenameResult.NetworkError -> {
                                android
                                    .widget
                                    .Toast
                                    .makeText(
                                        this@SortActivity,
                                        "Network error: ${renameResult.message}",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                            }
                            is SmbClient.RenameResult.UnknownError -> {
                                android
                                    .widget
                                    .Toast
                                    .makeText(
                                        this@SortActivity,
                                        "Error: ${renameResult.message}",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                            }
                        }
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    binding.copyProgressLayout.visibility = View.GONE

                    if (success) {
                        // Update the list with new URL (for local mode)
                        imageFiles[currentIndex] = newUrl

                        android
                            .widget
                            .Toast
                            .makeText(
                                this@SortActivity,
                                "File renamed successfully",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()

                        // Reload current media with new name
                        loadMedia()
                    } else {
                        android
                            .widget
                            .Toast
                            .makeText(
                                this@SortActivity,
                                "Failed to rename file",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.copyProgressLayout.visibility = View.GONE
                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Error renaming file: ${e.message}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
                e.printStackTrace()
            }
        }
    }

    private suspend fun renameWithPermission(
        uri: Uri,
        newFileName: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Logger.d("SortActivity", "Requesting rename permission for Android 11+")

                val intentSender =
                    MediaStore
                        .createWriteRequest(
                            contentResolver,
                            listOf(uri),
                        ).intentSender

                pendingRenameUri = uri
                pendingNewFileName = newFileName
                val request = IntentSenderRequest.Builder(intentSender).build()

                withContext(Dispatchers.Main) {
                    renamePermissionLauncher.launch(request)
                }
            } catch (e: Exception) {
                Logger.e("SortActivity", "Error requesting rename permission", e)
                withContext(Dispatchers.Main) {
                    android
                        .widget
                        .Toast
                        .makeText(
                            this@SortActivity,
                            "Cannot request rename permission: ${e.message}",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                }
            }
        }
    }

    private suspend fun writeFileToStandardFolderWithPermission(
        fileName: String,
        data: ByteArray,
        folderName: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Create MediaStore entry
                val collection =
                    when (folderName) {
                        "Download" ->
                            android
                                .provider
                                .MediaStore
                                .Downloads
                                .EXTERNAL_CONTENT_URI
                        "Pictures" ->
                            android
                                .provider
                                .MediaStore
                                .Images
                                .Media
                                .EXTERNAL_CONTENT_URI
                        else -> {
                            Logger.e("SortActivity", "Unknown folder: $folderName")
                            return@withContext false
                        }
                    }

                val values =
                    android.content.ContentValues().apply {
                        put(
                            android
                                .provider
                                .MediaStore
                                .MediaColumns
                                .DISPLAY_NAME,
                            fileName,
                        )
                        put(
                            android
                                .provider
                                .MediaStore
                                .MediaColumns
                                .MIME_TYPE,
                            getMimeType(fileName),
                        )
                        put(
                            android
                                .provider
                                .MediaStore
                                .MediaColumns
                                .RELATIVE_PATH,
                            if (folderName ==
                                "Download"
                            ) {
                                android.os.Environment.DIRECTORY_DOWNLOADS
                            } else {
                                android
                                    .os
                                    .Environment
                                    .DIRECTORY_PICTURES
                            },
                        )

                        if (android
                                .os
                                .Build
                                .VERSION
                                .SDK_INT >=
                            android
                                .os
                                .Build
                                .VERSION_CODES
                                .Q
                        ) {
                            put(
                                android
                                    .provider
                                    .MediaStore
                                    .MediaColumns
                                    .IS_PENDING,
                                1,
                            )
                        }
                    }

                // Insert and get URI
                val newUri = contentResolver.insert(collection, values) ?: return@withContext false

                // Write data
                contentResolver.openOutputStream(newUri)?.use { output ->
                    output.write(data)
                    output.flush()
                }

                // Mark as ready
                if (android
                        .os
                        .Build
                        .VERSION
                        .SDK_INT >=
                    android
                        .os
                        .Build
                        .VERSION_CODES
                        .Q
                ) {
                    val readyValues =
                        android.content.ContentValues().apply {
                            put(
                                android
                                    .provider
                                    .MediaStore
                                    .MediaColumns
                                    .IS_PENDING,
                                0,
                            )
                        }
                    contentResolver.update(newUri, readyValues, null, null)
                }

                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private fun getMimeType(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            else -> "application/octet-stream"
        }

    private suspend fun handleWriteSuccess(
        fileName: String,
        data: ByteArray,
        folderName: String,
    ) {
        Logger.d("SortActivity", "User granted write permission, writing file: $fileName")

        val result = localStorageClient?.writeFileToStandardFolder(fileName, data, folderName)

        withContext(Dispatchers.Main) {
            if (result == true) {
                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "File copied successfully",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()

                // Jump to next image if setting enabled
                if (preferenceManager.isGoNextAfterCopy() && imageFiles.isNotEmpty()) {
                    currentIndex =
                        if (currentIndex < imageFiles.size - 1) {
                            currentIndex + 1
                        } else {
                            0
                        }
                    loadMedia()
                }
            } else {
                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "Failed to write file",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    private suspend fun handleRenameSuccess(
        uri: Uri,
        newFileName: String,
    ) {
        Logger.d("SortActivity", "User granted permission, renaming file: $uri to $newFileName")

        withContext(Dispatchers.Main) {
            binding.progressText.text = "Renaming..."
            binding.copyProgressLayout.visibility = View.VISIBLE
        }

        try {
            val result = localStorageClient?.renameFile(uri, newFileName)
            val success = result?.first ?: false
            val newUrl = result?.second ?: uri.toString()

            handleRenameResultLocal(success, newUrl, uri.toString())
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                binding.copyProgressLayout.visibility = View.GONE
                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "Error renaming file: ${e.message}",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
            }
            e.printStackTrace()
        }
    }

    private suspend fun handleRenameResultLocal(
        success: Boolean,
        newUrl: String,
        oldUrl: String,
    ) {
        withContext(Dispatchers.Main) {
            binding.copyProgressLayout.visibility = View.GONE

            if (success) {
                // Update the list with new URL
                val index = imageFiles.indexOf(oldUrl)
                if (index != -1) {
                    imageFiles[index] = newUrl
                }

                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "File renamed successfully",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()

                // Reload current media with new name
                loadMedia()
            } else {
                android
                    .widget
                    .Toast
                    .makeText(
                        this@SortActivity,
                        "Failed to rename file",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()

        // Stop media playback immediately
        exoPlayer?.playWhenReady = false

        // Cancel preload job
        preloadJob?.cancel()
        preloadJob = null

        Logger.d("SortActivity", "onStop: Media playback stopped, preload cancelled")
    }

    override fun onDestroy() {
        super.onDestroy()
        preloadJob?.cancel()
        exoPlayer?.release()
        exoPlayer = null
        try {
            smbClient.disconnect()
        } catch (e: Exception) {
            // Ignore disconnect errors on destroy
        }
    }

    /**
     * Handles media loading errors with progressive user feedback.
     *
     * Logs errors, tracks consecutive failures, and shows appropriate
     * user notifications. Displays snackbar for single errors and
     * detailed dialog for multiple consecutive failures.
     *
     * @param mediaUrl The URL of the media file that failed to load
     * @param errorType The category of error (e.g., "Image Load", "Video Load")
     * @param errorMessage Detailed error description
     */
    private fun handleMediaError(
        mediaUrl: String,
        errorType: String,
        errorMessage: String,
    ) {
        val fileName = mediaUrl.substringAfterLast('/')
        errorLog.add(MediaError(fileName, errorType, errorMessage))
        consecutiveErrors++

        lifecycleScope.launch(Dispatchers.Main) {
            if (consecutiveErrors >= maxConsecutiveErrors) {
                showErrorReportDialog()
            } else {
                Toast
                    .makeText(
                        this@SortActivity,
                        "⚠ $errorType error ($consecutiveErrors/${maxConsecutiveErrors})",
                        Toast.LENGTH_SHORT,
                    ).show()
            }
        }
    }

    /**
     * Displays a detailed error report dialog for multiple consecutive failures.
     *
     * Shows recent error history, statistics, and troubleshooting recommendations.
     * Provides options to continue, view full log, or exit the sorting session.
     * Resets error counters when user acknowledges the issues.
     */
    private fun showErrorReportDialog() {
        val recentErrors = errorLog.takeLast(10)
        val errorSummary =
            buildString {
                append("⚠️ MULTIPLE ERRORS DETECTED\n\n")
                append("Consecutive errors: $consecutiveErrors\n")
                append("Total errors: ${errorLog.size}\n\n")
                append("Recent errors:\n")
                append("━".repeat(40))
                append("\n\n")

                recentErrors.forEach { error ->
                    val time =
                        java
                            .text
                            .SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(error.timestamp))
                    append("[$time] ${error.errorType}\n")
                    append("File: ${error.fileName}\n")
                    append("Error: ${error.errorMessage}\n\n")
                }

                append("\nRecommendations:\n")
                append("• Check network connection\n")
                append("• Verify SMB server is accessible\n")
                append("• Check file permissions\n")
                append("• Some files may be corrupted\n")
            }

        android
            .app
            .AlertDialog
            .Builder(this)
            .setTitle("Media Loading Issues")
            .setMessage(errorSummary)
            .setPositiveButton("Continue") { dialog, _ ->
                consecutiveErrors = 0
                dialog.dismiss()
            }.setNegativeButton("View Full Log") { _, _ ->
                showFullErrorLog()
            }.setNeutralButton("Exit") { _, _ ->
                finish()
            }.setCancelable(false)
            .show()
    }

    /**
     * Displays the complete error log in a scrollable dialog.
     *
     * Shows all logged errors in chronological order with timestamps.
     * Provides options to close, copy to clipboard, or clear the log.
     * Resets error counters when dialog is dismissed.
     */
    private fun showFullErrorLog() {
        val fullLog =
            buildString {
                append("FULL ERROR LOG\n")
                append("Total errors: ${errorLog.size}\n")
                append("━".repeat(50))
                append("\n\n")

                errorLog.reversed().forEach { error ->
                    val time =
                        java
                            .text
                            .SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(java.util.Date(error.timestamp))
                    append("[$time]\n")
                    append("Type: ${error.errorType}\n")
                    append("File: ${error.fileName}\n")
                    append("Error: ${error.errorMessage}\n")
                    append("\n")
                }
            }

        val scrollView = android.widget.ScrollView(this)
        val textView =
            android.widget.TextView(this).apply {
                text = fullLog
                typeface = android.graphics.Typeface.MONOSPACE
                textSize = 12f
                setPadding(32, 32, 32, 32)
            }
        scrollView.addView(textView)

        android
            .app
            .AlertDialog
            .Builder(this)
            .setTitle("Error Log (${errorLog.size} entries)")
            .setView(scrollView)
            .setPositiveButton("Close") { dialog, _ ->
                consecutiveErrors = 0
                dialog.dismiss()
            }.setNeutralButton("Copy") { dialog, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Error Log", fullLog)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Error log copied to clipboard", Toast.LENGTH_SHORT).show()
                consecutiveErrors = 0
                dialog.dismiss()
            }.setNegativeButton("Clear Log") { dialog, _ ->
                errorLog.clear()
                consecutiveErrors = 0
                Toast.makeText(this, "Error log cleared", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }.show()
    }
}
