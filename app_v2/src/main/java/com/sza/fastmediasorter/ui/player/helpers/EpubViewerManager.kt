package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Color
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
    
    // Font size control (6-72px, default 18px)
    // Extended range: 6px allows ~300% zoom out from default 18px
    // MAX 72px for very large text
    private var currentFontSize: Int = 18
    private val MIN_FONT_SIZE = 6
    private val MAX_FONT_SIZE = 144 // Increased max size for "HUGE" setting
    private val MAX_SEARCH_RESULTS = 500 // Limit cross-chapter search results to prevent OOM (M-3 fix)
    
    // Translation font size (separate from EPUB font size)
    private var translationFontSize: Int = 16
    private val MIN_TRANSLATION_FONT_SIZE = 6
    private val MAX_TRANSLATION_FONT_SIZE = 72
    
    // Font family for EPUB content (loaded from settings)
    private var currentFontFamily: String = "Georgia, serif"
    
    // Reader style settings (loaded from AppSettings)
    private var currentReaderTheme: EpubStyleManager.ReaderTheme = EpubStyleManager.ReaderTheme.LIGHT
    private var currentLineHeight: Float = 1.6f
    private var currentHorizontalMargin: Int = 16
    
    // Translation state
    private var translationEnabled = false
    
    // Fullscreen mode state
    private var isFullscreenMode = false
    
    // Settings loading gate — await before first chapter render (C-3 fix)
    private val settingsReady = CompletableDeferred<Unit>()
    
    // WebView for HTML rendering
    private var webView: WebView? = null

    // Selection bridge: captures the latest selected text from WebView via JS interface
    private val selectionBridge = EpubSelectionBridge()

    // TTS Read Aloud delegate (extracted because EpubViewerManager exceeds 1000 lines)
    private val ttsDelegate = EpubTtsDelegate(binding.root.context)

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

    // Gesture detector for swipe navigation
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
            translationFontSize = savedFontSettings.second.coerceIn(
                MIN_TRANSLATION_FONT_SIZE,
                MAX_TRANSLATION_FONT_SIZE
            )

            val settings = settingsRepository.getSettings().first()
            
            // Apply font size from settings if not AUTO
            if (settings.ocrDefaultFontSize != "AUTO") {
                val multiplier = when (settings.ocrDefaultFontSize) {
                    "MINIMUM" -> 0.7f
                    "SMALL" -> 0.85f
                    "MEDIUM" -> 1.0f
                    "LARGE" -> 1.15f
                    "HUGE" -> 1.3f
                    else -> 1.0f
                }
                currentFontSize = (18 * multiplier).toInt().coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
                translationFontSize = (16 * multiplier).toInt().coerceIn(MIN_TRANSLATION_FONT_SIZE, MAX_TRANSLATION_FONT_SIZE)
            }
            
            // Apply font family from settings if not DEFAULT
            if (settings.ocrDefaultFontFamily != "DEFAULT") {
                currentFontFamily = when (settings.ocrDefaultFontFamily) {
                    "SERIF" -> "Georgia, serif"
                    "MONOSPACE" -> "Courier New, monospace"
                    else -> "Georgia, serif"
                }
            } else {
                currentFontFamily = "sans-serif"
            }
            
            // Load reader style settings
            currentReaderTheme = EpubStyleManager.ReaderTheme.fromName(settings.textReaderTheme)
            currentLineHeight = settings.epubLineHeight
            currentHorizontalMargin = settings.epubHorizontalMargin
            
            // Signal that settings are loaded (C-3 fix)
            settingsReady.complete(Unit)
        }
        
        // Initialize swipe gesture detector for chapter navigation and font size control
        swipeGestureDetector = android.view.GestureDetector(binding.root.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent): Boolean {
                return true
            }

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
                        // Swipe right = increase font size
                        increaseFontSize()
                        android.widget.Toast.makeText(
                            binding.root.context,
                            binding.root.context.getString(R.string.epub_font_size, currentFontSize),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        // Swipe left = decrease font size
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
                        // Swipe up - show controls if not in fullscreen
                        if (!isFullscreenMode) {
                            safeViews.epubControlsLayout.isVisible = true
                            Timber.d("EPUB: Controls shown via swipe up")
                        }
                    } else {
                        // Swipe down
                        if (isFullscreenMode) {
                            // In fullscreen: check if at bottom, then exit fullscreen
                            checkAndExitFullscreenAtBottom()
                        } else {
                            // Not in fullscreen: hide controls if at bottom
                            checkAndHideControlsAtBottom()
                        }
                    }
                    return true
                }
                return false
            }
        })

        // Setup chapter indicator click to show "Go to chapter" dialog
        binding.tvEpubChapterIndicator.setOnClickListener {
            if (chapterCount > 1) {
                showGoToChapterDialog()
            }
        }
        
        // Setup translation overlay gesture controls
        setupTranslationOverlayGestures()
        
        // Setup close button for translation overlay
        safeViews.btnCloseTranslation.setOnClickListener {
            closeTranslationOverlay()
        }
        
        // Setup background click to close overlay (click outside overlay)
        safeViews.translationOverlayBackground.setOnClickListener {
            closeTranslationOverlay()
        }
        
        Timber.d("EpubViewerManager initialized, fontSize=$currentFontSize, translationFontSize=$translationFontSize")
    }
    
    /**
     * Setup gesture controls for translation overlay:
     * - Horizontal swipe left/right: change translation font size
     * - Vertical swipe up/down at scroll edges: close overlay
     * - Click: close overlay
     */
    private fun setupTranslationOverlayGestures() {
        val translationGestureDetector = android.view.GestureDetector(
            binding.root.context,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
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
                        // Horizontal swipe: change translation font size
                        if (diffX < 0) {
                            // Swipe left = increase translation font size
                            increaseTranslationFontSize()
                        } else {
                            // Swipe right = decrease translation font size
                            decreaseTranslationFontSize()
                        }
                        return true
                    } else if (!isHorizontalSwipe && kotlin.math.abs(diffY) > 100 && kotlin.math.abs(velocityY) > 100) {
                        // Vertical swipe: close overlay if at scroll edge
                        val scrollView = safeViews.translationScrollView
                        val canScrollUp = scrollView.canScrollVertically(-1)
                        val canScrollDown = scrollView.canScrollVertically(1)
                        
                        if (diffY < 0 && !canScrollUp) {
                            // Swipe up at top edge - close overlay
                            Timber.d("EPUB Translation: Swipe up at top edge - closing overlay")
                            closeTranslationOverlay()
                            return true
                        } else if (diffY > 0 && !canScrollDown) {
                            // Swipe down at bottom edge - close overlay
                            Timber.d("EPUB Translation: Swipe down at bottom edge - closing overlay")
                            closeTranslationOverlay()
                            return true
                        }
                    }
                    return false
                }
                
                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                    // Click on overlay - close it
                    Timber.d("EPUB Translation: Overlay clicked - closing")
                    closeTranslationOverlay()
                    return true
                }
            }
        )
        
        // Attach gesture detector to translation overlay
        safeViews.translationOverlay.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            translationGestureDetector.onTouchEvent(event)
            true // Consume all touches to prevent propagation to WebView
        }
    }
    
    /**
     * Close translation overlay and return to original document view
     */
    private fun closeTranslationOverlay() {
        safeViews.translationOverlay.isVisible = false
        safeViews.translationOverlayBackground.isVisible = false
        safeViews.tvTranslatedText.text = ""
        translationEnabled = false
        Timber.d("EPUB Translation: Overlay closed, translationEnabled set to false")
        
        // Update button icon
        updateTranslateButtonIcon()
    }
    
    /**
     * Increase translation text font size
     */
    private fun increaseTranslationFontSize() {
        if (translationFontSize < MAX_TRANSLATION_FONT_SIZE) {
            translationFontSize += 2
            applyTranslationFontSize()
            saveTranslationFontSize()
            
            android.widget.Toast.makeText(
                binding.root.context,
                "Translation font: ${translationFontSize}px",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            Timber.d("EPUB Translation: Font size increased to $translationFontSize")
        }
    }
    
    /**
     * Decrease translation text font size
     */
    private fun decreaseTranslationFontSize() {
        if (translationFontSize > MIN_TRANSLATION_FONT_SIZE) {
            translationFontSize -= 2
            applyTranslationFontSize()
            saveTranslationFontSize()
            
            android.widget.Toast.makeText(
                binding.root.context,
                "Translation font: ${translationFontSize}px",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            Timber.d("EPUB Translation: Font size decreased to $translationFontSize")
        }
    }
    
    /**
     * Apply current translation font size to TextView
     */
    private fun applyTranslationFontSize() {
        safeViews.tvTranslatedText.textSize = translationFontSize.toFloat()
    }
    
    /**
     * Save translation font size to SharedPreferences
     */
    private fun saveTranslationFontSize() {
        val prefs = binding.root.context.getSharedPreferences("epub_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("translation_font_size", translationFontSize).apply()
    }
    
    /**
     * Display EPUB file in WebView
     */
    fun displayEpub(mediaFile: MediaFile) {
        // Reset views - hide all other media viewers
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
        
        // Show loading toast for network files
        val isNetworkFile = mediaFile.path.startsWith("smb://") || 
                           mediaFile.path.startsWith("sftp://") || 
                           mediaFile.path.startsWith("ftp://") ||
                           mediaFile.path.startsWith("https://")
        
        val loadingToastJob = coroutineScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(if (isNetworkFile) 0 else 2000)
            if (binding.progressBar.isVisible) {
                android.widget.Toast.makeText(
                    binding.root.context,
                    binding.root.context.getString(com.sza.fastmediasorter.R.string.please_wait),
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
                            callback.showError("Failed to load EPUB: ${e.message}")
                        }
                        throw e
                    }
                }
                
                if (!file.exists()) {
                    loadingToastJob.cancel()
                    binding.progressBar.isVisible = false
                    callback.showError("EPUB file not found")
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
                            // DON'T hide progressBar here - WebViewClient will handle it after loading
                            // binding.progressBar.isVisible = false
                            
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
                                callback.showError("EPUB has no readable content")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to parse EPUB")
                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            binding.progressBar.isVisible = false
                            callback.showError("Failed to parse EPUB: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "EPUB display error")
                loadingToastJob.cancel()
                binding.progressBar.isVisible = false
                callback.showError("EPUB display error: ${e.message}")
            }
        }
    }
    
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
                
                // Read HTML content
                val htmlContent = resource.data.toString(Charsets.UTF_8)
                
                // Process HTML with jsoup
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
                    
                    // Update chapter indicator
                    updateChapterIndicator()
                    
                    // Auto-translate new chapter if translation is enabled
                    Timber.d("EPUB: Chapter loaded, checking translation state. translationEnabled=$translationEnabled")
                    if (translationEnabled) {
                        Timber.d("EPUB: Auto-translating new chapter (translation was enabled)")
                        translateCurrentChapter()
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
                    callback.showError("Failed to load chapter: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Preprocess HTML content: inject custom CSS, sync theme, handle images
     */
    private suspend fun preprocessHtml(htmlContent: String, resource: Resource): String {
        // Parse HTML with jsoup
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

        // Generate CSS via EpubStyleManager using current settings
        val css = EpubStyleManager.generateCss(
            theme = currentReaderTheme,
            fontSizePx = currentFontSize,
            fontFamily = currentFontFamily,
            lineHeight = currentLineHeight,
            horizontalPaddingPx = currentHorizontalMargin
        )
        
        doc.head().prepend(css)
        
        // Handle embedded images - extract from EPUB and convert to base64 data URIs
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
                    // Extract URL from style attribute
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
                                
                                // Replace URL in style with data URI
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
     * Helper: Convert image resource to data URI and set it to element attribute
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
                // Convert to base64 data URI
                val imageData = imageResource.data
                val base64 = android.util.Base64.encodeToString(imageData, android.util.Base64.NO_WRAP)
                val mimeType = imageResource.mediaType?.name ?: "image/jpeg"
                val dataUri = "data:$mimeType;base64,$base64"
                
                // Replace src with data URI
                element.attr(attrName, dataUri)
                Timber.d("EPUB: Converted image '$src' to data URI (${imageData.size} bytes, mime=$mimeType)")
            } else {
                Timber.w("EPUB: Image resource not found after all attempts: original='$src'")
                // List available image resources for debugging (only once per chapter)
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
     * Helper: Find image resource using multiple fallback strategies
     */
    private fun findImageResource(src: String, baseResource: Resource, book: Book): Resource? {
        // Resolve relative path (e.g., "../images/pic.jpg" or "images/pic.jpg")
        val resourceHref = resolveResourcePath(baseResource.href, src)
        Timber.d("EPUB: Resolving image - original='$src', base='${baseResource.href}', resolved='$resourceHref'")
        
        // Try multiple methods to find the resource
        var imageResource = book.resources.getByHref(resourceHref)
        
        // If not found, try original src path
        if (imageResource == null) {
            imageResource = book.resources.getByHref(src)
            if (imageResource != null) {
                Timber.d("EPUB: Found image by original path '$src'")
            }
        }
        
        // If still not found, try without leading path separators
        if (imageResource == null) {
            val simplePath = src.trimStart('/', '.')
            imageResource = book.resources.getByHref(simplePath)
            if (imageResource != null) {
                Timber.d("EPUB: Found image by simple path '$simplePath'")
            }
        }
        
        // If still not found, search by filename only
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
     * Helper: Find image resource by path from WebView request
     * Used by shouldInterceptRequest to serve images from EPUB
     */
    private fun findImageResourceByPath(path: String, book: Book): Resource? {
        // Try exact match first
        var resource = book.resources.getByHref(path)
        if (resource != null) {
            Timber.d("EPUB: Found resource by exact path '$path'")
            return resource
        }
        
        // Try without leading slash
        val pathWithoutSlash = path.trimStart('/')
        resource = book.resources.getByHref(pathWithoutSlash)
        if (resource != null) {
            Timber.d("EPUB: Found resource by path without slash '$pathWithoutSlash'")
            return resource
        }
        
        // Try with common prefixes
        val commonPrefixes = listOf("OEBPS/", "OPS/", "EPUB/", "")
        for (prefix in commonPrefixes) {
            resource = book.resources.getByHref(prefix + pathWithoutSlash)
            if (resource != null) {
                Timber.d("EPUB: Found resource with prefix '$prefix$pathWithoutSlash'")
                return resource
            }
        }
        
        // Search by filename only
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
     * Resolve relative resource path from HTML content
     * Example: base="OEBPS/Text/chapter01.xhtml", relative="../Images/pic.jpg" -> "OEBPS/Images/pic.jpg"
     */
    private fun resolveResourcePath(baseHref: String, relativePath: String): String {
        // Remove leading "./" if present
        val cleaned = relativePath.removePrefix("./")
        
        // If no path separators in base or relative is already absolute, return as-is
        if (!baseHref.contains("/")) {
            return cleaned
        }
        
        // Get base directory (remove filename from base href)
        val baseParts = baseHref.split("/").dropLast(1)
        val relativeParts = cleaned.split("/")
        
        // Resolve ".." by going up directories
        val resolvedParts = baseParts.toMutableList()
        for (part in relativeParts) {
            when (part) {
                ".." -> if (resolvedParts.isNotEmpty()) resolvedParts.removeAt(resolvedParts.size - 1)
                "." -> { /* skip current directory marker */ }
                else -> resolvedParts.add(part)
            }
        }
        
        return resolvedParts.joinToString("/")
    }
    
    /**
     * Update chapter indicator text (e.g., "Chapter 5/12")
     */
    private fun updateChapterIndicator() {
        binding.tvEpubChapterIndicator.text = "${currentChapterIndex + 1}/$chapterCount"
        
        // Show chapter indicator only if more than one chapter
        binding.tvEpubChapterIndicator.isVisible = chapterCount > 1
        
        // Update chapter progress in window title or status bar if needed
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
    
    /**
     * Navigate to previous chapter
     */
    fun showPreviousChapter() {
        if (currentChapterIndex > 0) {
            // Clear translation when changing chapter
            safeViews.translationOverlay.isVisible = false
            binding.translationLensOverlay.isVisible = false
            stopTtsOnChapterChange()
            coroutineScope.launch {
                showChapter(currentChapterIndex - 1)
                // Re-apply translation if it was enabled
                if (translationEnabled) {
                    // Small delay to let chapter render
                    kotlinx.coroutines.delay(500)
                    translateCurrentChapter()
                }
            }
        } else {
            Timber.d("EPUB: Already at first chapter")
        }
    }
    
    /**
     * Navigate to next chapter
     */
    fun showNextChapter() {
        if (currentChapterIndex < chapterCount - 1) {
            // Clear translation when changing chapter
            safeViews.translationOverlay.isVisible = false
            binding.translationLensOverlay.isVisible = false
            stopTtsOnChapterChange()
            coroutineScope.launch {
                showChapter(currentChapterIndex + 1)
                // Re-apply translation if it was enabled
                if (translationEnabled) {
                    // Small delay to let chapter render
                    kotlinx.coroutines.delay(500)
                    translateCurrentChapter()
                }
            }
        } else {
            Timber.d("EPUB: Already at last chapter")
        }
    }
    
    /**
     * Navigate to first chapter (To begin)
     */
    fun showFirstChapter() {
        if (currentChapterIndex > 0) {
            // Clear translation when changing chapter
            safeViews.translationOverlay.isVisible = false
            binding.translationLensOverlay.isVisible = false
            stopTtsOnChapterChange()
            coroutineScope.launch {
                showChapter(0)
                // Re-apply translation if it was enabled
                if (translationEnabled) {
                    // Small delay to let chapter render
                    kotlinx.coroutines.delay(500)
                    translateCurrentChapter()
                }
            }
        } else {
            Timber.d("EPUB: Already at first chapter")
        }
    }
    
    /**
     * Implementation of BaseDocumentViewerManager abstract methods for touch zone handling
     */
    override fun onPreviousPageRequest() {
        showPreviousChapter()
    }
    
    override fun onNextPageRequest() {
        showNextChapter()
    }
    
    override fun onExitFullscreenRequest() {
        exitFullscreenMode()
        Timber.d("EPUB: Exit fullscreen requested")
    }
    
    override fun isInFullscreenMode(): Boolean {
        return isFullscreenMode
    }
    
    /**
     * Enter fullscreen mode - hide controls and show exit button
     */
    fun enterFullscreenMode() {
        isFullscreenMode = true
        callback.onEnterFullscreenMode()
        safeViews.epubControlsLayout.isVisible = false
        binding.btnExitEpubFullscreen.isVisible = true
        Timber.d("EPUB: Entered fullscreen mode")
    }
    
    /**
     * Exit fullscreen mode - show controls and hide exit button
     */
    fun exitFullscreenMode() {
        isFullscreenMode = false
        callback.onExitFullscreenMode()
        safeViews.epubControlsLayout.isVisible = true
        binding.btnExitEpubFullscreen.isVisible = false
        Timber.d("EPUB: Exited fullscreen mode")
    }
    
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

    /**
     * Close current EPUB and release resources
     */
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

    // ── TTS Read Aloud ─────────────────────────────────────────────────────────

    /** Toggle TTS Read Aloud for the current EPUB chapter. */
    fun toggleReadAloud() {
        ttsDelegate.toggle(webView)
    }

    /** Stop TTS on chapter navigation. */
    fun stopTtsOnChapterChange() {
        ttsDelegate.stop()
    }

    // ────────────────────────────────────────────────────────────────

    /**
     * Release all resources on activity destroy
     */
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
        
        // ML-010: Clear WebViewDatabase credentials to prevent data retention (ML-010)
        try {
            android.webkit.WebViewDatabase.getInstance(binding.root.context).clearHttpAuthUsernamePassword()
            Timber.d("EpubViewerManager: Cleared WebViewDatabase HTTP auth credentials (ML-010)")
        } catch (e: Exception) {
            Timber.e(e, "EpubViewerManager: Error clearing WebViewDatabase credentials (ML-010)")
        }
        
        webView = null
        Timber.d("EpubViewerManager: Released")
    }
    
    /**
     * Get current chapter progress (for status display)
     */
    fun getCurrentProgress(): String {
        return if (chapterCount > 0) {
            "${currentChapterIndex + 1}/$chapterCount"
        } else {
            ""
        }
    }
    
    /**
     * Increase font size
     */
    fun increaseFontSize() {
        if (currentFontSize < MAX_FONT_SIZE) {
            currentFontSize += 2
            saveFontSize()
            reloadCurrentChapter()
            Timber.d("EPUB: Font size increased to $currentFontSize")
        }
    }
    
    /**
     * Decrease font size
     */
    fun decreaseFontSize() {
        if (currentFontSize > MIN_FONT_SIZE) {
            currentFontSize -= 2
            saveFontSize()
            reloadCurrentChapter()
            Timber.d("EPUB: Font size decreased to $currentFontSize")
        }
    }
    
    /**
     * Get current font size
     */
    fun getCurrentFontSize(): Int = currentFontSize
    
    /**
     * Save font size to SharedPreferences
     */
    private fun saveFontSize() {
        val prefs = binding.root.context.getSharedPreferences("epub_settings", android.content.Context.MODE_PRIVATE)
        prefs.edit().putInt("font_size", currentFontSize).apply()
    }
    
    /**
     * Reload current chapter with updated font size
     */
    private fun reloadCurrentChapter() {
        coroutineScope.launch {
            showChapter(currentChapterIndex)
        }
    }
    
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
            EpubStyleManager.ReaderTheme.LIGHT -> chipLight.isChecked = true
            EpubStyleManager.ReaderTheme.DARK -> chipDark.isChecked = true
            EpubStyleManager.ReaderTheme.SEPIA -> chipSepia.isChecked = true
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
                checkedIds.contains(R.id.chipDark) -> EpubStyleManager.ReaderTheme.DARK
                checkedIds.contains(R.id.chipSepia) -> EpubStyleManager.ReaderTheme.SEPIA
                checkedIds.contains(R.id.chipOled) -> EpubStyleManager.ReaderTheme.OLED_BLACK
                else -> pendingTheme
            }
        }
        
        // Font chip listeners — write to local pending vars (C-1 fix)
        val chipGroupFont = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupFont)
        chipGroupFont.setOnCheckedStateChangeListener { _, checkedIds ->
            pendingFontFamily = when {
                checkedIds.contains(R.id.chipSerif) -> "Georgia, serif"
                checkedIds.contains(R.id.chipMonospace) -> "Courier New, monospace"
                else -> "sans-serif"
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
        
        // Show dialog
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle(R.string.epub_reader_settings)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                // Apply pending values to class fields on OK (C-1 fix)
                currentReaderTheme = pendingTheme
                currentFontFamily = pendingFontFamily
                currentFontSize = pendingFontSize
                
                // Apply slider values
                currentLineHeight = sliderLineHeight.value
                currentHorizontalMargin = sliderMargin.value.toInt()
                
                // Save all settings
                saveFontSize()
                saveReaderSettings()
                
                // Reload chapter with new styles
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

    /**
     * Show Table of Contents dialog for quick chapter navigation
     */
    fun showTableOfContents() {
        val book = currentBook
        if (book == null) {
            Timber.w("EPUB: Cannot show TOC - no book loaded")
            return
        }
        
        val context = binding.root.context
        
        // Get TOC from book metadata
        val toc = book.tableOfContents
        val tocReferences = toc.tocReferences
        
        if (tocReferences.isEmpty()) {
            // Fallback: use spine if no TOC available
            showSpineBasedToc()
            return
        }
        
        // Build chapter list from TOC (flatten nested structure)
        val chapters = mutableListOf<Pair<String, Int>>() // Title to SpineIndex
        flattenToc(tocReferences, chapters, 0)
        
        if (chapters.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.epub_no_toc),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Show as BottomSheetDialog with RecyclerView
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(context)
        val view: android.view.View = android.view.LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_epub_toc, null)
        
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvTocTitle)
        val tvProgress = view.findViewById<android.widget.TextView>(R.id.tvChapterProgress)
        val rvChapters = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTocChapters)
        
        tvTitle.text = book.title ?: context.getString(R.string.epub_table_of_contents)
        tvProgress.text = context.getString(R.string.epub_chapter_progress, currentChapterIndex + 1, chapterCount)
        
        val adapter = EpubTocAdapter(
            chapters = chapters,
            currentChapterSpineIndex = currentChapterIndex,
            onChapterSelected = { spineIndex ->
                bottomSheet.dismiss()
                coroutineScope.launch {
                    showChapter(spineIndex)
                }
            }
        )
        
        rvChapters.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        rvChapters.adapter = adapter
        
        // Scroll to current chapter position
        val currentPos = adapter.findCurrentChapterPosition()
        if (currentPos > 0) {
            rvChapters.scrollToPosition(currentPos)
        }
        
        bottomSheet.setContentView(view)
        bottomSheet.show()
        
        Timber.d("EPUB: TOC BottomSheet shown with ${chapters.size} entries")
    }
    
    /**
     * Recursively flatten TOC structure into a simple list
     */
    private fun flattenToc(
        tocRefs: List<io.documentnode.epub4j.domain.TOCReference>,
        output: MutableList<Pair<String, Int>>,
        depth: Int
    ) {
        for (ref in tocRefs) {
            // Get chapter title with indentation and bullet point
            val indent = "  ".repeat(depth)
            val title = "$indent• ${ref.title ?: "Chapter ${output.size + 1}"}"
            
            // Find spine index for this TOC entry's resource
            val resource = ref.resource
            val spineIndex = findSpineIndexForResource(resource)
            
            if (spineIndex >= 0) {
                output.add(title to spineIndex)
            }
            
            // Recursively add children
            if (ref.children.isNotEmpty()) {
                flattenToc(ref.children, output, depth + 1)
            }
        }
    }
    
    /**
     * Find spine index for a given resource
     */
    private fun findSpineIndexForResource(resource: io.documentnode.epub4j.domain.Resource?): Int {
        if (resource == null) return -1
        
        val spine = currentBook?.spine ?: return -1
        val spineRefs = spine.spineReferences
        val resourceHref = resource.href ?: return -1
        
        for (i in spineRefs.indices) {
            // Compare by href to handle different Resource object instances for same file (M-5 fix)
            if (spineRefs[i].resource?.href == resourceHref) {
                return i
            }
        }
        
        return -1
    }
    
    /**
     * Fallback: show spine-based TOC when metadata TOC is empty
     */
    private fun showSpineBasedToc() {
        val book = currentBook ?: return
        val context = binding.root.context
        val spine = book.spine
        
        if (spine.spineReferences.isEmpty()) {
            android.widget.Toast.makeText(
                context,
                "No chapters available",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        // Generate chapter list from spine as Pair<Title, SpineIndex>
        val chapters = spine.spineReferences.mapIndexed { index, spineRef ->
            val title = spineRef.resource.title
            val displayTitle = if (title.isNullOrBlank()) {
                "Chapter ${index + 1}"
            } else {
                title
            }
            Pair(displayTitle, index)
        }
        
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(context)
        val view: android.view.View = android.view.LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_epub_toc, null)
        
        val tvTitle = view.findViewById<android.widget.TextView>(R.id.tvTocTitle)
        val tvProgress = view.findViewById<android.widget.TextView>(R.id.tvChapterProgress)
        val rvChapters = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTocChapters)
        
        tvTitle.text = book.title ?: context.getString(R.string.epub_table_of_contents)
        tvProgress.text = context.getString(R.string.epub_chapter_progress, currentChapterIndex + 1, chapterCount)
        
        val adapter = EpubTocAdapter(
            chapters = chapters,
            currentChapterSpineIndex = currentChapterIndex,
            onChapterSelected = { spineIndex ->
                bottomSheet.dismiss()
                coroutineScope.launch {
                    showChapter(spineIndex)
                }
            }
        )
        
        rvChapters.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        rvChapters.adapter = adapter
        
        val currentPos = adapter.findCurrentChapterPosition()
        if (currentPos > 0) {
            rvChapters.scrollToPosition(currentPos)
        }
        
        bottomSheet.setContentView(view)
        bottomSheet.show()
        
        Timber.d("EPUB: Spine-based TOC BottomSheet shown with ${chapters.size} chapters")
    }
    
    /**
     * Search for text in current EPUB chapter using WebView's built-in search.
     * WebView.findAllAsync() automatically highlights matches.
     * 
     * @param query Search query
     * @param onResult Callback with number of matches found
     */
    fun searchInEpub(query: String, onResult: (Int) -> Unit = {}) {
        val webView = webView ?: run {
            onResult(0)
            return
        }
        
        if (query.isBlank()) {
            webView.clearMatches()
            onResult(0)
            Timber.d("EPUB search cleared")
            return
        }
        
        // Set listener BEFORE triggering search (M-1 fix: listener must be set before findAllAsync)
        webView.setFindListener { _, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                onResult(numberOfMatches)
                Timber.d("EPUB search for '$query': $numberOfMatches matches in current chapter")
            }
        }
        
        // WebView.findAllAsync() is deprecated in API 16+ but still functional
        @Suppress("DEPRECATION")
        webView.findAllAsync(query)
    }
    
    /**
     * Navigate to next search match in current chapter
     */
    fun nextSearchMatch() {
        webView?.findNext(true) // forward = true
        Timber.d("EPUB: Next search match")
    }
    
    /**
     * Navigate to previous search match in current chapter
     */
    fun previousSearchMatch() {
        webView?.findNext(false) // forward = false
        Timber.d("EPUB: Previous search match")
    }
    
    /**
     * Show cross-chapter search BottomSheet dialog.
     * Scans all spine chapters for matches, displays results with context snippets.
     * Tapping a result navigates to that chapter and highlights the match in-page.
     */
    fun showCrossChapterSearch() {
        val book = currentBook
        if (book == null) {
            Timber.w("EPUB: Cannot search - no book loaded")
            return
        }
        
        val context = binding.root.context
        val bottomSheet = com.google.android.material.bottomsheet.BottomSheetDialog(context)
        val view: android.view.View = android.view.LayoutInflater.from(context)
            .inflate(R.layout.bottom_sheet_epub_search, null)
        
        val etQuery = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearchAllQuery)
        val searchProgress = view.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.searchProgress)
        val tvStatus = view.findViewById<android.widget.TextView>(R.id.tvSearchStatus)
        val rvResults = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvSearchResults)
        
        rvResults.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        
        var searchJob: kotlinx.coroutines.Job? = null
        
        // Handle IME search action
        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = etQuery.text?.toString()?.trim() ?: ""
                if (query.length >= 2) {
                    searchJob?.cancel()
                    searchJob = performCrossChapterSearch(
                        book, query, context, bottomSheet,
                        searchProgress, tvStatus, rvResults
                    )
                }
                true
            } else false
        }
        
        bottomSheet.setContentView(view)
        
        // Cancel search coroutine when BottomSheet is dismissed (C-2 fix)
        bottomSheet.setOnDismissListener { searchJob?.cancel() }
        
        bottomSheet.show()
        
        // Focus input
        etQuery.requestFocus()
        val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        etQuery.postDelayed({ imm.showSoftInput(etQuery, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }, 200)
        
        Timber.d("EPUB: Cross-chapter search dialog shown")
    }
    
    /**
     * Execute cross-chapter search in background coroutine.
     */
    private fun performCrossChapterSearch(
        book: io.documentnode.epub4j.domain.Book,
        query: String,
        context: android.content.Context,
        bottomSheet: com.google.android.material.bottomsheet.BottomSheetDialog,
        searchProgress: com.google.android.material.progressindicator.LinearProgressIndicator,
        tvStatus: android.widget.TextView,
        rvResults: androidx.recyclerview.widget.RecyclerView
    ): kotlinx.coroutines.Job {
        return coroutineScope.launch {
            // Show progress
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                searchProgress.visibility = android.view.View.VISIBLE
                tvStatus.visibility = android.view.View.VISIBLE
                tvStatus.text = context.getString(R.string.epub_searching)
                rvResults.adapter = null
            }
            
            // Scan all chapters on IO
            val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val allResults = mutableListOf<EpubSearchResult>()
                val spine = book.spine
                val contextWindow = 60 // chars around match
                
                for (i in 0 until spine.spineReferences.size) {
                    kotlinx.coroutines.yield() // Check cancellation
                    
                    val spineRef = spine.spineReferences[i]
                    val resource = spineRef.resource
                    val title = resource.title?.takeIf { it.isNotBlank() } ?: "Chapter ${i + 1}"
                    
                    try {
                        val htmlContent = String(resource.data, Charsets.UTF_8)
                        // Strip HTML tags to get plain text
                        val plainText = org.jsoup.Jsoup.parse(htmlContent).text()
                        
                        // Find all occurrences (case-insensitive)
                        var searchFrom = 0
                        val lowerText = plainText.lowercase()
                        val lowerQuery = query.lowercase()
                        
                        while (searchFrom < lowerText.length) {
                            val pos = lowerText.indexOf(lowerQuery, searchFrom)
                            if (pos < 0) break
                            
                            // Limit total results to prevent OOM (M-3 fix)
                            if (allResults.size >= MAX_SEARCH_RESULTS) break
                            
                            // Extract context snippet
                            val snippetStart = (pos - contextWindow).coerceAtLeast(0)
                            val snippetEnd = (pos + query.length + contextWindow).coerceAtMost(plainText.length)
                            val snippet = buildString {
                                if (snippetStart > 0) append("…")
                                append(plainText.substring(snippetStart, snippetEnd))
                                if (snippetEnd < plainText.length) append("…")
                            }
                            
                            allResults.add(
                                EpubSearchResult(
                                    chapterIndex = i,
                                    chapterTitle = title,
                                    contextSnippet = snippet,
                                    matchStartInText = pos,
                                    matchedText = plainText.substring(pos, pos + query.length)
                                )
                            )
                            
                            searchFrom = pos + query.length
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "EPUB: Error searching chapter $i")
                    }
                    
                    // Stop scanning more chapters if limit reached (M-3 fix)
                    if (allResults.size >= MAX_SEARCH_RESULTS) break
                }
                
                allResults
            }
            
            // Show results on Main
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                searchProgress.visibility = android.view.View.GONE
                
                if (results.isEmpty()) {
                    tvStatus.text = context.getString(R.string.epub_search_no_results)
                } else {
                    val chaptersWithMatches = results.map { it.chapterIndex }.distinct().size
                    val statusText = if (results.size >= MAX_SEARCH_RESULTS) {
                        "${context.getString(R.string.epub_search_results, results.size, chaptersWithMatches)} (max)"
                    } else {
                        context.getString(R.string.epub_search_results, results.size, chaptersWithMatches)
                    }
                    tvStatus.text = statusText
                    
                    rvResults.adapter = EpubSearchResultAdapter(results) { result ->
                        bottomSheet.dismiss()
                        // Navigate to chapter and highlight using onPageFinished (M-2 fix)
                        coroutineScope.launch {
                            showChapter(result.chapterIndex)
                            // Trigger in-page highlight after WebView finishes loading
                            // showChapter uses loadDataWithBaseURL → WebViewClient.onPageFinished fires
                            // We use a short delay as fallback since we need the search query context
                            kotlinx.coroutines.delay(300)
                            searchInEpub(query) {}
                        }
                    }
                }
                
                Timber.d("EPUB: Cross-chapter search for '$query': ${results.size} results")
            }
        }
    }
    
    /**
     * Clear search highlighting in WebView
     */
    fun clearSearch() {
        webView?.clearMatches()
        Timber.d("EPUB: Search cleared")
    }
    
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
                // Not a real error — chapter may simply have no text content
                Timber.d("EPUB OCR: No text extracted")
                // Show toast directly: this is a user-initiated action, not background network spam,
                // so ToastThrottler cooldown must not suppress it
                android.widget.Toast.makeText(
                    binding.root.context,
                    binding.root.context.getString(R.string.translation_error_no_text),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@evaluateJavascript
            }
            
            // Remove quotes from JavaScript string result
            val extractedText = result.trim().removeSurrounding("\"")
            
            if (extractedText.isNotBlank()) {
                // Copy to clipboard
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
                // Blank after unquoting — treat same as no text, not an error
                Timber.d("EPUB OCR: Extracted text is blank")
                // Show toast directly: same reasoning as above — user action, not background error
                android.widget.Toast.makeText(
                    binding.root.context,
                    binding.root.context.getString(R.string.translation_error_no_text),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Toggle translation on/off for current chapter.
     * Extracts text from WebView and displays translated text in overlay.
     */
    fun toggleTranslation() {
        Timber.d("EPUB Translation: toggleTranslation() called, current state: $translationEnabled")
        translationEnabled = !translationEnabled
        
        Timber.d("EPUB Translation: New state after toggle: $translationEnabled")
        
        if (translationEnabled) {
            Timber.d("EPUB Translation: Starting translation for current chapter")
            translateCurrentChapter()
        } else {
            Timber.d("EPUB Translation: Hiding translation overlay")
            // Hide translation overlay
            safeViews.translationOverlay.isVisible = false
        }
        
        // Update command panel button icon
        updateTranslateButtonIcon()
    }
    
    /**
     * Force enable translation and translate current chapter.
     * Used when settings are changed via long-press dialog.
     */
    fun forceTranslate() {
        Timber.d("EPUB Translation: forceTranslate() called")
        translationEnabled = true
        translateCurrentChapter()
        
        // Update command panel button icon
        updateTranslateButtonIcon()
    }
    
    /**
     * Translate only the selected text fragment (called from DocumentSelectionActionModeCallback).
     * Shows result in the shared translation overlay.
     */
    private fun handleTranslateSelection(text: String) {
        if (text.isBlank()) return
        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
            val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
            val translated = translationManager.translate(text, sourceLang, targetLang)
            withContext(Dispatchers.Main) {
                if (translated != null) {
                    callback.displayTranslatedText(translated)
                } else {
                    callback.showError(binding.root.context.getString(R.string.translation_error))
                }
            }
        }
    }

    /**
     * Extract text from current chapter and translate it.
     * Displays result in simple text overlay (not Lens style).
     */
    private fun translateCurrentChapter() {
        Timber.d("EPUB Translation: translateCurrentChapter() started")
        
        val webView = webView ?: run {
            Timber.e("EPUB Translation: WebView is null, cannot proceed")
            callback.showError("WebView not available for translation")
            return
        }
        
        Timber.d("EPUB Translation: WebView available, extracting VISIBLE text via JavaScript")
        
        // Extract only VISIBLE text from current viewport (not entire document)
        // This gets text from elements currently on screen
        webView.evaluateJavascript(
            "(function() { " +
            "  var scrollTop = window.pageYOffset || document.documentElement.scrollTop; " +
            "  var viewportHeight = window.innerHeight; " +
            "  var visibleElements = []; " +
            "  var allElements = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li, div'); " +
            "  for (var i = 0; i < allElements.length; i++) { " +
            "    var elem = allElements[i]; " +
            "    var rect = elem.getBoundingClientRect(); " +
            "    if (rect.top < viewportHeight && rect.bottom > 0 && elem.innerText && elem.innerText.trim()) { " +
            "      visibleElements.push(elem.innerText.trim()); " +
            "    } " +
            "  } " +
            "  return visibleElements.join(' '); " +
            "})();"
        ) { result ->
            Timber.d("EPUB Translation: JavaScript execution completed")
            Timber.d("EPUB Translation: Raw result: $result")
            Timber.d("EPUB Translation: Result length: ${result?.length ?: 0}")
            
            if (result == null || result == "null" || result.trim().isEmpty() || result.trim() == "\"\"") {
                Timber.e("EPUB Translation: JavaScript returned null, empty or blank result")
                coroutineScope.launch(Dispatchers.Main) {
                    callback.showError(binding.root.context.getString(R.string.translation_error_no_text))
                }
                return@evaluateJavascript
            }
            
            // Remove quotes from JavaScript string result
            val extractedText = result.trim().removeSurrounding("\"")
                .replace("\\n", "\n")
                .replace("\\t", " ")
                .trim()
            
            Timber.d("EPUB Translation: Extracted text length: ${extractedText.length} chars")
            Timber.d("EPUB Translation: First 200 chars: ${extractedText.take(200)}")
            
            if (extractedText.isBlank()) {
                Timber.e("EPUB Translation: Extracted text is blank after processing")
                callback.showError("No text found in current chapter")
                return@evaluateJavascript
            }
            
            Timber.d("EPUB Translation: Starting translation coroutine")
            
            // Translate extracted text
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    Timber.d("EPUB Translation: Loading translation settings")
                    val settings = settingsRepository.getSettings().first()
                    val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
                    val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
                    
                    Timber.d("EPUB Translation: Source language: ${settings.translationSourceLanguage} -> $sourceLang")
                    Timber.d("EPUB Translation: Target language: ${settings.translationTargetLanguage} -> $targetLang")
                    Timber.d("EPUB Translation: Calling translationManager.translate()")
                    
                    val translatedText = translationManager.translate(extractedText, sourceLang, targetLang)
                    
                    Timber.d("EPUB Translation: Translation completed, result length: ${translatedText?.length ?: 0}")
                    
                    withContext(Dispatchers.Main) {
                        if (translatedText != null && translatedText.isNotBlank()) {
                            Timber.d("EPUB Translation: Displaying translated text (${translatedText.length} chars)")
                            // Use callback to display translated text in text viewer
                            callback.displayTranslatedText(translatedText)
                            // Hide lens overlay if visible
                            binding.translationLensOverlay.isVisible = false
                            Timber.i("EPUB Translation: SUCCESS - Chapter translated and displayed")
                        } else {
                            Timber.e("EPUB Translation: Translation returned null or blank")
                            callback.showError("Translation failed")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "EPUB Translation: EXCEPTION during translation process")
                    withContext(Dispatchers.Main) {
                        callback.showError("Translation error: ${e.message}")
                    }
                }
            }
        }
    }
    
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
                    binding.btnTranslateEpubCmd.alpha = if (translationEnabled) 1.0f else 0.55f
                    Timber.d("EPUB: Translate button icon updated successfully: $sourceLang -> $targetLang")
                    Timber.d("EPUB: Button drawable is now: ${binding.btnTranslateEpubCmd.drawable}")
                    Timber.d("EPUB: Button imageTintList is now: ${binding.btnTranslateEpubCmd.imageTintList}")
                }
            } catch (e: Exception) {
                Timber.e(e, "EPUB: Failed to update translate button icon")
            }
        }
    }
    
    /**
     * Check if WebView is scrolled to bottom and hide controls if needed
     */
    private fun checkAndHideControlsAtBottom() {
        val webView = this.webView ?: return
        
        // Execute JavaScript to check scroll position
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
        
        // Execute JavaScript to check scroll position
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
    /**
     * Apply font settings from settings dialog without triggering translation
     */
    fun applyFontSettings(settings: com.sza.fastmediasorter.domain.models.TranslationSessionSettings) {
        Timber.d("EPUB: Applying font settings: ${settings.fontSize} (${settings.fontSize.multiplier}x), ${settings.fontFamily}")
        
        // 1. Update font size based on multiplier
        if (settings.fontSize != com.sza.fastmediasorter.domain.models.TranslationFontSize.AUTO) {
            val multiplier = settings.fontSize.multiplier
            // Base size 18px * multiplier
            currentFontSize = (18 * multiplier).toInt().coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
            
            // Base translation size 16px * multiplier
            translationFontSize = (16 * multiplier).toInt().coerceIn(MIN_TRANSLATION_FONT_SIZE, MAX_TRANSLATION_FONT_SIZE)
            
            saveFontSize()
            saveTranslationFontSize()
            
            // Update translation text size immediately if visible
            if (safeViews.translationOverlay.isVisible) {
                applyTranslationFontSize()
            }
        }
        
        // 2. Update font family
        if (settings.fontFamily != com.sza.fastmediasorter.domain.models.TranslationFontFamily.DEFAULT) {
            currentFontFamily = when (settings.fontFamily) {
                com.sza.fastmediasorter.domain.models.TranslationFontFamily.SERIF -> "Georgia, serif"
                com.sza.fastmediasorter.domain.models.TranslationFontFamily.MONOSPACE -> "Courier New, monospace"
                else -> "sans-serif"
            }
        } else {
            currentFontFamily = "sans-serif"
        }
        
        // 3. Reload current chapter to apply CSS changes
        reloadCurrentChapter()
        
        android.widget.Toast.makeText(
            binding.root.context,
            "Font settings applied",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
    
    /**
     * Scroll WebView to the very top (Home)
     */
    fun scrollToHome() {
        webView?.post {
            webView?.scrollTo(0, 0)
        }
        Timber.d("EPUB: Scrolled to home (top)")
    }

    /**
     * Scroll WebView to the very bottom (End)
     */
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
            onTranslate     = ::handleTranslateSelection,
            onSearchGoogle  = { openGoogleSearch(binding.root.context, it) }
        )
}
