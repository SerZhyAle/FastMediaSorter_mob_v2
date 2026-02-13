package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
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
import timber.log.Timber
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.utils.MediaStoreNotifier
import com.sza.fastmediasorter.utils.UserActionLogger
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.abs

/**
 * Manages text viewing/editing in PlayerActivity:
 * - Shows text viewer UI
 * - Loads text files (local/network/cloud via NetworkFileManager)
 * - Supports copy-to-clipboard and in-place edit/save (when resource is writable)
 * - Supports translation of text content via TranslationManager
 * - Supports dynamic font size adjustment via horizontal swipe gestures
 */
class TextViewerManager(
    private val context: Context,
    private val binding: ActivityPlayerUnifiedBinding,
    private val networkFileManager: NetworkFileManager,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val callback: TextViewerCallback,
    private val translationManager: TranslationManager
) {

    companion object {
        // Font size limits (in sp)
        private const val MIN_FONT_SIZE_SP = 6f
        private const val MAX_FONT_SIZE_SP = 72f
        private const val DEFAULT_TEXT_FONT_SIZE_SP = 14f
        private const val DEFAULT_TRANSLATION_FONT_SIZE_SP = 14f
        private const val FONT_SIZE_STEP_SP = 2f
        
        // Swipe threshold as percentage of screen dimension
        private const val SWIPE_THRESHOLD_PERCENT = 0.05f // 5% of screen width/height
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }

    interface TextViewerCallback {
        fun showError(message: String)
        fun showTranslationSettingsDialog()
        fun exitFullscreenMode()
        fun setTouchZonesEnabled(enabled: Boolean)
    }

    private var currentFile: MediaFile? = null
    private var translationEnabled = false
    private var isTranslationExpanded = false
    
    // Dynamic font sizes (session-scoped, persist until user exits player)
    private var textFontSizeSp: Float = DEFAULT_TEXT_FONT_SIZE_SP
    private var translationFontSizeSp: Float = DEFAULT_TRANSLATION_FONT_SIZE_SP
    
    // Current font family (loaded from settings, applied to all text views)
    private var currentTypeface: android.graphics.Typeface = android.graphics.Typeface.SANS_SERIF
    
    // Store original text without line numbers for editing/translation
    private var originalTextWithoutNumbers: String = ""
    
    // Gesture detectors for font size adjustment
    private lateinit var textGestureDetector: GestureDetector
    private lateinit var translationGestureDetector: GestureDetector
    
    // Track which view was active before OCR (to restore after close)
    private var previousActiveView: View? = null
    
    // Track EPUB WebView visibility state (to restore after translation close)
    private var wasEpubWebViewVisible = false
    private val safeViews = PlayerBindingSafeViews(binding)

    fun setupControls() {
        // Setup gesture detectors for font size adjustment
        setupGestureDetectors()
        
        // Close button for text viewer (OCR result or text file)
        safeViews.btnCloseTextViewer.setOnClickListener {
            UserActionLogger.logButtonClick("CloseTextViewer", "TextViewerManager")
            if (currentFile == null) {
                // OCR result - just hide and restore image/video view
                hideOcrText()
            } else {
                // Text file - hide and exit fullscreen
                safeViews.textViewerContainer.isVisible = false
                safeViews.textScrollView.isVisible = false
                safeViews.tvTextContent.text = ""
                currentFile = null
                callback.exitFullscreenMode()
            }
        }
        
        // Close button for translation overlay
        binding.btnCloseTranslation.setOnClickListener {
            UserActionLogger.logButtonClick("CloseTranslation", "TextViewerManager")
            hideTranslationOverlay()
        }
        
        // Click on background to close translation overlay
        safeViews.translationOverlayBackground.setOnClickListener {
            Timber.d("BUTTON: translationOverlayBackground clicked - hiding translation overlay")
            hideTranslationOverlay()
        }
        
        // Setup translation overlay click to expand/collapse + swipe for font size
        safeViews.translationOverlay.setOnClickListener {
            toggleTranslationOverlaySize()
        }
        
        // Setup translation overlay touch listener for horizontal swipe gestures
        binding.translationScrollView.setOnTouchListener { v, event ->
            val handled = translationGestureDetector.onTouchEvent(event)
            // Let ScrollView handle vertical scrolling if gesture wasn't a horizontal swipe
            if (!handled) {
                v.onTouchEvent(event)
            }
            true
        }
        
        // Setup text viewer touch listener for horizontal swipe gestures
        safeViews.textScrollView.setOnTouchListener { _, event ->
            textGestureDetector.onTouchEvent(event)
            false // Let ScrollView handle scrolling
        }
        
        // Text action buttons (now in top command panel)
        binding.btnCopyTextCmd.setOnClickListener {
            val text = safeViews.tvTextContent.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, R.string.text_copied, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnEditTextCmd.setOnClickListener {
            enterEditMode()
        }

        safeViews.btnCancelEdit.setOnClickListener {
            exitEditMode()
        }

        safeViews.btnSaveText.setOnClickListener {
            saveEditedText()
        }
        
        binding.btnTranslateTextCmd.setOnClickListener {
            toggleTranslation()
        }
        binding.btnTranslateTextCmd.setOnLongClickListener {
            callback.showTranslationSettingsDialog()
            true
        }
        
        // Click outside OCR text to dismiss (tap on container background, not on text itself)
        safeViews.textViewerContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                // Only dismiss if showing OCR text (currentFile is null for OCR)
                if (currentFile == null && safeViews.textViewerContainer.isVisible) {
                    // Check if touch is outside the text content area
                    val textViewLocation = IntArray(2)
                    safeViews.tvTextContent.getLocationOnScreen(textViewLocation)
                    val textViewRect = android.graphics.Rect(
                        textViewLocation[0],
                        textViewLocation[1],
                        textViewLocation[0] + safeViews.tvTextContent.width,
                        textViewLocation[1] + safeViews.tvTextContent.height
                    )
                    
                    val containerLocation = IntArray(2)
                    safeViews.textViewerContainer.getLocationOnScreen(containerLocation)
                    val touchX = containerLocation[0] + event.x.toInt()
                    val touchY = containerLocation[1] + event.y.toInt()
                    
                    if (!textViewRect.contains(touchX, touchY)) {
                        hideOcrText()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }
    
    /**
     * Setup gesture detectors for horizontal swipe to change font size
     */
    private fun setupGestureDetectors() {
        // Gesture detector for text content (tvTextContent)
        textGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                // Calculate swipe threshold based on screen size (5% of smaller dimension)
                val screenWidth = binding.root.width
                val screenHeight = binding.root.height
                val swipeThreshold = (minOf(screenWidth, screenHeight) * SWIPE_THRESHOLD_PERCENT).toInt().coerceAtLeast(50)
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // Check for horizontal swipes (font size adjustment)
                if (abs(diffX) > abs(diffY) && 
                    abs(diffX) > swipeThreshold && 
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    
                    if (diffX > 0) {
                        // Swipe right = increase font size
                        increaseTextFontSize()
                    } else {
                        // Swipe left = decrease font size
                        decreaseTextFontSize()
                    }
                    return true
                }
                
                // Check for vertical swipes
                if (abs(diffY) > abs(diffX) && 
                    abs(diffY) > swipeThreshold && 
                    abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    
                    val scrollView = safeViews.textScrollView
                    val isAtTop = !scrollView.canScrollVertically(-1)
                    val isAtBottom = !scrollView.canScrollVertically(1)
                    
                    // For OCR text (currentFile is null), close only when at scroll edges
                    if (currentFile == null && safeViews.textViewerContainer.isVisible) {
                        if (diffY < 0 && isAtBottom) {
                            // Swipe up at bottom = close OCR
                            Timber.d("OCR text: Swipe up at bottom - closing OCR viewer")
                            hideOcrText()
                            return true
                        } else if (diffY > 0 && isAtTop) {
                            // Swipe down at top = close OCR
                            Timber.d("OCR text: Swipe down at top - closing OCR viewer")
                            hideOcrText()
                            return true
                        }
                        // Not at edge - let scroll happen
                        return false
                    }
                    
                    // For regular text files, exit fullscreen only when at edge
                    if (diffY < 0 && isAtBottom) {
                        // Swipe up from bottom = exit fullscreen
                        Timber.d("Text: Swipe up at bottom - exit fullscreen")
                        callback.exitFullscreenMode()
                        return true
                    } else if (diffY > 0 && isAtTop) {
                        // Swipe down from top = exit fullscreen
                        Timber.d("Text: Swipe down at top - exit fullscreen")
                        callback.exitFullscreenMode()
                        return true
                    }
                }
                
                return false
            }
        })
        
        // Gesture detector for translation overlay (tvTranslatedText)
        translationGestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                // Calculate swipe threshold based on screen size
                val screenWidth = binding.root.width
                val screenHeight = binding.root.height
                val swipeThreshold = (minOf(screenWidth, screenHeight) * SWIPE_THRESHOLD_PERCENT).toInt().coerceAtLeast(50)
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // Only handle horizontal swipes (ignore vertical scrolling)
                if (abs(diffX) > abs(diffY) && 
                    abs(diffX) > swipeThreshold && 
                    abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    
                    if (diffX > 0) {
                        // Swipe right = increase font size
                        increaseTranslationFontSize()
                    } else {
                        // Swipe left = decrease font size
                        decreaseTranslationFontSize()
                    }
                    return true
                }
                return false
            }
            
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                // Single tap toggles overlay size
                toggleTranslationOverlaySize()
                return true
            }
        })
    }
    
    /**
     * Apply font settings from session configuration
     * Called when user changes font settings in translation settings dialog
     */
    fun applyFontSettings(settings: com.sza.fastmediasorter.domain.models.TranslationSessionSettings) {
        val baseTextSize = DEFAULT_TEXT_FONT_SIZE_SP
        val baseTranslationSize = DEFAULT_TRANSLATION_FONT_SIZE_SP
        
        // Apply font size multiplier
        if (settings.fontSize != com.sza.fastmediasorter.domain.models.TranslationFontSize.AUTO) {
            textFontSizeSp = (baseTextSize * settings.fontSize.multiplier).coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            translationFontSizeSp = (baseTranslationSize * settings.fontSize.multiplier).coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
            applyTextFontSize()
            applyTranslationFontSize()
        } else {
            // AUTO mode - reset to defaults
            textFontSizeSp = DEFAULT_TEXT_FONT_SIZE_SP
            translationFontSizeSp = DEFAULT_TRANSLATION_FONT_SIZE_SP
            applyTextFontSize()
            applyTranslationFontSize()
        }
        
        // Apply font family and save to class variable for later use (e.g., displayOcrText)
        currentTypeface = when (settings.fontFamily) {
            com.sza.fastmediasorter.domain.models.TranslationFontFamily.SERIF -> android.graphics.Typeface.SERIF
            com.sza.fastmediasorter.domain.models.TranslationFontFamily.MONOSPACE -> android.graphics.Typeface.MONOSPACE
            else -> android.graphics.Typeface.SANS_SERIF
        }
        safeViews.tvTextContent.typeface = currentTypeface
        binding.tvTranslatedText.typeface = currentTypeface
        
        Timber.d("Applied font settings: size=${settings.fontSize.name} (${textFontSizeSp}sp), family=${settings.fontFamily.name}")
    }
    
    /**
     * Increase text viewer font size
     */
    private fun increaseTextFontSize() {
        textFontSizeSp = (textFontSizeSp + FONT_SIZE_STEP_SP).coerceAtMost(MAX_FONT_SIZE_SP)
        applyTextFontSize()
        Timber.d("Text font size increased to ${textFontSizeSp}sp")
        showFontSizeToast(textFontSizeSp)
    }
    
    /**
     * Decrease text viewer font size
     */
    private fun decreaseTextFontSize() {
        textFontSizeSp = (textFontSizeSp - FONT_SIZE_STEP_SP).coerceAtLeast(MIN_FONT_SIZE_SP)
        applyTextFontSize()
        Timber.d("Text font size decreased to ${textFontSizeSp}sp")
        showFontSizeToast(textFontSizeSp)
    }
    
    /**
     * Apply current text font size to text content TextView
     */
    private fun applyTextFontSize() {
        safeViews.tvTextContent.setTextSize(TypedValue.COMPLEX_UNIT_SP, textFontSizeSp)
    }
    
    /**
     * Increase translation overlay font size
     */
    private fun increaseTranslationFontSize() {
        translationFontSizeSp = (translationFontSizeSp + FONT_SIZE_STEP_SP).coerceAtMost(MAX_FONT_SIZE_SP)
        applyTranslationFontSize()
        Timber.d("Translation font size increased to ${translationFontSizeSp}sp")
        showFontSizeToast(translationFontSizeSp)
    }
    
    /**
     * Decrease translation overlay font size
     */
    private fun decreaseTranslationFontSize() {
        translationFontSizeSp = (translationFontSizeSp - FONT_SIZE_STEP_SP).coerceAtLeast(MIN_FONT_SIZE_SP)
        applyTranslationFontSize()
        Timber.d("Translation font size decreased to ${translationFontSizeSp}sp")
        showFontSizeToast(translationFontSizeSp)
    }
    
    /**
     * Apply current translation font size to translated text TextView
     */
    private fun applyTranslationFontSize() {
        binding.tvTranslatedText.setTextSize(TypedValue.COMPLEX_UNIT_SP, translationFontSizeSp)
    }
    
    /**
     * Apply translation font size (called from PlayerActivity for image translation).
     * Uses the same font size setting as text translation to keep consistency.
     */
    fun applyTranslationFontSizeForImageTranslation() {
        applyTranslationFontSize()
    }
    
    /**
     * Show brief toast with current font size
     */
    private fun showFontSizeToast(sizeSp: Float) {
        UserActionLogger.logGesture("FontSizeChange", "TextViewerManager", "size=${sizeSp.toInt()}sp")
        Toast.makeText(context, "${sizeSp.toInt()}sp", Toast.LENGTH_SHORT).show()
    }

    fun displayText(mediaFile: MediaFile, isWritable: Boolean) {
        currentFile = mediaFile


        binding.imageView.isVisible = false
        binding.photoView.isVisible = false
        binding.playerView.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        binding.pdfControlsLayout.isVisible = false
        binding.btnTranslateImage.isVisible = false
        
        // Hide PDF action buttons (they are for PDF files only)
        binding.btnGoogleLensPdfCmd.isVisible = false
        binding.btnOcrPdfCmd.isVisible = false
        binding.btnTranslatePdfCmd.isVisible = false
        binding.btnSearchPdfCmd.isVisible = false
        
        // Hide EPUB action buttons (they are for EPUB files only)
        binding.btnSearchEpubCmd.isVisible = false
        binding.btnTranslateEpubCmd.isVisible = false
        
        // Hide EPUB WebView and controls (they are for EPUB files only)
        binding.epubWebView.isVisible = false
        binding.epubControlsLayout.isVisible = false
        binding.btnExitEpubFullscreen.isVisible = false

        safeViews.textViewerContainer.isVisible = true
        safeViews.textScrollView.isVisible = true
        safeViews.textEditContainer.isVisible = false
        safeViews.tvTextContent.text = ""
        binding.progressBar.isVisible = true
        
        // Show text action buttons in command panel
        binding.btnCopyTextCmd.isVisible = true
        binding.btnSearchTextCmd.isVisible = true
        
        // Apply saved font size (persists during session)
        applyTextFontSize()

        binding.btnEditTextCmd.isVisible = isWritable

        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            
            withContext(Dispatchers.Main) {
                // Show translate button only if translation is enabled in settings AND supported by flavor
                binding.btnTranslateTextCmd.isVisible = BuildConfig.ENABLE_TRANSLATION && settings.enableTranslation
            }
            try {
                val file = try {
                    networkFileManager.prepareFileForRead(mediaFile)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.isVisible = false
                        callback.showError("Failed to load text file: ${e.message}")
                    }
                    return@launch
                }

                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.isVisible = false
                        callback.showError("Text file not found")
                    }
                    return@launch
                }

                val appSettings = settingsRepository.getSettings().first()
                val maxSize = appSettings.textSizeMax

                if (file.length() > maxSize) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.isVisible = false
                        safeViews.tvTextContent.text =
                            "File too large to display directly (${file.length() / 1024} KB).\n" +
                                "Max size: ${maxSize / 1024} KB.\n\n" +
                                "Please open in external viewer."
                    }
                    return@launch
                }

                val text = StringBuilder()
                try {
                    // Use UTF-8 encoding for full Cyrillic and multi-charset support
                    val lines = mutableListOf<String>()
                    BufferedReader(InputStreamReader(file.inputStream(), Charsets.UTF_8)).use { reader ->
                        var line = reader.readLine()
                        while (line != null) {
                            lines.add(line)
                            line = reader.readLine()
                        }
                    }
                    
                    // Store original text without line numbers
                    originalTextWithoutNumbers = lines.joinToString("\n")
                    
                    // Add line numbers if enabled in settings
                    val showLineNumbers = settings.showTextLineNumbers
                    if (showLineNumbers && lines.isNotEmpty()) {
                        val maxLineNum = lines.size
                        val numWidth = maxLineNum.toString().length
                        
                        lines.forEachIndexed { index, line ->
                            val lineNum = (index + 1).toString().padStart(numWidth, ' ')
                            text.append("$lineNum │ $line\n")
                        }
                    } else {
                        // No line numbers - just join lines
                        text.append(originalTextWithoutNumbers)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error reading text file")
                    withContext(Dispatchers.Main) {
                        safeViews.tvTextContent.text = "Error reading file: ${e.message}"
                    }
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    val finalString = text.toString()
                    Timber.d("TextViewerManager: Displaying text, length=${finalString.length}")
                    
                    if (finalString.isEmpty()) {
                        safeViews.tvTextContent.text = context.getString(R.string.file_is_empty)
                    } else {
                        safeViews.tvTextContent.text = finalString
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading text file")
                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    callback.showError("Error: ${e.message}")
                }
            }
        }
    }

    private fun enterEditMode() {
        // Use original text without line numbers for editing
        val textToEdit = originalTextWithoutNumbers.ifBlank { 
            safeViews.tvTextContent.text.toString() 
        }
        safeViews.etTextContent.setText(textToEdit)

        safeViews.textScrollView.isVisible = false
        // Hide text action buttons in command panel during edit
        binding.btnCopyTextCmd.isVisible = false
        binding.btnEditTextCmd.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false
        safeViews.textEditContainer.isVisible = true
        safeViews.etTextContent.requestFocus()

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(safeViews.etTextContent, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun exitEditMode() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(safeViews.etTextContent.windowToken, 0)

        safeViews.textEditContainer.isVisible = false
        safeViews.textScrollView.isVisible = true
        // Restore text action buttons in command panel
        binding.btnCopyTextCmd.isVisible = true
        binding.btnEditTextCmd.isVisible = true
        binding.btnSearchTextCmd.isVisible = true
    }

    private fun saveEditedText() {
        val newText = safeViews.etTextContent.text.toString()
        val fileToSave = currentFile

        if (fileToSave == null) {
            callback.showError("No file loaded")
            return
        }

        binding.progressBar.isVisible = true

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val localFile = networkFileManager.prepareFileForWrite(fileToSave)
                if (localFile == null) {
                    withContext(Dispatchers.Main) {
                        binding.progressBar.isVisible = false
                        callback.showError("Cannot write to this file")
                    }
                    return@launch
                }

                localFile.writeText(newText)
                MediaStoreNotifier.notifyFile(context, localFile.absolutePath, "text-edit")

                val isNetworkFile = !fileToSave.path.startsWith("/")
                if (isNetworkFile) {
                    val uploadSuccess = networkFileManager.uploadEditedFile(fileToSave, localFile)
                    if (!uploadSuccess) {
                        withContext(Dispatchers.Main) {
                            binding.progressBar.isVisible = false
                            callback.showError("File saved locally but failed to upload to server")
                        }
                        return@launch
                    }

                    networkFileManager.clearEditingCache()
                }

                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    // Update original text and re-display with line numbers if enabled
                    originalTextWithoutNumbers = newText
                    
                    val settings = settingsRepository.getSettings().first()
                    if (settings.showTextLineNumbers && newText.isNotBlank()) {
                        val lines = newText.lines()
                        val maxLineNum = lines.size
                        val numWidth = maxLineNum.toString().length
                        val numberedText = lines.mapIndexed { index, line ->
                            val lineNum = (index + 1).toString().padStart(numWidth, ' ')
                            "$lineNum │ $line"
                        }.joinToString("\n")
                        safeViews.tvTextContent.text = numberedText
                    } else {
                        safeViews.tvTextContent.text = newText
                    }
                    
                    exitEditMode()
                    Toast.makeText(context, R.string.toast_text_saved, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error saving text file")
                withContext(Dispatchers.Main) {
                    binding.progressBar.isVisible = false
                    callback.showError("Error saving file: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Scroll text viewer down (one screen height)
     */
    fun scrollDown() {
        val scrollView = safeViews.textScrollView
        val scrollAmount = scrollView.height
        scrollView.smoothScrollBy(0, scrollAmount)
    }
    
    /**
     * Scroll text viewer up (one screen height)
     */
    fun scrollUp() {
        val scrollView = safeViews.textScrollView
        val scrollAmount = scrollView.height
        scrollView.smoothScrollBy(0, -scrollAmount)
    }
    
    /**
     * Handle mouse wheel scroll for smooth scrolling
     */
    fun handleMouseWheelScroll(verticalScroll: Float) {
        val scrollView = safeViews.textScrollView
        // Negative because scroll down is negative in MotionEvent
        // Multiply by 100 for reasonable scroll speed
        val scrollAmount = (verticalScroll * -100).toInt()
        scrollView.smoothScrollBy(0, scrollAmount)
    }
    
    /**
     * Force enable translation and translate current text.
     * Used when settings are changed via long-press dialog.
     */
    fun forceTranslate() {
        translationEnabled = true
        translateCurrentText()
        updateTranslateButtonTint()
    }
    
    /**
     * Toggle translation on/off for current text content
     */
    private fun toggleTranslation() {
        translationEnabled = !translationEnabled
        
        if (translationEnabled) {
            translateCurrentText()
        } else {
            hideTranslationOverlay()
        }
        
        updateTranslateButtonTint()
    }
    
    /**
     * Hide translation overlay and reset to compact mode
     */
    private fun hideTranslationOverlay() {
        safeViews.translationOverlay.isVisible = false
        safeViews.translationOverlayBackground.isVisible = false
        // Reset to compact mode when closing
        if (isTranslationExpanded) {
            isTranslationExpanded = true // Set to true so toggle makes it false
            toggleTranslationOverlaySize()
        }
        translationEnabled = false
        updateTranslateButtonTint()
    }
    
    /**
     * Toggle translation overlay between compact and fullscreen modes
     */
    private fun toggleTranslationOverlaySize() {
        isTranslationExpanded = !isTranslationExpanded
        
        val layoutParams = safeViews.translationOverlay.layoutParams as android.widget.FrameLayout.LayoutParams
        val scrollViewLayoutParams = binding.translationScrollView.layoutParams as android.widget.RelativeLayout.LayoutParams
        
        if (isTranslationExpanded) {
            // Fullscreen mode: match_parent height, no margin, opaque background
            layoutParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.setMargins(0, 0, 0, 0)
            layoutParams.gravity = android.view.Gravity.NO_GRAVITY
            
            scrollViewLayoutParams.height = android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
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
     * Translate current text content
     */
    private fun translateCurrentText() {
        // Use original text without line numbers for translation
        val textToTranslate = originalTextWithoutNumbers.ifBlank { 
            safeViews.tvTextContent.text.toString() 
        }
        
        if (textToTranslate.isBlank()) {
            callback.showError("No text to translate")
            return
        }
        
        safeViews.translationOverlay.isVisible = true
        safeViews.translationOverlayBackground.isVisible = true
        // Apply saved translation font size
        applyTranslationFontSize()
        
        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            val sourceLang = TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
            val targetLang = TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
            
            Timber.d("translateCurrentText: source=${settings.translationSourceLanguage} → mlkit=$sourceLang, target=${settings.translationTargetLanguage} → mlkit=$targetLang")
            
            val translated = translationManager.translate(
                textToTranslate,
                sourceLang,
                targetLang
            )
            
            withContext(Dispatchers.Main) {
                if (translated != null) {
                    binding.tvTranslatedText.text = translated
                } else {
                    binding.tvTranslatedText.text = "Translation failed"
                }
            }
        }
    }
    
    /**
     * Update translate button tint based on translation state
     */
    fun updateCloseButtonVisibility(showCommandPanel: Boolean) {
        // Show close button only in fullscreen mode (when command panel is hidden)
        // User request: "right top corner close button... needed if I play text file in fullscreen... not needed if command panel is on top"
        safeViews.btnCloseTextViewer.isVisible = !showCommandPanel
    }

    private fun updateTranslateButtonTint() {
        val color = if (translationEnabled) 0xFFF44336.toInt() else 0xFFFFFFFF.toInt()
        binding.btnTranslateTextCmd.imageTintList = android.content.res.ColorStateList.valueOf(color)
    }

    /**
     * Update translation button icon with language codes
     */
    fun updateTranslationButtonIcon(sourceLang: String, targetLang: String) {
        val drawable = LanguageBadgeDrawable(context, sourceLang, targetLang)
        binding.btnTranslateTextCmd.setImageDrawable(drawable)
    }

    /**
     * Display OCR result text (from image or PDF) in text viewer.
     * Uses same UI as TXT files but with read-only mode and OCR-specific styling.
     * Supports:
     * - Text selection for copying
     * - Swipe left/right to decrease/increase font size
     * - Swipe up at bottom or down at top to close
     * - Close button in corner
     */
    fun displayOcrText(text: String) {
        currentFile = null // Mark as OCR result (not a file)
        translationEnabled = false
        
        // Save which view was active before OCR
        previousActiveView = when {
            binding.photoView.isVisible -> binding.photoView
            binding.imageView.isVisible -> binding.imageView
            binding.playerView.isVisible -> binding.playerView
            binding.pdfControlsLayout.isVisible -> binding.pdfControlsLayout
            else -> null
        }
        
        // Hide all other viewers and overlays
        binding.playerView.isVisible = false
        binding.photoView.isVisible = false
        binding.imageView.isVisible = false
        binding.pdfControlsLayout.isVisible = false
        safeViews.translationOverlay.isVisible = false
        binding.translationLensOverlay.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        binding.btnTranslateImage.isVisible = false
        
        
        // Disable touch zones to allow text selection and interaction
        callback.setTouchZonesEnabled(false)
        // Show text viewer container
        safeViews.textViewerContainer.isVisible = true
        safeViews.textScrollView.isVisible = true
        safeViews.textEditContainer.isVisible = false
        binding.progressBar.isVisible = false
        safeViews.btnCloseTextViewer.isVisible = true  // Show close button for OCR result
        
        // OCR result is read-only - hide edit/translate/search buttons
        binding.btnEditTextCmd.isVisible = false
        safeViews.btnSaveText.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false
        
        // Copy button is visible for copying OCR text
        binding.btnCopyTextCmd.isVisible = true
        
        // Apply styling for OCR text: dark text on white background, selectable
        safeViews.tvTextContent.apply {
            // Set OCR text
            setText(text)
            
            // Apply font size and family from settings
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textFontSizeSp)
            typeface = currentTypeface
            
            // Dark gray text on white background for readability
            setTextColor(0xFF424242.toInt()) // Material Gray 800
            setBackgroundColor(0xFFFFFFFF.toInt()) // White
            
            // Enable text selection for copying
            setTextIsSelectable(true)
        }
        
        // Use standard textGestureDetector for swipe gestures (font size + close at edges)
        // The existing gesture detector already handles:
        // - Swipe left = decrease font, swipe right = increase font
        // - Swipe up at bottom = close, swipe down at top = close (for OCR, currentFile is null)
        safeViews.textScrollView.setOnTouchListener { _, event ->
            textGestureDetector.onTouchEvent(event)
            false // Let ScrollView handle scrolling
        }
        
        // Remove container click handler - only close via button or swipe gestures
        safeViews.textViewerContainer.setOnClickListener(null)
        
        Timber.d("OCR text displayed (${text.length} chars)")
    }
    
    /**
     * Hide OCR text viewer (dismissal when user taps outside)
     */
    fun hideOcrText() {
        safeViews.textViewerContainer.isVisible = false
        safeViews.textScrollView.isVisible = false
        safeViews.tvTextContent.text = ""
        safeViews.translationOverlay.isVisible = false
        safeViews.translationOverlayBackground.isVisible = false
        currentFile = null
        translationEnabled = false
        
        // Restore previously active view without navigation
        previousActiveView?.isVisible = true
        previousActiveView = null
        
        // Restore EPUB WebView if it was visible before translation
        if (wasEpubWebViewVisible) {
            binding.epubWebView.isVisible = true
            wasEpubWebViewVisible = false
            Timber.d("OCR text hidden, EPUB WebView restored")
        }
        
        // Re-enable touch zones for navigation
        callback.setTouchZonesEnabled(true)
        
        Timber.d("OCR text hidden, previous view restored")
    }
    
    /**
     * Display translated text in text viewer (for non-lens mode translations).
     * Similar to displayOcrText but styled for translation results.
     * Shows with a close button to return to previous view.
     */
    fun displayTranslatedText(text: String) {
        currentFile = null // Mark as translation result (not a file)
        translationEnabled = false
        
        // Save which view was active before showing translation
        val isPdfActive = binding.pdfControlsLayout.isVisible
        val isEpubActive = binding.epubControlsLayout.isVisible
        
        // Track EPUB WebView visibility state BEFORE hiding it
        wasEpubWebViewVisible = binding.epubWebView.isVisible
        
        previousActiveView = when {
            binding.photoView.isVisible -> binding.photoView
            binding.imageView.isVisible -> binding.imageView
            binding.playerView.isVisible -> binding.playerView
            isPdfActive -> binding.pdfControlsLayout
            isEpubActive -> binding.epubControlsLayout
            else -> null
        }
        
        // Hide all other viewers and overlays (but keep PDF/EPUB controls visible if they were active)
        binding.playerView.isVisible = false
        binding.photoView.isVisible = false
        binding.imageView.isVisible = false
        // Keep PDF/EPUB controls visible if they were active for navigation
        if (!isPdfActive) binding.pdfControlsLayout.isVisible = false
        if (!isEpubActive) binding.epubControlsLayout.isVisible = false
        binding.epubWebView.isVisible = false
        safeViews.translationOverlay.isVisible = false
        binding.translationLensOverlay.isVisible = false
        binding.audioCoverArtView.isVisible = false
        binding.audioInfoOverlay.isVisible = false
        binding.btnTranslateImage.isVisible = false
        
        // Disable touch zones to allow text selection and interaction
        callback.setTouchZonesEnabled(false)
        
        // Show text viewer container
        safeViews.textViewerContainer.isVisible = true
        safeViews.textScrollView.isVisible = true
        safeViews.textEditContainer.isVisible = false
        binding.progressBar.isVisible = false
        safeViews.btnCloseTextViewer.isVisible = true  // Show close button for translation result
        
        // Add bottom padding if PDF/EPUB controls are visible so they're not covered
        val bottomPadding = if (isPdfActive || isEpubActive) {
            // Convert 60dp to pixels (approximate control height + margin)
            (60 * binding.root.context.resources.displayMetrics.density).toInt()
        } else {
            0
        }
        safeViews.textScrollView.setPadding(
            safeViews.textScrollView.paddingLeft,
            safeViews.textScrollView.paddingTop,
            safeViews.textScrollView.paddingRight,
            bottomPadding
        )
        
        // Translation result is read-only - hide edit/translate/search buttons
        binding.btnEditTextCmd.isVisible = false
        safeViews.btnSaveText.isVisible = false
        binding.btnTranslateTextCmd.isVisible = false
        binding.btnSearchTextCmd.isVisible = false
        
        // Copy button is visible for copying translated text
        binding.btnCopyTextCmd.isVisible = true
        
        // Apply styling for translated text: dark text on light blue background, selectable
        safeViews.tvTextContent.apply {
            // Set translated text
            setText(text)
            
            // Apply font size and family from settings
            setTextSize(TypedValue.COMPLEX_UNIT_SP, textFontSizeSp)
            typeface = currentTypeface
            
            // Dark blue-gray text on light blue background for translation distinction
            setTextColor(0xFF1A237E.toInt()) // Material Indigo 900
            setBackgroundColor(0xFFE3F2FD.toInt()) // Material Blue 50 (light blue)
            
            // Enable text selection for copying
            setTextIsSelectable(true)
        }
        
        // Use standard textGestureDetector for swipe gestures (font size + close at edges)
        safeViews.textScrollView.setOnTouchListener { _, event ->
            textGestureDetector.onTouchEvent(event)
            false // Let ScrollView handle scrolling
        }
        
        // Remove container click handler - only close via button or swipe gestures
        safeViews.textViewerContainer.setOnClickListener(null)
        
        Timber.d("Translated text displayed (${text.length} chars)")
    }
    
    /**
     * Search for text in current document.
     * Returns number of matches found.
     */
    fun searchText(query: String): Int {
        if (query.isBlank()) {
            clearSearch()
            return 0
        }
        
        val fullText = safeViews.tvTextContent.text.toString()
        if (fullText.isBlank()) return 0
        
        // Find all occurrences (case-insensitive)
        val matches = Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(fullText)
        val matchCount = matches.count()
        
        Timber.d("Search for '$query' found $matchCount matches")
        return matchCount
    }
    
    /**
     * Highlight current search match in TextView.
     * Uses BackgroundColorSpan to highlight the match.
     */
    fun highlightSearchMatch(query: String, matchIndex: Int) {
        if (query.isBlank()) return
        
        val fullText = safeViews.tvTextContent.text.toString()
        val matches = Regex(Regex.escape(query), RegexOption.IGNORE_CASE).findAll(fullText).toList()
        
        if (matchIndex >= 0 && matchIndex < matches.size) {
            val match = matches[matchIndex]
            val start = match.range.first
            val end = match.range.last + 1
            
            // Scroll to match position
            val layout = safeViews.tvTextContent.layout
            if (layout != null) {
                val line = layout.getLineForOffset(start)
                val y = layout.getLineTop(line)
                safeViews.textScrollView.smoothScrollTo(0, y - 100) // Offset for visibility
            }
            
            Timber.d("Highlighted match $matchIndex at position $start-$end")
        }
    }
    
    
    /**
     * Clear search highlighting
     */
    fun clearSearch() {
        // Clear search state if needed
        Timber.d("Search cleared")
    }
    
    /**
     * Scroll text viewer to the very top (Home)
     */
    fun scrollToTop() {
        safeViews.textScrollView.post {
            safeViews.textScrollView.fullScroll(android.view.View.FOCUS_UP)
        }
        UserActionLogger.logNavigation("Top", "TextViewerManager")
    }

    /**
     * Scroll text viewer to the very bottom (End)
     */
    fun scrollToBottom() {
        safeViews.textScrollView.post {
            safeViews.textScrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
        UserActionLogger.logNavigation("Bottom", "TextViewerManager")
    }
}
