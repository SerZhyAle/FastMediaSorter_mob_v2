package com.sza.fastmediasorter.ui.player.standalone

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.ActionMode
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.github.chrisbanes.photoview.OnSingleFlingListener
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.capability.MediaCapabilities
import com.sza.fastmediasorter.core.share.SharePrintHost
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityStandaloneDocumentBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaResource
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.ui.player.DefaultPlayerProbe
import com.sza.fastmediasorter.ui.player.StandalonePlayerViewModel
import com.sza.fastmediasorter.ui.player.helpers.DocumentPrintHost
import com.sza.fastmediasorter.ui.player.helpers.DocumentPrintManager
import com.sza.fastmediasorter.ui.player.helpers.DocumentSelectionActionModeAugmentingCallback
import com.sza.fastmediasorter.ui.player.helpers.DocumentSelectionActionModeCallback
import com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager
import com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
import com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentOpenManager
import com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerHost
import com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerOutcome
import com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerProviderFactory
import com.sza.fastmediasorter.ui.player.helpers.PdfPageSwipeDetector
import com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFileOperationsHandler
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.util.showBoundTo
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * S0380: specialized standalone activity for document files (PDF / EPUB / Office) opened from
 * external intents. Inflates a trimmed layout (no audio/video/text-editor view hierarchies) and
 * drives only the decoupled [PdfViewerManager] / [EpubViewerManager] / [OfficeDocumentViewerHost]
 * picked by the resolved [MediaType]. File operations / favourite reuse the shared standalone
 * helpers + ViewModel. Unsupported types are rejected with the unsupported-format toast.
 */
@SuppressLint("UnsafeIntentLaunch")
@AndroidEntryPoint
class DocumentStandaloneActivity : BaseActivity<ActivityStandaloneDocumentBinding>(), SharePrintHost, DocumentPrintHost {

    private val viewModel: StandalonePlayerViewModel by viewModels()

    // Root-based view seam: the pdf/epub navigation buttons live in id-less <include> partials, so
    // ViewBinding does not expose them as binding fields - reach them via PlayerBindingSafeViews.
    private val safeViews: PlayerBindingSafeViews by lazy { PlayerBindingSafeViews(binding.root) }

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

    // S1329: the domain types this host used to field-inject - the six repositories and use cases plus
    // the network collaborators it only forwarded - now live behind the factory, which builds each
    // manager itself. The host names no data-layer type of its own (Rule 3).
    @Inject lateinit var standaloneHostFactory: StandaloneHostFactory

    // S0473: usage-statistics sink, forwarded into the standalone PdfViewerManager.
    @Inject lateinit var statsSink: com.sza.fastmediasorter.domain.stats.StatsSink

    @Inject lateinit var keyBindingManager: com.sza.fastmediasorter.core.input.KeyBindingManager

    @Inject lateinit var capabilityAvailability: CapabilityAvailability

    @Inject lateinit var mediaCapabilities: MediaCapabilities

    // S0393 U4/U5: keyboard / D-pad handler (pdf/epub keys + paging).
    private lateinit var keyboardHandler: com.sza.fastmediasorter.ui.player.helpers.PlayerKeyboardHandler

    private val networkFileManager: NetworkFileManager by lazy {
        standaloneHostFactory.createNetworkFileManager(
            context = this,
            callback = object : NetworkFileManager.NetworkFileCallback {
                override fun getCurrentResource() = null
                override fun showError(message: String) = showToastError(message)
            }
        )
    }

