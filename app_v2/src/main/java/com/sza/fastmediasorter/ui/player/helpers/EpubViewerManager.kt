package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
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
    
    // Translation font size (separate from EPUB font size)
    private var translationFontSize: Int = 16
    private val MIN_TRANSLATION_FONT_SIZE = 6
    private val MAX_TRANSLATION_FONT_SIZE = 72
    
    // Font family for EPUB content (loaded from settings)
    private var currentFontFamily: String = "Georgia, serif"
    
    // Translation state
    private var translationEnabled = false
    
    // Fullscreen mode state
    private var isFullscreenMode = false
    
    // WebView for HTML rendering
    private var webView: WebView? = null
    
    // Gesture detector for swipe navigation
    private var swipeGestureDetector: android.view.GestureDetector
    
    init {
        // Load saved font size from SharedPreferences
        val prefs = binding.root.context.getSharedPreferences("epub_settings", android.content.Context.MODE_PRIVATE)
        currentFontSize = prefs.getInt("font_size", 18)
        translationFontSize = prefs.getInt("translation_font_size", 16)
        
        // Load font settings from repository (apply if not AUTO/DEFAULT)
        coroutineScope.launch {
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
        
        // Initialize WebView with settings
        webView = binding.epubWebView
        webView?.apply {
            settings.javaScriptEnabled = true // Enable JavaScript for scroll position detection
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            
            // Enable text selection for copying to clipboard
            // Long-press on text should show selection handles and copy menu
            isLongClickable = true
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setOnLongClickListener(null) // Use default WebView long click behavior
            
            // Setup WebViewClient to hide progress bar when page is fully loaded
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Hide progress bar after WebView finishes rendering HTML
                    binding.progressBar.post {
                        binding.progressBar.isVisible = false
                    }
                    Timber.d("EPUB: WebView finished loading chapter")
                }
                
                // Intercept requests to file:///android_asset/ and serve images from EPUB
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: android.webkit.WebResourceRequest?
                ): android.webkit.WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    
                    // Check if this is a request to android_asset that we need to intercept
                    if (url.startsWith("file:///android_asset/")) {
                        val resourcePath = url.removePrefix("file:///android_asset/")
                        Timber.d("EPUB: Intercepting request for asset: $resourcePath")
                        
                        // Try to find this resource in EPUB
                        val book = currentBook
                        if (book != null) {
                            val imageResource = findImageResourceByPath(resourcePath, book)
                            
                            if (imageResource != null) {
                                try {
                                    val imageData = imageResource.data
                                    val mimeType = imageResource.mediaType?.name ?: "image/jpeg"
                                    val inputStream = java.io.ByteArrayInputStream(imageData)
                                    
                                    Timber.d("EPUB: Serving intercepted asset '$resourcePath' from EPUB (${imageData.size} bytes, $mimeType)")
                                    
                                    return android.webkit.WebResourceResponse(
                                        mimeType,
                                        "UTF-8",
                                        inputStream
                                    )
                                } catch (e: Exception) {
                                    Timber.e(e, "EPUB: Error serving intercepted asset '$resourcePath'")
                                }
                            } else {
                                Timber.w("EPUB: Asset '$resourcePath' not found in EPUB resources")
                                // List available images for debugging
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
            
            // Attach touch listener to WebView for gesture detection
            setOnTouchListener { _, event ->
                // Pass touch events to gesture detector first
                swipeGestureDetector.onTouchEvent(event)
                
                // Return false to allow WebView's default touch handling (zoom, scroll, text selection)
                // unless gesture was handled
                // Always return false to allow WebView's default touch handling (scroll, etc.)
                // The gesture detector is just "spying" on touches for custom actions (swipes)
                false
            }
        }
        
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
        
        Timber.d("EpubViewerManager initialized with WebView, fontSize=$currentFontSize, translationFontSize=$translationFontSize")
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
        safeViews.translationOverlay.setOnTouchListener { _, event ->
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
                    // Load into WebView
                    webView?.loadDataWithBaseURL(
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
        // Detect system dark mode
        val context = binding.root.context
        val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkTheme = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // Parse HTML with jsoup
        val doc = Jsoup.parse(htmlContent)
        
        // Inject custom CSS for theming
        val css = buildString {
            append("<style type='text/css'>")
            
            // Base styles
            append("body {")
            if (isDarkTheme) {
                append("background-color: #1E1E1E; color: #E0E0E0;")
            } else {
                append("background-color: #FFFFFF; color: #000000;")
            }
            append("font-family: $currentFontFamily;")
            append("font-size: ${currentFontSize}px;")
            append("line-height: 1.6;")
            append("padding: 16px;")
            append("margin: 0;")
            append("}")
            
            // Link colors
            append("a {")
            if (isDarkTheme) {
                append("color: #64B5F6;")
            } else {
                append("color: #1976D2;")
            }
            append("}")
            
            // Image handling - preserve aspect ratio, center alignment
            append("img {")
            append("max-width: 100%;")
            append("max-height: 100vh;")
            append("width: auto;")
            append("height: auto;")
            append("object-fit: contain;")
            append("display: block;")
            append("margin: 16px auto;")
            append("}")
            
            append("</style>")
        }
        
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
     * Close current EPUB and release resources
     */
    fun closeEpubBook() {
        currentBook = null
        currentEpubFile = null
        currentEpubPath = null
        currentChapterIndex = 0
        chapterCount = 0
        
        webView?.loadUrl("about:blank")
        
        Timber.d("EPUB: Book closed, resources released")
    }
    
    /**
     * Release all resources on activity destroy
     */
    fun release() {
        closeEpubBook()
        webView?.destroy()
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
        
        // Create styled chapter titles (with bullet points and link color)
        val chapterTitles = chapters.map { 
            val spannable = android.text.SpannableString(it.first)
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(0xFF0066CC.toInt()), // Blue link color
                0, spannable.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable
        }.toTypedArray()
        
        android.app.AlertDialog.Builder(context)
            .setTitle("${book.title ?: "EPUB"} - ${context.getString(R.string.epub_table_of_contents)}")
            .setItems(chapterTitles) { dialog, which ->
                val selectedChapterIndex = chapters[which].second
                coroutineScope.launch {
                    showChapter(selectedChapterIndex)
                }
                dialog.dismiss()
            }
            .setNegativeButton(context.getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
        
        Timber.d("EPUB: TOC dialog shown with ${chapters.size} entries")
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
        
        for (i in spineRefs.indices) {
            if (spineRefs[i].resource == resource) {
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
        
        // Generate chapter list from spine
        val chapterTitles = spine.spineReferences.mapIndexed { index, spineRef ->
            val title = spineRef.resource.title
            if (title.isNullOrBlank()) {
                "Chapter ${index + 1}"
            } else {
                title
            }
        }.toTypedArray()
        
        android.app.AlertDialog.Builder(context)
            .setTitle("${book.title ?: "EPUB"} - Chapters")
            .setItems(chapterTitles) { dialog, which ->
                coroutineScope.launch {
                    showChapter(which)
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
        
        Timber.d("EPUB: Spine-based TOC shown with ${chapterTitles.size} chapters")
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
        
        // WebView.findAllAsync() is deprecated in API 16+ but still functional
        // Modern alternative: WebView.setFindListener + findAllAsync
        @Suppress("DEPRECATION")
        webView.findAllAsync(query)
        
        // Set listener to get match count
        webView.setFindListener { _, numberOfMatches, isDoneCounting ->
            if (isDoneCounting) {
                onResult(numberOfMatches)
                Timber.d("EPUB search for '$query': $numberOfMatches matches in current chapter")
            }
        }
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
                Timber.e("EPUB OCR: No text extracted")
                callback.showError(binding.root.context.getString(R.string.translation_error_no_text))
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
                Timber.e("EPUB OCR: Extracted text is blank")
                callback.showError(binding.root.context.getString(R.string.translation_error_no_text))
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
                    // Clear any tint before setting custom drawable
                    binding.btnTranslateEpubCmd.imageTintList = null
                    binding.btnTranslateEpubCmd.setImageDrawable(languageBadge)
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
}
