package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import com.sza.fastmediasorter.domain.stats.ViewKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.abs

/** PlayerActivity PDF viewer: PdfRenderer pages (2x screen res), prev/next navigation, lifecycle, OCR translation, last-page restore. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@android.annotation.SuppressLint("SetTextI18n")
class PdfViewerManager(
    // S0380: decoupled from ActivityPlayerUnifiedBinding to a layout root so the PDF viewer drives
    // both the full unified player layout and the trimmed document standalone layout.
    root: View,
    private val networkFileManager: NetworkFileManager,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: PdfViewerCallback,
    private val translationManager: TranslationManager,
    private val playbackPositionRepository: com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository,
    // S0473: usage-statistics sink. Fire-and-forget; no-ops when collection is disabled.
    private val statsSink: StatsSink
) : BaseDocumentViewerManager(root) {
    private val safeViews = PlayerBindingSafeViews(root)

    interface PdfViewerCallback {
        fun showError(message: String)
        fun onEnterFullscreenMode()
        fun onExitFullscreenMode()
        fun displayOcrText(text: String)
        fun displayTranslatedText(text: String)
        fun shareFileToGoogleLens(file: File)
        fun isLandscapeMode(): Boolean
    }

    // PDF Renderer state
    private var pdfRenderer: PdfRenderer? = null
    private var currentPdfPage: PdfRenderer.Page? = null
    private var pdfParcelFileDescriptor: ParcelFileDescriptor? = null
    private var currentPdfPageIndex = 0
    var pdfPageCount = 0
    private var currentPdfFile: File? = null
    private var currentPdfPath: String? = null // Original file path for position saving

    // Translation state
    private var translationEnabled = false
    private var currentPageBitmap: Bitmap? = null

    fun getCurrentPageBitmap(): Bitmap? = currentPageBitmap

    private var isTranslationExpanded = false
    private var isLensStyleEnabled = false // Google Lens style mode
    private val translationCoordinator by lazy {
        PdfTranslationCoordinator(
            root = root,
            safeViews = safeViews,
            coroutineScope = coroutineScope,
            settingsRepository = settingsRepository,
            translationManager = translationManager,
            getCurrentPageBitmap = { currentPageBitmap },
            getCurrentPdfFile = { currentPdfFile },
            getCurrentPdfPageIndex = { currentPdfPageIndex },
            setIsLensStyleEnabled = { isLensStyleEnabled = it },
            onError = callback::showError,
            onSimpleTextTranslated = callback::displayTranslatedText,
        )
    }
    // Note: Translation cache moved to global TranslationCacheManager singleton

    // Fullscreen mode state
    private var isFullscreenMode = false

    // Scroll mode state
    private var isScrollMode = false
    private var pdfPageAdapter: PdfPageAdapter? = null
    private var bitmapCache: PdfBitmapCache? = null
    private var rendererWrapper: PdfRendererWrapper? = null

    // Color mode state (night/sepia)
    private var currentColorMode = PdfColorConversion.PdfColorMode.NORMAL

    // Page render job for cancellation on rapid navigation (C2 fix)
    private var pageRenderJob: Job? = null

    // S0196 Phase 04: one-shot tag emitted when the first PDF page bitmap is pushed to
    // the PhotoView - "primary content rendered" for the StandalonePlayer docs branch.
    private var firstPageRenderedLogged = false

    // Single-thread dispatcher for PdfRenderer (non-thread-safe API)
    private val pdfDispatcher = Dispatchers.IO.limitedParallelism(1)

    // Text selection mode overlay manager
    private val pdfTextSelectionManager = PdfTextSelectionManager(
        root               = root,
        safeViews          = safeViews,
        settingsRepository = settingsRepository,
        coroutineScope     = coroutineScope,
        translationManager = translationManager,
        pdfDispatcher      = pdfDispatcher,
        onTranslateResult  = { callback.displayTranslatedText(it) },
        onError            = { callback.showError(it) },
        onReadAloud        = { text -> speakText(text) }
    )

    // TTS delegate for "Read Aloud" feature
    private val pdfTtsDelegate = PdfTtsDelegate(
        context                = root.context,
        coroutineScope         = coroutineScope,
        pdfTextSelectionManager = pdfTextSelectionManager
    )

    // Delegate for link detection, tap-to-open, search, OCR text copy, and Google Lens sharing
    private val pdfLinkAndSearchManager = PdfLinkAndSearchManager(
        root               = root,
        safeViews          = safeViews,
        settingsRepository = settingsRepository,
        coroutineScope     = coroutineScope,
        translationManager = translationManager,
        onError            = { callback.showError(it) },
        onShareToGoogleLens = { file -> callback.shareFileToGoogleLens(file) }
    )

    init {

        // S0949: widen the shared PDF PhotoView zoom range to 0.3x..10x so both the in-app player and
        // the document standalone host expose the same swipe/pinch/button band (default was 1x..3x).
        safeViews.photoView.setScaleLevels(PDF_MIN_SCALE, PDF_MEDIUM_SCALE, PDF_MAX_SCALE)

        // Listen for scale/position changes on PhotoView to update translation overlay
        safeViews.photoView.setOnMatrixChangeListener {
            // Update translation overlay position when PDF is zoomed/panned
            if (safeViews.translationLensOverlay.isVisible == true && currentPageBitmap != null) {
                val viewWidth = safeViews.photoView.width
                val viewHeight = safeViews.photoView.height
                val bitmapWidth = currentPageBitmap?.width ?: 1
                val bitmapHeight = currentPageBitmap?.height ?: 1

                safeViews.translationLensOverlay.setScale(
                    bitmapWidth,
                    bitmapHeight,
                    viewWidth,
                    viewHeight
                )
            }
        }

        // Setup translation overlay click to expand/collapse
        safeViews.translationOverlay.setOnClickListener {
            toggleTranslationOverlaySize()
        }

        // Setup close button for translation overlay (simple mode)
        safeViews.btnCloseTranslation.setOnClickListener {
            safeViews.translationOverlay.isVisible = false
            if (isTranslationExpanded) {
                toggleTranslationOverlaySize()
            }
        }

        // Setup exit from fullscreen mode
        safeViews.pdfFullscreenOverlay?.setOnClickListener {
            exitFullscreenMode()
        }
        safeViews.pdfFullscreenPhotoView?.setOnClickListener {
            exitFullscreenMode()
        }
        safeViews.btnExitPdfFullscreen?.setOnClickListener {
            exitFullscreenMode()
        }

        // Setup page indicator click to show "Go to page" dialog
        safeViews.tvPdfPageIndicatorOrNull?.setOnClickListener {
            if (pdfPageCount > 1) {
                showGoToPageDialog()
            }
        }

        // Text selection mode button: toggle overlay with selectable page text
        safeViews.btnSelectTextPdf?.setOnClickListener {
            openPdfTextSelection()
        }
    }

    /** Toggle translation overlay between compact and fullscreen modes */
    private fun toggleTranslationOverlaySize() {
        isTranslationExpanded = !isTranslationExpanded

        val layoutParams = safeViews.translationOverlay.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        val scrollViewLayoutParams = safeViews.translationScrollView.layoutParams as? android.widget.LinearLayout.LayoutParams ?: return

        if (isTranslationExpanded) {
            // Fullscreen mode: match_parent height, no margin, opaque background
            layoutParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.setMargins(0, 0, 0, 0)
            layoutParams.gravity = android.view.Gravity.NO_GRAVITY

            scrollViewLayoutParams.height = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            scrollViewLayoutParams.setMargins(0, 0, 0, 0)

            safeViews.translationOverlay.setCardBackgroundColor(android.graphics.Color.parseColor("#FF000000")) // Opaque black

        } else {
            // Compact mode: wrap_content with maxHeight=300dp, margin, semi-transparent
            layoutParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            val margin = (8 * root.context.resources.displayMetrics.density).toInt()
            layoutParams.setMargins(margin, margin, margin, margin)
            layoutParams.gravity = android.view.Gravity.TOP or android.view.Gravity.START

            // Use 40% of screen height for compact mode (adapts to all screen sizes)
            val screenHeight = root.context.resources.displayMetrics.heightPixels
            val maxHeightPx = (screenHeight * 0.4f).toInt()
            scrollViewLayoutParams.height = maxHeightPx
            scrollViewLayoutParams.setMargins(0, 0, 0, 0)

            safeViews.translationOverlay.setCardBackgroundColor(android.graphics.Color.parseColor("#B0000000")) // Semi-transparent

        }

        safeViews.translationOverlay.layoutParams = layoutParams
        safeViews.translationScrollView.layoutParams = scrollViewLayoutParams
    }

    /** Show dialog to jump to specific PDF page */
    private fun showGoToPageDialog() {
        val context = root.context
        val editText = android.widget.EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = context.getString(com.sza.fastmediasorter.R.string.goto_page_hint, pdfPageCount)
            setText("${currentPdfPageIndex + 1}")
            selectAll()
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(com.sza.fastmediasorter.R.string.goto_page_title)
            .setMessage(com.sza.fastmediasorter.R.string.goto_page_message)
            .setView(editText)
            .setPositiveButton(com.sza.fastmediasorter.R.string.goto_page_button) { dialog, _ ->
                val pageNumber = editText.text.toString().toIntOrNull()
                if (pageNumber != null && pageNumber in 1..pdfPageCount) {
                    showPdfPage(pageNumber - 1) // Convert to 0-based index
                } else {
                    callback.showError(context.getString(com.sza.fastmediasorter.R.string.invalid_page_number, pdfPageCount))
                }
                dialog.dismiss()
            }
            .setNegativeButton(com.sza.fastmediasorter.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /** Display PDF file in PhotoView (reused for PDF pages) */
    fun displayPdf(mediaFile: MediaFile) {
        safeViews.imageView.isVisible = false
        safeViews.photoDualSurfaceContainerOrNull?.isVisible = true
        safeViews.photoView.isVisible = true // Reuse PhotoView for PDF pages.
        safeViews.photoViewSurfaceBOrNull?.isVisible = false
        safeViews.playerView.isVisible = false
        safeViews.epubWebView.isVisible = false
        safeViews.epubControlsLayout.isVisible = false
        safeViews.btnExitEpubFullscreen.isVisible = false
        safeViews.audioCoverArtView.isVisible = false
        safeViews.audioInfoOverlay.isVisible = false
        // S0380: text-viewer and the deprecated image-translate button are cross-type views the
        // trimmed document-standalone layout omits (it never shows text/image). Reset by presence so
        // PdfViewerManager works on both the full unified layout and the trimmed standalone one.
        safeViews.setVisibleIfPresent(R.id.textViewerContainer, false)
        safeViews.setVisibleIfPresent(R.id.btnTranslateImage, false)
        safeViews.pdfControlsLayout.isVisible = true
        safeViews.playerProgressBar.isVisible = true
        // btnCopyTextCmd is re-shown by CommandPanelController in landscape; routes to copyPageTextToClipboard().
        safeViews.btnCopyTextCmd.isVisible = false
        safeViews.btnCopyTextCmd.setOnClickListener { copyPageTextToClipboard() }
        safeViews.btnEditTextCmd.isVisible = false
        safeViews.btnTranslateTextCmd.isVisible = false
        safeViews.btnSearchTextCmd.isVisible = false
        safeViews.btnSearchEpubCmd.isVisible = false
        safeViews.btnTranslateEpubCmd.isVisible = false
        closePdfRenderer()
        pdfLinkAndSearchManager.clearUrlCache()
        // Translation cache is intentionally NOT cleared - preserves translations when switching files.
        val isNetworkFile = mediaFile.path.startsWith("smb://") || mediaFile.path.startsWith("sftp://") || mediaFile.path.startsWith("ftp://") || mediaFile.path.startsWith("https://")
        val loadingToastJob = coroutineScope.launch(Dispatchers.Main) {
            // Immediate toast for network (always slow), 2s delay for local.
            kotlinx.coroutines.delay(if (isNetworkFile) 0 else 2000)
            if (safeViews.playerProgressBar.isVisible) {
                android.widget.Toast.makeText(
                    root.context,
                    root.context.getString(com.sza.fastmediasorter.R.string.please_wait),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }

        coroutineScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(50) // Let UI render the ProgressBar before heavy IO.
            val settings = withContext(Dispatchers.IO) { settingsRepository.getSettings().first() }
            // PDF cmd buttons: landscape + feature flag; portrait routes via overflow menu.
            val isLandscape = callback.isLandscapeMode()
            safeViews.btnTranslatePdfCmd.isVisible = isLandscape && settings.enableTranslation
            // Lens is ALWAYS_OFF by default (ShareTargetDefault); visible only when user explicitly opted in.
            safeViews.btnGoogleLensPdfCmd.isVisible = isLandscape && "lens" in settings.enabledShareTargets
            safeViews.btnOcrPdfCmd.isVisible = isLandscape && settings.enableOcr
            safeViews.btnSearchPdfCmd.isVisible = isLandscape
            safeViews.btnSelectTextPdf?.isVisible = true // OCR (API<35) or native (API35+).

            try {
                val startTime = System.currentTimeMillis()
                val file = withContext(Dispatchers.IO) {
                    try {
                        networkFileManager.prepareFileForRead(mediaFile)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            safeViews.playerProgressBar.isVisible = false
                            callback.showError(root.context.getString(R.string.pdf_load_failed))
                        }
                        throw e
                    }
                }

                if (!file.exists()) {
                    loadingToastJob.cancel()
                    safeViews.playerProgressBar.isVisible = false
                    callback.showError(root.context.getString(R.string.pdf_file_not_found))
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    try {
                        pdfParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                        pdfRenderer = PdfRenderer(pdfParcelFileDescriptor!!)
                        pdfPageCount = pdfRenderer?.pageCount ?: 0
                        currentPdfPageIndex = 0
                        currentPdfFile = file
                        currentPdfPath = mediaFile.path // Store original path for position saving
                        // S0473: one document opened; pages carries the loaded page count. Emitted
                        // once on successful open, not per page render.
                        statsSink.record(StatsEvent.View(ViewKind.DOCUMENT, pages = pdfPageCount.toLong()))

                        // Create thread-safe wrapper and cache for scroll mode
                        rendererWrapper = PdfRendererWrapper(pdfRenderer!!)
                        bitmapCache = PdfBitmapCache()
                        isScrollMode = settings.pdfScrollMode
                        currentColorMode = PdfColorConversion.fromName(settings.pdfColorMode)

                        // Restore last viewed page position
                        val savedPage = playbackPositionRepository.getPosition(mediaFile.path)
                        val startPage = if (savedPage != null && savedPage > 0 && savedPage < pdfPageCount) {
                            savedPage.toInt()
                        } else {
                            0
                        }

                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            if (pdfPageCount > 0) {
                                if (isScrollMode) {
                                    setupScrollMode(startPage)
                                } else {
                                    setupPageMode(startPage)
                                }
                                if (startPage > 0) {
                                }

                                // Force command panel button visibility update after page is rendered
                                // Only show in landscape mode; portrait uses overflow menu
                                val isLandscapePostRender = callback.isLandscapeMode()
                                safeViews.btnTranslatePdfCmd.isVisible = isLandscapePostRender && settings.enableTranslation
                                safeViews.btnGoogleLensPdfCmd.isVisible = isLandscapePostRender && "lens" in settings.enabledShareTargets
                                safeViews.btnOcrPdfCmd.isVisible = isLandscapePostRender && settings.enableOcr
                                safeViews.btnSearchPdfCmd.isVisible = isLandscapePostRender

                                // Hide navigation controls for single-page PDFs
                                val isSinglePage = pdfPageCount == 1
                                safeViews.btnPdfPrevPage.isVisible = !isSinglePage
                                safeViews.btnPdfNextPage.isVisible = !isSinglePage
                                safeViews.tvPdfPageIndicatorOrNull?.isVisible = !isSinglePage
                            } else {
                                safeViews.tvPdfPageIndicatorOrNull?.text = root.context.getString(R.string.pdf_empty)
                            }
                        }
                    } catch (e: SecurityException) {
                        Timber.w(e, "Protected PDF cannot be opened by the platform renderer")
                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            safeViews.playerProgressBar.isVisible = false
                            callback.showError(root.context.getString(R.string.protected_file_unsupported))
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error initializing PDF renderer")
                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            safeViews.playerProgressBar.isVisible = false
                            callback.showError(root.context.getString(R.string.pdf_read_failed))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading PDF")
                loadingToastJob.cancel()
                safeViews.playerProgressBar.isVisible = false
                callback.showError(root.context.getString(R.string.pdf_display_error))
            }
        }
    }

    /** Navigate to previous PDF page */
    fun showPreviousPage() {
        if (currentPdfPageIndex > 0) {
            // Clear translation overlays before page change
            clearTranslationOverlays()
            stopTtsOnPageChange()
            showPdfPage(currentPdfPageIndex - 1)

            // Re-translate new page if translation was enabled
            if (translationEnabled) {
                coroutineScope.launch {
                    delay(500) // Wait for PDF page to render
                    translateCurrentPage()
                }
            }
        }
    }

    /** Navigate to next PDF page */
    fun showNextPage() {
        if (currentPdfPageIndex < pdfPageCount - 1) {
            // Clear translation overlays before page change
            clearTranslationOverlays()
            stopTtsOnPageChange()
            showPdfPage(currentPdfPageIndex + 1)

            // Re-translate new page if translation was enabled
            if (translationEnabled) {
                coroutineScope.launch {
                    delay(500) // Wait for PDF page to render
                    translateCurrentPage()
                }
            }
        }
    }

    /** Navigate to first PDF page (To begin) */
    fun showFirstPage() {
        if (currentPdfPageIndex > 0) {
            // Clear translation overlays before page change
            clearTranslationOverlays()
            stopTtsOnPageChange()
            showPdfPage(0)

            // Re-translate new page if translation was enabled
            if (translationEnabled) {
                coroutineScope.launch {
                    delay(500) // Wait for PDF page to render
                    translateCurrentPage()
                }
            }
        }
    }

    // ========== Scroll Mode ==========

    /** Setup page mode (single-page PhotoView with fling navigation). This is the legacy/default mode. */
    private fun setupPageMode(startPage: Int) {
        safeViews.photoDualSurfaceContainerOrNull?.isVisible = true
        safeViews.photoView.isVisible = true
        safeViews.photoViewSurfaceBOrNull?.isVisible = false
        safeViews.pdfScrollRecyclerView.isVisible = false
        safeViews.btnPdfPrevPage.isVisible = pdfPageCount > 1
        safeViews.btnPdfNextPage.isVisible = pdfPageCount > 1
        safeViews.playerProgressBar.isVisible = true
        showPdfPage(startPage)
    }

    /** Setup scroll mode (vertical RecyclerView with all pages). Uses PdfPageAdapter with PdfRendererWrapper for thread-safe rendering. */
    private fun setupScrollMode(startPage: Int) {
        safeViews.photoDualSurfaceContainerOrNull?.isVisible = false
        safeViews.photoView.isVisible = false
        safeViews.photoViewSurfaceBOrNull?.isVisible = false
        safeViews.pdfScrollRecyclerView.isVisible = true
        safeViews.playerProgressBar.isVisible = false
        safeViews.btnPdfPrevPage.isVisible = false
        safeViews.btnPdfNextPage.isVisible = false
        val wrapper = rendererWrapper ?: return
        val cache = bitmapCache ?: return
        val adapter = PdfPageAdapter(
            rendererWrapper = wrapper, bitmapCache = cache, coroutineScope = coroutineScope,
            renderWidth = root.resources.displayMetrics.widthPixels,
        ).also { it.setColorFilter(PdfColorConversion.getColorFilter(currentColorMode)) }
        pdfPageAdapter = adapter
        val layoutManager = LinearLayoutManager(root.context, LinearLayoutManager.VERTICAL, false)
        safeViews.pdfScrollRecyclerView.layoutManager = layoutManager
        safeViews.pdfScrollRecyclerView.adapter = adapter
        safeViews.pdfScrollRecyclerView.clearOnScrollListeners() // M5 fix: prevent accumulation.
        safeViews.pdfScrollRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
                val visiblePage = if (firstVisible >= 0) firstVisible else layoutManager.findFirstVisibleItemPosition()
                if (visiblePage >= 0 && visiblePage != currentPdfPageIndex) {
                    currentPdfPageIndex = visiblePage
                    safeViews.tvPdfPageIndicatorOrNull?.text = "${visiblePage + 1} / $pdfPageCount"
                    saveCurrentPagePosition()
                }
            }
        })
        if (startPage > 0) layoutManager.scrollToPositionWithOffset(startPage, 0)
        currentPdfPageIndex = startPage
        safeViews.tvPdfPageIndicatorOrNull?.text = "${startPage + 1} / $pdfPageCount"
        safeViews.tvPdfPageIndicatorOrNull?.isVisible = pdfPageCount > 1
    }

    /** Toggle between page mode and scroll mode. Persists preference to settings. */
    fun toggleScrollMode() {
        isScrollMode = !isScrollMode

        // Persist preference
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val current = settingsRepository.getSettings().first()
                settingsRepository.updateSettings(current.copy(pdfScrollMode = isScrollMode))
            } catch (e: Exception) {
                Timber.e(e, "PDF: Failed to persist scroll mode preference")
            }
        }

        // Clean up current adapter if switching away from scroll mode
        if (!isScrollMode) {
            pdfPageAdapter?.invalidateCache()
            pdfPageAdapter = null
            safeViews.pdfScrollRecyclerView.adapter = null
            setupPageMode(currentPdfPageIndex)
        } else {
            // Close current page before switching (PdfRenderer requires it)
            currentPdfPage?.close()
            currentPdfPage = null
            // Clear PhotoView reference BEFORE recycling bitmap (M3 fix)
            safeViews.photoView.setImageBitmap(null)
            currentPageBitmap?.recycle()
            currentPageBitmap = null
            bitmapCache?.clear()
            setupScrollMode(currentPdfPageIndex)
        }
    }

    /** Check if currently in scroll mode. */
    fun isInScrollMode(): Boolean = isScrollMode

    /** Toggle PDF color mode: NORMAL → NIGHT → SEPIA → NORMAL. Applies ColorMatrixColorFilter to both page mode (PhotoView) and scroll mode (adapter). Persists preference to settings. */
    fun toggleColorMode() {
        currentColorMode = PdfColorConversion.nextMode(currentColorMode)
        val filter = PdfColorConversion.getColorFilter(currentColorMode)

        // Apply to PhotoView (page mode)
        safeViews.photoView.colorFilter = filter

        // Apply to adapter (scroll mode)
        pdfPageAdapter?.setColorFilter(filter)

        // Persist preference
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val current = settingsRepository.getSettings().first()
                settingsRepository.updateSettings(current.copy(pdfColorMode = currentColorMode.name))
            } catch (e: Exception) {
                Timber.e(e, "PDF: Failed to persist color mode preference")
            }
        }
    }

    /** Get current color mode name for UI display. */
    fun getCurrentColorModeName(): String = currentColorMode.name

    /** Show thumbnail navigation BottomSheet. Displays a grid of low-res page thumbnails for quick page jumping. */
    fun showThumbnailNavigation() = PdfThumbnailSheet.show(
        root = root,
        safeViews = safeViews,
        rendererWrapper = rendererWrapper,
        pdfPageCount = pdfPageCount,
        currentPdfPageIndex = currentPdfPageIndex,
        coroutineScope = coroutineScope,
        isScrollMode = isScrollMode,
        onPageScrollSelected = { page ->
            val layoutManager = safeViews.pdfScrollRecyclerView.layoutManager as? LinearLayoutManager
            layoutManager?.scrollToPositionWithOffset(page, 0)
            currentPdfPageIndex = page
            safeViews.tvPdfPageIndicatorOrNull?.text = "${page + 1} / $pdfPageCount"
            saveCurrentPagePosition()
        },
        onPagePicked = ::showPdfPage,
    )

    /** Toggle translation on/off for current PDF page */
    fun toggleTranslation() {
        translationEnabled = !translationEnabled

        if (translationEnabled) {
            // Translate current page
            translateCurrentPage()
        } else {
            // Clear and hide all translation overlays
            clearTranslationOverlays()
            if (isTranslationExpanded) {
                toggleTranslationOverlaySize()
            }
        }

        // Use alpha instead of imageTintList: tinting with a solid colour destroys
        // the LanguageBadgeDrawable text, making the badge appear as a solid block.
        safeViews.btnTranslatePdfCmd.alpha = if (translationEnabled) 1.0f else 0.55f
    }

    /** Force enable translation and translate current page. Used when settings are changed via long-press dialog. */
    fun forceTranslate() {
        translationEnabled = true
        translateCurrentPage()

        // Update command panel button alpha to indicate active translation
        safeViews.btnTranslatePdfCmd.alpha = 1.0f
    }

    /** Extract text from current PDF page using OCR (no translation). Shows recognized text in overlay for user to copy. */
    fun extractTextFromCurrentPage() {
        pdfLinkAndSearchManager.extractTextFromCurrentPage(
            currentBitmap = currentPageBitmap,
            onOcrResult   = { callback.displayOcrText(it) }
        )
    }

    /** OCR current PDF page and copy extracted text to clipboard. Does not switch views - stays on the PDF page. */
    fun copyPageTextToClipboard() {
        pdfLinkAndSearchManager.copyPageTextToClipboard(currentPageBitmap)
    }

    /** OCR + ML Kit translation of the current page (overlay or Lens style). Delegated to [PdfTranslationCoordinator]. */
    private fun translateCurrentPage() = translationCoordinator.translateCurrentPage()

    // ── TTS Read Aloud ─────────────────────────────────────────────────────────

    /** Toggle TTS for the current PDF page. Extracts page text (native API 35+ or OCR fallback), then speaks it. */
    fun toggleReadAloud() {
        pdfTtsDelegate.toggle(currentPdfPageIndex, currentPageBitmap, pdfRenderer)
    }

    /** Speak arbitrary [text] aloud - used by the text-selection floating ActionMode callback. */
    fun speakText(text: String) {
        pdfTtsDelegate.speakText(text)
    }

    /** Stop TTS when user navigates to another page. */
    fun stopTtsOnPageChange() {
        pdfTtsDelegate.stop()
    }

    /** Release TTS engine. Called from close(). */
    private fun releaseTts() {
        pdfTtsDelegate.release()
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    /** Close PDF renderer and release resources. Saves current page position before closing. */
    fun close() {
        // Save position before closing
        saveCurrentPagePosition()

        // Cancel any in-flight page render (C2 fix)
        pageRenderJob?.cancel()
        pageRenderJob = null

        pdfLinkAndSearchManager.clearUrlCache()

        // Detach adapter BEFORE closing renderer to trigger onViewRecycled → cancel render jobs (C1 fix)
        safeViews.pdfScrollRecyclerView.adapter = null
        pdfPageAdapter?.invalidateCache()
        pdfPageAdapter = null
        bitmapCache?.clear()
        bitmapCache = null
        // Don't call rendererWrapper.close() - closePdfRenderer() closes the underlying PdfRenderer
        rendererWrapper = null

        // Clear PhotoView reference to bitmap BEFORE recycling (M3 fix)
        safeViews.photoView.setImageBitmap(null)
        closePdfRenderer()
        currentPageBitmap?.recycle()
        currentPageBitmap = null
        translationEnabled = false
        currentPdfPath = null
        releaseTts()
    }

    /** Save current PDF page position for later restoration. Uses playback position repository (page number stored as position, page count as duration). */
    private fun saveCurrentPagePosition() {
        val path = currentPdfPath ?: return
        if (pdfPageCount <= 0) return

        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Store page index as position, page count as duration
                playbackPositionRepository.savePosition(
                    filePath = path,
                    position = currentPdfPageIndex.toLong(),
                    duration = pdfPageCount.toLong()
                )
            } catch (e: Exception) {
                Timber.e(e, "PDF: Failed to save page position")
            }
        }
    }

    // ========== Private Helper Methods ==========

    fun showPdfPage(index: Int) {
        if (pdfRenderer == null || pdfPageCount == 0) return
        if (index < 0 || index >= pdfPageCount) return

        // Cancel previous render job to prevent race condition (C2 fix)
        pageRenderJob?.cancel()

        // Show progress bar while rendering page (prevents "frozen" UI during heavy rendering)
        safeViews.playerProgressBar.isVisible = true

        // Clear translation overlays IMMEDIATELY when starting page change
        // This prevents old translations from briefly showing during page transition
        clearTranslationOverlays()

        // Perform page rendering on single-thread dispatcher (M7 fix: PdfRenderer is not thread-safe)
        pageRenderJob = coroutineScope.launch(pdfDispatcher) {
            try {
                // Close previous page before opening new one (C3 fix: sequential on single thread)
                currentPdfPage?.close()
                currentPdfPage = null

                val page = pdfRenderer?.openPage(index) ?: return@launch
                currentPdfPage = page

                // Calculate render size with safety limits to prevent OOM crashes
                // Base: 2x screen width for zoom quality, but cap at 2560px max dimension
                val screenWidth = root.resources.displayMetrics.widthPixels
                val desiredWidth = screenWidth * 2
                val aspectRatio = page.height.toFloat() / page.width.toFloat()

                // Calculate dimensions respecting aspect ratio
                var width = desiredWidth
                var height = (width * aspectRatio).toInt()

                // Safety limit: max 2560px on longest side (prevents 100MB+ bitmaps)
                val MAX_DIMENSION = 2560
                if (width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    if (width > height) {
                        width = MAX_DIMENSION
                        height = (width * aspectRatio).toInt()
                    } else {
                        height = MAX_DIMENSION
                        width = (height / aspectRatio).toInt()
                    }
                }

                // Create new bitmap (in background thread to avoid UI freeze)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                // White background for PDF pages
                bitmap.eraseColor(Color.WHITE)

                // CRITICAL: Render page in background (this is the heavy operation)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                // Close page immediately after render - PdfRenderer allows only ONE page open at a time. Leaving it open blocks PdfRendererWrapper used by thumbnail navigation, causing thumbnails to never load (openPage throws, returns null silently).
                currentPdfPage?.close()
                currentPdfPage = null

                // Extract PDF link annotations for tap-to-open (API 35+, fast - no OCR needed)
                if (Build.VERSION.SDK_INT >= 35) {
                    pdfLinkAndSearchManager.extractLinksNative(currentPdfFile!!, index, width, height)
                }

                // Switch to main thread to update UI
                withContext(Dispatchers.Main) {
                    // Clear PhotoView BEFORE recycling old bitmap (M3 fix)
                    safeViews.photoView.setImageBitmap(null)
                    currentPageBitmap?.recycle()

                    safeViews.photoView.setImageBitmap(bitmap)

                    // S0196 Phase 04: emit once on the first successful page push to the view.
                    if (!firstPageRenderedLogged) {
                        firstPageRenderedLogged = true
                        Timber.d("PdfViewerManager: firstPageRendered index=$index pageCount=$pdfPageCount")
                    }

                    // Apply color filter (night mode / sepia)
                    safeViews.photoView.colorFilter = PdfColorConversion.getColorFilter(currentColorMode)

                    // Store bitmap for translation
                    currentPageBitmap = bitmap

                    currentPdfPageIndex = index
                    safeViews.tvPdfPageIndicatorOrNull?.text = "${index + 1} / $pdfPageCount"

                    // Hide progress bar AFTER page is fully rendered and displayed
                    // Use post() to ensure bitmap is actually drawn before hiding progress
                    safeViews.photoView.post {
                        safeViews.playerProgressBar.isVisible = false
                    }

                    saveCurrentPagePosition()

                    safeViews.btnPdfPrevPage.isEnabled = index > 0
                    safeViews.btnPdfPrevPage.alpha = if (index > 0) 1.0f else 0.5f

                    safeViews.btnPdfNextPage.isEnabled = index < pdfPageCount - 1
                    safeViews.btnPdfNextPage.alpha = if (index < pdfPageCount - 1) 1.0f else 0.5f

                    // Auto-translate new page if translation is enabled
                    if (translationEnabled) {
                        translateCurrentPage()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error rendering PDF page")
                withContext(Dispatchers.Main) {
                    safeViews.playerProgressBar.isVisible = false
                    callback.showError(root.context.getString(R.string.pdf_page_render_failed))
                }
            }
        }
    }

    /** Enter fullscreen mode to view current PDF page with zoom/pan support. Hides all controls and shows only the page bitmap in a PhotoView. */
    private fun enterFullscreenMode() {
        val bitmap = currentPageBitmap ?: return

        isFullscreenMode = true

        // Hide all UI elements for immersive viewing
        callback.onEnterFullscreenMode()

        // Show fullscreen overlay with current page bitmap
        safeViews.pdfFullscreenPhotoView?.setImageBitmap(bitmap)
        safeViews.pdfFullscreenPhotoView?.setScale(1f, true)
        safeViews.pdfFullscreenOverlay?.isVisible = true

    }

    /** Public entry to PDF page-fullscreen, reachable from the overflow `menu_fullscreen` item. Decouples fullscreen from the long-press gesture, which now opens text selection. */
    fun requestPdfFullscreen() {
        enterFullscreenMode()
    }

    /** Exit fullscreen mode and return to normal PDF viewing with controls. */
    fun exitFullscreenMode() {
        if (!isFullscreenMode) return

        isFullscreenMode = false
        safeViews.pdfFullscreenOverlay?.isVisible = false
        safeViews.pdfFullscreenPhotoView?.setImageBitmap(null)

        // Restore UI elements
        callback.onExitFullscreenMode()

    }

    /** Check if fullscreen mode is active (for back button handling) */
    override fun isInFullscreenMode(): Boolean = isFullscreenMode

    /** Check if PDF is currently active (renderer is open). Used by gesture handlers to route PDF-specific gestures. */
    fun isPdfActive(): Boolean = pdfRenderer != null

    /**
     * S1273: whether a swipe may currently turn a page. False in scroll mode, where the RecyclerView
     * owns vertical movement and a page-turn gesture would fight it.
     */
    fun isPageSwipeEnabled(): Boolean = pdfRenderer != null && !isScrollMode

    /** S1273: turn one page in [next] direction. Entry point for gesture-driven paging. */
    fun turnPage(next: Boolean) {
        if (next) showNextPage() else showPreviousPage()
    }

    /**
     * Handle a PDF fling: a horizontal swipe steps the zoom (S0949).
     *
     * S1273 moved page turns to [PdfPageSwipeDetector]. PhotoView withholds this callback above
     * scale 1.0, for multi-touch, and below the platform fling velocity, so it can never see the
     * gestures that turn a page - only the unzoomed horizontal flick that survives those guards.
     * Wired from each host's PhotoView single-fling listener; returns true if handled.
     */
    fun handlePdfFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float): Boolean {
        if (pdfRenderer == null || e1 == null || isScrollMode) {
            return false
        }

        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x
        val swipeThreshold = (root.width * PDF_SWIPE_THRESHOLD_FRACTION).toInt()
            .coerceAtLeast(PDF_MIN_SWIPE_THRESHOLD_PX)
        var handled = false
        if (abs(diffX) > abs(diffY) && abs(diffX) > swipeThreshold &&
            abs(velocityX) > PDF_SWIPE_VELOCITY_THRESHOLD
        ) {
            // S0949: horizontal swipe = zoom step (right = in, left = out), clamped to 0.3x..10x.
            stepPdfZoom(zoomIn = diffX > 0)
            handled = true
        }
        return handled
    }

    /**
     * S0949: shared PDF zoom-step - one multiplicative step clamped to 0.3x..10x.
     * Drives the horizontal-swipe gesture and the on-screen zoom buttons so both hosts share one range.
     */
    fun stepPdfZoom(zoomIn: Boolean) {
        if (pdfRenderer == null) return
        val photoView = safeViews.photoView
        val factor = if (zoomIn) PDF_ZOOM_STEP_FACTOR else 1f / PDF_ZOOM_STEP_FACTOR
        photoView.setScale((photoView.scale * factor).coerceIn(PDF_MIN_SCALE, PDF_MAX_SCALE), true)
    }

    /** Handle long press gesture: open the text-selection overlay for the current page, pre-selecting the word under the long-press point (view coordinates). Returns true if handled (PDF active), false otherwise. */
    fun handlePdfLongPress(x: Float, y: Float): Boolean {
        if (pdfRenderer != null && currentPageBitmap != null) {
            openPdfTextSelection(x, y)
            return true
        }
        return false
    }

    /** Single entry to the PDF text-selection overlay, shared by the TXT button (no point) and the long-press gesture (with the touch point). Shows the text-extraction toast, then opens the overlay; a non-NaN point pre-selects the nearest word. */
    private fun openPdfTextSelection(x: Float = Float.NaN, y: Float = Float.NaN) {
        android.widget.Toast.makeText(
            root.context,
            root.context.getString(R.string.pdf_text_extracting),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        val point = if (!x.isNaN() && !y.isNaN()) android.graphics.PointF(x, y) else null
        pdfTextSelectionManager.enterTextSelectionMode(
            pageIndex          = currentPdfPageIndex,
            currentBitmap      = currentPageBitmap,
            pdfRenderer        = pdfRenderer,
            point
        )
    }

    /** Implementation of BaseDocumentViewerManager abstract methods for touch zone handling */
    override fun onPreviousPageRequest() {
        showPreviousPage()
    }

    override fun onNextPageRequest() {
        showNextPage()
    }

    override fun onExitFullscreenRequest() {
        exitFullscreenMode()
    }

    /** Clear all translation overlays (both normal and Lens style) Used when changing pages to prevent old translations from showing */
    private fun clearTranslationOverlays() {
        safeViews.translationOverlay.isVisible = false
        safeViews.tvTranslatedText.text = ""

        safeViews.translationLensOverlay.isVisible = false
        safeViews.translationLensOverlay.clear()

        // Hide font size controls when translation is inactive
        safeViews.btnTranslationFontDecrease?.visibility = android.view.View.GONE
        safeViews.btnTranslationFontIncrease?.visibility = android.view.View.GONE

        // Close text selection overlay on page change
        pdfTextSelectionManager.exitTextSelectionMode()
    }

    fun updateButtonVisibility() {
        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            withContext(Dispatchers.Main) {
                // Only HIDE buttons if feature is disabled in settings
                // CommandPanelController controls showing buttons based on orientation
                if (!settings.enableTranslation) safeViews.btnTranslatePdfCmd.isVisible = false
                if ("lens" !in settings.enabledShareTargets) safeViews.btnGoogleLensPdfCmd.isVisible = false
                if (!settings.enableOcr) safeViews.btnOcrPdfCmd.isVisible = false
                // btnSearchPdfCmd visibility controlled by CommandPanelController
            }
        }
    }

    private fun closePdfRenderer() {
        exitFullscreenMode()
        pdfTextSelectionManager.exitTextSelectionMode()
        safeViews.btnSelectTextPdf?.isVisible = false
        try {
            currentPdfPage?.close()
            currentPdfPage = null
            pdfRenderer?.close()
            pdfRenderer = null
            pdfParcelFileDescriptor?.close()
            pdfParcelFileDescriptor = null
        } catch (e: Exception) {
            Timber.e(e, "Error closing PDF renderer")
        }
    }

    /** Search for text in current PDF page. Returns total number of matches found on current page. Note: Searches only in current page's cached OCR/translation text. */
    suspend fun searchInPdf(query: String): Int =
        pdfLinkAndSearchManager.searchInPdf(query, currentPdfPath, currentPdfPageIndex)

    /** Navigate to next search result (highlights on screen). */
    fun nextSearchResult() = pdfLinkAndSearchManager.nextSearchResult()

    /** Navigate to previous search result. */
    fun previousSearchResult() = pdfLinkAndSearchManager.previousSearchResult()

    /** Get current search state as (currentIndex+1, totalMatches, query). */
    fun getSearchState(): Triple<Int, Int, String> = pdfLinkAndSearchManager.getSearchState()

    /** Called on single-tap in page mode. Delegates to [PdfLinkAndSearchManager]. Returns true if a link was found and opened. */
    fun handlePdfTap(tapX: Float, tapY: Float): Boolean =
        pdfLinkAndSearchManager.handlePdfTap(
            tapX               = tapX,
            tapY               = tapY,
            currentPageIndex   = currentPdfPageIndex,
            currentBitmap      = currentPageBitmap,
            isScrollMode       = isScrollMode,
            pdfRendererActive  = pdfRenderer != null
        )

    /** Share current PDF page to Google Lens for visual search/text recognition. Saves current page bitmap to temp file and delegates to activity callback. */
    fun shareCurrentPageToGoogleLens() {
        pdfLinkAndSearchManager.shareCurrentPageToGoogleLens(getCurrentPageBitmap())
    }

    companion object {
        // S0949: shared PDF zoom-step contract. Range 0.3x..10x, one multiplicative step per swipe/button.
        private const val PDF_MIN_SCALE = 0.3f
        private const val PDF_MEDIUM_SCALE = 2.0f
        private const val PDF_MAX_SCALE = 10.0f
        private const val PDF_ZOOM_STEP_FACTOR = 1.5f

        private const val PDF_SWIPE_THRESHOLD_FRACTION = 0.05f
        private const val PDF_MIN_SWIPE_THRESHOLD_PX = 50
        private const val PDF_SWIPE_VELOCITY_THRESHOLD = 100
    }
}

