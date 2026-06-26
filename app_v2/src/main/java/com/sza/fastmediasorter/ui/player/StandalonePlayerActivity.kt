package com.sza.fastmediasorter.ui.player

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.View
import android.view.ActionMode
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.BuildConfig
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
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.model.StereoMode
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.ui.player.contracts.PlayerHostCapabilities
import com.sza.fastmediasorter.ui.player.contracts.VideoPlayerHandle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.PictureInPictureManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFileOperationsHandler
import com.sza.fastmediasorter.ui.player.helpers.PlayerDisplayMode
import com.sza.fastmediasorter.ui.player.helpers.PlayerOrientationModeManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFullscreenManager
import com.sza.fastmediasorter.ui.player.helpers.StandalonePlayerLifecycleManager
import com.sza.fastmediasorter.ui.player.helpers.StandalonePlayerSettingsManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneVideoControlsManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneVideoTouchDelegate
import com.sza.fastmediasorter.ui.player.helpers.SearchControlsManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneViewManager
import com.sza.fastmediasorter.ui.player.helpers.DocumentSelectionActionModeAugmentingCallback
import com.sza.fastmediasorter.ui.player.helpers.btnPdfHome
import com.sza.fastmediasorter.ui.player.helpers.btnPdfNextPage
import com.sza.fastmediasorter.ui.player.helpers.btnPdfPrevPage
import com.sza.fastmediasorter.ui.player.helpers.btnEpubPrevChapter
import com.sza.fastmediasorter.ui.player.helpers.btnEpubHome
import com.sza.fastmediasorter.ui.player.helpers.btnEpubNextChapter
import com.sza.fastmediasorter.ui.player.helpers.btnEpubToc
import com.sza.fastmediasorter.ui.player.helpers.btnEpubFontSizeDecrease
import com.sza.fastmediasorter.ui.player.helpers.btnEpubFontSizeIncrease
import com.sza.fastmediasorter.ui.player.VideoTrackSelectionManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler
import com.sza.fastmediasorter.ui.common.input.InputHelpDialogFragment
import com.sza.fastmediasorter.ui.common.input.UiSurface
import com.sza.fastmediasorter.ui.dialog.FileInfoDialog
import androidx.appcompat.widget.PopupMenu
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.media3.common.Player

/** Standalone Activity for playing/viewing media opened from external sources (Intent.ACTION_VIEW and Intent.ACTION_SEND). Detached from the main resource/database tree - no resource system, no playlists, no history. All viewer routing is delegated to StandaloneViewManager. */
// StandalonePlayerActivity is intentionally exported and unprotected to work as an "Open With" handler for any app. UnsafeIntentLaunch is suppressed because no intent data is forwarded to startActivity/startService - received URIs are only passed to ExoPlayer/Glide as media.
@SuppressLint("UnsafeIntentLaunch")
@AndroidEntryPoint
// TODO(S0393): remove once nothing launches StandalonePlayerActivity. All external routing already
// goes to the specialized PhotoVideo/Audio/Document/Text hosts via StandalonePlayerDispatcherActivity
// (no manifest alias targets this class). Its previously-unique capabilities have been harvested into
// the specialized hosts (S0393 HARVEST.md U1 PiP, U2 playback-control dialog, U3 WebView ActionMode,
// U4/U5 keyboard, U7/U8 EPUB translator guard). Kept only as a direct/fallback target until removal.
@Deprecated("S0393: superseded by the specialized standalone hosts; pending removal once unreferenced.")
class StandalonePlayerActivity : BaseActivity<ActivityPlayerUnifiedBinding>(), PlayerHostCapabilities {

    private val viewModel: StandalonePlayerViewModel by viewModels()

