package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.util.TypedValue
import androidx.core.view.isVisible
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Manages the translation overlay panel for the text viewer:
 * - Toggle translation on/off for the full page text
 * - Translate a selected text fragment
 * - Expand/collapse the overlay between compact and fullscreen modes
 * - Hide the overlay and reset state
 *
 * Requires [BuildConfig.ENABLE_TRANSLATION] to be true for translation calls to proceed.
 */
class TextTranslationOverlayManager(
    private val context: Context,
    private val safeViews: PlayerBindingSafeViews,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
    private val translationManager: TranslationManager,
    private val getTranslationFontSizeSp: () -> Float,
    private val applyTranslationFontSize: () -> Unit,
    private val callback: TranslationCallback
) {

    interface TranslationCallback {
        fun showError(message: String)
        fun showTranslationSettingsDialog()
        fun onTranslationToggled(enabled: Boolean)
    }

    private var translationEnabled = false
    private var isTranslationExpanded = false

    // ===== Public API =====

    val isEnabled: Boolean get() = translationEnabled

    /**
     * Toggle translation on/off. When enabled, translates [getOriginalText].
     */
    fun toggleTranslation(getOriginalText: () -> String) {
        translationEnabled = !translationEnabled
        if (translationEnabled) {
            translateCurrentText(getOriginalText())
        } else {
            hideOverlay()
        }
        callback.onTranslationToggled(translationEnabled)
    }

    /**
     * Force translation to enabled state and translate immediately.
     * Called when settings are changed via long-press dialog.
     */
    fun forceTranslate(getOriginalText: () -> String) {
        translationEnabled = true
        translateCurrentText(getOriginalText())
        callback.onTranslationToggled(translationEnabled)
    }

    /**
     * Translate a selected text fragment and show in overlay.
     */
    fun translateSelectedText(text: String) {
        if (text.isBlank()) return
        safeViews.translationOverlay.isVisible = true
        safeViews.translationOverlayBackground.isVisible = true
        applyTranslationFontSize()
        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            val sourceLang =
                TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
            val targetLang =
                TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)
            val translated = translationManager.translate(text, sourceLang, targetLang)
            withContext(Dispatchers.Main) {
                safeViews.tvTranslatedText.text =
                    translated ?: context.getString(R.string.translation_error)
            }
        }
    }

    /**
     * Hide translation overlay and reset expanded/enabled state.
     */
    fun hideOverlay() {
        safeViews.translationOverlay.isVisible = false
        safeViews.translationOverlayBackground.isVisible = false
        if (isTranslationExpanded) {
            isTranslationExpanded = true // set true so toggle flips to false
            toggleOverlaySize()
        }
        translationEnabled = false
        callback.onTranslationToggled(false)
    }

    /**
     * Toggle overlay between compact (40% height) and fullscreen modes.
     */
    fun toggleOverlaySize() {
        isTranslationExpanded = !isTranslationExpanded

        val layoutParams =
            safeViews.translationOverlay.layoutParams as android.widget.FrameLayout.LayoutParams
        val scrollViewLayoutParams =
            safeViews.translationScrollView.layoutParams as android.widget.RelativeLayout.LayoutParams

        if (isTranslationExpanded) {
            layoutParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.setMargins(0, 0, 0, 0)
            layoutParams.gravity = android.view.Gravity.NO_GRAVITY

            scrollViewLayoutParams.height =
                android.widget.RelativeLayout.LayoutParams.MATCH_PARENT
            scrollViewLayoutParams.setMargins(0, 0, 0, 0)

            safeViews.translationOverlay.setCardBackgroundColor(
                android.graphics.Color.parseColor("#FF000000")
            )
            Timber.d("Translation overlay expanded to fullscreen")
        } else {
            layoutParams.width = android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            layoutParams.height = android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            val density = context.resources.displayMetrics.density
            val margin = (8 * density).toInt()
            layoutParams.setMargins(margin, margin, margin, margin)
            layoutParams.gravity = android.view.Gravity.TOP or android.view.Gravity.START

            val screenHeight = context.resources.displayMetrics.heightPixels
            val maxHeightPx = (screenHeight * 0.4f).toInt()
            scrollViewLayoutParams.height = maxHeightPx
            scrollViewLayoutParams.setMargins(0, 0, 0, 0)

            safeViews.translationOverlay.setCardBackgroundColor(
                android.graphics.Color.parseColor("#B0000000")
            )
            Timber.d("Translation overlay collapsed to compact mode")
        }

        safeViews.translationOverlay.layoutParams = layoutParams
        safeViews.translationScrollView.layoutParams = scrollViewLayoutParams
    }

    /**
     * Reset enabled state without hiding overlay (used when viewer is closed externally).
     */
    fun resetState() {
        translationEnabled = false
        isTranslationExpanded = false
    }

    // ===== Private helpers =====

    private fun translateCurrentText(text: String) {
        if (text.isBlank()) {
            callback.showError(context.getString(R.string.translation_error_no_text))
            return
        }

        safeViews.translationOverlay.isVisible = true
        safeViews.translationOverlayBackground.isVisible = true
        applyTranslationFontSize()

        coroutineScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettings().first()
            val sourceLang =
                TranslationManager.languageCodeToMLKit(settings.translationSourceLanguage)
            val targetLang =
                TranslationManager.languageCodeToMLKit(settings.translationTargetLanguage)

            Timber.d(
                "translateCurrentText: source=${settings.translationSourceLanguage} → " +
                        "mlkit=$sourceLang, target=${settings.translationTargetLanguage} → mlkit=$targetLang"
            )

            val translated = translationManager.translate(text, sourceLang, targetLang)
            withContext(Dispatchers.Main) {
                if (translated != null) {
                    safeViews.tvTranslatedText.text = translated
                } else {
                    safeViews.tvTranslatedText.text =
                        context.getString(R.string.translation_failed)
                }
            }
        }
    }
}
