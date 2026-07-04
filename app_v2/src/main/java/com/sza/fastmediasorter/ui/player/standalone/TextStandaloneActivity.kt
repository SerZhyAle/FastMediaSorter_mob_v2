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
import kotlinx.coroutines.flow.first
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
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
import com.sza.fastmediasorter.databinding.ActivityStandaloneTextBinding
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.dialog.FileInfoDialog
import com.sza.fastmediasorter.ui.player.DefaultPlayerProbe
import com.sza.fastmediasorter.ui.player.StandalonePlayerViewModel
import com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFileOperationsHandler
import com.sza.fastmediasorter.ui.player.helpers.TextViewerManager
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import androidx.appcompat.app.AppCompatActivity
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.share.SharePrintHost
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.ui.player.helpers.DocumentPrintHost
import com.sza.fastmediasorter.ui.player.helpers.DocumentPrintManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.Lazy
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * S0380: specialized standalone activity for plain-text files opened from external intents.
 * Inflates a trimmed layout (no media/pdf/epub/audio view hierarchies) and drives only
 * [TextViewerManager], so the heavy media SDKs (ExoPlayer/Glide/PDF/WebView) are never class-loaded
 * for a text open. File operations / favourite reuse the shared standalone helpers + ViewModel.
 */
@SuppressLint("UnsafeIntentLaunch")
@AndroidEntryPoint
class TextStandaloneActivity : BaseActivity<ActivityStandaloneTextBinding>(), SharePrintHost, DocumentPrintHost {

    private val viewModel: StandalonePlayerViewModel by viewModels()

