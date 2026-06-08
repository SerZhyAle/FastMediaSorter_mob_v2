package com.sza.fastmediasorter.ui.player.standalone

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.data.cloud.CloudFileOperationHandler
import com.sza.fastmediasorter.data.cloud.DropboxClient
import com.sza.fastmediasorter.data.cloud.GoogleDriveRestClient
import com.sza.fastmediasorter.data.cloud.OneDriveRestClient
import com.sza.fastmediasorter.data.network.FtpFileOperationHandler
import com.sza.fastmediasorter.data.network.SmbClient
import com.sza.fastmediasorter.data.network.SmbFileOperationHandler
import com.sza.fastmediasorter.data.network.SftpFileOperationHandler
import com.sza.fastmediasorter.data.remote.ftp.FtpClient
import com.sza.fastmediasorter.data.remote.sftp.SftpClient
import com.sza.fastmediasorter.databinding.ActivityStandalonePhotoVideoBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.dialog.FileInfoDialog
import com.sza.fastmediasorter.ui.player.StandalonePlayerViewModel
import com.sza.fastmediasorter.ui.player.VideoTrackSelectionManager
import com.sza.fastmediasorter.ui.player.contracts.PlayerHostCapabilities
import com.sza.fastmediasorter.ui.player.contracts.VideoPlayerHandle
import com.sza.fastmediasorter.ui.player.helpers.PhotoVideoStandaloneKeyboardManager
import com.sza.fastmediasorter.ui.player.helpers.PhotoVideoStandaloneVideoHandle
import com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFileOperationsHandler
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFullscreenManager
import com.sza.fastmediasorter.ui.player.helpers.StandalonePlayerSettingsManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneVideoControlsManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneVideoTouchDelegate
import com.sza.fastmediasorter.ui.player.helpers.StandaloneViewManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S0380: specialized standalone activity for image/gif/video files opened from external intents.
 * Inflates a trimmed layout (no pdf/epub/text/audio-cover/office view hierarchies) and drives only
 * the image/gif/video paths of the shared [StandaloneViewManager] (ADR-2 Unified Playback Logic).
 * Audio is a separate lane: an audio URI is rejected with the unsupported-format toast.
 * File operations / favourite reuse the shared standalone helpers + ViewModel.
 */