    // Delete permission launchers - pending state and result handling live in fileOperations.
    // API 30+: MediaStore.createDeleteRequest auto-deletes after user grants permission.
    private val batchDeleteLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        fileOperations.handleBatchDeleteResult(result.resultCode == RESULT_OK)
    }

    // API 29: RecoverableSecurityException - user grants, then we retry delete.
    private val recoverableDeleteLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        fileOperations.handleRecoverableDeleteResult(result.resultCode == RESULT_OK)
    }

    // S0681: SAF tree picker for the «..» entry of the copy-to-resource dialog. The chosen folder
    // receives a copy of the current file via the shared handler.
    private var pendingCopyToCustomFolder = false
    private val customPathPickerLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (!pendingCopyToCustomFolder) return@registerForActivityResult
        pendingCopyToCustomFolder = false
        if (uri == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val label = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() } ?: getString(R.string.select_folder)
        fileOperations.copyCurrentFileToPath(uri.toString(), label)
    }

    private val fileOperations: StandaloneFileOperationsHandler by lazy {
        StandaloneFileOperationsHandler(
            activity = this,
            root = binding.root,
            getCurrentMediaFile = { viewModel.state.value.mediaFile },
            resolveOpenInFmsTarget = resolveOpenInFmsTargetUseCase,
            onRenameComplete = { newUri, newName -> viewModel.onRenameComplete(newUri, newName) },
            updateAudioMediaItem = { newUri -> viewManager.updateAudioMediaItem(newUri) },
            batchDeleteLauncher = batchDeleteLauncher,
            recoverableDeleteLauncher = recoverableDeleteLauncher,
            sendToMenuManager = sendToMenuManager,
            getCurrentSettings = { settingsRepository.getSettings().first() },
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            onPickCustomFolderForCopy = {
                pendingCopyToCustomFolder = true
                customPathPickerLauncher.launch(null)
            },
        )
    }

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

    // S0391: compile-tier capability flags; supplies the cloud-support flag to the debug logger.
    @Inject lateinit var mediaCapabilities: com.sza.fastmediasorter.core.capability.MediaCapabilities
    @Inject lateinit var keyBindingManager: com.sza.fastmediasorter.core.input.KeyBindingManager
    @Inject lateinit var resolveOpenInFmsTargetUseCase: com.sza.fastmediasorter.domain.usecase.ResolveOpenInFmsTargetUseCase
    @Inject lateinit var sendToMenuManager: com.sza.fastmediasorter.ui.share.SendToMenuManager
    @Inject lateinit var fileOperationUseCase: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
    @Inject lateinit var getDestinationsUseCase: com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase

    private lateinit var viewManager: StandaloneViewManager
    private var pipManager: PictureInPictureManager? = null
    private lateinit var lifecycleManager: StandalonePlayerLifecycleManager
    private lateinit var keyboardHandler: PlayerKeyboardHandler
    private var videoControlsManager: StandaloneVideoControlsManager? = null
    private var standaloneTrackSelectionManager: VideoTrackSelectionManager? = null
    private var videoTouchDelegate: StandaloneVideoTouchDelegate? = null
    private var playerSettingsManager: StandalonePlayerSettingsManager? = null
    private var fullscreenManager: StandaloneFullscreenManager? = null
    private var searchControlsManager: SearchControlsManager? = null

    // S0667: single decision point mapping device orientation to fullscreen/command-panel mode.
    private val orientationModeManager = PlayerOrientationModeManager()
    /** Cached from settingsRepository; updated by observeTranslationSettings(). */
    private var cachedTranslationEnabled = true
    /** Set to true after the first successful viewManager.show(); prevents reload on rename state updates. */
    private var contentLoaded = false

    // ── Document WebView floating ActionMode augmentation ───────────────────── WebView cannot use setCustomSelectionActionModeCallback (TextView-only). Instead, we intercept every startActionMode call: when a document WebView is open and the mode is TYPE_FLOATING (WebView text selection), we wrap the system callback to inject our "Translate" / "Search in Google" items.

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        val documentCallback = if (
            type == ActionMode.TYPE_FLOATING &&
            ::viewManager.isInitialized
        ) viewManager.getDocumentSelectionActionModeCallback() else null
        return if (documentCallback != null && callback != null) {
            super.startActionMode(DocumentSelectionActionModeAugmentingCallback(callback, documentCallback), type)
        } else {
            super.startActionMode(callback, type)
        }
    }

    // S0438: a player host keeps the screen on when either the global or the dependent player setting is on.
    override fun keepScreenAwakeFor(settings: AppSettings): Boolean =
        settings.preventSleep || settings.keepScreenOnPlayer

    override fun getViewBinding(): ActivityPlayerUnifiedBinding {
        return ActivityPlayerUnifiedBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        // See PlayerActivity.onCreate - must run before super → setContentView.
        com.sza.fastmediasorter.ui.player.helpers.PlayerLayoutModePrefs.applyControlsThemeOverlay(this)
        super.onCreate(savedInstanceState)
    }

    // Player layout has its own immersive insets handling - skip global edge-to-edge
    override fun shouldEnableEdgeToEdge(): Boolean = false

    /**
     * Initial focus for a non-touch open. btnPlayPause lives in controlsOverlay, which stays
     * GONE in the standalone player (standalone uses ExoPlayer's own PlayerView controls), so
     * focusing it was a silent no-op. The top command bar Back button is always visible here
     * (setupViews makes topCommandPanel visible) and is the entry point into the command-bar
     * focus chain. topCommandPanel ImageButtons are focusable by default and show a focus
     * state via their selectableItemBackgroundBorderless background, so no extra wiring is
     * needed for D-pad traversal across the bar. S0289.
     */
    override fun getInitialFocusView(): View? {
        return binding.btnBack
    }

    override fun setupViews() {
        val t0 = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
        setupWindowAndInsets()

        val incomingUri = resolveIncomingUri()
        val isDefaultPlayerProbe = DefaultPlayerProbe.isProbe(incomingUri)
        if (BuildConfig.DEBUG) {
            Timber.d("StandalonePlayer[debug]: setupViews START - isProbe=$isDefaultPlayerProbe uri=$incomingUri")
        }
        if (isDefaultPlayerProbe) {
            Timber.d("StandalonePlayer: short-circuiting default-player probe before standalone init")
            finish()
            return
        }

        val viewManagerT0 = if (BuildConfig.DEBUG) SystemClock.uptimeMillis() else 0L
        viewManager = StandaloneViewManager(
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
            // S0380: StandaloneViewManager is fully root-based now (document viewers decoupled),
            // so only the layout root is needed - the binding param was removed.
            playbackPositionRepository = playbackPositionRepository
        )
        if (BuildConfig.DEBUG) Timber.d("StandalonePlayer[debug]: StandaloneViewManager() constructor done in ${SystemClock.uptimeMillis() - viewManagerT0}ms")

        lifecycleManager = StandalonePlayerLifecycleManager(activity = this, viewManager = viewManager)
        lifecycleManager.onCreate(null)

        fullscreenManager = StandaloneFullscreenManager(this)

        viewManager.setFullscreenCallbacks(
            onEnter = {
                fullscreenManager?.enterFullscreenWithPanel(binding.topCommandPanel) { isActive ->
                    updateFullscreenButtonState(isActive)
                }
            },
            onExit = {
                fullscreenManager?.exitFullscreenWithPanel(binding.topCommandPanel) { isActive ->
                    updateFullscreenButtonState(isActive)
                }
            }
        )

        pipManager = PictureInPictureManager(
            activity = this,
            playerView = binding.playerView,
            chromeToHide = listOf(binding.toolbar, binding.topCommandPanel),
            getPlayer = { viewManager.getExoPlayer() },
            onPlay = { viewManager.play() },
            onPause = { viewManager.pause() },
            isVideoPlaying = { viewManager.isMediaPlaying() }
        )
        // Initial PiP button state is applied in observePipSettings() via settings flow

        setupCloseButton()
        setupBackPressHandler()
        hidePlaylistControls()
        setupFileOperationButtons()
        setupFullscreenButton()
        setupPdfButtons()
        setupEpubButtons()
        setupSearchControls()

        if (BuildConfig.DEBUG) Timber.d("StandalonePlayer[debug]: pre-parseIncomingIntent total=${SystemClock.uptimeMillis() - t0}ms")
        parseIncomingIntent()
        setupKeyboardHandler()
        if (BuildConfig.DEBUG) Timber.d("StandalonePlayer[debug]: setupViews DONE total=${SystemClock.uptimeMillis() - t0}ms")
    }

    private fun setupKeyboardHandler() {
        keyboardHandler = PlayerKeyboardHandler(
            callback = object : PlayerKeyboardHandler.PlayerKeyboardCallback {
                override fun onDeleteFile() = fileOperations.deleteCurrentFile()
                override fun onExitPlayer() = finish()
                override fun onToggleSlideshow() { /* standalone has no playlist slideshow */ }
                override fun onShowRenameDialog() = fileOperations.showStandaloneRenameDialog()
                override fun onShowFileInfo() = showFileInfo()
                override fun onToggleCommandPanel() {
                    toggleStandaloneFullscreen()
                }
                override fun onToggleCopyPanel() { /* not applicable in standalone */ }
                override fun onToggleMovePanel() { /* not applicable in standalone */ }
                override fun onShowEditDialog() { /* not applicable in standalone */ }
                override fun getActivePlayer(): Player? = viewManager.getExoPlayer()
                override fun getCurrentMediaType(): MediaType? =
                    viewModel.state.value.mediaFile?.type
                override fun onPdfNextPage() = viewManager.showPdfNextPage()
                override fun onPdfPreviousPage() = viewManager.showPdfPreviousPage()
                override fun onPdfHome() = viewManager.showPdfFirstPage()
                override fun onPdfEnd() { /* not exposed in standalone */ }
                override fun onEpubNextPage() = viewManager.showEpubNextChapter()
                override fun onEpubPreviousPage() = viewManager.showEpubPreviousChapter()
                override fun onEpubHome() = viewManager.showEpubFirstChapter()
                override fun onEpubEnd() { /* not exposed in standalone */ }
                override fun onTextScrollDown() = viewManager.textViewerManagerProvider().scrollDown()
                override fun onTextScrollUp() = viewManager.textViewerManagerProvider().scrollUp()
                override fun onTextHome() = viewManager.textViewerManagerProvider().scrollToTop()
                override fun onTextEnd() = viewManager.textViewerManagerProvider().scrollToBottom()
                override fun onSeekForward(seconds: Int) {
                    val p = viewManager.getExoPlayer() ?: return
                    p.seekTo((p.currentPosition + seconds * 1000L).coerceAtMost(p.duration))
                }
                override fun onSeekBackward(seconds: Int) {
                    val p = viewManager.getExoPlayer() ?: return
                    p.seekTo((p.currentPosition - seconds * 1000L).coerceAtLeast(0L))
                }
                override fun onEpubScrollDelta(verticalScroll: Float) {
                    if (verticalScroll > 0) viewManager.showEpubPreviousChapter()
                    else viewManager.showEpubNextChapter()
                }
                override fun onNavigationScroll(verticalScroll: Float) { /* single-file standalone */ }
                override fun onToggleMute() {
                    val p = viewManager.getPlayer(viewModel.state.value.mediaFile?.type) ?: return
                    p.volume = if (p.volume > 0f) 0f else 1f
                }
                override fun onToggleFullscreen() {
                    toggleStandaloneFullscreen()
                }
                override fun onChangeVolume(delta: Int) {
                    val p = viewManager.getPlayer(viewModel.state.value.mediaFile?.type) ?: return
                    p.volume = (p.volume + delta * 0.1f).coerceIn(0f, 1f)
                }
                override fun onShowHelp() {
                    InputHelpDialogFragment.show(supportFragmentManager, UiSurface.PLAYER)
                }
                override fun onDocumentSearch() {
                    searchControlsManager?.showSearchPanel()
                }
                override fun onSaveCurrent() { /* save frame not supported in standalone */ }
                override fun onShowContextMenu() {
                    toggleStandaloneFullscreen()
                }
                // Standalone plays a single file - no playlist navigation.
                override fun onNextFile() {}
                override fun onPreviousFile() {}
                override fun onToggleFavourite() = viewModel.toggleFavorite()
                override fun onUndoOperation() {}
            },
            keyBindingManager = keyBindingManager,
        )
    }

    /** S0289 Phase 08: keep the standalone player aligned with PlayerActivity's multimodal baseline - bespoke `keyboardHandler` consumes its keys first, then `super.onKeyDown` lets BaseActivity's TV / back / context defaults take over. No duplicate gamepad-analog helper is required here; the standalone surface does not own a media-resource list. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handleKeyDown(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    /** S0289 Phase 08: pointer events route through the player's bespoke handler first; if it does not consume them, the call falls through to BaseActivity, which delegates to the shared `ActivityMouseDispatchHelper` (wheel scroll, back/context, etc.). */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handlePointerEvent(window.decorView, event)) return true
        return super.dispatchGenericMotionEvent(event)
    }

    // Standalone player surface routes motion through its own keyboard/pointer handler; the shared
    // gamepad navigation layer must not also move focus here. S0508.
    override fun shouldHandleGamepadNavigation(): Boolean = false

    override fun observeData() {
        observeViewModelState()
        observeViewModelEvents()
        observeMessages()
        observeFavoriteState()
        observeTranslationSettings()
        observePipSettings()
    }

    override fun onResumeWithViews() {
        lifecycleManager.onResume()
    }

    override fun onPause() {
        // Skip pausing playback when entering PiP - the activity is technically paused
        // but media must keep running inside the PiP window.
        val isInPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode
        if (!isInPip && ::lifecycleManager.isInitialized) lifecycleManager.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::lifecycleManager.isInitialized) lifecycleManager.onDestroy()
        pipManager?.release()
        pipManager = null
        fullscreenManager?.exitFullscreen()
        fullscreenManager = null
        videoControlsManager = null
        standaloneTrackSelectionManager = null
        videoTouchDelegate = null
        playerSettingsManager = null
        viewManager.release()
        super.onDestroy()
    }

    internal fun standaloneViewManager(): StandaloneViewManager = viewManager

    internal fun standaloneTrackSelectionManager(): VideoTrackSelectionManager? =
        standaloneTrackSelectionManager

    internal fun currentMediaType(): MediaType? = viewModel.state.value.mediaType

    internal fun showPlaybackControlDialog() {
        if (isFinishing || isDestroyed || !::viewManager.isInitialized) return
        val currentType = currentMediaType()
        if (currentType != MediaType.VIDEO && currentType != MediaType.AUDIO) return
        val fragmentManager = supportFragmentManager
        if (fragmentManager.isStateSaved) return
        if (fragmentManager.findFragmentByTag(PlaybackControlDialogFragment.TAG) != null) return
        PlaybackControlDialogFragment().show(
            fragmentManager,
            PlaybackControlDialogFragment.TAG
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Honour the user's PiP preference - pipManager.isEnabled tracks the latest setting value.
        pipManager?.onUserLeaveHint(pipManager?.isEnabled ?: false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Re-apply insets after orientation change so panels don't overlap OS bars
        binding.topCommandPanel.post {
            binding.topCommandPanel.requestApplyInsets()
            Timber.d("StandalonePlayer: insets reapplied after orientation change")
        }
        binding.root.findViewById<android.view.View?>(R.id.bottomPanelsContainer)?.also {
            it.post { it.requestApplyInsets() }
        }
        // playerView re-measures itself via ExoPlayer's internal SurfaceView listener - no manual action needed
        updateEpubTranslatorVisibility()

        val mediaType = currentMediaType()
        val isVisualMedia = mediaType == MediaType.VIDEO ||
            mediaType == MediaType.IMAGE ||
            mediaType == MediaType.GIF
        // Standalone has no app-level orientation lock - it follows the device by manifest.
        val targetMode = orientationModeManager.resolve(
            isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE,
            followsDevice = true,
            isVisualMedia = isVisualMedia
        )
        when (targetMode) {
            PlayerDisplayMode.FULLSCREEN ->
                if (binding.topCommandPanel.isVisible) {
                    fullscreenManager?.enterFullscreenWithPanel(binding.topCommandPanel) { isActive ->
                        updateFullscreenButtonState(isActive)
                    }
                }
            PlayerDisplayMode.COMMAND_PANEL ->
                if (!binding.topCommandPanel.isVisible) {
                    fullscreenManager?.exitFullscreenWithPanel(binding.topCommandPanel) { isActive ->
                        updateFullscreenButtonState(isActive)
                    }
                }
            null -> Unit
        }
    }

    // Single-arg override kept for legacy minSdk 23: on API 24-25 the framework calls only this
    // signature (two-arg added in API 26), so migrating away would drop PiP handling on those devices.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        pipManager?.onPictureInPictureModeChanged(isInPictureInPictureMode)
    }

    // ── Intent Parsing ────────────────────────────────────────────────────

    private fun parseIncomingIntent() {
        debugLogLaunchConditions(intent)

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
            Intent.ACTION_SEND_MULTIPLE -> {
                // Open the first file from the list; multi-file playlist not supported standalone.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
                }
            }
            else -> intent?.data
        }

        if (uri == null) {
            Timber.w("StandalonePlayer: no URI in intent, finishing")
            Toast.makeText(this, R.string.error_opening_file_simple, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Probe files created by DefaultPlayerHelper for the "set as default" flow must not be
        // played - they are 1-byte stubs and will crash viewers. Silently finish.
        if (DefaultPlayerProbe.isProbe(uri)) {
            Timber.d("StandalonePlayer: ignoring default-player probe URI, finishing")
            finish()
            return
        }

        val mimeType = intent.type

        val displayName = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (e: Exception) {
            Timber.w(e, "StandalonePlayer: failed to query display name")
            null
        } ?: uri.lastPathSegment

        Timber.d("StandalonePlayer: incoming uri=$uri mime=$mimeType name=$displayName")
        viewModel.loadFromUri(uri, mimeType, displayName)
    }

    @SuppressLint("UnsafeIntentLaunch") // debug-only logging; no intent is re-launched here
    private fun debugLogLaunchConditions(incomingIntent: Intent?) =
        com.sza.fastmediasorter.ui.player.helpers.StandaloneLaunchDebugLogger.log(
            this, incomingIntent, mediaCapabilities,
        )

    // ── Window / Insets Setup ─────────────────────────────────────────────

    private fun setupWindowAndInsets() {
        // Mirror PlayerActivity: opt out of auto-fit, handle insets manually
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // topCommandPanel: pad for status bar (top) + caption bar (Chrome OS window title) + nav bar (left/right in landscape).
        // statusBars() returns 0 in Chrome OS windowed mode; captionBar() carries the actual title-bar height.
        ViewCompat.setOnApplyWindowInsetsListener(binding.topCommandPanel) { view, insets ->
            val topInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.captionBar()
            )
            val navBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(navBar.left, topInsets.top, navBar.right, view.paddingBottom)
            insets
        }
        binding.topCommandPanel.post { binding.topCommandPanel.requestApplyInsets() }

        // bottomPanelsContainer: pad for nav bar (bottom + sides) - only exists in landscape layout
        binding.root.findViewById<android.view.View?>(R.id.bottomPanelsContainer)?.also { container ->
            ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
                val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                view.setPadding(sys.left, view.paddingTop, sys.right, sys.bottom)
                insets
            }
            container.post { container.requestApplyInsets() }
        }
    }

    // ── Close Button & Back Navigation ────────────────────────────────────

    private fun setupCloseButton() {
        binding.btnBack.setImageResource(R.drawable.ic_clear)
        binding.btnBack.setOnClickListener { finish() }
        binding.topCommandPanel.isVisible = true
    }

    // ── PDF Page Navigation ──────────────────────────────────────────────

    private fun setupPdfButtons() {
        binding.btnPdfPrevPage.setOnClickListener    { viewManager.showPdfPreviousPage() }
        binding.btnPdfHome.setOnClickListener        { viewManager.showPdfFirstPage() }
        binding.btnPdfNextPage.setOnClickListener    { viewManager.showPdfNextPage() }
        binding.btnTranslatePdfCmd.setOnClickListener { viewManager.togglePdfTranslation() }
    }

    // ── EPUB Navigation ──────────────────────────────────────────────────

    private fun setupEpubButtons() {
        binding.btnEpubPrevChapter.setOnClickListener      { viewManager.showEpubPreviousChapter() }
        binding.btnEpubHome.setOnClickListener             { viewManager.showEpubFirstChapter() }
        binding.btnEpubNextChapter.setOnClickListener      { viewManager.showEpubNextChapter() }
        binding.btnEpubToc.setOnClickListener              { viewManager.showEpubTableOfContents() }
        binding.btnEpubFontSizeDecrease.setOnClickListener { viewManager.decreaseEpubFontSize() }
        binding.btnEpubFontSizeIncrease.setOnClickListener { viewManager.increaseEpubFontSize() }
        binding.btnEpubTextSettingsCmd.setOnClickListener  { viewManager.showEpubReaderSettings() }
        binding.btnExitEpubFullscreen.setOnClickListener   { viewManager.exitEpubFullscreen() }
        // btnTranslateEpubCmd listener is set by SearchControlsManager.setupSearchControls() (ADR-4)
    }

    // ── Fullscreen Button ────────────────────────────────────────────────

    private fun setupFullscreenButton() {
        binding.btnFullscreenCmd.setOnClickListener { toggleStandaloneFullscreen() }
        updateFullscreenButtonState(false)
        // Exit fullscreen when transient system bars appear (user edge-swipe in immersive mode).
        // Guard ensures this only acts when the user has entered panel-hiding fullscreen.
        fullscreenManager?.setupTransientBarsExitCallback(window.decorView) {
            if (!binding.topCommandPanel.isVisible) {
                fullscreenManager?.exitFullscreenWithPanel(binding.topCommandPanel) { isActive ->
                    updateFullscreenButtonState(isActive)
                }
            }
        }
    }

    private fun toggleStandaloneFullscreen() {
        fullscreenManager?.toggleFullscreenWithPanel(binding.topCommandPanel) { isActive ->
            updateFullscreenButtonState(isActive)
        }
    }

    private fun updateFullscreenButtonState(isActive: Boolean) {
        binding.btnFullscreenCmd.setImageResource(
            if (isActive) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
        )
        binding.btnFullscreenCmd.contentDescription = getString(
            if (isActive) R.string.exit_fullscreen else R.string.fullscreen_mode
        )
    }

    private fun applyFullscreenButtonVisibility(type: MediaType) {
        binding.btnFullscreenCmd.isVisible = type == MediaType.IMAGE
            || type == MediaType.GIF
            || type == MediaType.VIDEO
            || type == MediaType.PDF
            || type == MediaType.EPUB
            || type == MediaType.OFFICE_DOCUMENT
    }

    // ── Search Controls ──────────────────────────────────────────────────

    private fun setupSearchControls() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        searchControlsManager = SearchControlsManager(
            binding                   = binding,
            textViewerManagerProvider = { viewManager.textViewerManagerProvider() },
            pdfViewerManagerProvider  = { viewManager.pdfViewerManagerProvider() },
            epubViewerManagerProvider = { viewManager.epubViewerManagerProvider() },
            lifecycleScope            = lifecycleScope,
            inputMethodManager        = imm,
            callback                  = object : SearchControlsManager.SearchControlsCallback {
                override fun getCurrentMediaFile() = viewModel.state.value.mediaFile
                override fun scheduleHideControls() { /* no auto-hide in standalone */ }
                override fun onEpubTranslate()      { viewManager.toggleEpubTranslation() }
                override fun showTranslationSettingsDialog() { /* out of scope */ }
            }
        )
        searchControlsManager?.setupSearchControls()
    }

    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun hidePlaylistControls() {
        binding.btnPreviousCmd.isVisible = false
        binding.btnNextCmd.isVisible = false
        binding.btnSlideshowCmd.isVisible = false
    }

    // ── File Operation Buttons ───────────────────────────────────────────

    private fun setupFileOperationButtons() {
        // Delete
        binding.btnDeleteCmd.visibility = View.VISIBLE
        binding.btnDeleteCmd.setOnClickListener { deleteCurrentFile() }

        // Share
        binding.btnShareCmd.visibility = View.VISIBLE
        binding.btnShareCmd.setOnClickListener { shareCurrentFile() }

        // Favorite
        binding.btnFavorite.visibility = View.VISIBLE
        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }

        // Rename - hidden until capability check completes asynchronously
        binding.btnRenameCmd.isVisible = false
        binding.btnRenameCmd.setOnClickListener { showStandaloneRenameDialog() }

        // File Info
        binding.btnInfoCmd.visibility = View.VISIBLE
        binding.btnInfoCmd.setImageResource(R.drawable.ic_info)
        binding.btnInfoCmd.contentDescription = getString(R.string.file_information)
        binding.btnInfoCmd.setOnClickListener { showFileInfo() }

        // More actions (Open in FMS)
        binding.btnOverflowMenu.visibility = View.VISIBLE
        binding.btnOverflowMenu.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.inflate(R.menu.overflow_menu_standalone_player)
            // S0459: this deprecated host only wires "Open in FMS"; the shared menu also declares
            // image/audio items (e.g. Google Lens) that have no handler here. Hide everything else so
            // no orphaned item renders as a dead tap. (Full host removal tracked under S0393.)
            for (i in 0 until popup.menu.size()) {
                val mi = popup.menu.getItem(i)
                mi.isVisible = mi.itemId == R.id.menu_open_in_fms
            }
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_open_in_fms -> { openInFms(); true }
                    else -> false
                }
            }
            popup.show()
        }

    }

    // ── File Info ─────────────────────────────────────────────────────────

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

    // ── Delete ────────────────────────────────────────────────────────────

    private fun deleteCurrentFile() = fileOperations.deleteCurrentFile()
    private fun shareCurrentFile() = fileOperations.shareCurrentFile()
    private fun openInFms() = fileOperations.openInFms()

    // ── Favorite State Observation ───────────────────────────────────────

    private fun observeFavoriteState() {
        collectOnLifecycle(viewModel.isFavorite) { isFav ->
            binding.btnFavorite.setImageResource(
                if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
            binding.btnFavorite.contentDescription = getString(
                if (isFav) R.string.cd_remove_from_favorites else R.string.cd_add_to_favorites
            )
        }
    }

    // ── Video Controls Setup ─────────────────────────────────────────────

    private fun setupPlaybackControls(pv: androidx.media3.ui.PlayerView) {
        val controlsManager = StandaloneVideoControlsManager(
            playerView = pv,
            callback = object : StandaloneVideoControlsManager.StandaloneVideoControlsCallback {
                override fun showPlaybackControlDialog() = this@StandalonePlayerActivity.showPlaybackControlDialog()
            }
        )
        videoControlsManager = controlsManager
        controlsManager.setupVideoControls()
    }

    private fun setupVideoControls(pv: androidx.media3.ui.PlayerView) {
        if (!mediaCapabilities.supportsVideo) return

        setupPlaybackControls(pv)

        fullscreenManager?.enterFullscreen()

        val trackManager = VideoTrackSelectionManager(
            getPlayer = { viewManager.getExoPlayer() },
            getPlayerView = { pv }
        )
        standaloneTrackSelectionManager = trackManager

        val settingsManager = StandalonePlayerSettingsManager(
            activity = this,
            playerView = pv,
            settingsRepository = settingsRepository,
            trackSelectionManager = trackManager,
            lifecycleScope = lifecycleScope
        )
        playerSettingsManager = settingsManager

        val controlsManager = videoControlsManager ?: return

        // Detect track availability once media is ready via a listener
        viewManager.getExoPlayer()?.addListener(object : androidx.media3.common.Player.Listener {
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
        val touchListener = android.view.View.OnTouchListener { _, event ->
            val consumed = touchDelegate.handleTouchEvent(event)
            if (!consumed && event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                pv.performClick()
            }
            consumed
        }
        pv.setOnTouchListener(touchListener)

        Timber.d("StandalonePlayer: video controls setup complete")
    }

    private fun resolveIncomingUri(): Uri? {
        return when (intent?.action) {
            Intent.ACTION_VIEW -> intent?.data
            Intent.ACTION_SEND -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableExtra(Intent.EXTRA_STREAM)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent?.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)?.firstOrNull()
                } else {
                    @Suppress("DEPRECATION")
                    intent?.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
                }
            }
            else -> intent?.data
        }
    }

    // ── Media Type Routing ────────────────────────────────────────────────

    private fun observeViewModelState() {
        collectOnLifecycle(viewModel.state) { state ->
            binding.progressBar.isVisible = state.isLoading

            if (state.isLoading) return@collectOnLifecycle

            state.errorMessage?.let { error ->
                Timber.w("StandalonePlayer: error state - $error")
                Toast.makeText(this@StandalonePlayerActivity, error, Toast.LENGTH_SHORT).show()
                finish()
                return@collectOnLifecycle
            }

            val file = state.mediaFile ?: return@collectOnLifecycle
            val type = state.mediaType ?: return@collectOnLifecycle

            if (type == MediaType.BINARY_ARCHIVE || type == MediaType.BINARY_DISK ||
                type == MediaType.BINARY_EXECUTABLE || type == MediaType.BINARY_OTHER) {
                Timber.w("StandalonePlayer: unsupported binary type $type for ${file.name}")
                Toast.makeText(
                    this@StandalonePlayerActivity,
                    R.string.unsupported_format_use_external_player,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@collectOnLifecycle
            }

            if (!contentLoaded) {
                val onVideoReady: ((androidx.media3.ui.PlayerView) -> Unit)? =
                    if (type == MediaType.VIDEO) ({ pv -> setupVideoControls(pv) }) else null
                viewManager.show(file, type, onVideoReady)
                if (type == MediaType.AUDIO) {
                    // Standalone audio uses the same PlayerView controller overlay, so wire
                    // Control explicitly even though it does not go through the video setup path.
                    setupPlaybackControls(binding.playerView)
                }
                contentLoaded = true
                applyFullscreenButtonVisibility(type)
                // EpubViewerManager unconditionally shows btnTranslateEpubCmd; enforce orientation guard.
                if (type == MediaType.EPUB) updateEpubTranslatorVisibility()
            }
            updateRenameButtonVisibility()
        }
    }

    private fun observeViewModelEvents() {
        collectOnLifecycle(viewModel.events) { event ->
            when (event) {
                is StandalonePlayerViewModel.StandalonePlayerEvent.ShowError -> {
                    Toast.makeText(this@StandalonePlayerActivity, event.message, Toast.LENGTH_SHORT).show()
                }
                is StandalonePlayerViewModel.StandalonePlayerEvent.FinishActivity -> {
                    finish()
                }
            }
        }
    }

    // ── EPUB Translator Visibility ───────────────────────────────────────

    /** Mirrors CommandPanelController behaviour for standalone mode: hides btnTranslateEpubCmd in portrait, shows it in landscape when EPUB is active and translation is enabled (flavor flag + user setting). */
    private fun updateEpubTranslatorVisibility() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        binding.btnTranslateEpubCmd.isVisible =
            BuildConfig.ENABLE_TRANSLATION && cachedTranslationEnabled && isLandscape && viewManager.isEpubActive()
    }

    /** Keeps [cachedTranslationEnabled] in sync with the settings repository. */
    private fun observeTranslationSettings() {
        collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            cachedTranslationEnabled = settings.enableTranslation
            // Re-evaluate EPUB button whenever settings change
            updateEpubTranslatorVisibility()
        }
    }

    private fun observePipSettings() {
        collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            val isAudio = viewModel.state.value.mediaType == MediaType.AUDIO
            pipManager?.setupPipButton(settings.enablePictureInPicture, isAudio)
        }
    }

    // ── Standalone Rename ────────────────────────────────────────────────────

    /** Checks asynchronously (IO dispatcher) whether the current file URI supports rename, then updates [binding.btnRenameCmd] visibility on the Main thread. SAF documents: check FLAG_SUPPORTS_RENAME via DocumentsContract query. MediaStore URIs: optimistic - show button, handle failure at attempt time. All other schemes: hidden. */
    private fun updateRenameButtonVisibility() = fileOperations.updateRenameButtonVisibility()
    private fun showStandaloneRenameDialog() = fileOperations.showStandaloneRenameDialog()

    private fun observeMessages() {
        collectOnLifecycle(viewModel.messageFlow) { message ->
            Toast.makeText(this@StandalonePlayerActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    // ── PlayerHostCapabilities ────────────────────────────────────────────────

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

    override val videoPlayerHandle: VideoPlayerHandle get() = standaloneVideoPlayerHandle

    private val standaloneVideoPlayerHandle: VideoPlayerHandle by lazy {
        StandaloneVideoPlayerHandle()
    }

    override val isAudioServiceActive: Boolean = false

    override fun showMessage(message: String) = viewModel.showMessage(message)

    override fun requestFinishAfterDelete() = finish()

    private inner class StandaloneVideoPlayerHandle : VideoPlayerHandle {
        override fun getAvailableAudioTracks(): List<VideoTrackSelectionManager.TrackInfo> =
            standaloneTrackSelectionManager?.getAvailableAudioTracks() ?: emptyList()

        override fun selectAudioTrack(groupIndex: Int, trackIndex: Int) {
            standaloneTrackSelectionManager?.selectAudioTrack(groupIndex, trackIndex)
        }

        override fun getAvailableSubtitleTracks(): List<VideoTrackSelectionManager.TrackInfo> =
            standaloneTrackSelectionManager?.getAvailableSubtitleTracks() ?: emptyList()

        override fun selectSubtitleTrack(groupIndex: Int, trackIndex: Int) {
            standaloneTrackSelectionManager?.selectSubtitleTrack(groupIndex, trackIndex)
        }

        override fun getHueAdjustmentDegrees(): Float =
            if (::viewManager.isInitialized) viewManager.getHueAdjustmentDegrees() else 0f

        override fun setHueAdjustmentDegrees(degrees: Float) {
            if (::viewManager.isInitialized) viewManager.setHueAdjustmentDegrees(degrees)
        }

        override fun getBrightnessProgress(): Int =
            if (::viewManager.isInitialized) viewManager.getBrightnessProgress() else 50

        override fun setBrightnessProgress(progress: Int) {
            if (::viewManager.isInitialized) viewManager.setBrightnessProgress(progress)
        }

        override fun getBrightnessPercentOffset(): Int =
            if (::viewManager.isInitialized) viewManager.getBrightnessPercentOffset() else 0

        override fun getPlaybackSpeed(): Float {
            val mediaType = viewModel.state.value.mediaType
            return if (::viewManager.isInitialized) viewManager.getPlaybackSpeed(mediaType) else 1.0f
        }

        override fun setPlaybackSpeed(speed: Float) {
            val mediaType = viewModel.state.value.mediaType
            if (::viewManager.isInitialized) viewManager.setPlaybackSpeed(mediaType, speed)
        }
    }
}
