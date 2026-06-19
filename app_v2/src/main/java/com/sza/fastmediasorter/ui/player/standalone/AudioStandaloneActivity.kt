package com.sza.fastmediasorter.ui.player.standalone

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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
import kotlinx.coroutines.launch
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.cache.UnifiedFileCache
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.domain.model.AppSettings
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
import com.sza.fastmediasorter.databinding.ActivityStandaloneAudioBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.dialog.FileInfoDialog
import com.sza.fastmediasorter.ui.player.DefaultPlayerProbe
import com.sza.fastmediasorter.ui.player.PlaybackControlDialogFragment
import com.sza.fastmediasorter.ui.player.StandalonePlayerViewModel
import com.sza.fastmediasorter.ui.player.VideoTrackSelectionManager
import com.sza.fastmediasorter.ui.player.contracts.PlayerHostCapabilities
import com.sza.fastmediasorter.ui.player.contracts.VideoPlayerHandle
import com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler
import com.sza.fastmediasorter.ui.player.helpers.StandaloneKeyboardManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFileOperationsHandler
import com.sza.fastmediasorter.ui.player.helpers.StandaloneViewManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber
import javax.inject.Inject

/**
 * S0380: specialized standalone activity for audio files opened from external intents.
 * Inflates a trimmed layout (no image/video-gesture/pdf/epub/text/office view hierarchies) and drives
 * only the audio path of the shared [StandaloneViewManager] (ADR-2 Unified Playback Logic): the
 * background [com.sza.fastmediasorter.ui.player.helpers.AudioServiceController] feeds a [Player] into
 * the root-based playerView. Non-audio URIs are rejected with the unsupported-format toast.
 * File operations / favourite reuse the shared standalone helpers + ViewModel.
 */
