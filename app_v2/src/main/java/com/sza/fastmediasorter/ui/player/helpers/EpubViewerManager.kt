package com.sza.fastmediasorter.ui.player.helpers

import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.documentnode.epub4j.domain.Book
import io.documentnode.epub4j.domain.Resource
import io.documentnode.epub4j.epub.EpubReader
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.File
import java.io.FileInputStream

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
    binding: ActivityPlayerUnifiedBinding,
    private val networkFileManager: NetworkFileManager,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: EpubViewerCallback,
    private val playbackPositionRepository: com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository,
    private val translationManager: TranslationManager
) : BaseDocumentViewerManager(binding) {
    private val safeViews = PlayerBindingSafeViews(binding)

    interface EpubViewerCallback {
        fun showError(message: String)
        fun displayTranslatedText(text: String)
        fun onEnterFullscreenMode()
        fun onExitFullscreenMode()
    }

    // EPUB state
    private var currentBook: Book? = null
    private var currentChapterIndex = 0
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

    // Settings loading gate — await before first chapter render (C-3 fix)
    private val settingsReady = CompletableDeferred<Unit>()

    // WebView for HTML rendering
    private var webView: WebView? = null

    // Selection bridge: captures the latest selected text from WebView via JS interface
    private val selectionBridge = EpubSelectionBridge()

    // TTS Read Aloud delegate
    private val ttsDelegate = EpubTtsDelegate(binding.root.context)

    // Translation overlay delegate (extracted from this class — S0002 Wave 42)
    private val translationHelper = EpubTranslationOverlayHelper(
        binding = binding,
        safeViews = safeViews,
        settingsRepository = settingsRepository,
        translationManager = translationManager,
        coroutineScope = coroutineScope,
        webViewProvider = { webView },
        callback = callback,
        updateTranslateButtonIcon = ::updateTranslateButtonIcon
    )

    // Search + TOC delegate (extracted from this class — S0002 Wave 42)
    private val searchAndTocPresenter = EpubSearchAndTocPresenter(
        binding = binding,
        coroutineScope = coroutineScope,
        webViewProvider = { webView },
        bookProvider = { currentBook },
        currentChapterIndexProvider = { currentChapterIndex },
        chapterCountProvider = { chapterCount },
        onNavigateToChapter = ::showChapter
    )

    /**
     * JS interface injected into WebView to capture text selection events.
     * The JS snippet in preprocessHtml fires `EpubSelectionBridge.onSelectionChanged`
     * on every `selectionchange` DOM event.
     */
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
                val prefs = binding.root.context.applicationContext
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

            // Load reader style settings
            currentReaderTheme = EpubStyleManager.ReaderTheme.fromName(settings.textReaderTheme)
            currentLineHeight = settings.epubLineHeight
            currentHorizontalMargin = settings.epubHorizontalMargin

            // Signal that settings are loaded (C-3 fix)
            settingsReady.complete(Unit)
        }

        // Initialize swipe gesture detector for chapter navigation and font size control
        swipeGestureDetector = android.view.GestureDetector(
            binding.root.context,
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
                        // Horizontal swipe: font size control
                        if (diffX > 0) {
                            increaseFontSize()
                            android.widget.Toast.makeText(
                                binding.root.context,
                                binding.root.context.getString(R.string.epub_font_size, currentFontSize),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            decreaseFontSize()
                            android.widget.Toast.makeText(
                                binding.root.context,
                                binding.root.context.getString(R.string.epub_font_size, currentFontSize),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                        return true
                    } else if (!isHorizontalSwipe && kotlin.math.abs(diffY) > 100 && kotlin.math.abs(velocityY) > 100) {
                        // Vertical swipe: control panel visibility or exit fullscreen
                        if (diffY < 0) {
                            // Swipe up — show controls if not in fullscreen
                            if (!isFullscreenMode) {
                                safeViews.epubControlsLayout.isVisible = true
                                Timber.d("EPUB: Controls shown via swipe up")
                            }
                        } else {
                            // Swipe down
                            if (isFullscreenMode) {
                                checkAndExitFullscreenAtBottom()
                            } else {
                                checkAndHideControlsAtBottom()
                            }
                        }
                        return true
                    }
                    return false
                }
            }
        )

        // Setup chapter indicator click to show "Go to chapter" dialog
        binding.tvEpubChapterIndicator.setOnClickListener {
            if (chapterCount > 1) {
                showGoToChapterDialog()
            }
        }

        Timber.d("EpubViewerManager initialized, fontSize=$currentFontSize")
    }

    // ── EPUB display ─────────────────────────────────────────────────────────

    /**
     * Display EPUB file in WebView
     */
    fun displayEpub(mediaFile: MediaFile) {
        // Reset views — hide all other media viewers
        binding.imageView.isVisible = false
        binding.photoView.isVisible = false
        binding.playerView.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        safeViews.textViewerContainer.isVisible = false
        safeViews.pdfControlsLayout.isVisible = false
        safeViews.btnTranslateImage.isVisible = false
        binding.progressBar.isVisible = true

        // Force UI update to ensure progressBar is actually visible before async work
        binding.progressBar.post { binding.progressBar.invalidate() }

        // Hide text action buttons (they are for TXT files only)
        binding.btnCopyTextCmd.isVisible = false
        binding.btnEditTextCmd.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false

        // Hide PDF action buttons (they are for PDF files only)
        binding.btnGoogleLensPdfCmd.isVisible = false
        binding.btnOcrPdfCmd.isVisible = false
        binding.btnTranslatePdfCmd.isVisible = false
        binding.btnSearchPdfCmd.isVisible = false

        // Show EPUB action buttons in command panel
        binding.btnSearchEpubCmd.isVisible = true
        binding.btnTranslateEpubCmd.isVisible = true

        // Update translate button icon with language badge
        updateTranslateButtonIcon()

        // Show EPUB UI
        binding.epubWebView.isVisible = true
        safeViews.epubControlsLayout.isVisible = true
        binding.btnExitEpubFullscreen.isVisible = false // Hidden initially, shown in fullscreen

        closeEpubBook()
        val context = binding.root.context

        // Show loading toast for network files
        val isNetworkFile = mediaFile.path.startsWith("smb://") ||
            mediaFile.path.startsWith("sftp://") ||
            mediaFile.path.startsWith("ftp://") ||
            mediaFile.path.startsWith("https://")

        val loadingToastJob = coroutineScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(if (isNetworkFile) 0 else 2000)
            if (binding.progressBar.isVisible) {
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
                            binding.progressBar.isVisible = false
                            callback.showError(context.getString(R.string.epub_load_failed))
                        }
                        throw e
                    }
                }

                if (!file.exists()) {
                    loadingToastJob.cancel()
                    binding.progressBar.isVisible = false
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

                        Timber.d("EPUB: Loaded '${book.title}' with $chapterCount chapters")

                        // Restore last viewed chapter position
                        val savedChapter = playbackPositionRepository.getPosition(mediaFile.path)
                        val startChapter = if (savedChapter != null && savedChapter > 0 && savedChapter < chapterCount) {
                            savedChapter.toInt()
                        } else {
                            0
                        }

                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            // DON'T hide progressBar here — WebViewClient will handle it after loading

                            if (chapterCount > 0) {
                                showChapter(startChapter)
                                if (startChapter > 0) {
                                    Timber.d("EPUB: Restored to chapter ${startChapter + 1}/$chapterCount")
                                }

                                // Hide navigation controls for single-chapter EPUBs
                                val isSingleChapter = chapterCount == 1
                                binding.btnEpubPrevChapter.isVisible = !isSingleChapter
                                binding.btnEpubNextChapter.isVisible = !isSingleChapter
                            } else {
                                callback.showError(context.getString(R.string.epub_no_readable_content))
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse EPUB")
                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            binding.progressBar.isVisible = false
                            callback.showError(context.getString(R.string.epub_parse_failed))
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "EPUB display error")
                loadingToastJob.cancel()
                binding.progressBar.isVisible = false
                callback.showError(context.getString(R.string.epub_display_error))
            }
        }
    }

    // ── Chapter rendering ────────────────────────────────────────────────────

    /**
     * Show specific chapter by index
     */
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
            binding.progressBar.isVisible = true
        }

        withContext(Dispatchers.IO) {
            try {
                val spineRef = spine.spineReferences[chapterIndex]
                val resource = spineRef.resource

                val htmlContent = resource.data.toString(Charsets.UTF_8)
                val processedHtml = preprocessHtml(htmlContent, resource)

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
                    Timber.d("EPUB: Chapter loaded, checking translation state. translationEnabled=${translationHelper.translationEnabled}")
                    if (translationHelper.translationEnabled) {
                        Timber.d("EPUB: Auto-translating new chapter (translation was enabled)")
                        translationHelper.translateCurrentChapter()
                    } else {
                        Timber.d("EPUB: Skipping auto-translation (translationEnabled=false)")
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

                    Timber.d("EPUB: Displayed chapter ${chapterIndex + 1}/$chapterCount")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to show chapter $chapterIndex")
                withContext(Dispatchers.Main) {
                    callback.showError(binding.root.context.getString(R.string.epub_chapter_load_failed))
                }
            }
        }
    }

    /**
     * Preprocess HTML content: inject custom CSS, sync theme, handle images
     */
    private suspend fun preprocessHtml(htmlContent: String, resource: Resource): String {
        val doc = Jsoup.parse(htmlContent)

        // Remove script tags to prevent XSS from untrusted EPUB content (M-6 fix)
        doc.select("script").remove()

        // Inject trusted selection-bridge script (our own JS, not from EPUB)
        doc.body()?.append(
            """<script>
               document.addEventListener('selectionchange', function() {
                   if (typeof EpubSelectionBridge !== 'undefined') {
                       EpubSelectionBridge.onSelectionChanged(window.getSelection().toString());
                   }
               });
               </script>"""
        )

        val css = EpubStyleManager.generateCss(
            theme = currentReaderTheme,
            fontSizePx = currentFontSize,
            fontFamily = currentFontFamily,
            lineHeight = currentLineHeight,
            horizontalPaddingPx = currentHorizontalMargin
        )
        doc.head().prepend(css)

        // Handle embedded images — extract from EPUB and convert to base64 data URIs
        val book = currentBook
        if (book != null) {
            val images = doc.select("img")
            Timber.d("EPUB: Found ${images.size} <img> tags in chapter")

            for (img in images) {
                val src = img.attr("src")
                if (src.isNotBlank() && !src.startsWith("data:") && !src.startsWith("http")) {
                    convertResourceToDataUri(img, "src", src, resource, book)
                }
            }

            // Also handle background images in style attributes
            val elementsWithStyle = doc.select("[style*=url]")
            Timber.d("EPUB: Found ${elementsWithStyle.size} elements with background-image in style")

            for (element in elementsWithStyle) {
                val style = element.attr("style")
                if (style.contains("url(") && !style.contains("data:")) {
                    val urlStart = style.indexOf("url(") + 4
                    val urlEnd = style.indexOf(")", urlStart)
                    if (urlEnd > urlStart) {
                        val url = style.substring(urlStart, urlEnd).trim('\'', '"', ' ')
                        if (url.isNotBlank() && !url.startsWith("data:") && !url.startsWith("http")) {
                            val imageResource = findImageResource(url, resource, book)
                            if (imageResource != null) {
                                val imageData = imageResource.data
                                val base64 = android.util.Base64.encodeToString(imageData, android.util.Base64.NO_WRAP)
                                val mimeType = imageResource.mediaType?.name ?: "image/jpeg"
                                val dataUri = "data:$mimeType;base64,$base64"
                                val newStyle = style.replace("url($url)", "url($dataUri)")
                                element.attr("style", newStyle)
                                Timber.d("EPUB: Converted background-image '$url' to data URI")
                            } else {
                                Timber.w("EPUB: Background image not found: $url")
                            }
                        }
                    }
                }
            }
        }

        return doc.html()
    }

    /**
     * Convert image resource to data URI and set it to element attribute
     */
    private fun convertResourceToDataUri(
        element: org.jsoup.nodes.Element,
        attrName: String,
        src: String,
        baseResource: Resource,
        book: Book
    ) {
        try {
            val imageResource = findImageResource(src, baseResource, book)

            if (imageResource != null) {
                val imageData = imageResource.data
                val base64 = android.util.Base64.encodeToString(imageData, android.util.Base64.NO_WRAP)
                val mimeType = imageResource.mediaType?.name ?: "image/jpeg"
                val dataUri = "data:$mimeType;base64,$base64"
                element.attr(attrName, dataUri)
                Timber.d("EPUB: Converted image '$src' to data URI (${imageData.size} bytes, mime=$mimeType)")
            } else {
                Timber.w("EPUB: Image resource not found after all attempts: original='$src'")
                if (src.contains("cover", ignoreCase = true)) {
                    val imageResources = book.resources.all.filter { it.mediaType?.name?.startsWith("image/") == true }
                    Timber.w("EPUB: Available images in EPUB: ${imageResources.map { it.href }.joinToString()}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "EPUB: Failed to process image '$src'")
        }
    }

    /**
     * Find image resource using multiple fallback strategies
     */
    private fun findImageResource(src: String, baseResource: Resource, book: Book): Resource? {
        val resourceHref = resolveResourcePath(baseResource.href, src)
        Timber.d("EPUB: Resolving image - original='$src', base='${baseResource.href}', resolved='$resourceHref'")

        var imageResource = book.resources.getByHref(resourceHref)

        if (imageResource == null) {
            imageResource = book.resources.getByHref(src)
            if (imageResource != null) Timber.d("EPUB: Found image by original path '$src'")
        }

        if (imageResource == null) {
            val simplePath = src.trimStart('/', '.')
            imageResource = book.resources.getByHref(simplePath)
            if (imageResource != null) Timber.d("EPUB: Found image by simple path '$simplePath'")
        }

        if (imageResource == null) {
            val filename = src.substringAfterLast('/')
            for (res in book.resources.all) {
                if (res.href.endsWith(filename)) {
                    imageResource = res
                    Timber.d("EPUB: Found image by filename match '${res.href}'")
                    break
                }
            }
        }

        return imageResource
    }

    /**
     * Find image resource by path from WebView request.
     * Used by shouldInterceptRequest to serve images from EPUB.
     */
    private fun findImageResourceByPath(path: String, book: Book): Resource? {
        var resource = book.resources.getByHref(path)
        if (resource != null) {
            Timber.d("EPUB: Found resource by exact path '$path'")
            return resource
        }

        val pathWithoutSlash = path.trimStart('/')
        resource = book.resources.getByHref(pathWithoutSlash)
        if (resource != null) {
            Timber.d("EPUB: Found resource by path without slash '$pathWithoutSlash'")
            return resource
        }

        val commonPrefixes = listOf("OEBPS/", "OPS/", "EPUB/", "")
        for (prefix in commonPrefixes) {
            resource = book.resources.getByHref(prefix + pathWithoutSlash)
            if (resource != null) {
                Timber.d("EPUB: Found resource with prefix '$prefix$pathWithoutSlash'")
                return resource
            }
        }

        val filename = path.substringAfterLast('/')
        for (res in book.resources.all) {
            if (res.href.endsWith(filename)) {
                Timber.d("EPUB: Found resource by filename match '${res.href}' for request '$path'")
                return res
            }
        }

        return null
    }

    /**
     * Resolve relative resource path from HTML content.
     * Example: base="OEBPS/Text/chapter01.xhtml", relative="../Images/pic.jpg" -> "OEBPS/Images/pic.jpg"
     */
    private fun resolveResourcePath(baseHref: String, relativePath: String): String {
        val cleaned = relativePath.removePrefix("./")

        if (!baseHref.contains("/")) return cleaned

        val baseParts = baseHref.split("/").dropLast(1)
        val relativeParts = cleaned.split("/")
        val resolvedParts = baseParts.toMutableList()

        for (part in relativeParts) {
            when (part) {
                ".." -> if (resolvedParts.isNotEmpty()) resolvedParts.removeAt(resolvedParts.size - 1)
                "."  -> { /* skip current directory marker */ }
                else -> resolvedParts.add(part)
            }
        }

        return resolvedParts.joinToString("/")
    }

    // ── Chapter indicator and navigation dialogs ─────────────────────────────

    /**
     * Update chapter indicator text (e.g., "5/12")
     */
    private fun updateChapterIndicator() {
        binding.tvEpubChapterIndicator.text = "${currentChapterIndex + 1}/$chapterCount"
        binding.tvEpubChapterIndicator.isVisible = chapterCount > 1
        Timber.d("EPUB: Chapter indicator updated: ${currentChapterIndex + 1}/$chapterCount")
    }

    /**
     * Show dialog to jump to specific EPUB chapter
     */
    private fun showGoToChapterDialog() {
        val context = binding.root.context
        val editText = android.widget.EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = context.getString(R.string.epub_go_to_chapter_hint, chapterCount)
            setText("${currentChapterIndex + 1}")
            selectAll()
        }

        android.app.AlertDialog.Builder(context)
            .setTitle(R.string.epub_go_to_chapter_title)
            .setMessage(context.getString(R.string.epub_go_to_chapter_message, chapterCount))
            .setView(editText)
            .setPositiveButton(R.string.epub_go_to_chapter_go) { dialog, _ ->
                val chapterNumber = editText.text.toString().toIntOrNull()
                if (chapterNumber != null && chapterNumber in 1..chapterCount) {
                    coroutineScope.launch {
                        showChapter(chapterNumber - 1) // Convert to 0-based index
                    }
                    Timber.d("Jumped to chapter $chapterNumber")
                } else {
                    callback.showError(context.getString(R.string.epub_invalid_chapter_number, chapterCount))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.epub_go_to_chapter_cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // ── Chapter navigation (public) ──────────────────────────────────────────

    /** Navigate to previous chapter */
    fun showPreviousChapter() {
        if (currentChapterIndex > 0) {
            safeViews.translationOverlay.isVisible = false
            binding.translationLensOverlay.isVisible = false
            stopTtsOnChapterChange()
            coroutineScope.launch {
                showChapter(currentChapterIndex - 1)
                if (translationHelper.translationEnabled) {
                    kotlinx.coroutines.delay(500)
                    translationHelper.translateCurrentChapter()
                }
            }
        } else {
            Timber.d("EPUB: Already at first chapter")
        }
    }

    /** Navigate to next chapter */
    fun showNextChapter() {
        if (currentChapterIndex < chapterCount - 1) {
            safeViews.translationOverlay.isVisible = false
            binding.translationLensOverlay.isVisible = false
            stopTtsOnChapterChange()
            coroutineScope.launch {
                showChapter(currentChapterIndex + 1)
                if (translationHelper.translationEnabled) {
                    kotlinx.coroutines.delay(500)
                    translationHelper.translateCurrentChapter()
                }
            }
        } else {
            Timber.d("EPUB: Already at last chapter")
        }
    }

    /** Navigate to first chapter */
    fun showFirstChapter() {
        if (currentChapterIndex > 0) {
            safeViews.translationOverlay.isVisible = false
            binding.translationLensOverlay.isVisible = false
            stopTtsOnChapterChange()
            coroutineScope.launch {
                showChapter(0)
                if (translationHelper.translationEnabled) {
                    kotlinx.coroutines.delay(500)
                    translationHelper.translateCurrentChapter()
                }
            }
        } else {
            Timber.d("EPUB: Already at first chapter")
        }
    }

    // ── BaseDocumentViewerManager overrides ──────────────────────────────────

    override fun onPreviousPageRequest() { showPreviousChapter() }
    override fun onNextPageRequest() { showNextChapter() }
    override fun onExitFullscreenRequest() {
        exitFullscreenMode()
        Timber.d("EPUB: Exit fullscreen requested")
    }
    override fun isInFullscreenMode(): Boolean = isFullscreenMode

    // ── Fullscreen mode ───────────────────────────────────────────────────────

    /** Enter fullscreen mode — hide controls and show exit button */
    fun enterFullscreenMode() {
        isFullscreenMode = true
        callback.onEnterFullscreenMode()
        safeViews.epubControlsLayout.isVisible = false
        binding.btnExitEpubFullscreen.isVisible = true
        Timber.d("EPUB: Entered fullscreen mode")
    }

    /** Exit fullscreen mode — show controls and hide exit button */
    fun exitFullscreenMode() {
        isFullscreenMode = false
        callback.onExitFullscreenMode()
        safeViews.epubControlsLayout.isVisible = true
        binding.btnExitEpubFullscreen.isVisible = false
        Timber.d("EPUB: Exited fullscreen mode")
    }

    // ── WebView lifecycle ─────────────────────────────────────────────────────

    /**
     * Create WebView on first EPUB open and add it to the container FrameLayout.
     * Deferred creation prevents Chromium from loading into native memory at layout inflation,
     * avoiding native OOM on emulators and low-memory devices.
     */
    private fun getOrCreateWebView(): WebView {
        webView?.let { return it }
        return WebView(binding.epubWebView.context).also { wv ->
            binding.epubWebView.addView(
                wv,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            webView = wv
            configureWebView(wv)
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun configureWebView(wv: WebView) {
        wv.settings.javaScriptEnabled = true
        wv.settings.loadWithOverviewMode = true
        wv.settings.useWideViewPort = true
        wv.settings.builtInZoomControls = true
        wv.settings.displayZoomControls = false
        wv.settings.setSupportZoom(true)
        wv.isLongClickable = true
        wv.isClickable = true
        wv.isFocusable = true
        wv.isFocusableInTouchMode = true
        wv.setOnLongClickListener(null)
        wv.addJavascriptInterface(selectionBridge, "EpubSelectionBridge")
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.post {
                    binding.progressBar.isVisible = false
                }
                Timber.d("EPUB: WebView finished loading chapter")
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): android.webkit.WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (url.startsWith("file:///android_asset/")) {
                    val resourcePath = url.removePrefix("file:///android_asset/")
                    Timber.d("EPUB: Intercepting request for asset: $resourcePath")
                    val book = currentBook
                    if (book != null) {
                        val imageResource = findImageResourceByPath(resourcePath, book)
                        if (imageResource != null) {
                            try {
                                val imageData = imageResource.data
                                val mimeType = imageResource.mediaType?.name ?: "image/jpeg"
                                val inputStream = java.io.ByteArrayInputStream(imageData)
                                Timber.d("EPUB: Serving intercepted asset '$resourcePath' from EPUB (${imageData.size} bytes, $mimeType)")
                                return android.webkit.WebResourceResponse(mimeType, "UTF-8", inputStream)
                            } catch (e: Exception) {
                                Timber.e(e, "EPUB: Error serving intercepted asset '$resourcePath'")
                            }
                        } else {
                            Timber.w("EPUB: Asset '$resourcePath' not found in EPUB resources")
                            val imageResources = book.resources.all.filter {
                                it.mediaType?.name?.startsWith("image/") == true
                            }
                            Timber.w("EPUB: Available images: ${imageResources.map { it.href }.joinToString()}")
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        wv.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            swipeGestureDetector.onTouchEvent(event)
            false
        }
    }

    // ── Book lifecycle ────────────────────────────────────────────────────────

    /** Close current EPUB and release resources */
    fun closeEpubBook() {
        currentBook = null
        currentEpubFile = null
        currentEpubPath = null
        currentChapterIndex = 0
        chapterCount = 0

        // SecurityException is thrown on HorizonOS (Quest) because the underlying
        // Chromium WebView tries to read system preferences it has no access to.
        try {
            webView?.loadUrl("about:blank")
        } catch (e: SecurityException) {
            Timber.w("EpubViewerManager: loadUrl(about:blank) denied by system (Quest/HorizonOS) — ignored")
        }

        Timber.d("EPUB: Book closed, resources released")
    }

    // ── TTS Read Aloud ────────────────────────────────────────────────────────

    /** Toggle TTS Read Aloud for the current EPUB chapter. */
    fun toggleReadAloud() {
        ttsDelegate.toggle(webView)
    }

    /** Stop TTS on chapter navigation. */
    fun stopTtsOnChapterChange() {
        ttsDelegate.stop()
    }

    // ── Release ───────────────────────────────────────────────────────────────

    /** Release all resources on activity destroy */
    fun release() {
        ttsDelegate.release()
        closeEpubBook()
        webView?.let { wv ->
            try {
                // Detach WebView from window BEFORE destroy to prevent native crash
                (wv.parent as? android.view.ViewGroup)?.removeView(wv)
                wv.removeAllViews()
                wv.clearCache(true)
                wv.destroy()
                Timber.d("EpubViewerManager: WebView properly destroyed")
            } catch (e: Exception) {
                Timber.e(e, "EpubViewerManager: Error destroying WebView")
            }
        }

        // ML-010: Clear WebViewDatabase credentials to prevent data retention
        try {
            android.webkit.WebViewDatabase.getInstance(binding.root.context).clearHttpAuthUsernamePassword()
            Timber.d("EpubViewerManager: Cleared WebViewDatabase HTTP auth credentials (ML-010)")
        } catch (e: Exception) {
            Timber.e(e, "EpubViewerManager: Error clearing WebViewDatabase credentials (ML-010)")
        }

        webView = null
        Timber.d("EpubViewerManager: Released")
    }

    // ── Chapter progress ──────────────────────────────────────────────────────

    /** Get current chapter progress (for status display) */
    fun getCurrentProgress(): String {
        return if (chapterCount > 0) "${currentChapterIndex + 1}/$chapterCount" else ""
    }

    // ── Font size control (public) ────────────────────────────────────────────

    /** Increase EPUB body font size */
    fun increaseFontSize() {
        if (currentFontSize < MAX_FONT_SIZE) {
            currentFontSize += 2
            saveFontSize()
            reloadCurrentChapter()
            Timber.d("EPUB: Font size increased to $currentFontSize")
        }
    }

    /** Decrease EPUB body font size */
    fun decreaseFontSize() {
        if (currentFontSize > MIN_FONT_SIZE) {
            currentFontSize -= 2
            saveFontSize()
            reloadCurrentChapter()
            Timber.d("EPUB: Font size decreased to $currentFontSize")
        }
    }

    /** Get current font size */
    fun getCurrentFontSize(): Int = currentFontSize

    private fun saveFontSize() {
        val prefs = binding.root.context.getSharedPreferences("epub_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("font_size", currentFontSize).apply()
    }

    private fun reloadCurrentChapter() {
        coroutineScope.launch {
            showChapter(currentChapterIndex)
        }
    }

    // ── Reader settings dialog ────────────────────────────────────────────────

    /**
     * Show reader settings dialog: theme, font, font size, line height, margin.
     * Changes are applied immediately and persisted to AppSettings.
     */
    fun showReaderSettingsDialog() {
        val context = binding.root.context
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

        // Set current values
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

        // Theme chip listeners — write to local pending vars, not to class fields (C-1 fix)
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

        // Font chip listeners — write to local pending vars (C-1 fix)
        val chipGroupFont = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupFont)
        chipGroupFont.setOnCheckedStateChangeListener { _, checkedIds ->
            pendingFontFamily = when {
                checkedIds.contains(R.id.chipSerif)     -> "Georgia, serif"
                checkedIds.contains(R.id.chipMonospace) -> "Courier New, monospace"
                else                                    -> "sans-serif"
            }
        }

        // Font size buttons — use pending var (C-1 fix)
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

                Timber.d("EPUB: Reader settings applied — theme=${currentReaderTheme.name}, font=$currentFontFamily, size=$currentFontSize, lh=$currentLineHeight, margin=$currentHorizontalMargin")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Persist reader style settings to AppSettings via repository.
     */
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

    // ── TOC — delegated to EpubSearchAndTocPresenter ─────────────────────────

    /** Show Table of Contents dialog for quick chapter navigation */
    fun showTableOfContents() = searchAndTocPresenter.showTableOfContents()

    // ── Search — delegated to EpubSearchAndTocPresenter ──────────────────────

    /**
     * Search for text in current EPUB chapter using WebView's built-in search.
     * WebView.findAllAsync() highlights matches automatically.
     */
    fun searchInEpub(query: String, onResult: (Int) -> Unit = {}) =
        searchAndTocPresenter.searchInEpub(query, onResult)

    /** Navigate to next search match in the current chapter */
    fun nextSearchMatch() = searchAndTocPresenter.nextSearchMatch()

    /** Navigate to previous search match in the current chapter */
    fun previousSearchMatch() = searchAndTocPresenter.previousSearchMatch()

    /**
     * Show cross-chapter search BottomSheet dialog.
     * Scans all spine chapters for matches and displays results with context snippets.
     */
    fun showCrossChapterSearch() = searchAndTocPresenter.showCrossChapterSearch()

    /** Clear search highlighting in WebView */
    fun clearSearch() = searchAndTocPresenter.clearSearch()

    // ── Translation — delegated to EpubTranslationOverlayHelper ──────────────

    /**
     * Toggle translation on/off for current chapter.
     * Extracts text from WebView and displays translated text in overlay.
     */
    fun toggleTranslation() = translationHelper.toggleTranslation()

    /**
     * Force enable translation and translate current chapter.
     * Used when settings are changed via long-press dialog.
     */
    fun forceTranslate() = translationHelper.forceTranslate()

    // ── Text extraction ───────────────────────────────────────────────────────

    /**
     * Extract text from current chapter and copy to clipboard (OCR functionality)
     */
    fun extractTextFromCurrentChapter() {
        val webView = webView ?: run {
            Timber.e("EPUB OCR: WebView is null")
            callback.showError("WebView not available")
            return
        }

        Timber.d("EPUB OCR: Extracting text from current chapter")

        webView.evaluateJavascript(
            "(function() { " +
            "  var text = document.documentElement.innerText || document.body.innerText || ''; " +
            "  return text.trim(); " +
            "})();"
        ) { result ->
            if (result == null || result == "null" || result.trim().isEmpty() || result.trim() == "\"\"") {
                Timber.d("EPUB OCR: No text extracted")
                // Show toast directly: user-initiated action — ToastThrottler cooldown must not suppress it
                android.widget.Toast.makeText(
                    binding.root.context,
                    binding.root.context.getString(R.string.translation_error_no_text),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@evaluateJavascript
            }

            val extractedText = result.trim().removeSurrounding("\"")

            if (extractedText.isNotBlank()) {
                val clipboard = binding.root.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("EPUB Text", extractedText)
                clipboard.setPrimaryClip(clip)

                android.widget.Toast.makeText(
                    binding.root.context,
                    binding.root.context.getString(R.string.text_copied),
                    android.widget.Toast.LENGTH_SHORT
                ).show()

                Timber.d("EPUB OCR: Text extracted and copied (${extractedText.length} chars)")
            } else {
                Timber.d("EPUB OCR: Extracted text is blank")
                android.widget.Toast.makeText(
                    binding.root.context,
                    binding.root.context.getString(R.string.translation_error_no_text),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ── Translate button icon ─────────────────────────────────────────────────

    /**
     * Update translate button icon with language badge showing source -> target languages
     */
    private fun updateTranslateButtonIcon() {
        Timber.d("EPUB: updateTranslateButtonIcon() called")
        coroutineScope.launch {
            try {
                val settings = settingsRepository.getSettings().first()
                val sourceLang = settings.translationSourceLanguage
                val targetLang = settings.translationTargetLanguage

                Timber.d("EPUB: Creating LanguageBadgeDrawable for $sourceLang -> $targetLang")

                val languageBadge = LanguageBadgeDrawable(
                    binding.root.context,
                    sourceLang,
                    targetLang
                )

                withContext(Dispatchers.Main) {
                    // Clear any tint before setting custom drawable — tinting with a solid
                    // colour destroys the LanguageBadgeDrawable text (badge → solid block).
                    binding.btnTranslateEpubCmd.imageTintList = null
                    binding.btnTranslateEpubCmd.setImageDrawable(languageBadge)
                    // Keep alpha consistent with current translation state
                    binding.btnTranslateEpubCmd.alpha = if (translationHelper.translationEnabled) 1.0f else 0.55f
                    Timber.d("EPUB: Translate button icon updated successfully: $sourceLang -> $targetLang")
                }
            } catch (e: Exception) {
                Timber.e(e, "EPUB: Failed to update translate button icon")
            }
        }
    }

    // ── Font settings from translation session dialog ──────────────────────────

    /**
     * Apply font settings from settings dialog without triggering translation
     */
    fun applyFontSettings(settings: com.sza.fastmediasorter.domain.models.TranslationSessionSettings) {
        Timber.d("EPUB: Applying font settings: ${settings.fontSize} (${settings.fontSize.multiplier}x), ${settings.fontFamily}")

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
            binding.root.context,
            "Font settings applied",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    // ── Scroll helpers ────────────────────────────────────────────────────────

    /** Scroll WebView to the very top (Home) */
    fun scrollToHome() {
        webView?.post { webView?.scrollTo(0, 0) }
        Timber.d("EPUB: Scrolled to home (top)")
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
        Timber.d("EPUB: Scrolled to end (bottom)")
    }

    // ── Swipe-gesture helpers ─────────────────────────────────────────────────

    /**
     * Check if WebView is scrolled to bottom and hide controls if needed
     */
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
                Timber.d("EPUB: Controls hidden - user at bottom of page")
                android.widget.Toast.makeText(
                    binding.root.context,
                    binding.root.context.getString(R.string.epub_controls_hidden),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            } else {
                Timber.d("EPUB: Not at bottom, controls remain visible")
            }
        }
    }

    /**
     * Check if WebView is scrolled to bottom and exit fullscreen if needed
     */
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
                    binding.root.context,
                    binding.root.context.getString(R.string.epub_exit_fullscreen),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                Timber.d("EPUB: Exited fullscreen - swipe down at bottom")
            } else {
                Timber.d("EPUB: Not at bottom, staying in fullscreen")
            }
        }
    }

    // ── ActionMode selection callback ─────────────────────────────────────────

    /**
     * Returns a [DocumentSelectionActionModeCallback] wired to this manager's JS selection bridge
     * and translation handler.
     *
     * WebView does not support [android.widget.TextView.setCustomSelectionActionModeCallback].
     * The hosting Activity should override [android.app.Activity.startActionMode] and wrap the
     * incoming callback with this one to inject "Translate" / "Search in Google" items into the
     * WebView floating text-selection ActionMode.
     */
    fun getSelectionActionModeCallback(): DocumentSelectionActionModeCallback =
        DocumentSelectionActionModeCallback(
            showTranslate   = BuildConfig.ENABLE_TRANSLATION,
            getSelectedText = { selectionBridge.lastSelectedText },
            onTranslate     = translationHelper::handleTranslateSelection,
            onSearchGoogle  = { openGoogleSearch(binding.root.context, it) }
        )
}
