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
import com.sza.fastmediasorter.databinding.ActivityStandaloneDocumentBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.repository.NetworkCredentialsRepository
import com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.dialog.FileInfoDialog
import com.sza.fastmediasorter.ui.player.StandalonePlayerViewModel
import com.sza.fastmediasorter.ui.player.helpers.EpubViewerManager
import com.sza.fastmediasorter.ui.player.helpers.NetworkFileManager
import com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentOpenManager
import com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerHost
import com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerOutcome
import com.sza.fastmediasorter.ui.player.helpers.OfficeDocumentViewerProviderFactory
import com.sza.fastmediasorter.ui.player.helpers.PdfViewerManager
import com.sza.fastmediasorter.ui.player.helpers.PlayerBindingSafeViews
import com.sza.fastmediasorter.ui.player.helpers.StandaloneFileOperationsHandler
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.Lazy
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
class DocumentStandaloneActivity : BaseActivity<ActivityStandaloneDocumentBinding>() {

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

    private val translationManager: TranslationManager by lazy {
        TranslationManager(
            context = this,
            settingsRepository = settingsRepository,
            callback = object : TranslationManager.TranslationCallback {
                override fun showError(message: String) = showToastError(message)
                override fun showModelDownloadPrompt(
                    languageName: String,
                    onConfirm: () -> Unit,
                    onCancel: () -> Unit
                ) { /* translation download UI not exposed in standalone */ }
            }
        )
    }

    private val fileOperations: StandaloneFileOperationsHandler by lazy {
        StandaloneFileOperationsHandler(
            activity = this,
            root = binding.root,
            getCurrentMediaFile = { viewModel.state.value.mediaFile },
            findResourceForPath = { parentDir -> viewModel.findResourceForPath(parentDir) },
            onRenameComplete = { newUri, newName -> viewModel.onRenameComplete(newUri, newName) },
            updateAudioMediaItem = { /* no audio in document activity */ },
            batchDeleteLauncher = batchDeleteLauncher,
            recoverableDeleteLauncher = recoverableDeleteLauncher
        )
    }

    // Lazily created document viewers - only the one matching the resolved type is ever touched.
    private val pdfViewerManager: PdfViewerManager by lazy {
        PdfViewerManager(
            root = binding.root,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : PdfViewerManager.PdfViewerCallback {
                override fun showError(message: String) = showToastError(message)
                override fun displayOcrText(text: String) { /* OCR overlay not exposed in standalone */ }
                override fun displayTranslatedText(text: String) { /* shown inline in the PDF overlay */ }
                override fun shareFileToGoogleLens(file: File) { /* Google Lens not exposed in standalone */ }
                override fun isLandscapeMode(): Boolean =
                    resources.configuration.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE
                override fun onEnterFullscreenMode() { binding.topCommandPanel.isVisible = false }
                override fun onExitFullscreenMode() { binding.topCommandPanel.isVisible = true }
            },
            translationManager = translationManager,
            playbackPositionRepository = playbackPositionRepository
        )
    }

    private val epubViewerManager: EpubViewerManager by lazy {
        EpubViewerManager(
            root = binding.root,
            networkFileManager = networkFileManager,
            settingsRepository = settingsRepository,
            coroutineScope = lifecycleScope,
            callback = object : EpubViewerManager.EpubViewerCallback {
                override fun showError(message: String) = showToastError(message)
                override fun displayTranslatedText(text: String) { /* shown inline in the EPUB overlay */ }
                override fun onEnterFullscreenMode() { binding.topCommandPanel.isVisible = false }
                override fun onExitFullscreenMode() { binding.topCommandPanel.isVisible = true }
            },
            playbackPositionRepository = playbackPositionRepository,
            translationManager = translationManager
        )
    }