@SuppressLint("UnsafeIntentLaunch")
@AndroidEntryPoint
class AudioStandaloneActivity :
    BaseActivity<ActivityStandaloneAudioBinding>(), PlayerHostCapabilities {

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
    @Inject lateinit var resolveOpenInFmsTargetUseCase: com.sza.fastmediasorter.domain.usecase.ResolveOpenInFmsTargetUseCase
    @Inject lateinit var keyBindingManager: com.sza.fastmediasorter.core.input.KeyBindingManager
    @Inject lateinit var searchLyricsUseCase: com.sza.fastmediasorter.domain.usecase.SearchLyricsUseCase
    @Inject lateinit var sendToMenuManager: com.sza.fastmediasorter.ui.share.SendToMenuManager

    // S0393 U4/U5: keyboard / D-pad layer (audio transport + paging), ported from legacy host.
    private lateinit var keyboardHandler: PlayerKeyboardHandler

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

    private val pagingControls: StandalonePagingControlsBinder by lazy {
        StandalonePagingControlsBinder(
            viewModel = viewModel,
            btnPrev = binding.btnPagePrev,
            btnNext = binding.btnPageNext,
            btnRandom = binding.btnPageRandom,
            btnSlideshow = binding.btnPageSlideshow,
        )
    }

    /** Path of the file last handed to the viewManager; lets folder paging re-render on change. */
    private var lastShownPath: String? = null

    /** Backs the runtime [supportsFolderPaging] capability; updated from VM state. */
    private var folderPagingEnabled = false

    private val fileOperations: StandaloneFileOperationsHandler by lazy {
        StandaloneFileOperationsHandler(
            activity = this,
            root = binding.root,
            getCurrentMediaFile = { viewModel.state.value.mediaFile },
            resolveOpenInFmsTarget = resolveOpenInFmsTargetUseCase,
            onRenameComplete = { newUri, newName -> viewModel.onRenameComplete(newUri, newName) },
            // A SAF rename must keep the background-service playback uninterrupted.
            updateAudioMediaItem = { newUri -> viewManager.updateAudioMediaItem(newUri) },
            batchDeleteLauncher = batchDeleteLauncher,
            recoverableDeleteLauncher = recoverableDeleteLauncher,
            sendToMenuManager = sendToMenuManager,
            getCurrentSettings = { settingsRepository.getSettings().first() }
        )
    }

    // S0438: a player host keeps the screen on when either the global or the dependent player setting is on.
    override fun keepScreenAwakeFor(settings: AppSettings): Boolean =
        settings.preventSleep || settings.keepScreenOnPlayer

    override fun getViewBinding(): ActivityStandaloneAudioBinding =
        ActivityStandaloneAudioBinding.inflate(layoutInflater)

    // This activity owns its insets handling - skip global edge-to-edge.
    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun getInitialFocusView(): View = binding.btnBack

    override fun setupViews() {
        setupWindowAndInsets()
        setupCloseButton()
        setupBackPressHandler()
        setupFileOperationButtons()
        setupPlaybackControls()
        pagingControls.setupClicks()
        setupKeyboardHandler()
        parseIncomingIntent()
    }

    // S0393: playback-speed picker for the audio lane (replaces the legacy speed control).
    private fun showPlaybackSpeedDialog() {
        val player = viewManager.getPlayer(viewModel.state.value.mediaType) ?: return
        val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        val labels = speeds.map { "${it}x" }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cd_playback_speed)
            .setItems(labels) { _, which -> player.setPlaybackSpeed(speeds[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // The audio lane reuses the shared PlayerView controller layout, so the gear button must be
    // wired explicitly in this specialized host as well.
    private fun setupPlaybackControls() {
        binding.playerView.findViewById<android.widget.ImageButton>(R.id.btnPlaybackControl)?.apply {
            setOnClickListener { showPlaybackControlDialog() }
            contentDescription = getString(R.string.control)
        }
    }

    private fun showPlaybackControlDialog() {
        if (isFinishing || isDestroyed) return
        if (viewModel.state.value.mediaType != MediaType.AUDIO) return
        val fragmentManager = supportFragmentManager
        if (fragmentManager.isStateSaved) return
        if (fragmentManager.findFragmentByTag(PlaybackControlDialogFragment.TAG) != null) return
        PlaybackControlDialogFragment().show(
            fragmentManager,
            PlaybackControlDialogFragment.TAG
        )
    }

    // S0393 wave-C: lyrics search via SearchLyricsUseCase, shown in a scrollable dialog.
    private fun showLyrics() {
        val file = viewModel.state.value.mediaFile ?: return
        lifecycleScope.launch {
            val text = searchLyricsUseCase.execute(file).getOrNull()
            if (!text.isNullOrBlank()) {
                com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
                    this@AudioStandaloneActivity, title = getString(R.string.lyrics), message = text)
            } else {
                Toast.makeText(this@AudioStandaloneActivity, R.string.lyrics_not_found, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    // S0393 wave-C: sleep timer - pause playback after the chosen interval.
    private fun showSleepTimerDialog() {
        val minutes = intArrayOf(15, 30, 45, 60)
        val labels = minutes.map { "$it ${getString(R.string.minutes)}" }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.menu_sleep_timer)
            .setItems(labels) { _, which ->
                sleepTimerJob?.cancel()
                sleepTimerJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(minutes[which] * 60_000L)
                    viewManager.getPlayer(viewModel.state.value.mediaType)?.pause()
                    Toast.makeText(this@AudioStandaloneActivity, R.string.menu_sleep_timer, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // S0393 wave-C: search the current track in YouTube Music (app if installed, else browser).
    private fun searchInYouTubeMusic() {
        val file = viewModel.state.value.mediaFile ?: return
        val query = file.name.substringBeforeLast('.')
        val uri = Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(query))
        try {
            val app = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.youtube.music")
            if (app.resolveActivity(packageManager) != null) startActivity(app)
            else startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Timber.e(e, "AudioStandalone: YouTube Music search failed")
            Toast.makeText(this, R.string.search_in_youtube_music_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupKeyboardHandler() {
        keyboardHandler = StandaloneKeyboardManager(
            keyBindingManager = keyBindingManager,
            getActivePlayer = { viewManager.getPlayer(viewModel.state.value.mediaType) },
            getCurrentMediaType = { viewModel.state.value.mediaType },
            onDelete = { fileOperations.deleteCurrentFile() },
            onExit = { finish() },
            onShowRename = { fileOperations.showStandaloneRenameDialog() },
            onShowInfo = { showFileInfo() },
            onToggleCommandPanel = {
                binding.topCommandPanel.isVisible = !binding.topCommandPanel.isVisible
            },
            onToggleFavourite = { viewModel.toggleFavorite() },
            onNextFile = { viewModel.pageNext() },
            onPreviousFile = { viewModel.pagePrevious() },
            onToggleSlideshow = { viewModel.toggleSlideshow() },
            onShowHelp = {
                com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment.show(
                    supportFragmentManager,
                    com.sza.fastmediasorter.ui.common.input.UiSurface.PLAYER
                )
            },
        ).handler
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handlePointerEvent(window.decorView, event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    // Standalone player surface routes motion through its own keyboard/pointer handler; the shared
    // gamepad navigation layer must not also move focus here. S0508.
    override fun shouldHandleGamepadNavigation(): Boolean = false

    // S0393: reapply window insets after rotation (configChanges -> no recreate), ported from legacy.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.topCommandPanel.post { binding.topCommandPanel.requestApplyInsets() }
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
        // Without bottom-inset padding the ExoPlayer button row is hidden behind the nav bar
        // because setDecorFitsSystemWindows(false) makes the window draw behind it.
        ViewCompat.setOnApplyWindowInsetsListener(binding.mediaContentArea) { view, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(0, 0, 0, nav.bottom)
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
            // S0393: shared menu - hide image/video-only items; keep audio items (YouTube/lyrics/sleep).
            listOf(R.id.menu_edit_crop_to_file, R.id.menu_edit_compress, R.id.menu_edit_image,
                R.id.menu_black_screen, R.id.menu_google_lens, R.id.menu_ocr_image,
                R.id.menu_translate_image, R.id.menu_print, R.id.menu_save_frame)
                .forEach { popup.menu.findItem(it)?.isVisible = false }
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_open_in_fms -> { fileOperations.openInFms(); true }
                    R.id.menu_youtube_music -> { searchInYouTubeMusic(); true }
                    R.id.menu_lyrics -> { showLyrics(); true }
                    R.id.menu_sleep_timer -> { showSleepTimerDialog(); true }
                    R.id.menu_playback_speed -> { showPlaybackSpeedDialog(); true }
                    else -> false
                }
            }
            popup.show()
        }
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
        if (DefaultPlayerProbe.isProbe(uri)) {
            Timber.d("AudioStandalone: ignoring default-player probe URI, finishing")
            finish()
            return
        }
        val displayName = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            Timber.w(e, "AudioStandalone: failed to query display name")
            null
        } ?: uri.lastPathSegment
        // Folder paging enumerates only audio neighbours - the only type this host renders.
        viewModel.setHostSupportedTypes(setOf(MediaType.AUDIO))
        viewModel.loadFromUri(uri, intent?.type, displayName)
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            binding.progressBar.isVisible = state.isLoading
            if (state.isLoading) return@collectOnLifecycle
            state.errorMessage?.let { error ->
                Toast.makeText(this@AudioStandaloneActivity, error, Toast.LENGTH_SHORT).show()
                finish()
                return@collectOnLifecycle
            }
            val file = state.mediaFile ?: return@collectOnLifecycle
            val type = state.mediaType ?: return@collectOnLifecycle
            if (type != MediaType.AUDIO) {
                // Image/video + documents are other lanes - reject here, mirroring PhotoVideo/Text.
                Toast.makeText(
                    this@AudioStandaloneActivity,
                    R.string.unsupported_format_use_external_player,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@collectOnLifecycle
            }
            if (file.path != lastShownPath) {
                viewManager.show(file, MediaType.AUDIO)
                lastShownPath = file.path
            }
            folderPagingEnabled = state.supportsFolderPaging
            pagingControls.applyState(state.supportsFolderPaging, state.isSlideshowActive)
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
            Toast.makeText(this@AudioStandaloneActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRenameButtonVisibility() = fileOperations.updateRenameButtonVisibility()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResumeWithViews() {
        viewManager.onResume()
    }

    override fun onPause() {
        viewManager.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        // release() stops + releases the background AudioServiceController, so no direct service wiring.
        viewManager.release()
        super.onDestroy()
    }

    // ── PlayerHostCapabilities ──────────────────────────────────────────────────

    override val supportsListNavigation: Boolean = false
    // Slideshow auto-advance steps through the enumerated folder list, so it tracks folder paging.
    override val supportsSlideshow: Boolean get() = folderPagingEnabled
    override val supportsPersistentAudio: Boolean = false
    override val supportsCast: Boolean = false
    override val supportsDeleteUndo: Boolean = true
    override val supportsCommandPanelFolding: Boolean = false
    override val supportsFolderPaging: Boolean get() = folderPagingEnabled

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

    // Audio still uses the shared playback-control dialog, which reads speed from this handle.
    override val videoPlayerHandle: VideoPlayerHandle by lazy {
        object : VideoPlayerHandle {
            override fun getAvailableAudioTracks(): List<VideoTrackSelectionManager.TrackInfo> = emptyList()

            override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) = Unit

            override fun getAvailableSubtitleTracks(): List<VideoTrackSelectionManager.TrackInfo> = emptyList()

            override fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) = Unit

            override fun getHueAdjustmentDegrees(): Float = 0f

            override fun setHueAdjustmentDegrees(degrees: Float) = Unit

            override fun getBrightnessProgress(): Int = 50

            override fun setBrightnessProgress(progress: Int) = Unit

            override fun getBrightnessPercentOffset(): Int = 0

            override fun getPlaybackSpeed(): Float =
                viewManager.getPlayer(viewModel.state.value.mediaType)?.playbackParameters?.speed ?: 1.0f

            override fun setPlaybackSpeed(speed: Float) {
                viewManager.getPlayer(viewModel.state.value.mediaType)?.setPlaybackSpeed(speed)
            }
        }
    }

    // The shared playback-control dialog (which reads this flag) is not wired in this trimmed lane.
    override val isAudioServiceActive: Boolean = false

    override fun showMessage(message: String) = viewModel.showMessage(message)

    override fun requestFinishAfterDelete() = finish()
}
