package com.sza.fastmediasorter.ui.player.helpers

import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.util.showBoundToHost
import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.epub.EpubReader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import kotlin.math.roundToInt

/**
 * Manages EPUB e-book viewing in PlayerActivity:
 * - Opens and parses EPUB files using epub4j-core library
 * - Renders HTML content in WebView with custom CSS theming
 * - Handles chapter navigation (previous/next)
 * - Extracts and serves embedded resources (images, fonts)
 * - Saves and restores last viewed chapter position
 * - Syncs styling with app theme (dark/light mode)
 *
 * Search/TOC presentation delegated to [EpubSearchAndTocPresenter].
 * Translation overlay lifecycle delegated to [EpubTranslationOverlayHelper].
 */
@android.annotation.SuppressLint("SetTextI18n")
class EpubViewerManager(
    // S0380: decoupled from ActivityPlayerUnifiedBinding to a layout root so the EPUB viewer drives
    // both the full unified player layout and the trimmed document standalone layout.
    root: View,
    private val networkFileManager: NetworkFileManager,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: EpubViewerCallback,
    private val playbackPositionRepository: com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository,
    private val translationManager: TranslationManager,
    // S0704: non-null only in the unified player; null in the document standalone (direct write).
    private val loadingIndicatorCoordinator: PlayerLoadingIndicatorCoordinator? = null,
) : BaseDocumentViewerManager(root) {
    private val safeViews = PlayerBindingSafeViews(root)

    /** S0704: route the EPUB load spinner through the coordinator when present, else write directly. */
    private fun setEpubLoadSpinner(visible: Boolean) {
        val coord = loadingIndicatorCoordinator
        if (coord != null) {
            if (visible) coord.show(LoadingSource.EPUB_LOAD) else coord.hide(LoadingSource.EPUB_LOAD)
        } else {
            safeViews.playerProgressBar.isVisible = visible
        }
    }

    /**
     * S1549: re-point at a re-inflated hierarchy. The parsed book, the chapter index and the live
     * WebView all survive - the WebView is moved into the new container rather than re-created,
     * which is what keeps the reading position across a rotation.
     */
    override fun rebindLayoutRoot(newRoot: View) {
        super.rebindLayoutRoot(newRoot)
        safeViews.rebindRoot(newRoot)
        webViewLifecycle.rebindLayoutRoot(newRoot)
    }

    interface EpubViewerCallback {
        fun showError(message: String)
        fun displayTranslatedText(text: String)
        fun onEnterFullscreenMode()
        fun onExitFullscreenMode()
    }

    // EPUB state
    private var currentBook: Book? = null
    private var currentChapterIndex = 0
    // S0196 Phase 04: one-shot tag emitted when the first EPUB chapter finishes loading in
    // the WebView - "primary content rendered" for the StandalonePlayer docs branch.
    private var firstChapterRenderedLogged = false
    var chapterCount = 0
    private var currentEpubFile: File? = null
    private var currentEpubPath: String? = null // Original file path for position saving

    // Font size control (6-144px, default 18px)
    // Extended range: 6px allows ~300% zoom out from default 18px; 144px for "HUGE" setting
    private var currentFontSize: Int = 18
    private val MIN_FONT_SIZE = 6
    private val MAX_FONT_SIZE = 144

    // Font family for EPUB content (loaded from settings)
    private var currentFontFamily: String = "Georgia, serif"

    // Reader style settings (loaded from AppSettings)
    private var currentReaderTheme: EpubStyleManager.ReaderTheme = EpubStyleManager.ReaderTheme.LIGHT
    private var currentLineHeight: Float = 1.6f
    private var currentHorizontalMargin: Int = 16

    // Fullscreen mode state
    private var isFullscreenMode = false

    // Settings loading gate - await before first chapter render (C-3 fix)
    private val settingsReady = CompletableDeferred<Unit>()

    // Selection bridge: captures the latest selected text from WebView via JS interface
    private val selectionBridge = EpubSelectionBridge()
    private val calculatorEnabledFlow = MutableStateFlow(false)

    // TTS Read Aloud delegate
    private val ttsDelegate = EpubTtsDelegate(root.context)
    private val resourceContentHelper = EpubResourceContentHelper()
    private val webViewLifecycle = EpubWebViewLifecycle(
        root = root,
        safeViews = safeViews,
        resourceContentHelper = resourceContentHelper,
        selectionBridge = selectionBridge,
        onPageRendered = {
            if (!firstChapterRenderedLogged) {
                firstChapterRenderedLogged = true
                Timber.d("EpubViewerManager: firstChapterRendered chapter=$currentChapterIndex chapterCount=$chapterCount")
            }
        },
        swipeGestureProvider = { swipeGestureDetector },
        bookProvider = { currentBook },
        loadingIndicatorCoordinator = loadingIndicatorCoordinator,
    )
    // Backwards-compat alias for existing references.
    private val webView: WebView? get() = webViewLifecycle.current()

    // Translation overlay delegate (extracted from this class - S0002 Wave 42)
    private val translationHelper = EpubTranslationOverlayHelper(
        root = root,
        safeViews = safeViews,
        settingsRepository = settingsRepository,
        translationManager = translationManager,
        coroutineScope = coroutineScope,
        webViewProvider = { webView },
        callback = callback,
        updateTranslateButtonIcon = ::updateTranslateButtonIcon
    )

    // Search + TOC delegate (extracted from this class - S0002 Wave 42)
    private val searchAndTocPresenter = EpubSearchAndTocPresenter(
        root = root,
        coroutineScope = coroutineScope,
        webViewProvider = { webView },
        bookProvider = { currentBook },
        currentChapterIndexProvider = { currentChapterIndex },
        chapterCountProvider = { chapterCount },
        onNavigateToChapter = ::showChapter
    )

    /** JS interface injected into WebView to capture text selection events. The JS snippet in preprocessHtml fires `EpubSelectionBridge.onSelectionChanged` on every `selectionchange` DOM event. */
    inner class EpubSelectionBridge {
        @Volatile var lastSelectedText: String = ""

        @JavascriptInterface
        fun onSelectionChanged(text: String) {
            lastSelectedText = text
        }
    }

    // Gesture detector for swipe navigation and font-size control
    private var swipeGestureDetector: android.view.GestureDetector

    init {
        // Load font settings from repository (apply if not AUTO/DEFAULT)
        coroutineScope.launch {
            val savedFontSettings = withContext(Dispatchers.IO) {
                val prefs = root.context.applicationContext
                    .getSharedPreferences("epub_settings", android.content.Context.MODE_PRIVATE)
                Pair(
                    prefs.getInt("font_size", 18),
                    prefs.getInt("translation_font_size", 16)
                )
            }

            currentFontSize = savedFontSettings.first.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
            translationHelper.restoreFontSize(savedFontSettings.second)

            val settings = settingsRepository.getSettings().first()

            // Apply font size from settings if not AUTO
            if (settings.ocrDefaultFontSize != "AUTO") {
                val multiplier = when (settings.ocrDefaultFontSize) {
                    "MINIMUM" -> 0.7f
                    "SMALL"   -> 0.85f
                    "MEDIUM"  -> 1.0f
                    "LARGE"   -> 1.15f
                    "HUGE"    -> 1.3f
                    else      -> 1.0f
                }
                currentFontSize = (18 * multiplier).toInt().coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
                translationHelper.restoreFontSize(
                    (16 * multiplier).toInt()
                )
            }

            // Apply font family from settings if not DEFAULT
            currentFontFamily = if (settings.ocrDefaultFontFamily != "DEFAULT") {
                when (settings.ocrDefaultFontFamily) {
                    "SERIF"      -> "Georgia, serif"
                    "MONOSPACE"  -> "Courier New, monospace"
                    else         -> "Georgia, serif"
                }
            } else {
                "sans-serif"
            }

            currentReaderTheme = EpubStyleManager.ReaderTheme.fromName(settings.textReaderTheme)
            currentLineHeight = settings.epubLineHeight
            currentHorizontalMargin = settings.epubHorizontalMargin

            // Signal that settings are loaded (C-3 fix)
            settingsReady.complete(Unit)
        }
        coroutineScope.launch {
            settingsRepository.getSettings()
                .map { it.enableCalculator }
                .distinctUntilChanged()
                .collect { enabled ->
                    calculatorEnabledFlow.value = enabled
                }
        }

        // Initialize swipe gesture detector for chapter navigation and font size control
        swipeGestureDetector = android.view.GestureDetector(
            root.context,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: android.view.MotionEvent): Boolean = true

                override fun onFling(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false

                    val diffX = e2.x - e1.x
                    val diffY = e2.y - e1.y
                    val isHorizontalSwipe = kotlin.math.abs(diffX) > kotlin.math.abs(diffY)

                    if (isHorizontalSwipe && kotlin.math.abs(diffX) > 100 && kotlin.math.abs(velocityX) > 100) {
                        // S0760: per-step size Toast removed (arrived too late); proportional step is self-evident.
                        if (diffX > 0) increaseFontSize() else decreaseFontSize()
                        return true
                    } else if (!isHorizontalSwipe && kotlin.math.abs(diffY) > 100 && kotlin.math.abs(velocityY) > 100) {
                        if (diffY < 0) {
                            if (!isFullscreenMode) safeViews.epubControlsLayout.isVisible = true
                        } else if (isFullscreenMode) checkAndExitFullscreenAtBottom()
                        else checkAndHideControlsAtBottom()
                        return true
                    }
                    return false
                }
            }
        )

        // Setup chapter indicator click to show "Go to chapter" dialog
        safeViews.tvEpubChapterIndicator.setOnClickListener {
            if (chapterCount > 1) {
                showGoToChapterDialog()
            }
        }

    }

    // ── EPUB display ─────────────────────────────────────────────────────────

    /** Display EPUB file in WebView */
    fun displayEpub(mediaFile: MediaFile) {
        // Reset views - hide all other media viewers
        safeViews.imageView.isVisible = false
        safeViews.photoView.isVisible = false
        safeViews.playerView.isVisible = false
        safeViews.audioCoverArtView.isVisible = false
        safeViews.audioInfoOverlay.isVisible = false
        // S0380: text-viewer and the deprecated image-translate button are cross-type views the
        // trimmed document-standalone layout omits (it never shows text/image). Reset by presence so
        // EpubViewerManager works on both the full unified layout and the trimmed standalone one.
        safeViews.setVisibleIfPresent(R.id.textViewerContainer, false)
        safeViews.pdfControlsLayout.isVisible = false
        safeViews.setVisibleIfPresent(R.id.btnTranslateImage, false)
        setEpubLoadSpinner(true)

        // Force UI update to ensure progressBar is actually visible before async work
        safeViews.playerProgressBar.post { safeViews.playerProgressBar.invalidate() }

        // Hide text action buttons (they are for TXT files only)
        safeViews.btnCopyTextCmd.isVisible = false
        safeViews.btnEditTextCmd.isVisible = false
        safeViews.btnTranslateTextCmd.isVisible = false
        safeViews.btnSearchTextCmd.isVisible = false

        // Hide PDF action buttons (they are for PDF files only)
        safeViews.btnGoogleLensPdfCmd.isVisible = false
        safeViews.btnOcrPdfCmd.isVisible = false
        safeViews.btnTranslatePdfCmd.isVisible = false
        safeViews.btnSearchPdfCmd.isVisible = false

        // Show EPUB action buttons in command panel
        safeViews.btnSearchEpubCmd.isVisible = true
        safeViews.btnTranslateEpubCmd.isVisible = true

        // Update translate button icon with language badge
        updateTranslateButtonIcon()

        safeViews.epubWebView.isVisible = true
        safeViews.epubControlsLayout.isVisible = true
        safeViews.btnExitEpubFullscreen.isVisible = false // Hidden initially, shown in fullscreen

        closeEpubBook()
        val context = root.context

        // Show loading toast for network files
        val isNetworkFile = mediaFile.path.startsWith("smb://") ||
            mediaFile.path.startsWith("sftp://") ||
            mediaFile.path.startsWith("ftp://") ||
            mediaFile.path.startsWith("https://")

        val loadingToastJob = coroutineScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(if (isNetworkFile) 0 else 2000)
            if (safeViews.playerProgressBar.isVisible) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.please_wait),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

        coroutineScope.launch(Dispatchers.Main) {
            // Give UI thread time to render the ProgressBar before starting heavy IO work
            kotlinx.coroutines.delay(50)

            try {
                val file = withContext(Dispatchers.IO) {
                    try {
                        networkFileManager.prepareFileForRead(mediaFile)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            setEpubLoadSpinner(false)
                            callback.showError(context.getString(R.string.epub_load_failed))
                        }
                        throw e
                    }
                }

                if (!file.exists()) {
                    loadingToastJob.cancel()
                    setEpubLoadSpinner(false)
                    callback.showError(context.getString(R.string.epub_file_not_found))
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    try {
                        // Parse EPUB file
                        val epubReader = EpubReader()
                        val book = FileInputStream(file).use { inputStream ->
                            epubReader.readEpub(inputStream)
                        }

                        currentBook = book
                        currentEpubFile = file
                        currentEpubPath = mediaFile.path

                        // Get spine (reading order) for navigation
                        val spine = book.spine
                        chapterCount = spine.spineReferences.size
                        currentChapterIndex = 0

                        // Restore last viewed chapter position
                        val savedChapter = playbackPositionRepository.getPosition(mediaFile.path)
                        val startChapter = if (savedChapter != null && savedChapter > 0 && savedChapter < chapterCount) {
                            savedChapter.toInt()
                        } else {
                            0
                        }

                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            // DON'T hide progressBar here - WebViewClient will handle it after loading

                            if (chapterCount > 0) {
                                showChapter(startChapter)
                                if (startChapter > 0) {
                                }

                                // Hide navigation controls for single-chapter EPUBs
                                val isSingleChapter = chapterCount == 1
                                safeViews.btnEpubPrevChapter.isVisible = !isSingleChapter
                                safeViews.btnEpubNextChapter.isVisible = !isSingleChapter
                            } else {
                                callback.showError(context.getString(R.string.epub_no_readable_content))
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse EPUB")
                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            setEpubLoadSpinner(false)
                            val messageRes = if (isProtectedEpubError(e)) {
                                R.string.protected_file_unsupported
                            } else {
                                R.string.epub_parse_failed
                            }
                            callback.showError(context.getString(messageRes))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "EPUB display error")
                loadingToastJob.cancel()
                setEpubLoadSpinner(false)
                callback.showError(context.getString(R.string.epub_display_error))
            }
        }
    }

    // ── Chapter rendering ────────────────────────────────────────────────────

    private fun isProtectedEpubError(error: Throwable): Boolean {
        // DRM-protected EPUBs require a licensed reader path; this app must not attempt bypass.
        val message = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return message.contains("password") ||
            message.contains("encrypted") ||
            message.contains("encryption") ||
            message.contains("drm")
    }

    /** Show specific chapter by index */
    private suspend fun showChapter(chapterIndex: Int) {
        // Wait for settings to be loaded before first render (C-3 fix)
        settingsReady.await()
        // Reset stale selection from previous chapter
        selectionBridge.lastSelectedText = ""
        val book = currentBook ?: return
        val spine = book.spine

        if (chapterIndex < 0 || chapterIndex >= spine.spineReferences.size) {
            Timber.w("Invalid chapter index: $chapterIndex")
            return
        }

        currentChapterIndex = chapterIndex

        // Show progress bar while loading chapter (prevents "frozen" UI during HTML processing)
        withContext(Dispatchers.Main) {
            setEpubLoadSpinner(true)
        }

        withContext(Dispatchers.IO) {
            try {
                val spineRef = spine.spineReferences[chapterIndex]
                val resource = spineRef.resource

                val htmlContent = resource.data.toString(Charsets.UTF_8)
                val processedHtml = resourceContentHelper.preprocessHtml(
                    htmlContent = htmlContent,
                    resource = resource,
                    book = currentBook,
                    style = EpubResourceContentHelper.ReaderStyle(
                        theme = currentReaderTheme,
                        fontSizePx = currentFontSize,
                        fontFamily = currentFontFamily,
                        lineHeight = currentLineHeight,
                        horizontalPaddingPx = currentHorizontalMargin
                    )
                )

                withContext(Dispatchers.Main) {
                    // Load into WebView (getOrCreateWebView ensures it is initialised and
                    // WebViewClient is wired up before loadDataWithBaseURL is called)
                    getOrCreateWebView().loadDataWithBaseURL(
                        "file:///android_asset/", // Base URL for resource loading
                        processedHtml,
                        "text/html",
                        "UTF-8",
                        null
                    )

                    updateChapterIndicator()

                    // Auto-translate new chapter if translation is enabled
                    if (translationHelper.translationEnabled) {
                        translationHelper.translateCurrentChapter()
                    }

                    // Save position (chapter index as position, total chapters as duration)
                    currentEpubPath?.let { path ->
                        coroutineScope.launch(Dispatchers.IO) {
                            playbackPositionRepository.savePosition(
                                path,
                                currentChapterIndex.toLong(),
                                chapterCount.toLong()
                            )
                        }
                    }

                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to show chapter $chapterIndex")
                withContext(Dispatchers.Main) {
                    callback.showError(root.context.getString(R.string.epub_chapter_load_failed))
                }
            }
        }
    }

    // ── Chapter indicator and navigation dialogs ─────────────────────────────

    /** Update chapter indicator text (e.g., "5/12") */
    private fun updateChapterIndicator() {
        safeViews.tvEpubChapterIndicator.text = "${currentChapterIndex + 1}/$chapterCount"
        safeViews.tvEpubChapterIndicator.isVisible = chapterCount > 1
    }

    /** Show dialog to jump to specific EPUB chapter */
    private fun showGoToChapterDialog() {
        val context = root.context
        val editText = android.widget.EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = context.getString(R.string.epub_go_to_chapter_hint, chapterCount)
            setText("${currentChapterIndex + 1}")
            selectAll()
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(R.string.epub_go_to_chapter_title)
            .setMessage(context.getString(R.string.epub_go_to_chapter_message, chapterCount))
            .setView(editText)
            .setPositiveButton(R.string.epub_go_to_chapter_go) { dialog, _ ->
                val chapterNumber = editText.text.toString().toIntOrNull()
                if (chapterNumber != null && chapterNumber in 1..chapterCount) {
                    coroutineScope.launch {
                        showChapter(chapterNumber - 1) // Convert to 0-based index
                    }
                } else {
                    callback.showError(context.getString(R.string.epub_invalid_chapter_number, chapterCount))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.epub_go_to_chapter_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .showBoundToHost(context)
    }

    // ── Chapter navigation (public) ──────────────────────────────────────────

    /** Navigate to previous chapter */
    fun showPreviousChapter() {
        if (currentChapterIndex > 0) navigateToChapter(currentChapterIndex - 1)
    }

    /** Navigate to next chapter */
    fun showNextChapter() {
        if (currentChapterIndex < chapterCount - 1) navigateToChapter(currentChapterIndex + 1)
    }

    /** Navigate to first chapter */
    fun showFirstChapter() {
        if (currentChapterIndex > 0) navigateToChapter(0)
    }

    private fun navigateToChapter(targetIndex: Int) {
        safeViews.translationOverlay.isVisible = false
        safeViews.translationLensOverlay.isVisible = false
        stopTtsOnChapterChange()
        coroutineScope.launch {
            showChapter(targetIndex)
            if (translationHelper.translationEnabled) {
                kotlinx.coroutines.delay(500)
                translationHelper.translateCurrentChapter()
            }
        }
    }

    // ── BaseDocumentViewerManager overrides ──────────────────────────────────

    override fun onPreviousPageRequest() { showPreviousChapter() }
    override fun onNextPageRequest() { showNextChapter() }
    override fun onExitFullscreenRequest() {
        exitFullscreenMode()
    }
    override fun isInFullscreenMode(): Boolean = isFullscreenMode

    // ── Fullscreen mode ───────────────────────────────────────────────────────

    /** Enter fullscreen mode - hide controls and show exit button */
    fun enterFullscreenMode() {
        isFullscreenMode = true
        callback.onEnterFullscreenMode()
        safeViews.epubControlsLayout.isVisible = false
        safeViews.btnExitEpubFullscreen.isVisible = true
    }

    /** Exit fullscreen mode - show controls and hide exit button */
    fun exitFullscreenMode() {
        isFullscreenMode = false
        callback.onExitFullscreenMode()
        safeViews.epubControlsLayout.isVisible = true
        safeViews.btnExitEpubFullscreen.isVisible = false
    }

    // ── WebView lifecycle ─────────────────────────────────────────────────────

    /** Create WebView on first EPUB open and add it to the container FrameLayout. Deferred creation prevents Chromium from loading into native memory at layout inflation, avoiding native OOM on emulators and low-memory devices. */
    private fun getOrCreateWebView(): WebView = webViewLifecycle.getOrCreate()

    // ── Book lifecycle ────────────────────────────────────────────────────────

    /** Close current EPUB and release resources */
    fun closeEpubBook() {
        currentBook = null
        currentEpubFile = null
        currentEpubPath = null
        currentChapterIndex = 0
        chapterCount = 0
        webViewLifecycle.loadBlank()
    }

    /** Toggle TTS Read Aloud for the current EPUB chapter. */
    fun toggleReadAloud() = ttsDelegate.toggle(webView)

    /** Stop TTS on chapter navigation. */
    fun stopTtsOnChapterChange() = ttsDelegate.stop()

    /** Release all resources on activity destroy */
    fun release() {
        ttsDelegate.release()
        closeEpubBook()
        webViewLifecycle.destroyAndClear()
    }

    // ── Chapter progress ──────────────────────────────────────────────────────

    /** Get current chapter progress (for status display) */
    fun getCurrentProgress(): String {
        return if (chapterCount > 0) "${currentChapterIndex + 1}/$chapterCount" else ""
    }

    // ── Font size control (public) ────────────────────────────────────────────

    /** Increase EPUB body font size. S0760: shared proportional step (px scale). */
    fun increaseFontSize() {
        val next = FontResizeController.increase(
            currentFontSize.toFloat(),
            MIN_FONT_SIZE.toFloat(),
            MAX_FONT_SIZE.toFloat(),
        ).roundToInt()
        if (next != currentFontSize) {
            currentFontSize = next
            saveFontSize()
            reloadCurrentChapter()
        }
    }

    /** Decrease EPUB body font size. S0760: shared proportional step (px scale). */
    fun decreaseFontSize() {
        val next = FontResizeController.decrease(
            currentFontSize.toFloat(),
            MIN_FONT_SIZE.toFloat(),
            MAX_FONT_SIZE.toFloat(),
        ).roundToInt()
        if (next != currentFontSize) {
            currentFontSize = next
            saveFontSize()
            reloadCurrentChapter()
        }
    }

    /** Get current font size */
    fun getCurrentFontSize(): Int = currentFontSize

    private fun saveFontSize() {
        val prefs = root.context.getSharedPreferences("epub_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("font_size", currentFontSize).apply()
    }

    private fun reloadCurrentChapter() {
        coroutineScope.launch {
            showChapter(currentChapterIndex)
        }
    }

    // ── Reader settings dialog ────────────────────────────────────────────────

    /** Show reader settings dialog: theme, font, font size, line height, margin. Changes are applied immediately and persisted to AppSettings. */
    fun showReaderSettingsDialog() {
        val context = root.context
        val view: android.view.View = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_epub_reader_settings, null)

        // Snapshot current values for Cancel rollback (C-1 fix)
        var pendingTheme = currentReaderTheme
        var pendingFontFamily = currentFontFamily
        var pendingFontSize = currentFontSize

        // Theme chips
        val chipLight = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipLight)
        val chipDark = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipDark)
        val chipSepia = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipSepia)
        val chipOled = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipOled)

        // Font chips
        val chipSerif = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipSerif)
        val chipSansSerif = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipSansSerif)
        val chipMonospace = view.findViewById<com.google.android.material.chip.Chip>(R.id.chipMonospace)

        // Font size controls
        val btnFontDecrease = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFontDecrease)
        val btnFontIncrease = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnFontIncrease)
        val tvFontSizeValue = view.findViewById<android.widget.TextView>(R.id.tvFontSizeValue)

        // Sliders
        val sliderLineHeight = view.findViewById<com.google.android.material.slider.Slider>(R.id.sliderLineHeight)
        val sliderMargin = view.findViewById<com.google.android.material.slider.Slider>(R.id.sliderMargin)

        when (currentReaderTheme) {
            EpubStyleManager.ReaderTheme.LIGHT      -> chipLight.isChecked = true
            EpubStyleManager.ReaderTheme.DARK       -> chipDark.isChecked = true
            EpubStyleManager.ReaderTheme.SEPIA      -> chipSepia.isChecked = true
            EpubStyleManager.ReaderTheme.OLED_BLACK -> chipOled.isChecked = true
        }

        when {
            currentFontFamily.contains("serif", ignoreCase = true) &&
                !currentFontFamily.contains("sans", ignoreCase = true) -> chipSerif.isChecked = true
            currentFontFamily.contains("monospace", ignoreCase = true) -> chipMonospace.isChecked = true
            else -> chipSansSerif.isChecked = true
        }

        tvFontSizeValue.text = currentFontSize.toString()
        sliderLineHeight.value = currentLineHeight.coerceIn(1.0f, 3.0f)
        sliderMargin.value = currentHorizontalMargin.toFloat().coerceIn(0f, 48f)

        // Theme chip listeners - write to local pending vars, not to class fields (C-1 fix)
        val chipGroupTheme = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupTheme)
        chipGroupTheme.setOnCheckedStateChangeListener { _, checkedIds ->
            pendingTheme = when {
                checkedIds.contains(R.id.chipLight) -> EpubStyleManager.ReaderTheme.LIGHT
                checkedIds.contains(R.id.chipDark)  -> EpubStyleManager.ReaderTheme.DARK
                checkedIds.contains(R.id.chipSepia) -> EpubStyleManager.ReaderTheme.SEPIA
                checkedIds.contains(R.id.chipOled)  -> EpubStyleManager.ReaderTheme.OLED_BLACK
                else                                -> pendingTheme
            }
        }

        // Font chip listeners - write to local pending vars (C-1 fix)
        val chipGroupFont = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupFont)
        chipGroupFont.setOnCheckedStateChangeListener { _, checkedIds ->
            pendingFontFamily = when {
                checkedIds.contains(R.id.chipSerif)     -> "Georgia, serif"
                checkedIds.contains(R.id.chipMonospace) -> "Courier New, monospace"
                else                                    -> "sans-serif"
            }
        }

        // Font size buttons - use pending var (C-1 fix)
        btnFontDecrease.setOnClickListener {
            if (pendingFontSize > MIN_FONT_SIZE) {
                pendingFontSize -= 2
                tvFontSizeValue.text = pendingFontSize.toString()
            }
        }
        btnFontIncrease.setOnClickListener {
            if (pendingFontSize < MAX_FONT_SIZE) {
                pendingFontSize += 2
                tvFontSizeValue.text = pendingFontSize.toString()
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(R.string.epub_reader_settings)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Apply pending values to class fields on OK (C-1 fix)
                currentReaderTheme = pendingTheme
                currentFontFamily = pendingFontFamily
                currentFontSize = pendingFontSize
                currentLineHeight = sliderLineHeight.value
                currentHorizontalMargin = sliderMargin.value.toInt()

                saveFontSize()
                saveReaderSettings()
                reloadCurrentChapter()

            }
            .setNegativeButton(android.R.string.cancel, null)
            .showBoundToHost(context)
    }

    /** Persist reader style settings to AppSettings via repository. */
    private fun saveReaderSettings() {
        coroutineScope.launch {
            val current = settingsRepository.getSettings().first()
            // Map fontFamily CSS value back to AppSettings key (M-7 fix: persist font choice)
            val fontFamilySetting = when {
                currentFontFamily.contains("monospace", ignoreCase = true) -> "MONOSPACE"
                currentFontFamily.contains("serif", ignoreCase = true) &&
                    !currentFontFamily.contains("sans", ignoreCase = true) -> "SERIF"
                else -> "DEFAULT"
            }
            settingsRepository.updateSettings(
                current.copy(
                    textReaderTheme = currentReaderTheme.name,
                    epubLineHeight = currentLineHeight,
                    epubHorizontalMargin = currentHorizontalMargin,
                    ocrDefaultFontFamily = fontFamilySetting
                )
            )
        }
    }

    // ── TOC - delegated to EpubSearchAndTocPresenter ─────────────────────────

    /** Show Table of Contents dialog for quick chapter navigation */
    fun showTableOfContents() = searchAndTocPresenter.showTableOfContents()

    // ── Search - delegated to EpubSearchAndTocPresenter ──────────────────────

    /** Search for text in current EPUB chapter using WebView's built-in search. WebView.findAllAsync() highlights matches automatically. */
    fun searchInEpub(query: String, onResult: (Int) -> Unit = {}) =
        searchAndTocPresenter.searchInEpub(query, onResult)

    /** Navigate to next search match in the current chapter */
    fun nextSearchMatch() = searchAndTocPresenter.nextSearchMatch()

    /** Navigate to previous search match in the current chapter */
    fun previousSearchMatch() = searchAndTocPresenter.previousSearchMatch()

    /** Show cross-chapter search BottomSheet dialog. Scans all spine chapters for matches and displays results with context snippets. */
    fun showCrossChapterSearch() = searchAndTocPresenter.showCrossChapterSearch()

    /** Clear search highlighting in WebView */
    fun clearSearch() = searchAndTocPresenter.clearSearch()

    // ── Translation - delegated to EpubTranslationOverlayHelper ──────────────

    /** Toggle translation on/off for current chapter. Extracts text from WebView and displays translated text in overlay. */
    fun toggleTranslation() = translationHelper.toggleTranslation()

    /** Force enable translation and translate current chapter. Used when settings are changed via long-press dialog. */
    fun forceTranslate() = translationHelper.forceTranslate()

    // ── Text extraction ───────────────────────────────────────────────────────

    /** Extract text from current chapter and copy to clipboard (OCR functionality) */
    fun extractTextFromCurrentChapter() {
        val webView = webView ?: run {
            Timber.e("EPUB OCR: WebView is null")
            callback.showError(root.context.getString(R.string.player_webview_unavailable))
            return
        }

        webView.evaluateJavascript(
            "(function() { " +
            "  var text = document.documentElement.innerText || document.body.innerText || ''; " +
            "  return text.trim(); " +
            "})();"
        ) { result ->
            if (result == null || result == "null" || result.trim().isEmpty() || result.trim() == "\"\"") {
                // Show toast directly: user-initiated action - ToastThrottler cooldown must not suppress it
                android.widget.Toast.makeText(
                    root.context,
                    root.context.getString(R.string.translation_error_no_text),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@evaluateJavascript
            }

            val extractedText = result.trim().removeSurrounding("\"")

            if (extractedText.isNotBlank()) {
                val clipboard = root.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("EPUB Text", extractedText)
                clipboard.setPrimaryClip(clip)

                android.widget.Toast.makeText(
                    root.context,
                    root.context.getString(R.string.text_copied),
                    android.widget.Toast.LENGTH_SHORT
                ).show()

            } else {
                android.widget.Toast.makeText(
                    root.context,
                    root.context.getString(R.string.translation_error_no_text),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── Translate button icon ─────────────────────────────────────────────────

    /** Update translate button icon with language badge showing source -> target languages */
    private fun updateTranslateButtonIcon() {
        coroutineScope.launch {
            try {
                val settings = settingsRepository.getSettings().first()
                val sourceLang = settings.translationSourceLanguage
                val targetLang = settings.translationTargetLanguage

                val languageBadge = LanguageBadgeDrawable(
                    root.context,
                    sourceLang,
                    targetLang
                )

                withContext(Dispatchers.Main) {
                    // Clear any tint before setting custom drawable - tinting with a solid
                    // colour destroys the LanguageBadgeDrawable text (badge → solid block).
                    safeViews.btnTranslateEpubCmd.imageTintList = null
                    safeViews.btnTranslateEpubCmd.setImageDrawable(languageBadge)
                    // Keep alpha consistent with current translation state
                    safeViews.btnTranslateEpubCmd.alpha = if (translationHelper.translationEnabled) 1.0f else 0.55f
                }
            } catch (e: Exception) {
                Timber.e(e, "EPUB: Failed to update translate button icon")
            }
        }
    }

    // ── Font settings from translation session dialog ──────────────────────────

    /** Apply font settings from settings dialog without triggering translation */
    fun applyFontSettings(settings: com.sza.fastmediasorter.domain.models.TranslationSessionSettings) {

        // 1. Update font size based on multiplier
        if (settings.fontSize != com.sza.fastmediasorter.domain.models.TranslationFontSize.AUTO) {
            val multiplier = settings.fontSize.multiplier
            currentFontSize = (18 * multiplier).toInt().coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)

            val newTranslationFontSize = (16 * multiplier).toInt()
            translationHelper.restoreFontSize(newTranslationFontSize)
            translationHelper.saveTranslationFontSize()

            saveFontSize()

            // Update translation text size immediately if overlay is visible
            if (safeViews.translationOverlay.isVisible) {
                translationHelper.applyTranslationFontSize()
            }
        }

        // 2. Update font family
        currentFontFamily = if (settings.fontFamily != com.sza.fastmediasorter.domain.models.TranslationFontFamily.DEFAULT) {
            when (settings.fontFamily) {
                com.sza.fastmediasorter.domain.models.TranslationFontFamily.SERIF     -> "Georgia, serif"
                com.sza.fastmediasorter.domain.models.TranslationFontFamily.MONOSPACE -> "Courier New, monospace"
                else                                                                   -> "sans-serif"
            }
        } else {
            "sans-serif"
        }

        // 3. Reload current chapter to apply CSS changes
        reloadCurrentChapter()

        android.widget.Toast.makeText(
            root.context,
            "Font settings applied",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    // ── Scroll helpers ────────────────────────────────────────────────────────

    /** Scroll WebView to the very top (Home) */
    fun scrollToHome() {
        webView?.post { webView?.scrollTo(0, 0) }
    }

    /** Scroll WebView to the very bottom (End) */
    fun scrollToEnd() {
        webView?.post {
            webView?.let { view ->
                @Suppress("DEPRECATION")
                val scrollHeight = (view.contentHeight * view.scale).toInt()
                view.scrollTo(0, scrollHeight)
            }
        }
    }

    // ── Swipe-gesture helpers ─────────────────────────────────────────────────

    /** Check if WebView is scrolled to bottom and hide controls if needed */
    private fun checkAndHideControlsAtBottom() {
        val webView = this.webView ?: return
        webView.evaluateJavascript(
            "(function() { " +
            "  var scrollTop = window.pageYOffset || document.documentElement.scrollTop; " +
            "  var scrollHeight = document.documentElement.scrollHeight; " +
            "  var clientHeight = document.documentElement.clientHeight; " +
            "  var isAtBottom = (scrollTop + clientHeight >= scrollHeight - 50); " +
            "  return isAtBottom; " +
            "})();"
        ) { result ->
            val isAtBottom = result?.toBoolean() == true
            if (isAtBottom) {
                safeViews.epubControlsLayout.isVisible = false
                android.widget.Toast.makeText(
                    root.context,
                    root.context.getString(R.string.epub_controls_hidden),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Check if WebView is scrolled to bottom and exit fullscreen if needed */
    private fun checkAndExitFullscreenAtBottom() {
        val webView = this.webView ?: return
        webView.evaluateJavascript(
            "(function() { " +
            "  var scrollTop = window.pageYOffset || document.documentElement.scrollTop; " +
            "  var scrollHeight = document.documentElement.scrollHeight; " +
            "  var clientHeight = document.documentElement.clientHeight; " +
            "  var isAtBottom = (scrollTop + clientHeight >= scrollHeight - 50); " +
            "  return isAtBottom; " +
            "})();"
        ) { result ->
            val isAtBottom = result?.toBoolean() == true
            if (isAtBottom) {
                exitFullscreenMode()
                android.widget.Toast.makeText(
                    root.context,
                    root.context.getString(R.string.epub_exit_fullscreen),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── ActionMode selection callback ─────────────────────────────────────────

    /** Returns a [DocumentSelectionActionModeCallback] wired to this manager's JS selection bridge and translation handler. WebView does not support [android.widget.TextView.setCustomSelectionActionModeCallback]. The hosting Activity should override [android.app.Activity.startActionMode] and wrap the incoming callback with this one to inject "Translate" / "Search in Google" items into the WebView floating text-selection ActionMode. */
    fun getSelectionActionModeCallback(): DocumentSelectionActionModeCallback =
        DocumentSelectionActionModeCallback(
            showTranslate   = BuildConfig.ENABLE_TRANSLATION,
            getSelectedText = { selectionBridge.lastSelectedText },
            onTranslate     = translationHelper::handleTranslateSelection,
            onSearchGoogle  = { openGoogleSearch(root.context, it) },
            isCalculatorAvailable = { calculatorEnabledFlow.value },
            onOpenCalculator = { openCalculatorForSelection(root.context, it) },
        )
}