@SuppressLint("UnsafeIntentLaunch")
@AndroidEntryPoint
class PhotoVideoStandaloneActivity :
    BaseActivity<ActivityStandalonePhotoVideoBinding>(), PlayerHostCapabilities {

    private val viewModel: StandalonePlayerViewModel by viewModels()

    private val batchDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> fileOperations.handleBatchDeleteResult(result.resultCode == RESULT_OK) }

    private val recoverableDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> fileOperations.handleRecoverableDeleteResult(result.resultCode == RESULT_OK) }

    // Standalone opens local/content URIs far more often than network paths, so these heavy
    // collaborators stay behind dagger.Lazy until a network-only flow actually needs them.
    @Inject lateinit var smbClient: Lazy<SmbClient>
    @Inject lateinit var sftpClient: Lazy<SftpClient>
    @Inject lateinit var ftpClient: Lazy<FtpClient>
    @Inject lateinit var googleDriveClient: Lazy<GoogleDriveRestClient>
    @Inject lateinit var dropboxClient: Lazy<DropboxClient>
    @Inject lateinit var oneDriveClient: Lazy<OneDriveRestClient>
    @Inject lateinit var credentialsRepository: Lazy<NetworkCredentialsRepository>
    @Inject lateinit var smbFileOperationHandler: Lazy<SmbFileOperationHandler>
    @Inject lateinit var sftpFileOperationHandler: Lazy<SftpFileOperationHandler>
    @Inject lateinit var ftpFileOperationHandler: Lazy<FtpFileOperationHandler>
    @Inject lateinit var cloudFileOperationHandler: Lazy<CloudFileOperationHandler>
    @Inject lateinit var unifiedCache: Lazy<UnifiedFileCache>
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var playbackPositionRepository: PlaybackPositionRepository
    @Inject lateinit var keyBindingManager: com.sza.fastmediasorter.core.input.KeyBindingManager

    private val viewManager: StandaloneViewManager by lazy {
        StandaloneViewManager(
            activity = this,
            root = binding.root,
            lifecycleScope = lifecycleScope,
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
            settingsRepository = settingsRepository,
            playbackPositionRepository = playbackPositionRepository
            // binding intentionally omitted: this trimmed layout never opens document viewers.
        )
    }

    private var videoControlsManager: StandaloneVideoControlsManager? = null
    private var fullscreenManager: StandaloneFullscreenManager? = null
    private var trackSelectionManager: VideoTrackSelectionManager? = null
    private var videoTouchDelegate: StandaloneVideoTouchDelegate? = null
    private var playerSettingsManager: StandalonePlayerSettingsManager? = null
    private lateinit var keyboardHandler: PlayerKeyboardHandler

    /** Set after the first successful viewManager.show(); prevents reload on rename state updates. */
    private var contentLoaded = false

    private val fileOperations: StandaloneFileOperationsHandler by lazy {
        StandaloneFileOperationsHandler(
            activity = this,
            root = binding.root,
            getCurrentMediaFile = { viewModel.state.value.mediaFile },
            findResourceForPath = { parentDir -> viewModel.findResourceForPath(parentDir) },
            onRenameComplete = { newUri, newName -> viewModel.onRenameComplete(newUri, newName) },
            updateAudioMediaItem = { /* audio is a separate lane - never handled here */ },
            batchDeleteLauncher = batchDeleteLauncher,
            recoverableDeleteLauncher = recoverableDeleteLauncher
        )
    }

    override fun getViewBinding(): ActivityStandalonePhotoVideoBinding =
        ActivityStandalonePhotoVideoBinding.inflate(layoutInflater)

    // This activity owns its immersive insets handling - skip global edge-to-edge.
    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun getInitialFocusView(): View = binding.btnBack

    override fun setupViews() {
        setupWindowAndInsets()
        setupCloseButton()
        setupBackPressHandler()
        setupFileOperationButtons()
        setupKeyboardHandler()
        parseIncomingIntent()
    }

    private fun setupWindowAndInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Pad the command panel for status/caption bar (top) + nav bar (left/right in landscape)
        // so its buttons stay inside the system-bar safe area (Rule 18).
        ViewCompat.setOnApplyWindowInsetsListener(binding.topCommandPanel) { view, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.captionBar()
            )
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(nav.left, top.top, nav.right, view.paddingBottom)
            insets
        }
        binding.topCommandPanel.post { binding.topCommandPanel.requestApplyInsets() }
    }

    private fun setupCloseButton() {
        binding.btnBack.setImageResource(R.drawable.ic_clear)
        binding.btnBack.setOnClickListener { finish() }
        binding.topCommandPanel.isVisible = true
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })
    }

    private fun setupFileOperationButtons() {
        binding.btnDeleteCmd.isVisible = true
        binding.btnDeleteCmd.setOnClickListener { fileOperations.deleteCurrentFile() }
        binding.btnShareCmd.isVisible = true
        binding.btnShareCmd.setOnClickListener { fileOperations.shareCurrentFile() }
        binding.btnFavorite.isVisible = true
        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
        binding.btnInfoCmd.isVisible = true
        binding.btnInfoCmd.setOnClickListener { showFileInfo() }
        // Rename stays hidden until the async capability check completes.
        binding.btnRenameCmd.isVisible = false
        binding.btnRenameCmd.setOnClickListener { fileOperations.showStandaloneRenameDialog() }
        binding.btnOverflowMenu.isVisible = true
        binding.btnOverflowMenu.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.inflate(R.menu.overflow_menu_standalone_player)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_open_in_fms -> { fileOperations.openInFms(); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun setupKeyboardHandler() {
        keyboardHandler = PhotoVideoStandaloneKeyboardManager(
            keyBindingManager = keyBindingManager,
            getActivePlayer = { viewManager.getExoPlayer() },
            getCurrentMediaType = { viewModel.state.value.mediaFile?.type },
            onDelete = { fileOperations.deleteCurrentFile() },
            onExit = { finish() },
            onShowRename = { fileOperations.showStandaloneRenameDialog() },
            onShowInfo = { showFileInfo() },
            onToggleCommandPanel = {
                binding.topCommandPanel.isVisible = !binding.topCommandPanel.isVisible
            },
            onToggleFullscreen = { fullscreenManager?.toggleFullscreen() },
            onToggleFavourite = { viewModel.toggleFavorite() },
        ).handler
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handlePointerEvent(window.decorView, event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    private fun showFileInfo() {
        val file = viewModel.state.value.mediaFile ?: return
        if (isFinishing || isDestroyed) return
        FileInfoDialog(
            this,
            file,
            smbClient.get(),
            sftpClient.get(),
            ftpClient.get(),
            credentialsRepository.get(),
            unifiedCache.get(),
            downloadNetworkFileUseCase = null,
            audioMetadataLoader = null,
            audioMetadataCacheRepository = null
        ).show()
    }

    private fun parseIncomingIntent() {
        val uri = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            else -> intent?.data
        }
        if (uri == null) {
            Toast.makeText(this, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val displayName = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            Timber.w(e, "PhotoVideoStandalone: failed to query display name")
            null
        } ?: uri.lastPathSegment
        viewModel.loadFromUri(uri, intent?.type, displayName)
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            binding.progressBar.isVisible = state.isLoading
            if (state.isLoading) return@collectOnLifecycle
            state.errorMessage?.let { error ->
                Toast.makeText(this@PhotoVideoStandaloneActivity, error, Toast.LENGTH_SHORT).show()
                finish()
                return@collectOnLifecycle
            }
            val file = state.mediaFile ?: return@collectOnLifecycle
            val type = state.mediaType ?: return@collectOnLifecycle
            if (type != MediaType.IMAGE && type != MediaType.GIF && type != MediaType.VIDEO) {
                // Audio + documents + binaries are other lanes - reject here, mirroring TextStandalone.
                Toast.makeText(
                    this@PhotoVideoStandaloneActivity,
                    R.string.unsupported_format_use_external_player,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@collectOnLifecycle
            }
            if (!contentLoaded) {
                Timber.d("S0380: PhotoVideoStandaloneActivity showing $type from external intent")
                val onVideoReady: ((PlayerView) -> Unit)? =
                    if (type == MediaType.VIDEO) ({ pv -> setupVideoControls(pv) }) else null
                viewManager.show(file, type, onVideoReady)
                contentLoaded = true
            }
            updateRenameButtonVisibility()
        }
        collectOnLifecycle(viewModel.isFavorite) { isFav ->
            binding.btnFavorite.setImageResource(
                if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            binding.btnFavorite.contentDescription = getString(
                if (isFav) R.string.cd_remove_from_favorites else R.string.cd_add_to_favorites
            )
        }
        collectOnLifecycle(viewModel.messageFlow) { message ->
            Toast.makeText(this@PhotoVideoStandaloneActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRenameButtonVisibility() = fileOperations.updateRenameButtonVisibility()

    // ── Video controls (mirrors StandalonePlayerActivity's VIDEO subset) ──────────

    private fun setupVideoControls(pv: PlayerView) {
        val controlsManager = StandaloneVideoControlsManager(
            playerView = pv,
            callback = object : StandaloneVideoControlsManager.StandaloneVideoControlsCallback {
                override fun showPlaybackControlDialog() { /* control dialog not wired in this lane */ }
            }
        )
        controlsManager.setupVideoControls()
        videoControlsManager = controlsManager

        val fsManager = StandaloneFullscreenManager(this)
        fullscreenManager = fsManager
        fsManager.enterFullscreen()

        val trackManager = VideoTrackSelectionManager(
            getPlayer = { viewManager.getExoPlayer() },
            getPlayerView = { pv }
        )
        trackSelectionManager = trackManager

        playerSettingsManager = StandalonePlayerSettingsManager(
            activity = this,
            playerView = pv,
            settingsRepository = settingsRepository,
            trackSelectionManager = trackManager,
            lifecycleScope = lifecycleScope
        )

        viewManager.getExoPlayer()?.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                controlsManager.updateTrackButtonsVisibility(
                    hasMultipleAudio = trackManager.hasMultipleAudioTracks(),
                    hasSubtitles = trackManager.hasSubtitleTracks()
                )
            }
        })

        val touchDelegate = StandaloneVideoTouchDelegate(
            activity = this,
            playerView = pv,
            rootView = binding.root,
            getBrightnessProgress = { viewManager.getBrightnessProgress() },
            setBrightnessProgress = { progress -> viewManager.setBrightnessProgress(progress) },
            getBrightnessPercentOffset = { viewManager.getBrightnessPercentOffset() }
        )
        touchDelegate.attachIndicator(binding.tvVideoGestureIndicator)
        videoTouchDelegate = touchDelegate

        @SuppressLint("ClickableViewAccessibility")
        val touchListener = View.OnTouchListener { _, event ->
            val consumed = touchDelegate.handleTouchEvent(event)
            if (!consumed && event.actionMasked == MotionEvent.ACTION_UP) pv.performClick()
            consumed
        }
        pv.setOnTouchListener(touchListener)
        Timber.d("PhotoVideoStandalone: video controls setup complete")
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResumeWithViews() {
        viewManager.onResume()
    }

    override fun onPause() {
        viewManager.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        fullscreenManager?.exitFullscreen()
        fullscreenManager = null
        videoControlsManager = null
        trackSelectionManager = null
        videoTouchDelegate = null
        playerSettingsManager = null
        viewManager.release()
        super.onDestroy()
    }

    // ── PlayerHostCapabilities ──────────────────────────────────────────────────

    override val supportsListNavigation: Boolean = false
    override val supportsSlideshow: Boolean = false
    override val supportsPersistentAudio: Boolean = false
    override val supportsCast: Boolean = false
    override val supportsDeleteUndo: Boolean = true
    override val supportsCommandPanelFolding: Boolean = false

    override val currentMediaFile: StateFlow<MediaFile?> by lazy {
        viewModel.state.map { it.mediaFile }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, viewModel.state.value.mediaFile)
    }

    override val currentMediaType: StateFlow<MediaType?> by lazy {
        viewModel.state.map { it.mediaType }
            .stateIn(lifecycleScope, SharingStarted.Eagerly, viewModel.state.value.mediaType)
    }

    override val stereoMode: StateFlow<StereoMode> get() = viewModel.stereoMode
    override val detectedStereoMode: StateFlow<StereoMode> get() = viewModel.detectedStereoMode

    override fun setStereoMode(mode: StereoMode) = viewModel.setStereoMode(mode)
    override fun rememberStereoModeForCurrentFile(mode: StereoMode) =
        viewModel.rememberStereoModeForCurrentFile(mode)

    override val videoPlayerHandle: VideoPlayerHandle get() = photoVideoHandle

    private val photoVideoHandle: VideoPlayerHandle by lazy {
        PhotoVideoStandaloneVideoHandle(
            viewManager = viewManager,
            trackSelectionManager = { trackSelectionManager },
            currentMediaType = { viewModel.state.value.mediaType },
        )
    }

    override val isAudioServiceActive: Boolean = false

    override fun showMessage(message: String) = viewModel.showMessage(message)

    override fun requestFinishAfterDelete() = finish()
}
