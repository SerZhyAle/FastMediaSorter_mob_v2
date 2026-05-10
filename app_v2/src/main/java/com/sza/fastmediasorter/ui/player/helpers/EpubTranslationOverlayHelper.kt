package com.sza.fastmediasorter.ui.player.helpers

import android.view.MotionEvent
import android.webkit.WebView
import androidx.core.view.isVisible
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages the EPUB translation overlay: gesture setup, font-size control,
 * open/close lifecycle, and chapter-text extraction+translation.
 *
 * Extracted from EpubViewerManager (S0002 Wave 42) to keep that class under 1500 LOC.
 *
 * Requires:
 * - [binding] — unified player binding for overlay views
 * - [safeViews] — safe-access wrapper for views that may be absent in some layouts
 * - [settingsRepository] — to read translation language pair
 * - [translationManager] — ML Kit wrapper
 * - [coroutineScope] — caller's coroutine scope
 * - [webViewProvider] — returns current WebView (nullable)
 * - [callback] — error and display callbacks
 * - [updateTranslateButtonIcon] — called whenever translation state changes to refresh button UI
 */
class EpubTranslationOverlayHelper(
    private val binding: ActivityPlayerUnifiedBinding,
    private val safeViews: PlayerBindingSafeViews,
    private val settingsRepository: SettingsRepository,
    private val translationManager: TranslationManager,
    private val coroutineScope: CoroutineScope,
    private val webViewProvider: () -> WebView?,
    private val callback: EpubViewerManager.EpubViewerCallback,
    private val updateTranslateButtonIcon: () -> Unit
) {

    // Translation font size (separate from EPUB body font size)
    var translationFontSize: Int = 16
        private set
    private val MIN_TRANSLATION_FONT_SIZE = 6
    private val MAX_TRANSLATION_FONT_SIZE = 72

    // Whether translation overlay is currently shown
    var translationEnabled: Boolean = false
        private set

    init {
        setupTranslationOverlayGestures()

        safeViews.btnCloseTranslation.setOnClickListener {
            closeTranslationOverlay()
        }

        safeViews.translationOverlayBackground.setOnClickListener {
            closeTranslationOverlay()
        }
    }

    // ── Overlay open / close ─────────────────────────────────────────────────

    /** Close translation overlay and reset translation state. */
    fun closeTranslationOverlay() {
        safeViews.translationOverlay.isVisible = false
        safeViews.translationOverlayBackground.isVisible = false
        safeViews.tvTranslatedText.text = ""
        translationEnabled = false
        Timber.d("EPUB Translation: Overlay closed, translationEnabled set to false")
        updateTranslateButtonIcon()
    }

    // ── Public translation control ───────────────────────────────────────────

    /**
     * Toggle translation on/off for the current chapter.
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
            safeViews.translationOverlay.isVisible = false
        }

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
        updateTranslateButtonIcon()
    }

    /**
     * Translate only the selected text fragment (called from DocumentSelectionActionModeCallback).
     * Shows result in the shared translation overlay via [callback].
     */
    fun handleTranslateSelection(text: String) {
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
     * Extract text from WebView and translate it.
     * Displays result via [callback.displayTranslatedText].
     */
    fun translateCurrentChapter() {
        Timber.d("EPUB Translation: translateCurrentChapter() started")

        val webView = webViewProvider() ?: run {
            Timber.e("EPUB Translation: WebView is null, cannot proceed")
            callback.showError("WebView not available for translation")
            return
        }

        Timber.d("EPUB Translation: WebView available, extracting VISIBLE text via JavaScript")

        // Extract only VISIBLE text from current viewport (not entire document)
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
                            callback.displayTranslatedText(translatedText)
                            // Hide lens overlay if visible
                            binding.translationLensOverlay.isVisible = false
                            Timber.i("EPUB Translation: SUCCESS - Chapter translated and displayed")
                        } else {
                            Timber.e("EPUB Translation: Translation returned null or blank")
                            callback.showError(binding.root.context.getString(R.string.translation_error))
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "EPUB Translation: EXCEPTION during translation process")
                    withContext(Dispatchers.Main) {
                        callback.showError(binding.root.context.getString(R.string.translation_error))
                    }
                }
            }
        }
    }

    // ── Translation font size ────────────────────────────────────────────────

    /**
     * Restore saved translation font size (called from EpubViewerManager.init).
     * Does NOT call [applyTranslationFontSize] — caller decides when to apply.
     */
    fun restoreFontSize(savedSize: Int) {
        translationFontSize = savedSize.coerceIn(MIN_TRANSLATION_FONT_SIZE, MAX_TRANSLATION_FONT_SIZE)
    }

    /** Apply current translation font size to the overlay TextView. */
    fun applyTranslationFontSize() {
        safeViews.tvTranslatedText.textSize = translationFontSize.toFloat()
    }

    /** Save translation font size to SharedPreferences. */
    fun saveTranslationFontSize() {
        val prefs = binding.root.context.getSharedPreferences(
            "epub_settings", android.content.Context.MODE_PRIVATE
        )
        prefs.edit().putInt("translation_font_size", translationFontSize).apply()
    }

    // ── Gesture setup (overlay swipe / tap) ─────────────────────────────────

    /**
     * Attach gesture listener to the translation overlay:
     * - Horizontal swipe: change translation font size
     * - Vertical swipe at edge: close overlay
     * - Tap: close overlay
     */
    private fun setupTranslationOverlayGestures() {
        val gestureDetector = android.view.GestureDetector(
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
                        if (diffX < 0) {
                            increaseTranslationFontSize()
                        } else {
                            decreaseTranslationFontSize()
                        }
                        return true
                    } else if (!isHorizontalSwipe && kotlin.math.abs(diffY) > 100 && kotlin.math.abs(velocityY) > 100) {
                        val scrollView = safeViews.translationScrollView
                        val canScrollUp = scrollView.canScrollVertically(-1)
                        val canScrollDown = scrollView.canScrollVertically(1)

                        if (diffY < 0 && !canScrollUp) {
                            Timber.d("EPUB Translation: Swipe up at top edge - closing overlay")
                            closeTranslationOverlay()
                            return true
                        } else if (diffY > 0 && !canScrollDown) {
                            Timber.d("EPUB Translation: Swipe down at bottom edge - closing overlay")
                            closeTranslationOverlay()
                            return true
                        }
                    }
                    return false
                }

                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                    Timber.d("EPUB Translation: Overlay clicked - closing")
                    closeTranslationOverlay()
                    return true
                }
            }
        )

        safeViews.translationOverlay.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP) v.performClick()
            gestureDetector.onTouchEvent(event)
            true // Consume all touches to prevent propagation to WebView
        }
    }

    // ── Private font-size helpers ────────────────────────────────────────────

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
}