    // S0872: explicit Lazy so onDestroy can release it only when it was actually created.
    private val translationManagerDelegate = lazy {
        standaloneHostFactory.createTranslationManager(
            context = this,
            callback = object : TranslationManager.TranslationCallback {
                override fun showError(message: String) = showToastError(message)

                // S0393 wave-C: real download prompt so first-use translation doesn't silently no-op.
                override fun showModelDownloadPrompt(
                    languageName: String,
                    onConfirm: () -> Unit,
                    onCancel: () -> Unit
                ) {
                    if (isFinishing || isDestroyed) {
                        onCancel()
                        return
                    }
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@DocumentStandaloneActivity)
                        .setTitle(R.string.download_translation_model_title)
                        .setMessage(getString(R.string.download_translation_model_message, languageName))
                        .setPositiveButton(android.R.string.ok) { _, _ -> onConfirm() }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
                        .setOnCancelListener { onCancel() }
                        .showBoundTo(this@DocumentStandaloneActivity)
                }
            }
        )
    }
    private val translationManager: TranslationManager by translationManagerDelegate

    private val fileOperations: StandaloneFileOperationsHandler by lazy {
        standaloneHostFactory.createFileOperationsHandler(
            activity = this,
            root = binding.root,
            callbacks = StandaloneFileOpsCallbacks(
                getCurrentMediaFile = { viewModel.state.value.mediaFile },
                onRenameComplete = { newUri, newName -> viewModel.onRenameComplete(newUri, newName) },
                updateAudioMediaItem = { /* no audio in document activity */ },
                batchDeleteLauncher = batchDeleteLauncher,
                recoverableDeleteLauncher = recoverableDeleteLauncher,
                onPickCustomFolderForCopy = {
                    pendingCustomPathOp = com.sza.fastmediasorter.domain.model.FileOperationType.COPY
                    customPathPickerLauncher.launch(null)
                },
            ),
        )
    }

    // S0612: Copy/Move destination panels, reusing the shared in-app manager bound to this layout root.
    // The factory supplies the global destination list - there is no resource context here, so
    // getCurrentResourceId returns -1 and that list is shown intact.
    private val destinationButtonsManager: com.sza.fastmediasorter.ui.player.DestinationButtonsManager by lazy {
        standaloneHostFactory.createDestinationButtons(
            root = binding.root,
            lifecycleScope = lifecycleScope,
            callback = object : com.sza.fastmediasorter.ui.player.DestinationButtonsManager.DestinationButtonsCallback {
                override fun onCopyClicked(destination: MediaResource) = fileOperations.copyCurrentFileTo(destination)
                override fun onMoveClicked(destination: MediaResource) = fileOperations.moveCurrentFileTo(destination)
                override fun onCustomPathPickerRequested(
                    operationType: com.sza.fastmediasorter.domain.model.FileOperationType
                ) {
                    pendingCustomPathOp = operationType
                    customPathPickerLauncher.launch(null)
                }
                override fun getCurrentResourceId(): Long = -1L
                override fun onUpdateCommandAvailability() { /* panels are self-managed in standalone */ }
                override fun shouldShowDestinationPanels(): Boolean =
                    viewModel.state.value.mediaFile != null && !destinationPanelsSuppressed
            },
            shouldNumberSlots = { false },
            slotKeyGlyph = { null },
        )
    }

    // S0741: fullscreen document viewers suppress the Copy/Move destination panels while active.
    // The async populate coroutine can finish after fullscreen already hid them, so the callback gate
    // above plus this exact-state restore keep the panels from flashing over the viewer.
    private var destinationPanelsSuppressed = false
    private var copyPanelVisibleBeforeFullscreen = false
    private var movePanelVisibleBeforeFullscreen = false

    private fun setDestinationPanelsSuppressed(suppressed: Boolean) {
        destinationPanelsSuppressed = suppressed
        val copyPanel: View? = binding.bottomPanelsContainer.copyToPanel
        val movePanel: View? = binding.bottomPanelsContainer.moveToPanel
        if (suppressed) {
            copyPanelVisibleBeforeFullscreen = copyPanel?.isVisible == true
            movePanelVisibleBeforeFullscreen = movePanel?.isVisible == true
            copyPanel?.isVisible = false
            movePanel?.isVisible = false
        } else {
            copyPanel?.isVisible = copyPanelVisibleBeforeFullscreen
            movePanel?.isVisible = movePanelVisibleBeforeFullscreen
        }
    }

    // Lazily created document viewers - only the one matching the resolved type is ever touched.
    // S0873: explicit Lazy so releaseActiveViewer()/onDestroy can release it when initialized - a
    // mixed-type folder page (PDF -> EPUB) with an in-flight load can resurrect an off-type viewer.
    private val pdfViewerManagerDelegate = lazy {
        standaloneHostFactory.createPdfViewerManager(
            root = binding.root,
            networkFileManager = networkFileManager,
            coroutineScope = lifecycleScope,
            callback = object : PdfViewerManager.PdfViewerCallback {
                override fun showError(message: String) = showToastError(message)

                // S0393 wave-C: show extracted page text in a scrollable, copyable dialog.
                override fun displayOcrText(text: String) {
                    com.sza.fastmediasorter.ui.dialog.ScrollableTextDialog.show(
                        this@DocumentStandaloneActivity,
                        title = getString(R.string.camera_ocr_pane_original),
                        message = text,
                        monospace = true,
                    )
                }
                override fun displayTranslatedText(text: String) { /* shown inline in the PDF overlay */ }
                override fun shareFileToGoogleLens(file: File) = shareToGoogleLens(file)
                override fun isLandscapeMode(): Boolean =
                    resources.configuration.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE
                override fun onEnterFullscreenMode() {
                    binding.topCommandPanel.isVisible = false
                    setDestinationPanelsSuppressed(true)
                }
                override fun onExitFullscreenMode() {
                    binding.topCommandPanel.isVisible = true
                    setDestinationPanelsSuppressed(false)
                }
            },
            translationManager = translationManager,
            statsSink = statsSink
        )
    }
    private val pdfViewerManager: PdfViewerManager by pdfViewerManagerDelegate

    // S0953: last PDF touch-down point, recorded so a long-press can pre-select the word under it
    // (mirrors PlayerGestureSetupManager's in-app reference). Meaningful only while a PDF is shown.
    private var lastPdfDownX = 0f
    private var lastPdfDownY = 0f

    // S0873: explicit Lazy (see pdf) so an initialized EPUB viewer is released on teardown/type-switch.
    private val epubViewerManagerDelegate = lazy {
        standaloneHostFactory.createEpubViewerManager(
            root = binding.root,
            networkFileManager = networkFileManager,
            coroutineScope = lifecycleScope,
            callback = object : EpubViewerManager.EpubViewerCallback {
                override fun showError(message: String) = showToastError(message)
                override fun displayTranslatedText(text: String) { /* shown inline in the EPUB overlay */ }
                override fun onEnterFullscreenMode() {
                    binding.topCommandPanel.isVisible = false
                    setDestinationPanelsSuppressed(true)
                }
                override fun onExitFullscreenMode() {
                    binding.topCommandPanel.isVisible = true
                    setDestinationPanelsSuppressed(false)
                }
            },
            translationManager = translationManager
        )
    }
    private val epubViewerManager: EpubViewerManager by epubViewerManagerDelegate

    // S0301 / S0380: embedded Office viewer host. Market = no-op host (external handoff), noLegal =
    // engine-backed read-only viewer. Built against the trimmed layout's officeDocumentViewerContainer.
    // Explicit Lazy so onDestroy can release it only when it was actually created (internal open).
    private val officeViewerHostDelegate = lazy {
        OfficeDocumentViewerProviderFactory().createViewerHost(
            root = binding.root,
            coroutineScope = lifecycleScope,
            callback = object : OfficeDocumentViewerHost.Callback {
                override fun showError(message: String) = showToastError(message)
                override fun onEnterFullscreenMode() {
                    binding.topCommandPanel.isVisible = false
                    setDestinationPanelsSuppressed(true)
                }
                override fun onExitFullscreenMode() {
                    binding.topCommandPanel.isVisible = true
                    setDestinationPanelsSuppressed(false)
                }
                override fun onRequireExternalFallback(mediaFile: MediaFile) =
                    openOfficeExternally(mediaFile)
            }
        )
    }
    private val officeViewerHost: OfficeDocumentViewerHost by officeViewerHostDelegate

    private val officeViewerProvider by lazy { OfficeDocumentViewerProviderFactory().create() }

    private val pagingControls: StandalonePagingControlsBinder by lazy {
        StandalonePagingControlsBinder(
            viewModel = viewModel,
            btnPrev = binding.btnPagePrev,
            btnNext = binding.btnPageNext,
            btnRandom = binding.btnPageRandom,
            btnSlideshow = binding.btnPageSlideshow,
        )
    }

    /** Resolved document type for the loaded file; gates which viewer the controls drive. */
    private var resolvedType: MediaType? = null

    /** Path of the file last rendered; lets folder paging swap to a neighbour on change. */
    private var lastShownPath: String? = null

    // S0613: print is a «Send to..» receiver. Implementing SharePrintHost makes the Print row appear
    // (the menu gates on host capability), and DocumentPrintHost lets the shared DocumentPrintManager
    // run here without depending on PlayerActivity.
    private val documentPrintManager by lazy {
        DocumentPrintManager(host = this, mediaCapabilities = mediaCapabilities)
    }

    override val printHostActivity: AppCompatActivity get() = this
    override val printNetworkFileManager: NetworkFileManager get() = networkFileManager

    // Print the rendered Office document only when the internal viewer was actually built (noLegal
    // internal render path); market external-handoff never creates it, so there is nothing to print.
    override fun printOfficeDocument(): Boolean =
        if (officeViewerHostDelegate.isInitialized()) officeViewerHost.print() else false
    override fun showPrintMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun printMediaFile(mediaFile: MediaFile): Boolean {
        documentPrintManager.printCurrentFile(mediaFile)
        return true
    }

    override fun getViewBinding(): ActivityStandaloneDocumentBinding =
        ActivityStandaloneDocumentBinding.inflate(layoutInflater)

    override fun shouldEnableEdgeToEdge(): Boolean = false

    override fun getInitialFocusView(): View = binding.btnBack

    override fun setupViews() {
        setupWindowAndInsets()
        setupCloseButton()
        setupBackPressHandler()
        setupFileOperationButtons()
        setupPdfButtons()
        setupEpubButtons()
        pagingControls.setupClicks()
        setupKeyboardHandler()
        parseIncomingIntent()
    }

    // S0393 U4/U5: keyboard / D-pad layer (pdf/epub page-keys + paging), ported from legacy host.
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
            onPdfNextPage = { pdfViewerManager.showNextPage() },
            onPdfPreviousPage = { pdfViewerManager.showPreviousPage() },
            onPdfHome = { pdfViewerManager.showFirstPage() },
            onEpubNextPage = { epubViewerManager.showNextChapter() },
            onEpubPreviousPage = { epubViewerManager.showPreviousChapter() },
            onEpubHome = { epubViewerManager.showFirstChapter() },
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
            keyboardHandler.handleKeyDown(keyCode, event)
        ) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (::keyboardHandler.isInitialized &&
            keyboardHandler.handlePointerEvent(window.decorView, event)
        ) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // Standalone document surface routes motion through its own keyboard/pointer handler; the shared
    // gamepad navigation layer must not also move focus here. S0508.
    override fun shouldHandleGamepadNavigation(): Boolean = false

    // S0393 U3: WebView floating-selection ActionMode augmentation (Translate / Search-in-Google),
    // ported from legacy host. WebView can't use setCustomSelectionActionModeCallback, so intercept
    // startActionMode and wrap the system callback when an EPUB/Office WebView selection is active.
    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        val documentCallback = if (type == ActionMode.TYPE_FLOATING) {
            activeDocumentSelectionCallback()
        } else {
            null
        }
        return if (documentCallback != null && callback != null) {
            super.startActionMode(
                DocumentSelectionActionModeAugmentingCallback(callback, documentCallback),
                type
            )
        } else {
            super.startActionMode(callback, type)
        }
    }

    /** Selection callback of the active WebView document viewer (Office over EPUB); null otherwise.
     *  Guards avoid forcing the lazy EPUB manager when no EPUB WebView is visible. */
    private fun activeDocumentSelectionCallback(): DocumentSelectionActionModeCallback? {
        val officeCallback = if (officeViewerHostDelegate.isInitialized() && officeViewerHost.isActive) {
            officeViewerHost.getSelectionActionModeCallback()
        } else {
            null
        }
        return officeCallback ?: epubViewerManager
            .takeIf { safeViews.epubWebViewOrNull?.isVisible == true }
            ?.getSelectionActionModeCallback()
    }

    // S0393 U7/U8: EPUB translator button is orientation-aware (landscape-only, translation enabled),
    // ported from legacy host. Hidden in portrait to mirror the in-app command panel.
    private var cachedTranslationEnabled = false

    private fun observeTranslationSettings() {
        collectOnLifecycle(appSettings) { settings ->
            cachedTranslationEnabled = settings.enableTranslation
            updateEpubTranslatorVisibility()
        }
    }

    private fun updateEpubTranslatorVisibility() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val epubActive = safeViews.epubWebViewOrNull?.isVisible == true
        binding.btnTranslateEpubCmd.isVisible = capabilityAvailability.isTranslationAvailable() &&
            cachedTranslationEnabled && isLandscape && epubActive
        // S0393 wave-C: EPUB OCR button (ML-Kit-gated like translation).
        binding.btnOcrEpubCmd.isVisible = capabilityAvailability.isTranslationAvailable() && epubActive
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateEpubTranslatorVisibility()
        // S0393: reapply window insets after rotation (configChanges -> no recreate), ported from legacy.
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
        // S0612: the Copy/Move panels container is the bottom-most child, so the nav-bar bottom inset
        // moves here from mediaContentArea. setDecorFitsSystemWindows(false) draws behind the nav bar;
        // a GONE 0-height container still reserves the nav gap, keeping the document content above it.
        binding.bottomPanelsContainer.root.let { panels ->
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
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Exit document fullscreen first; only then leave the activity.
                    when {
                        resolvedType == MediaType.PDF && pdfViewerManager.isInFullscreenMode() ->
                            pdfViewerManager.exitFullscreenMode()
                        resolvedType == MediaType.EPUB && epubViewerManager.isInFullscreenMode() ->
                            epubViewerManager.exitFullscreenMode()
                        else -> {
                            if (isTaskRoot) {
                                val intent = Intent(
                                    this@DocumentStandaloneActivity,
                                    com.sza.fastmediasorter.ui.main.MainActivity::class.java
                                ).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                                startActivity(intent)
                            }
                            finish()
                        }
                    }
                }
            }
        )
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
            // S1407: icons off by default on PopupMenu - match the embedded player's rendering.
            popup.applyStandaloneOverflowIcons()
            // S0393: this menu is shared with the image/audio hosts - hide their type-specific items here.
            // S0410 items (menu_image_text_settings / menu_draw_overlay) are image-host-only too: keep them
            // hidden here or they leak into the document overflow menu with no click handler (dead taps).
            // S1364: hiding menu_edit_section_standalone removes its children with it, so the editing
            // ids are no longer listed individually. That also retires menu_rotate_content_standalone
            // on this host, which rendered but had no branch in the when below - a dead tap.
            listOf(
                R.id.menu_edit_section_standalone,
                R.id.menu_rename_standalone, R.id.menu_autorotate_standalone,
                R.id.menu_black_screen, R.id.menu_google_lens, R.id.menu_youtube_music, R.id.menu_ocr_image,
                R.id.menu_translate_image, R.id.menu_image_text_settings,
                R.id.menu_print, R.id.menu_save_frame,
                R.id.menu_sleep_timer, R.id.menu_lyrics, R.id.menu_playback_speed
            )
                .forEach { popup.menu.findItem(it)?.isVisible = false }
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_open_in_fms -> {
                        fileOperations.openInFms()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    // ── PDF / EPUB navigation controls (mirror StandalonePlayerActivity) ──────────

    // Touch is forwarded to the PhotoView attacher; tap/long-press semantics come from the native
    // callbacks below, so performClick is not needed here.
    @SuppressLint("ClickableViewAccessibility")
    private fun setupPdfButtons() {
        // Navigation/zoom buttons live in player_pdf_controls_overlay_content (id-less include) →
        // reach via safeViews. The cmd buttons (translate/search) are direct command-panel children.
        safeViews.btnPdfPrevPage.setOnClickListener { pdfViewerManager.showPreviousPage() }
        safeViews.btnPdfHome.setOnClickListener { pdfViewerManager.showFirstPage() }
        safeViews.btnPdfNextPage.setOnClickListener { pdfViewerManager.showNextPage() }
        // S0949: route buttons through the shared PDF zoom-step contract (0.3x..10x, clamped) so
        // buttons and the horizontal-swipe gesture stay in one range on both hosts.
        safeViews.btnPdfZoomIn.setOnClickListener { pdfViewerManager.stepPdfZoom(zoomIn = true) }
        safeViews.btnPdfZoomOut.setOnClickListener { pdfViewerManager.stepPdfZoom(zoomIn = false) }
        binding.btnTranslatePdfCmd.setOnClickListener { pdfViewerManager.toggleTranslation() }
        binding.btnSearchPdfCmd.setOnClickListener { pdfViewerManager.showThumbnailNavigation() }
        // S0393 wave-C: OCR current page + share current page to Google Lens (buttons already in layout;
        // visibility owned by the viewer manager per settings).
        binding.btnOcrPdfCmd.setOnClickListener { pdfViewerManager.extractTextFromCurrentPage() }
        binding.btnGoogleLensPdfCmd.setOnClickListener { pdfViewerManager.shareCurrentPageToGoogleLens() }
        // S0951: touch parity with the in-app player. Uses PhotoView's native single-fling callback,
        // which after S1273 only carries the horizontal zoom step - page turns come from the touch
        // listener below. handlePdfFling owns the scroll-mode guard, so no extra checks here.
        binding.photoView.setOnSingleFlingListener(
            OnSingleFlingListener { e1, e2, velocityX, _ ->
                pdfViewerManager.handlePdfFling(e1, e2, velocityX)
            }
        )
        // S0953: full PDF touch parity with the in-app player (PlayerGestureSetupManager reference) -
        // single-tap opens links, long-press opens text selection. PhotoView native callbacks; the
        // touch listener only records the down point and forwards to the attacher so pinch/pan/fling
        // stay intact. handlePdfTap/handlePdfLongPress no-op when no PDF page is loaded.
        binding.photoView.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                return pdfViewerManager.handlePdfTap(e.x, e.y)
            }

            override fun onDoubleTap(e: MotionEvent): Boolean = false // pinch-to-zoom only, matches in-app

            override fun onDoubleTapEvent(e: MotionEvent): Boolean = false
        })
        // S1273: same page-turn gesture as the in-app player - PhotoView never delivers a two-finger
        // or slow swipe to a fling listener, so it is detected on the raw stream ahead of the attacher.
        PdfPageSwipeDetector.install(
            binding.photoView,
            object : PdfPageSwipeDetector.Host {
                override fun isPageSwipeEnabled(): Boolean = pdfViewerManager.isPageSwipeEnabled()

                override fun currentScale(): Float = binding.photoView.scale

                override fun turnPage(next: Boolean) = pdfViewerManager.turnPage(next)
            },
            onDown = { ev ->
                lastPdfDownX = ev.x
                lastPdfDownY = ev.y
            }
        )
        binding.photoView.setOnLongClickListener {
            pdfViewerManager.handlePdfLongPress(lastPdfDownX, lastPdfDownY)
        }
    }

    // S0393 wave-C: delegate to the shared host-agnostic Google Lens share.
    private fun shareToGoogleLens(file: File) =
        com.sza.fastmediasorter.core.share.GoogleLensShare.shareImageFile(this, file)

    private fun setupEpubButtons() {
        // Navigation/font buttons live in player_epub_controls_overlay_content (id-less include) →
        // reach via safeViews. The cmd buttons (toc/translate/search/settings) are command-panel children.
        safeViews.btnEpubPrevChapter.setOnClickListener { epubViewerManager.showPreviousChapter() }
        safeViews.btnEpubHome.setOnClickListener { epubViewerManager.showFirstChapter() }
        safeViews.btnEpubNextChapter.setOnClickListener { epubViewerManager.showNextChapter() }
        safeViews.btnEpubToc.setOnClickListener { epubViewerManager.showTableOfContents() }
        safeViews.btnEpubFontSizeDecrease.setOnClickListener { epubViewerManager.decreaseFontSize() }
        safeViews.btnEpubFontSizeIncrease.setOnClickListener { epubViewerManager.increaseFontSize() }
        binding.btnEpubTextSettingsCmd.setOnClickListener { epubViewerManager.showReaderSettingsDialog() }
        // S0393 wave-C: EPUB OCR - extract chapter text to clipboard (manager doesn't surface this button).
        binding.btnOcrEpubCmd.setOnClickListener { epubViewerManager.extractTextFromCurrentChapter() }
        binding.btnExitEpubFullscreen.setOnClickListener { epubViewerManager.exitFullscreenMode() }
        binding.btnTranslateEpubCmd.setOnClickListener { epubViewerManager.toggleTranslation() }
        binding.btnSearchEpubCmd.setOnClickListener { epubViewerManager.showCrossChapterSearch() }
    }

    private fun showFileInfo() {
        val file = viewModel.state.value.mediaFile ?: return
        if (isFinishing || isDestroyed) return
        standaloneHostFactory.createFileInfoDialog(context = this, mediaFile = file).show()
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
            Timber.d("DocumentStandalone: ignoring default-player probe URI, finishing")
            finish()
            return
        }
        val displayName = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (e: Exception) {
            Timber.w(e, "DocumentStandalone: failed to query display name")
            null
        } ?: uri.lastPathSegment
        // Folder paging enumerates only document neighbours - the types this host renders.
        viewModel.setHostSupportedTypes(setOf(MediaType.PDF, MediaType.EPUB, MediaType.OFFICE_DOCUMENT))
        viewModel.loadFromUri(uri, intent?.type, displayName)
    }

    override fun observeData() {
        observeTranslationSettings() // S0393 U7/U8: keep EPUB translator button orientation-aware
        collectOnLifecycle(viewModel.state) { state ->
            binding.progressBar.isVisible = state.isLoading
            if (state.isLoading) return@collectOnLifecycle
            state.errorMessage?.let { error ->
                Toast.makeText(this@DocumentStandaloneActivity, error, Toast.LENGTH_SHORT).show()
                finish()
                return@collectOnLifecycle
            }
            val file = state.mediaFile ?: return@collectOnLifecycle
            val type = state.mediaType ?: return@collectOnLifecycle
            if (type != MediaType.PDF && type != MediaType.EPUB && type != MediaType.OFFICE_DOCUMENT) {
                Toast.makeText(
                    this@DocumentStandaloneActivity,
                    R.string.unsupported_format_use_external_player,
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@collectOnLifecycle
            }
            if (file.path != lastShownPath) {
                // Release the previous viewer before paging to a neighbour of a different doc type
                // (e.g. PDF -> EPUB) so the new one renders into a clean container.
                if (lastShownPath != null) releaseActiveViewer()
                resolvedType = type
                displayDocument(file, type)
                destinationButtonsManager.populateDestinationButtons()
                lastShownPath = file.path
            }
            updateEpubTranslatorVisibility() // S0393 U7/U8: re-eval after the viewer changes

            pagingControls.applyState(state.supportsFolderPaging, state.isSlideshowActive)
            updateRenameButtonVisibility()
        }
        collectOnLifecycle(viewModel.isFavorite) { isFav ->
            binding.btnFavorite.setImageResource(
                if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star_outline
            )
        }
        collectOnLifecycle(viewModel.messageFlow) { message ->
            Toast.makeText(this@DocumentStandaloneActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun displayDocument(file: MediaFile, type: MediaType) {
        when (type) {
            MediaType.PDF -> pdfViewerManager.displayPdf(file)
            MediaType.EPUB -> epubViewerManager.displayEpub(file)
            MediaType.OFFICE_DOCUMENT -> displayOfficeDocument(file)
            else -> Unit
        }
    }

    // ── Office ────────────────────────────────────────────────────────────────

    private fun displayOfficeDocument(mediaFile: MediaFile) {
        val session = officeViewerProvider.resolve(mediaFile)
        when (session.outcome) {
            OfficeDocumentViewerOutcome.DISPLAY_INTERNALLY -> openOfficeInternally(mediaFile)
            OfficeDocumentViewerOutcome.SHOW_FALLBACK_DIALOG,
            OfficeDocumentViewerOutcome.DELEGATE_EXTERNAL -> openOfficeExternally(mediaFile)
        }
    }

    private fun openOfficeInternally(mediaFile: MediaFile) {
        lifecycleScope.launch {
            try {
                val preparedFile = networkFileManager.prepareFileForRead(mediaFile)
                val started = officeViewerHost.open(mediaFile, preparedFile)
                if (!started) openOfficeExternally(mediaFile)
            } catch (e: Exception) {
                Timber.e(e, "DocumentStandalone: failed to render Office document internally")
                openOfficeExternally(mediaFile)
            }
        }
    }

    private fun openOfficeExternally(mediaFile: MediaFile) {
        lifecycleScope.launch {
            try {
                val preparedFile = networkFileManager.prepareFileForRead(mediaFile)
                val opened = OfficeDocumentOpenManager.openPreparedFile(
                    activity = this@DocumentStandaloneActivity,
                    file = preparedFile,
                    displayName = mediaFile.name
                )
                if (!opened) showToastError(getString(R.string.no_app_to_open))
            } catch (e: Exception) {
                Timber.e(e, "DocumentStandalone: failed to open Office document externally")
                showToastError(getString(R.string.error_opening_file_simple))
            }
        }
    }

    private fun updateRenameButtonVisibility() = fileOperations.updateRenameButtonVisibility()

    private fun showToastError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        releaseActiveViewer()
        // S0872: pdf/epub/office managers never touch translationManager - release it here, only if built.
        if (translationManagerDelegate.isInitialized()) translationManager.release()
        super.onDestroy()
    }

    /**
     * S0873: release EVERY initialized viewer, not just the current resolvedType's. A mixed-type
     * folder page (e.g. PDF -> EPUB) runs this before the type switch while the outgoing viewer's
     * async load is still in flight; that load then resurrects an off-type PdfRenderer/PFD/WebView
     * which the old type-gated release could never reach (it leaked past onDestroy). Mirrors the
     * unified-host family contract (PlayerLifecycleManager / StandaloneViewManager). The isInitialized()
     * guards keep an untouched lazy viewer from being created just to release it.
     */
    private fun releaseActiveViewer() {
        if (pdfViewerManagerDelegate.isInitialized()) pdfViewerManager.close()
        if (epubViewerManagerDelegate.isInitialized()) epubViewerManager.release()
        if (officeViewerHostDelegate.isInitialized()) officeViewerHost.release()
    }
}