    // S0301 / S0380: embedded Office viewer host. Market = no-op host (external handoff), noLegal =
    // engine-backed read-only viewer. Built against the trimmed layout's officeDocumentViewerContainer.
    // Explicit Lazy so onDestroy can release it only when it was actually created (internal open).
    private val officeViewerHostDelegate = lazy {
        OfficeDocumentViewerProviderFactory().createViewerHost(
            root = binding.root,
            coroutineScope = lifecycleScope,
            callback = object : OfficeDocumentViewerHost.Callback {
                override fun showError(message: String) = showToastError(message)
                override fun onEnterFullscreenMode() { binding.topCommandPanel.isVisible = false }
                override fun onExitFullscreenMode() { binding.topCommandPanel.isVisible = true }
                override fun onRequireExternalFallback(mediaFile: MediaFile) =
                    openOfficeExternally(mediaFile)
            }
        )
    }
    private val officeViewerHost: OfficeDocumentViewerHost by officeViewerHostDelegate

    private val officeViewerProvider by lazy { OfficeDocumentViewerProviderFactory().create() }

    /** Resolved document type for the loaded file; gates which viewer the controls drive. */
    private var resolvedType: MediaType? = null

    /** Set after the first successful display; prevents reload on rename state updates. */
    private var contentLoaded = false

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
        parseIncomingIntent()
    }

    private fun setupWindowAndInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
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
            override fun handleOnBackPressed() {
                // Exit document fullscreen first; only then leave the activity.
                when {
                    resolvedType == MediaType.PDF && pdfViewerManager.isInFullscreenMode() ->
                        pdfViewerManager.exitFullscreenMode()
                    resolvedType == MediaType.EPUB && epubViewerManager.isInFullscreenMode() ->
                        epubViewerManager.exitFullscreenMode()
                    else -> finish()
                }
            }
        })
    }

    private fun setupFileOperationButtons() {
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
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_open_in_fms -> { fileOperations.openInFms(); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    // ── PDF / EPUB navigation controls (mirror StandalonePlayerActivity) ──────────

    private fun setupPdfButtons() {
        // Navigation/zoom buttons live in player_pdf_controls_overlay_content (id-less include) →
        // reach via safeViews. The cmd buttons (translate/search) are direct command-panel children.
        safeViews.btnPdfPrevPage.setOnClickListener { pdfViewerManager.showPreviousPage() }
        safeViews.btnPdfHome.setOnClickListener { pdfViewerManager.showFirstPage() }
        safeViews.btnPdfNextPage.setOnClickListener { pdfViewerManager.showNextPage() }
        safeViews.btnPdfZoomIn.setOnClickListener {
            binding.photoView.setScale(binding.photoView.scale * 1.25f, true)
        }
        safeViews.btnPdfZoomOut.setOnClickListener {
            binding.photoView.setScale(binding.photoView.scale / 1.25f, true)
        }
        binding.btnTranslatePdfCmd.setOnClickListener { pdfViewerManager.toggleTranslation() }
        binding.btnSearchPdfCmd.setOnClickListener { pdfViewerManager.showThumbnailNavigation() }
    }

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
        binding.btnExitEpubFullscreen.setOnClickListener { epubViewerManager.exitFullscreenMode() }
        binding.btnTranslateEpubCmd.setOnClickListener { epubViewerManager.toggleTranslation() }
        binding.btnSearchEpubCmd.setOnClickListener { epubViewerManager.showCrossChapterSearch() }
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
            Timber.w(e, "DocumentStandalone: failed to query display name")
            null
        } ?: uri.lastPathSegment
        viewModel.loadFromUri(uri, intent?.type, displayName)
    }

    override fun observeData() {
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
            if (!contentLoaded) {
                Timber.d("S0380: DocumentStandaloneActivity displaying $type from external intent")
                resolvedType = type
                displayDocument(file, type)
                contentLoaded = true
            }
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
        super.onDestroy()
    }

    /** Release only the viewer that was actually created (lazy delegates stay untouched otherwise). */
    private fun releaseActiveViewer() {
        when (resolvedType) {
            MediaType.PDF -> pdfViewerManager.close()
            MediaType.EPUB -> epubViewerManager.release()
            // Only release the Office host if it was actually built (internal render path);
            // an externally-delegated Office file never creates the host.
            MediaType.OFFICE_DOCUMENT -> if (officeViewerHostDelegate.isInitialized()) {
                officeViewerHost.release()
            }
            else -> Unit
        }
    }
}
