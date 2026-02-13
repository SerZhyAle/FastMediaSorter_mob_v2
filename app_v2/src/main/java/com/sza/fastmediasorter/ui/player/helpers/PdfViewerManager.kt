package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.isVisible
import com.github.chrisbanes.photoview.PhotoView
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaFile
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.math.abs

/**
 * Manages PDF viewing in PlayerActivity:
 * - Opens and renders PDF pages using Android PdfRenderer
 * - Handles page navigation (previous/next)
 * - Renders pages at high resolution (2x screen width) for zoom quality
 * - Manages PDF renderer lifecycle (close on file change/destroy)
 * - Supports OCR-based translation of PDF pages via TranslationManager
 * - Saves and restores last viewed page position
 */
class PdfViewerManager(
    binding: ActivityPlayerUnifiedBinding,
    private val networkFileManager: NetworkFileManager,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: PdfViewerCallback,
    private val translationManager: TranslationManager,
    private val playbackPositionRepository: com.sza.fastmediasorter.domain.repository.PlaybackPositionRepository
) : BaseDocumentViewerManager(binding) {
    private val safeViews = PlayerBindingSafeViews(binding)
    
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
    // Note: Translation cache moved to global TranslationCacheManager singleton
    
    // Fullscreen mode state
    private var isFullscreenMode = false
    
    init {
        
        // Listen for scale/position changes on PhotoView to update translation overlay
        binding.photoView.setOnMatrixChangeListener {
            // Update translation overlay position when PDF is zoomed/panned
            if (binding.translationLensOverlay.isVisible == true && currentPageBitmap != null) {
                val viewWidth = binding.photoView.width
                val viewHeight = binding.photoView.height
                val bitmapWidth = currentPageBitmap?.width ?: 1
                val bitmapHeight = currentPageBitmap?.height ?: 1
                
                binding.translationLensOverlay.setScale(
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
        binding.btnCloseTranslation.setOnClickListener {
            Timber.d("PDF: btnCloseTranslation clicked - hiding translation overlay")
            safeViews.translationOverlay.isVisible = false
            if (isTranslationExpanded) {
                toggleTranslationOverlaySize()
            }
        }
        
        // Setup exit from fullscreen mode
        binding.pdfFullscreenOverlay?.setOnClickListener {
            exitFullscreenMode()
        }
        binding.pdfFullscreenPhotoView?.setOnClickListener {
            exitFullscreenMode()
        }
        binding.btnExitPdfFullscreen?.setOnClickListener {
            exitFullscreenMode()
        }
        
        // Setup page indicator click to show "Go to page" dialog
        binding.tvPdfPageIndicator?.setOnClickListener {
            if (pdfPageCount > 1) {
                showGoToPageDialog()
            }
        }
    }
    
    /**
     * Toggle translation overlay between compact and fullscreen modes
     */
    private fun toggleTranslationOverlaySize() {
        isTranslationExpanded = !isTranslationExpanded
        
        val layoutParams = safeViews.translationOverlay.layoutParams as? android.widget.FrameLayout.LayoutParams ?: return
        val scrollViewLayoutParams = binding.translationScrollView.layoutParams as? android.widget.LinearLayout.LayoutParams ?: return
        
        if (isTranslationExpanded) {
            // Fullscreen mode: match_parent height, no margin, opaque background
            layoutParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.setMargins(0, 0, 0, 0)
            layoutParams.gravity = android.view.Gravity.NO_GRAVITY
            
            scrollViewLayoutParams.height = android.widget.LinearLayout.LayoutParams.MATCH_PARENT
            scrollViewLayoutParams.setMargins(0, 0, 0, 0)
            
            safeViews.translationOverlay.setCardBackgroundColor(android.graphics.Color.parseColor("#FF000000")) // Opaque black
            
            Timber.d("Translation overlay expanded to fullscreen")
        } else {
            // Compact mode: wrap_content with maxHeight=300dp, margin, semi-transparent
            layoutParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            val margin = (8 * binding.root.context.resources.displayMetrics.density).toInt()
            layoutParams.setMargins(margin, margin, margin, margin)
            layoutParams.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            
            // Use 40% of screen height for compact mode (adapts to all screen sizes)
            val screenHeight = binding.root.context.resources.displayMetrics.heightPixels
            val maxHeightPx = (screenHeight * 0.4f).toInt()
            scrollViewLayoutParams.height = maxHeightPx
            scrollViewLayoutParams.setMargins(0, 0, 0, 0)
            
            safeViews.translationOverlay.setCardBackgroundColor(android.graphics.Color.parseColor("#B0000000")) // Semi-transparent
            
            Timber.d("Translation overlay collapsed to compact mode")
        }
        
        safeViews.translationOverlay.layoutParams = layoutParams
        binding.translationScrollView.layoutParams = scrollViewLayoutParams
    }
    
    /**
     * Show dialog to jump to specific PDF page
     */
    private fun showGoToPageDialog() {
        val context = binding.root.context
        val editText = android.widget.EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = context.getString(com.sza.fastmediasorter.R.string.goto_page_hint, pdfPageCount)
            setText("${currentPdfPageIndex + 1}")
            selectAll()
        }
        
        android.app.AlertDialog.Builder(context)
            .setTitle(com.sza.fastmediasorter.R.string.goto_page_title)
            .setMessage(com.sza.fastmediasorter.R.string.goto_page_message)
            .setView(editText)
            .setPositiveButton(com.sza.fastmediasorter.R.string.goto_page_button) { dialog, _ ->
                val pageNumber = editText.text.toString().toIntOrNull()
                if (pageNumber != null && pageNumber in 1..pdfPageCount) {
                    showPdfPage(pageNumber - 1) // Convert to 0-based index
                    Timber.d("Jumped to page $pageNumber")
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
    
    /**
     * Display PDF file in PhotoView (reused for PDF pages)
     */
    fun displayPdf(mediaFile: MediaFile) {
        Timber.d("PDF PROGRESS: displayPdf() START - ${mediaFile.name}")
        // Reset views
        binding.imageView.isVisible = false
        binding.photoView.isVisible = true // Reuse PhotoView for PDF pages
        binding.playerView.isVisible = false
        
        // Hide EPUB viewer when displaying PDF
        binding.epubWebView.isVisible = false
        binding.epubControlsLayout.isVisible = false
        binding.btnExitEpubFullscreen.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        safeViews.textViewerContainer.isVisible = false
        binding.btnTranslateImage.isVisible = false
        binding.pdfControlsLayout.isVisible = true
        binding.progressBar.isVisible = true
        Timber.d("PDF PROGRESS: progressBar.isVisible = TRUE (before coroutine)")
        
        // Hide text action buttons (they are for TXT files only)
        binding.btnCopyTextCmd.isVisible = false
        binding.btnEditTextCmd.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false
        
        // Hide EPUB action buttons (they are for EPUB files only)
        binding.btnSearchEpubCmd.isVisible = false
        binding.btnTranslateEpubCmd.isVisible = false
        
        closePdfRenderer()
        // Note: Translation cache is NOT cleared here - preserves translations when switching files
        
        // Show immediate toast for network files (they always take time to download)
        val isNetworkFile = mediaFile.path.startsWith("smb://") || 
                           mediaFile.path.startsWith("sftp://") || 
                           mediaFile.path.startsWith("ftp://") ||
                           mediaFile.path.startsWith("https://")
        
        // Start a timer to show a toast if loading takes longer than 2s for local, immediate for network
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
            Timber.d("PDF PROGRESS: coroutine started, progressBar.isVisible=${binding.progressBar.isVisible}")
            // Give UI thread time to render the ProgressBar before starting heavy IO work
            kotlinx.coroutines.delay(50)
            Timber.d("PDF PROGRESS: after 50ms delay, progressBar.isVisible=${binding.progressBar.isVisible}")
            
            val settings = withContext(Dispatchers.IO) {
                settingsRepository.getSettings().first()
            }
            
            // Show buttons in command panel only in landscape mode AND if feature is enabled in settings
            // In portrait mode, these actions are available in overflow menu
            val isLandscape = callback.isLandscapeMode()
            binding.btnTranslatePdfCmd.isVisible = isLandscape && settings.enableTranslation
            binding.btnGoogleLensPdfCmd.isVisible = isLandscape && settings.enableGoogleLens
            binding.btnOcrPdfCmd.isVisible = isLandscape && settings.enableOcr
            binding.btnSearchPdfCmd.isVisible = isLandscape // Search always available in landscape
            Timber.d("PdfViewerManager: Command panel button visibility updated. isLandscape=$isLandscape, Lens=${settings.enableGoogleLens}, Translate=${settings.enableTranslation}, OCR=${settings.enableOcr}")
            
            try {
                Timber.d("PDF PROGRESS: calling prepareFileForRead(), progressBar.isVisible=${binding.progressBar.isVisible}")
                val startTime = System.currentTimeMillis()
                val file = withContext(Dispatchers.IO) {
                    try {
                        networkFileManager.prepareFileForRead(mediaFile)
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            binding.progressBar.isVisible = false
                            callback.showError("Failed to load PDF: ${e.message}")
                        }
                        throw e
                    }
                }
                Timber.d("PDF PROGRESS: prepareFileForRead() completed in ${System.currentTimeMillis() - startTime}ms, progressBar.isVisible=${binding.progressBar.isVisible}")
                
                if (!file.exists()) {
                    loadingToastJob.cancel()
                    binding.progressBar.isVisible = false
                    callback.showError("PDF file not found")
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
                        
                        // Restore last viewed page position
                        val savedPage = playbackPositionRepository.getPosition(mediaFile.path)
                        val startPage = if (savedPage != null && savedPage > 0 && savedPage < pdfPageCount) {
                            savedPage.toInt()
                        } else {
                            0
                        }
                        
                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            // DON'T hide progressBar here - showPdfPage() will handle it after rendering
                            // binding.progressBar.isVisible = false
                            if (pdfPageCount > 0) {
                                showPdfPage(startPage)
                                if (startPage > 0) {
                                    Timber.d("PDF: Restored to page ${startPage + 1}/$pdfPageCount")
                                }
                                
                                // Force command panel button visibility update after page is rendered
                                // Only show in landscape mode; portrait uses overflow menu
                                val isLandscapePostRender = callback.isLandscapeMode()
                                binding.btnTranslatePdfCmd.isVisible = isLandscapePostRender && settings.enableTranslation
                                binding.btnGoogleLensPdfCmd.isVisible = isLandscapePostRender && settings.enableGoogleLens
                                binding.btnOcrPdfCmd.isVisible = isLandscapePostRender && settings.enableOcr
                                binding.btnSearchPdfCmd.isVisible = isLandscapePostRender
                                Timber.d("PdfViewerManager: Post-render button visibility. isLandscape=$isLandscapePostRender, Lens=${settings.enableGoogleLens}, Translate=${settings.enableTranslation}, OCR=${settings.enableOcr}")
                                
                                // Hide navigation controls for single-page PDFs
                                val isSinglePage = pdfPageCount == 1
                                binding.btnPdfPrevPage.isVisible = !isSinglePage
                                binding.btnPdfNextPage.isVisible = !isSinglePage
                                binding.tvPdfPageIndicator?.isVisible = !isSinglePage
                            } else {
                                binding.tvPdfPageIndicator?.text = "Empty PDF"
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error initializing PDF renderer")
                        withContext(Dispatchers.Main) {
                            loadingToastJob.cancel()
                            binding.progressBar.isVisible = false
                            callback.showError("Cannot read PDF: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading PDF")
                loadingToastJob.cancel()
                binding.progressBar.isVisible = false
                callback.showError("Error: ${e.message}")
            }
        }
    }
    
    /**
     * Navigate to previous PDF page
     */
    fun showPreviousPage() {
        if (currentPdfPageIndex > 0) {
            // Clear translation overlays before page change
            clearTranslationOverlays()
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
    
    /**
     * Navigate to next PDF page
     */
    fun showNextPage() {
        if (currentPdfPageIndex < pdfPageCount - 1) {
            // Clear translation overlays before page change
            clearTranslationOverlays()
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
    
    /**
     * Navigate to first PDF page (To begin)
     */
    fun showFirstPage() {
        if (currentPdfPageIndex > 0) {
            // Clear translation overlays before page change
            clearTranslationOverlays()
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
    
    /**
     * Toggle translation on/off for current PDF page
     */
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
        
// Update command panel button tint
        binding.btnTranslatePdfCmd.imageTintList = if (translationEnabled) 
            android.content.res.ColorStateList.valueOf(0xFFF44336.toInt()) 
        else 
            android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
    }

    /**
     * Force enable translation and translate current page.
     * Used when settings are changed via long-press dialog.
     */
    fun forceTranslate() {
        translationEnabled = true
        translateCurrentPage()
        
        // Update command panel button tint to red (active)
        binding.btnTranslatePdfCmd.imageTintList = 
            android.content.res.ColorStateList.valueOf(0xFFF44336.toInt())
    }
    
    /**
     * Extract text from current PDF page using OCR (no translation)
     * Shows recognized text in overlay for user to copy
     */
    fun extractTextFromCurrentPage() {
        if (currentPageBitmap == null) {
            callback.showError("No page rendered for OCR")
            return
        }
        
        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            
            // Get source language for OCR
            val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
            
            // Perform OCR on current page bitmap
            val originalBitmap = currentPageBitmap!!
            val shouldScale = originalBitmap.width >= 1500 && originalBitmap.height >= 1500
            
            val ocrBitmap = if (shouldScale) {
                val targetWidth = 1200
                val targetHeight = (originalBitmap.height * targetWidth / originalBitmap.width.toFloat()).toInt()
                Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)
            } else {
                originalBitmap
            }
            
            // Extract text using TranslationManager (OCR only, no translation)
            val recognizedText = translationManager.extractTextOnly(ocrBitmap, sourceLang)
            
            // Release scaled bitmap if created
            if (shouldScale) {
                ocrBitmap.recycle()
            }
            
            withContext(Dispatchers.Main) {
                if (recognizedText != null && recognizedText.isNotBlank()) {
                    callback.displayOcrText(recognizedText)
                } else {
                    callback.showError(binding.root.context.getString(com.sza.fastmediasorter.R.string.ocr_no_text_found))
                }
            }
        }
    }
    
    /**
     * Translate current PDF page using OCR + Translation
     * Supports both overlay mode (simple text) and Lens style (text blocks with coordinates)
     */
    private fun translateCurrentPage() {
        if (currentPageBitmap == null) {
            callback.showError("No page rendered for translation")
            return
        }
        
        val filePath = currentPdfFile?.absolutePath ?: return
        
        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            isLensStyleEnabled = settings.translationLensStyle
            
            if (isLensStyleEnabled) {
                // Google Lens style mode - use text blocks with coordinates
                translateCurrentPageLensStyle(settings, filePath)
            } else {
                // Simple overlay mode - use cached text translation
                translateCurrentPageOverlay(settings, filePath)
            }
        }
    }
    
    /**
     * Simple overlay translation mode
     */
    private suspend fun translateCurrentPageOverlay(settings: com.sza.fastmediasorter.domain.model.AppSettings, filePath: String) {
        // Check global cache first
        val cachedTranslation = com.sza.fastmediasorter.core.cache.TranslationCacheManager.getTranslation(filePath, currentPdfPageIndex)
        if (cachedTranslation != null) {
            withContext(Dispatchers.Main) {
                safeViews.translationOverlay.isVisible = true
                binding.tvTranslatedText.text = cachedTranslation
                binding.translationLensOverlay.isVisible = false
            }
            Timber.d("Using cached translation for $filePath page $currentPdfPageIndex")
            return
        }
        
        val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
        val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
        
        // Decide if bitmap should be scaled for OCR
        // For small images (width or height < 1500px), use original resolution for better OCR accuracy
        val originalBitmap = currentPageBitmap!!
        val shouldScale = originalBitmap.width >= 1500 && originalBitmap.height >= 1500
        
        val ocrBitmap = if (shouldScale) {
            Timber.d("Scaling bitmap for OCR: ${originalBitmap.width}x${originalBitmap.height} → ${originalBitmap.width/2}x${originalBitmap.height/2}")
            Bitmap.createScaledBitmap(
                originalBitmap,
                originalBitmap.width / 2,
                originalBitmap.height / 2,
                true
            )
        } else {
            Timber.d("Using original bitmap for OCR (small image): ${originalBitmap.width}x${originalBitmap.height}")
            originalBitmap
        }
        
        val result = translationManager.recognizeAndTranslate(
            ocrBitmap,
            sourceLang,
            targetLang
        )
        
        // Release scaled bitmap after OCR (only if we created a scaled copy)
        if (shouldScale) {
            ocrBitmap.recycle()
        }
        
        withContext(Dispatchers.Main) {
            if (result != null) {
                val translatedText = result.second
                // Use callback to display translated text in text viewer
                callback.displayTranslatedText(translatedText)
                // Hide lens overlay if visible
                binding.translationLensOverlay.isVisible = false
                // Cache the translation in global cache
                com.sza.fastmediasorter.core.cache.TranslationCacheManager.putTranslation(filePath, currentPdfPageIndex, translatedText)
            } else {
                // No text detected - silently ignore (pages without text are normal)
                Timber.d("PDF Translation: No text detected on page $currentPdfPageIndex")
            }
        }
    }
    
    /**
     * Google Lens style translation mode - draw translated text blocks over original positions
     */
    private suspend fun translateCurrentPageLensStyle(settings: com.sza.fastmediasorter.domain.model.AppSettings, filePath: String) {
        // Check global cache first
        val cachedBlocks = com.sza.fastmediasorter.core.cache.TranslationCacheManager.getLensTranslation(filePath, currentPdfPageIndex)
        if (cachedBlocks != null) {
            withContext(Dispatchers.Main) {
                // Set source bitmap for color sampling
                binding.translationLensOverlay.setSourceBitmap(currentPageBitmap)
                
                // Convert TranslatedTextBlock to TranslationOverlayView.TranslatedBlock
                val overlayBlocks = cachedBlocks.map { block ->
                    com.sza.fastmediasorter.ui.player.views.TranslationOverlayView.TranslatedBlock(
                        originalText = block.originalText,
                        translatedText = block.translatedText,
                        boundingBox = block.boundingBox,
                        confidence = block.confidence
                    )
                }
                
                // Set scale factor using current bitmap dimensions
                // Note: We assume cached blocks were generated from same resolution bitmap
                // If orientation changed, this might be slightly off, but acceptable for cache
                val viewWidth = binding.photoView.width
                val viewHeight = binding.photoView.height
                val bitmapWidth = currentPageBitmap?.width ?: 1
                val bitmapHeight = currentPageBitmap?.height ?: 1
                
                binding.translationLensOverlay.setScale(
                    bitmapWidth,
                    bitmapHeight,
                    viewWidth,
                    viewHeight
                )
                
                // Update overlay and show
                binding.translationLensOverlay.setTranslatedBlocks(overlayBlocks)
                binding.translationLensOverlay.isVisible = true
                safeViews.translationOverlay.isVisible = false
                
                // Show font size controls when translation is active
                binding.btnTranslationFontDecrease?.visibility = android.view.View.VISIBLE
                binding.btnTranslationFontIncrease?.visibility = android.view.View.VISIBLE
            }
            Timber.d("Using cached lens translation for $filePath page $currentPdfPageIndex")
            return
        }

        val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
        val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
        
        // Decide if bitmap should be scaled for OCR
        // For small images (width or height < 1500px), use original resolution for better OCR accuracy
        val originalBitmap = currentPageBitmap!!
        val shouldScale = originalBitmap.width >= 1500 && originalBitmap.height >= 1500
        
        // Store OCR bitmap dimensions BEFORE potential recycle
        val ocrBitmapWidth: Int
        val ocrBitmapHeight: Int
        
        val ocrBitmap = if (shouldScale) {
            Timber.d("Scaling bitmap for OCR: ${originalBitmap.width}x${originalBitmap.height} → ${originalBitmap.width/2}x${originalBitmap.height/2}")
            val scaled = Bitmap.createScaledBitmap(
                originalBitmap,
                originalBitmap.width / 2,
                originalBitmap.height / 2,
                true
            )
            ocrBitmapWidth = scaled.width
            ocrBitmapHeight = scaled.height
            scaled
        } else {
            Timber.d("Using original bitmap for OCR (small image): ${originalBitmap.width}x${originalBitmap.height}")
            ocrBitmapWidth = originalBitmap.width
            ocrBitmapHeight = originalBitmap.height
            originalBitmap
        }
        
        val translatedBlocks = translationManager.recognizeAndTranslateBlocks(
            ocrBitmap,
            sourceLang,
            targetLang
        )
        
        // Release scaled bitmap after OCR (only if we created a scaled copy)
        if (shouldScale) {
            ocrBitmap.recycle()
        }
        
        // Cache the result if successful
        if (translatedBlocks != null && translatedBlocks.isNotEmpty()) {
            com.sza.fastmediasorter.core.cache.TranslationCacheManager.putLensTranslation(filePath, currentPdfPageIndex, translatedBlocks)
        }
        
        withContext(Dispatchers.Main) {
            if (translatedBlocks != null && translatedBlocks.isNotEmpty()) {
                // Set source bitmap for color sampling (use original bitmap, not scaled OCR version)
                binding.translationLensOverlay.setSourceBitmap(originalBitmap)
                
                // Convert TranslatedTextBlock to TranslationOverlayView.TranslatedBlock
                val overlayBlocks = translatedBlocks.map { block ->
                    com.sza.fastmediasorter.ui.player.views.TranslationOverlayView.TranslatedBlock(
                        originalText = block.originalText,
                        translatedText = block.translatedText,
                        boundingBox = block.boundingBox,
                        confidence = block.confidence
                    )
                }
                
                // Set scale factor using stored dimensions (bitmap may be recycled already)
                val viewWidth = binding.photoView.width
                val viewHeight = binding.photoView.height
                binding.translationLensOverlay.setScale(
                    ocrBitmapWidth,
                    ocrBitmapHeight,
                    viewWidth,
                    viewHeight
                )
                
                // Update overlay and show
                binding.translationLensOverlay.setTranslatedBlocks(overlayBlocks)
                binding.translationLensOverlay.isVisible = true
                safeViews.translationOverlay.isVisible = false
                
                // Show font size controls when translation is active
                binding.btnTranslationFontDecrease?.visibility = android.view.View.VISIBLE
                binding.btnTranslationFontIncrease?.visibility = android.view.View.VISIBLE
                
                Timber.d("Displaying ${overlayBlocks.size} translated blocks in Lens style")
            } else {
                // No text detected
                binding.translationLensOverlay.isVisible = false
                safeViews.translationOverlay.isVisible = false
                binding.btnTranslationFontDecrease?.visibility = android.view.View.GONE
                binding.btnTranslationFontIncrease?.visibility = android.view.View.GONE
            }
        }
    }
    
    /**
     * Close PDF renderer and release resources.
     * Saves current page position before closing.
     */
    fun close() {
        // Save position before closing
        saveCurrentPagePosition()
        
        closePdfRenderer()
        currentPageBitmap?.recycle()
        currentPageBitmap = null
        translationEnabled = false
        currentPdfPath = null
    }
    
    /**
     * Save current PDF page position for later restoration.
     * Uses playback position repository (page number stored as position, page count as duration).
     */
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
                Timber.d("PDF: Saved page position ${currentPdfPageIndex + 1}/$pdfPageCount for $path")
            } catch (e: Exception) {
                Timber.e(e, "PDF: Failed to save page position")
            }
        }
    }
    
    // ========== Private Helper Methods ==========
    
    fun showPdfPage(index: Int) {
        if (pdfRenderer == null || pdfPageCount == 0) return
        if (index < 0 || index >= pdfPageCount) return
        
        // Show progress bar while rendering page (prevents "frozen" UI during heavy rendering)
        binding.progressBar.isVisible = true
        
        // Clear translation overlays IMMEDIATELY when starting page change
        // This prevents old translations from briefly showing during page transition
        clearTranslationOverlays()
        
        // Perform page rendering in background thread to avoid blocking UI
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Close previous page and open new one
                currentPdfPage?.close()
                currentPdfPage = pdfRenderer?.openPage(index)
                
                currentPdfPage?.let { page ->
                    // Calculate render size with safety limits to prevent OOM crashes
                    // Base: 2x screen width for zoom quality, but cap at 2560px max dimension
                    val screenWidth = binding.root.resources.displayMetrics.widthPixels
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
                    
                    Timber.d("PDF: Rendering page $index at ${width}x${height} (screen=${screenWidth}px, page=${page.width}x${page.height})")
                    
                    // Create new bitmap (in background thread to avoid UI freeze)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    // White background for PDF pages
                    bitmap.eraseColor(Color.WHITE)
                    
                    // CRITICAL: Render page in background (this is the heavy operation)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    
                    // Switch to main thread to update UI
                    withContext(Dispatchers.Main) {
                        // Recycle old bitmap AFTER rendering new one (prevents brief blank screen)
                        currentPageBitmap?.recycle()
                        
                        binding.photoView.setImageBitmap(bitmap)
                        
                        // Store bitmap for translation
                        currentPageBitmap = bitmap
                        
                        currentPdfPageIndex = index
                        binding.tvPdfPageIndicator?.text = "${index + 1} / $pdfPageCount"
                        
                        // Hide progress bar AFTER page is fully rendered and displayed
                        // Use post() to ensure bitmap is actually drawn before hiding progress
                        binding.photoView.post {
                            binding.progressBar.isVisible = false
                        }
                        
                        // Save current page position
                        saveCurrentPagePosition()
                        
                        // Update navigation buttons
                        binding.btnPdfPrevPage.isEnabled = index > 0
                        binding.btnPdfPrevPage.alpha = if (index > 0) 1.0f else 0.5f
                        
                        binding.btnPdfNextPage.isEnabled = index < pdfPageCount - 1
                        binding.btnPdfNextPage.alpha = if (index < pdfPageCount - 1) 1.0f else 0.5f
                        
                        // Auto-translate new page if translation is enabled
                        if (translationEnabled) {
                            translateCurrentPage()
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error rendering PDF page")
                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    callback.showError("Error rendering page: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Enter fullscreen mode to view current PDF page with zoom/pan support.
     * Hides all controls and shows only the page bitmap in a PhotoView.
     */
    private fun enterFullscreenMode() {
        val bitmap = currentPageBitmap ?: return
        
        isFullscreenMode = true
        
        // Hide all UI elements for immersive viewing
        callback.onEnterFullscreenMode()
        
        // Show fullscreen overlay with current page bitmap
        binding.pdfFullscreenPhotoView?.setImageBitmap(bitmap)
        binding.pdfFullscreenPhotoView?.setScale(1f, true)
        binding.pdfFullscreenOverlay?.isVisible = true
        
        Timber.d("PDF fullscreen mode entered for page ${currentPdfPageIndex + 1}")
    }
    
    /**
     * Exit fullscreen mode and return to normal PDF viewing with controls.
     */
    fun exitFullscreenMode() {
        if (!isFullscreenMode) return
        
        isFullscreenMode = false
        binding.pdfFullscreenOverlay?.isVisible = false
        binding.pdfFullscreenPhotoView?.setImageBitmap(null)
        
        // Restore UI elements
        callback.onExitFullscreenMode()
        
        Timber.d("PDF fullscreen mode exited")
    }
    
    /**
     * Check if fullscreen mode is active (for back button handling)
     */
    override fun isInFullscreenMode(): Boolean = isFullscreenMode
    
    /**
     * Check if PDF is currently active (renderer is open).
     * Used by gesture handlers to route PDF-specific gestures.
     */
    fun isPdfActive(): Boolean = pdfRenderer != null
    
    /**
     * Handle fling gesture for PDF page navigation.
     * Called from PlayerGestureSetupManager's PhotoView fling listener.
     * Returns true if handled (PDF active and gesture recognized), false otherwise.
     */
    fun handlePdfFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        // Only handle when PDF is active
        if (pdfRenderer == null || e1 == null) return false
        
        // Check if PhotoView is zoomed - don't navigate when panning
        if (binding.photoView.scale > 1.05f) return false
        
        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x
        val SWIPE_THRESHOLD = (binding.root.width * 0.05f).toInt().coerceAtLeast(50)
        val SWIPE_VELOCITY_THRESHOLD = 100
        
        // Vertical swipe should be dominant
        if (abs(diffY) > abs(diffX)) {
            if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                if (diffY > 0) {
                    // Swipe down → previous page
                    Timber.d("PDF: Swipe down -> previous page")
                    showPreviousPage()
                    return true
                } else {
                    // Swipe up → next page
                    Timber.d("PDF: Swipe up -> next page")
                    showNextPage()
                    return true
                }
            }
        }
        return false
    }
    
    /**
     * Handle long press gesture for PDF fullscreen mode.
     * Called from PlayerGestureSetupManager's PhotoView long-click listener.
     * Returns true if handled (PDF active), false otherwise.
     */
    fun handlePdfLongPress(): Boolean {
        if (pdfRenderer != null && currentPageBitmap != null) {
            enterFullscreenMode()
            return true
        }
        return false
    }
    
    /**
     * Implementation of BaseDocumentViewerManager abstract methods for touch zone handling
     */
    override fun onPreviousPageRequest() {
        showPreviousPage()
    }
    
    override fun onNextPageRequest() {
        showNextPage()
    }
    
    override fun onExitFullscreenRequest() {
        exitFullscreenMode()
    }
    
    /**
     * Clear all translation overlays (both normal and Lens style)
     * Used when changing pages to prevent old translations from showing
     */
    private fun clearTranslationOverlays() {
        safeViews.translationOverlay.isVisible = false
        binding.tvTranslatedText.text = ""
        
        binding.translationLensOverlay.isVisible = false
        binding.translationLensOverlay.clear()
        
        // Hide font size controls when translation is inactive
        binding.btnTranslationFontDecrease?.visibility = android.view.View.GONE
        binding.btnTranslationFontIncrease?.visibility = android.view.View.GONE
    }
    
    fun updateButtonVisibility() {
        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            withContext(Dispatchers.Main) {
                // Only HIDE buttons if feature is disabled in settings
                // CommandPanelController controls showing buttons based on orientation
                if (!settings.enableTranslation) binding.btnTranslatePdfCmd.isVisible = false
                if (!settings.enableGoogleLens) binding.btnGoogleLensPdfCmd.isVisible = false
                if (!settings.enableOcr) binding.btnOcrPdfCmd.isVisible = false
                // btnSearchPdfCmd visibility controlled by CommandPanelController
                Timber.d("PdfViewerManager: Force updated command panel button visibility. Lens=${settings.enableGoogleLens}, OCR=${settings.enableOcr}")
            }
        }
    }

    private fun closePdfRenderer() {
        exitFullscreenMode()
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
    
    /**
     * Search state for PDF documents
     */
    private var searchResults = mutableListOf<Int>() // Match positions in current page text
    private var currentSearchIndex = -1
    private var lastSearchQuery = ""
    private var currentPageText = "" // Cache of current page OCR text
    
    /**
     * Search for text in current PDF page.
     * Returns total number of matches found on current page.
     * 
     * Note: Searches only in current page's cached OCR/translation text.
     */
    suspend fun searchInPdf(query: String): Int {
        if (query.isBlank()) {
            searchResults.clear()
            currentSearchIndex = -1
            lastSearchQuery = ""
            return 0
        }
        
        lastSearchQuery = query
        searchResults.clear()
        currentSearchIndex = -1
        
        return withContext(Dispatchers.IO) {
            // Search in current page's cached translation text
            val cachedText = currentPageText.ifBlank {
                com.sza.fastmediasorter.core.cache.TranslationCacheManager.getTranslation(
                    currentPdfPath ?: "", 
                    currentPdfPageIndex
                ) ?: ""
            }
            
            if (cachedText.isNotBlank()) {
                val matches = Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(cachedText)
                matches.forEach {
                    searchResults.add(it.range.first)
                }
            }
            
            Timber.d("PDF search for '$query': found ${searchResults.size} matches on current page")
            searchResults.size
        }
    }
    
    /**
     * Navigate to next search result (highlights on screen)
     */
    fun nextSearchResult() {
        if (searchResults.isEmpty()) return
        
        currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
        Timber.d("Navigated to search result ${currentSearchIndex + 1}/${searchResults.size}")
    }
    
    /**
     * Navigate to previous search result
     */
    fun previousSearchResult() {
        if (searchResults.isEmpty()) return
        
        currentSearchIndex = if (currentSearchIndex <= 0) {
            searchResults.size - 1
        } else {
            currentSearchIndex - 1
        }
        
        Timber.d("Navigated to search result ${currentSearchIndex + 1}/${searchResults.size}")
    }
    
    /**
     * Get current search state
     */
    fun getSearchState(): Triple<Int, Int, String> {
        // Returns (currentIndex+1, totalMatches, query)
        return Triple(
            if (searchResults.isEmpty()) 0 else currentSearchIndex + 1,
            searchResults.size,
            lastSearchQuery
        )
    }
    
    /**
     * Share current PDF page to Google Lens for visual search/text recognition.
     * Saves current page bitmap to temp file and delegates to activity callback.
     */
    fun shareCurrentPageToGoogleLens() {
        val bitmap = getCurrentPageBitmap() ?: return
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                // Save bitmap to temp file
                val context = binding.root.context
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val tempFile = File(cacheDir, "lens_share_temp.png")
                java.io.FileOutputStream(tempFile).use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                }
                
                withContext(Dispatchers.Main) {
                    callback.shareFileToGoogleLens(tempFile)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save PDF page for Google Lens")
                withContext(Dispatchers.Main) {
                    callback.showError("Failed to prepare image for Google Lens")
                }
            }
        }
    }
}