    private val batchDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> fileOperations.handleBatchDeleteResult(result.resultCode == RESULT_OK) }

    private val recoverableDeleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> fileOperations.handleRecoverableDeleteResult(result.resultCode == RESULT_OK) }

    // S0612: custom-path («..») destination for Copy/Move. The chosen SAF tree is persisted and the
    // pending operation type decides whether the current file is copied or moved into it.
    private var pendingCustomPathOp: com.sza.fastmediasorter.domain.model.FileOperationType? = null
    private val customPathPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val op = pendingCustomPathOp
        pendingCustomPathOp = null
        if (uri == null || op == null) return@registerForActivityResult
        contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val label = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
            ?.takeIf { it.isNotBlank() } ?: getString(R.string.select_folder)
        when (op) {
            com.sza.fastmediasorter.domain.model.FileOperationType.MOVE ->
                fileOperations.moveCurrentFileToPath(uri.toString(), label)
            else ->
                fileOperations.copyCurrentFileToPath(uri.toString(), label)
        }
    }

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
    @Inject lateinit var sendToMenuManager: com.sza.fastmediasorter.ui.share.SendToMenuManager
    @Inject lateinit var keyBindingManager: com.sza.fastmediasorter.core.input.KeyBindingManager
    @Inject lateinit var saveTextNoteUseCase: com.sza.fastmediasorter.domain.usecase.SaveTextNoteUseCase
    @Inject lateinit var capabilityAvailability: CapabilityAvailability
    @Inject lateinit var mediaCapabilities: MediaCapabilities
    @Inject lateinit var fileOperationUseCase: com.sza.fastmediasorter.domain.usecase.FileOperationUseCase
    // S0612: global destination list (no resource context) for the Copy/Move bottom panels.
    @Inject lateinit var getDestinationsUseCase: com.sza.fastmediasorter.domain.usecase.GetDestinationsUseCase

    // S0393 wave-C: enables in-place text editing (save) for writable local text files.
    private val textEditorSaveFlow by lazy {
        com.sza.fastmediasorter.ui.player.helpers.TextEditorSaveFlow(
            context = this,
            saveTextNoteUseCase = saveTextNoteUseCase,
            scope = lifecycleScope,
        )
    }

    // S0393 U4/U5: keyboard / D-pad handler (text-scroll + paging).
    private lateinit var keyboardHandler: com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler

    private val fileOperations: StandaloneFileOperationsHandler by lazy {
        StandaloneFileOperationsHandler(
            activity = this,
            root = binding.root,
            getCurrentMediaFile = { viewModel.state.value.mediaFile },
            resolveOpenInFmsTarget = resolveOpenInFmsTargetUseCase,
            onRenameComplete = { newUri, newName -> viewModel.onRenameComplete(newUri, newName) },
            updateAudioMediaItem = { /* no audio in text activity */ },
            batchDeleteLauncher = batchDeleteLauncher,
            recoverableDeleteLauncher = recoverableDeleteLauncher,
            sendToMenuManager = sendToMenuManager,
            getCurrentSettings = { settingsRepository.getSettings().first() },
            fileOperationUseCase = fileOperationUseCase,
            getDestinationsUseCase = getDestinationsUseCase,
            onPickCustomFolderForCopy = {
                pendingCustomPathOp = com.sza.fastmediasorter.domain.model.FileOperationType.COPY
                customPathPickerLauncher.launch(null)
            },
        )
    }

    // S0612: Copy/Move destination panels, reusing the shared in-app manager bound to this layout root.
    // No resource context: getCurrentResourceId returns -1 so the global destination list is shown intact.
    private val destinationButtonsManager: com.sza.fastmediasorter.ui.player.DestinationButtonsManager by lazy {
        com.sza.fastmediasorter.ui.player.DestinationButtonsManager(
            root = binding.root,
            settingsRepository = settingsRepository,
            getDestinationsUseCase = getDestinationsUseCase,
            lifecycleScope = lifecycleScope,
            callback = object : com.sza.fastmediasorter.ui.player.DestinationButtonsManager.DestinationButtonsCallback {
                override fun onCopyClicked(destination: MediaResource) = fileOperations.copyCurrentFileTo(destination)
                override fun onMoveClicked(destination: MediaResource) = fileOperations.moveCurrentFileTo(destination)
                override fun onCustomPathPickerRequested(operationType: com.sza.fastmediasorter.domain.model.FileOperationType) {
                    pendingCustomPathOp = operationType
                    customPathPickerLauncher.launch(null)
                }
                override fun getCurrentResourceId(): Long = -1L
                override fun onUpdateCommandAvailability() { /* panels are self-managed in standalone */ }
                override fun shouldShowDestinationPanels(): Boolean = viewModel.state.value.mediaFile != null
            },
            shouldNumberSlots = { false },
            slotKeyGlyph = { null },
        )
    }

    private val networkFileManager: NetworkFileManager by lazy {
        NetworkFileManager(
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
            callback = object : NetworkFileManager.NetworkFileCallback {
                override fun getCurrentResource() = null
                override fun showError(message: String) = showToastError(message)
            }
        )
    }

    // S0872: explicit Lazy so onDestroy can release it only when it was actually created.
    private val translationManagerDelegate = lazy {
        TranslationManager(
            context = this,
            settingsRepository = settingsRepository,
            callback = object : TranslationManager.TranslationCallback {
                override fun showError(message: String) = showToastError(message)
                // S0393 wave-C: real download prompt so first-use translation doesn't silently no-op.
                override fun showModelDownloadPrompt(
                    languageName: String,
                    onConfirm: () -> Unit,
                    onCancel: () -> Unit
                ) {
                    if (isFinishing || isDestroyed) { onCancel(); return }
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@TextStandaloneActivity)
                        .setTitle(R.string.download_translation_model_title)
                        .setMessage(getString(R.string.download_translation_model_message, languageName))
                        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
                        .setOnCancelListener { onCancel() }
                        .show()
                }
            }
        )
    }
    private val translationManager: TranslationManager by translationManagerDelegate

    private val textViewerManagerDelegate = lazy {
        TextViewerManager(
            context = this,
            root = binding.root,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            saveFlow = textEditorSaveFlow,
            callback = object : TextViewerManager.TextViewerCallback {
                override fun showError(message: String) = showToastError(message)
                override fun showTranslationSettingsDialog() { /* not exposed in standalone */ }
                override fun exitFullscreenMode() { /* not exposed in standalone */ }
                override fun setTouchZonesEnabled(enabled: Boolean) { /* not exposed in standalone */ }
                override fun showEncodingDialog() { /* not exposed in standalone */ }
                override fun launchEditorCalculator(initialInput: String) { /* read-only standalone text */ }
                override fun launchSelectionCalculator(initialInput: String) { /* read-only standalone text */ }
                override fun finishActivity() { finish() }
            },
            translationManager = translationManager
        )
    }
    private val textViewerManager: TextViewerManager by textViewerManagerDelegate

    private val pagingControls: StandalonePagingControlsBinder by lazy {
        StandalonePagingControlsBinder(
            viewModel = viewModel,
            btnPrev = binding.btnPagePrev,
            btnNext = binding.btnPageNext,
            btnRandom = binding.btnPageRandom,
            btnSlideshow = binding.btnPageSlideshow,
        )
    }

    /** Path of the file last rendered; lets folder paging swap to a neighbour on change. */
    private var lastShownPath: String? = null

    // S0613: print is a «Send to..» receiver. Implementing SharePrintHost makes the Print row appear,
    // and DocumentPrintHost lets the shared DocumentPrintManager print this host's text file.
    private val documentPrintManager by lazy {
        DocumentPrintManager(host = this, mediaCapabilities = mediaCapabilities)
    }

    override val printHostActivity: AppCompatActivity get() = this
    override val printNetworkFileManager: NetworkFileManager get() = networkFileManager
    // No Office viewer in the text host - only TEXT reaches it, so there is no internal Office print path.
    override fun printOfficeDocument(): Boolean = false
    override fun showPrintMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun printMediaFile(mediaFile: MediaFile): Boolean {
        documentPrintManager.printCurrentFile(mediaFile)
        return true
    }

    override fun getViewBinding(): ActivityStandaloneTextBinding =
        ActivityStandaloneTextBinding.inflate(layoutInflater)

    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun getInitialFocusView(): View = binding.btnBack

    override fun setupViews() {
        setupWindowAndInsets()
        setupCloseButton()
        setupBackPressHandler()
        setupFileOperationButtons()
        textViewerManager.setupControls()
        // S0920: the standalone text host always shows its own top command panel (with Back), so the
        // shared text-viewer's floating close button is redundant here and only hides the text. It is
        // meant solely for the in-app fullscreen text overlay; keep it hidden by staying in panel mode.
        textViewerManager.updateCloseButtonVisibility(showCommandPanel = true)
        setupTextActionButtons()
        pagingControls.setupClicks()
        setupKeyboardHandler()
        parseIncomingIntent()
    }

    // S0393 wave-C: surface the text-action buttons that TextViewerManager.setupControls() already binds
    // (copy is universal; translate is flavor-gated). Were present-but-gone (dead-weight) before.
    private fun setupTextActionButtons() {
        binding.btnCopyTextCmd.isVisible = true
        binding.btnTranslateTextCmd.isVisible = capabilityAvailability.isTranslationAvailable()
        binding.btnSearchTextCmd.isVisible = true
        binding.btnSearchTextCmd.setOnClickListener { showTextSearchDialog() }
    }

    // S0393 wave-C: simple find-in-text - prompt for a query, highlight the first match, report the count.
    private fun showTextSearchDialog() {
        val input = android.widget.EditText(this).apply { hint = getString(R.string.search) }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val q = input.text?.toString()?.trim().orEmpty()
                if (q.isNotEmpty()) {
                    val matches = textViewerManager.searchText(q)
                    if (matches > 0) textViewerManager.highlightSearchMatch(q, 0)
                    Toast.makeText(this, "${getString(R.string.search)}: $matches", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // S0393 U4/U5: keyboard / D-pad layer (text-scroll PAGE_UP/DOWN/HOME/END + paging), ported from legacy host.
    private fun setupKeyboardHandler() {
        keyboardHandler = com.sza.fastmediasorter.ui.player.helpers.StandaloneKeyboardManager(
            keyBindingManager = keyBindingManager,
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
            onTextScrollDown = { textViewerManager.scrollDown() },
            onTextScrollUp = { textViewerManager.scrollUp() },
            onTextHome = { textViewerManager.scrollToTop() },
            onTextEnd = { textViewerManager.scrollToBottom() },
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

    // Standalone text surface routes motion through its own keyboard/pointer handler; the shared
    // gamepad navigation layer must not also move focus here. S0508.
    override fun shouldHandleGamepadNavigation(): Boolean = false

    // S0393: reapply window insets after rotation (configChanges -> no recreate), ported from legacy.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.topCommandPanel.post { binding.topCommandPanel.requestApplyInsets() }
    }

    private fun setupWindowAndInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // S0920: keep the OS status bar visible on the dark player chrome (see StandaloneSystemBars).
        com.sza.fastmediasorter.ui.player.helpers.StandaloneSystemBars.showStatusBarWithLightIcons(this)
        ViewCompat.setOnApplyWindowInsetsListener(binding.topCommandPanel) { view, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.captionBar()
            )
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(nav.left, top.top, nav.right, view.paddingBottom)
            insets
        }
        // S0612: the Copy/Move panels container is the bottom-most child, so the nav-bar inset moves
        // here. When the panels are GONE the 0-height container still reserves the nav gap, keeping the
        // text content above the nav bar; when visible, the destination grids clear it too.
        binding.root.findViewById<View>(R.id.bottomPanelsContainer)?.let { panels ->
            ViewCompat.setOnApplyWindowInsetsListener(panels) { view, insets ->
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.setPadding(0, 0, 0, nav.bottom)
                insets
            }
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
        // S0920: wire the Copy/Move panel headers so a header tap expands/collapses its grid.
        destinationButtonsManager.bindHeaderToggles()
        binding.btnDeleteCmd.isVisible = true
        binding.btnDeleteCmd.setOnClickListener { fileOperations.deleteCurrentFile() }
        binding.btnShareCmd.isVisible = true
        binding.btnShareCmd.setOnClickListener { fileOperations.shareCurrentFile() }
        binding.btnFavorite.isVisible = true
        binding.btnFavorite.setOnClickListener { viewModel.toggleFavorite() }
        binding.btnRenameCmd.isVisible = false
        binding.btnRenameCmd.setOnClickListener { fileOperations.showStandaloneRenameDialog() }
        binding.btnInfoCmd.isVisible = true
        binding.btnInfoCmd.setOnClickListener { showFileInfo() }
        binding.btnOverflowMenu.isVisible = true
        binding.btnOverflowMenu.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.inflate(R.menu.overflow_menu_standalone_player)
            // S0393: this menu is shared with the image/audio hosts - hide their type-specific items here.
            // S0459 §11.7: "Send to Keep" is dropped from the overflow - the unified Send-to menu
            // (btnShareCmd -> SendToMenuManager) already offers the Keep-text receiver for TEXT content.
            listOf(R.id.menu_draw_overlay, R.id.menu_edit_crop_to_file, R.id.menu_edit_compress, R.id.menu_edit_image,
                R.id.menu_black_screen, R.id.menu_google_lens, R.id.menu_youtube_music, R.id.menu_ocr_image,
                R.id.menu_translate_image, R.id.menu_print, R.id.menu_save_frame,
                R.id.menu_sleep_timer, R.id.menu_lyrics, R.id.menu_playback_speed)
                .forEach { popup.menu.findItem(it)?.isVisible = false }
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_open_in_fms -> { fileOperations.openInFms(); true }
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
            Timber.d("TextStandalone: ignoring default-player probe URI, finishing")
            finish()
            return
        }
        val displayName = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            Timber.w(e, "TextStandalone: failed to query display name")
            null
        } ?: uri.lastPathSegment
        // Folder paging enumerates only text neighbours - the only type this host renders.
        viewModel.setHostSupportedTypes(setOf(MediaType.TEXT))
        viewModel.loadFromUri(uri, intent?.type, displayName)
    }

    override fun observeData() {
        collectOnLifecycle(viewModel.state) { state ->
            binding.progressBar.isVisible = state.isLoading
            if (state.isLoading) return@collectOnLifecycle
            state.errorMessage?.let { error ->
                Toast.makeText(this@TextStandaloneActivity, error, Toast.LENGTH_SHORT).show()
                finish()
                return@collectOnLifecycle
            }
            val file = state.mediaFile ?: return@collectOnLifecycle
            val type = state.mediaType ?: return@collectOnLifecycle
            if (type != MediaType.TEXT) {
                Toast.makeText(
                    this@TextStandaloneActivity,
                    R.string.unsupported_format_use_external_player,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@collectOnLifecycle
            }
            if (file.path != lastShownPath) {
                // S0393 wave-C: allow editing for writable local text files (content-URI opens stay read-only).
                val writable = file.path.startsWith("/") && runCatching { java.io.File(file.path).canWrite() }.getOrDefault(false)
                textViewerManager.displayText(file, isWritable = writable)
                binding.btnEditTextCmd.isVisible = writable
                destinationButtonsManager.populateDestinationButtons()
                lastShownPath = file.path
            }
            pagingControls.applyState(state.supportsFolderPaging, state.isSlideshowActive)
            updateRenameButtonVisibility()
        }
        collectOnLifecycle(viewModel.isFavorite) { isFav ->
            binding.btnFavorite.setImageResource(
                if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
        }
        collectOnLifecycle(viewModel.messageFlow) { message ->
            Toast.makeText(this@TextStandaloneActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateRenameButtonVisibility() = fileOperations.updateRenameButtonVisibility()

    private fun showToastError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        if (textViewerManagerDelegate.isInitialized()) textViewerManager.release()
        // S0872: TextViewerManager.release() never touches translationManager - release it here, only if built.
        if (translationManagerDelegate.isInitialized()) translationManager.release()
        super.onDestroy()
    }
}
