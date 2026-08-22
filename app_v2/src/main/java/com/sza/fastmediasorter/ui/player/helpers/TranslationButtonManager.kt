package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.utils.collectOnLifecycle
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.domain.models.TranslationSessionSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.dialog.TranslationSettingsDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages translation button setup and configuration for PlayerActivity.
 * 
 * Responsibilities:
 * - Initialize translation session settings from AppSettings defaults
 * - Update translation button icons with language badges (source -> target)
 * - Show translation settings dialog
 * - Apply font settings to translation overlays
 * 
 * Handles multiple translation button types:
 * - PDF translation (command panel)
 * - EPUB translation (command panel)
 * - Image/GIF translation (command panel + deprecated overlay)
 * - Text translation (via TextViewerManager)
 */
class TranslationButtonManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val binding: ActivityPlayerUnifiedBinding,
    private val settingsRepository: SettingsRepository,
    private val callback: TranslationButtonCallback
) {
    private val safeViews = PlayerBindingSafeViews(binding)

    /**
     * S1549: re-point every accessor at a re-inflated hierarchy. This manager is constructed once
     * per screen - its settings collector in [setupTranslationButtonIcons] must not double - so a
     * layout re-inflate re-points it instead of re-creating it.
     */
    fun rebindRoot(newRoot: android.view.View) = safeViews.rebindRoot(newRoot)

    interface TranslationButtonCallback {
        fun getTranslationSessionSettings(): TranslationSessionSettings
        fun setTranslationSessionSettings(settings: TranslationSessionSettings)
        fun getCurrentFileType(): MediaType?
        fun translateCurrentImage()
        fun updateTextViewerTranslationButtonIcon(sourceLang: String, targetLang: String)
        fun applyTextViewerFontSettings(settings: TranslationSessionSettings)
        fun applyTranslationManagerFontSettings(settings: TranslationSessionSettings)
        fun applyEpubFontSettings(settings: TranslationSessionSettings) // New callback
        fun forceTranslatePdf()
        fun forceTranslateText()
        fun forceTranslateEpub()
    }
    
    /**
     * Initialize translation session settings from AppSettings defaults.
     * Called once during PlayerActivity.onCreate()
     * 
     * Loads saved font settings from repository and applies them to all text-related managers:
     * - TextViewerManager (text files, OCR results)
     * - TranslationManager (image translation)
     * - TranslationOverlayView (Google Lens style blocks)
     * 
     * Note: Skips initialization if ENABLE_TRANSLATION=false in BuildConfig.
     */
    fun setupTranslationDefaults() {
        // Guard: Skip translation setup if not supported by this flavor
        if (!BuildConfig.ENABLE_TRANSLATION) {
            Timber.d("TranslationButtonManager: Translation not available (ENABLE_TRANSLATION=false)")
            return
        }
        
        lifecycleOwner.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            
            // Initialize translation session settings from AppSettings defaults
            val defaultFontSize = try {
                TranslationFontSize.valueOf(settings.ocrDefaultFontSize)
            } catch (e: Exception) {
                TranslationFontSize.AUTO
            }
            
            val defaultFontFamily = try {
                TranslationFontFamily.valueOf(settings.ocrDefaultFontFamily)
            } catch (e: Exception) {
                TranslationFontFamily.DEFAULT
            }
            
            val sessionSettings = TranslationSessionSettings(
                fontSize = defaultFontSize,
                fontFamily = defaultFontFamily
            )
            callback.setTranslationSessionSettings(sessionSettings)
            
            // Apply font settings to all managers immediately (not just on settings change)
            // This ensures saved font preferences are used from app start
            callback.applyTextViewerFontSettings(sessionSettings)
            callback.applyTranslationManagerFontSettings(sessionSettings)
            applyFontSettingsToOverlay(sessionSettings)
            
            Timber.d("Translation defaults initialized: fontSize=${defaultFontSize.name}, fontFamily=${defaultFontFamily.name}")
        }
    }
    
    /**
     * Setup translation button icons with language badges.
     * Starts a coroutine that observes settings changes and updates all translation button icons.
     * 
     * Note: Skips setup if ENABLE_TRANSLATION=false in BuildConfig.
     */
    fun setupTranslationButtonIcons() {
        // Guard: Skip translation button setup if not supported by this flavor
        if (!BuildConfig.ENABLE_TRANSLATION) {
            Timber.d("TranslationButtonManager: Translation buttons not available (ENABLE_TRANSLATION=false)")
            safeViews.btnTranslatePdfCmd.isVisible = false
            safeViews.btnTranslateEpubCmd.isVisible = false
            safeViews.btnTranslateImageCmd.isVisible = false
            safeViews.btnTranslateImage.isVisible = false
            return
        }
        
        Timber.d("TranslationButtonManager: setupTranslationButtonIcons() CALLED")
        lifecycleOwner.collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            Timber.d("TranslationButtonManager: Lifecycle STARTED, collecting settings")
            val sourceLang = settings.translationSourceLanguage
            val targetLang = settings.translationTargetLanguage
            Timber.d("TranslationButtonManager: Setting badges - source=$sourceLang, target=$targetLang")

            // Update PDF button (in command panel)
            val pdfDrawable = LanguageBadgeDrawable(context, sourceLang, targetLang, android.graphics.Color.WHITE)
            safeViews.btnTranslatePdfCmd.setImageDrawable(pdfDrawable)
            safeViews.btnTranslatePdfCmd.imageTintList = null // Remove tint to show custom drawable
            safeViews.btnTranslatePdfCmd.alpha = 0.55f // Inactive by default; PdfViewerManager sets 1.0f when active
            Timber.d("TranslationButtonManager: PDF button drawable set")

            // Update EPUB button (in command panel)
            val epubDrawable = LanguageBadgeDrawable(context, sourceLang, targetLang, android.graphics.Color.WHITE)
            safeViews.btnTranslateEpubCmd.setImageDrawable(epubDrawable)
            safeViews.btnTranslateEpubCmd.imageTintList = null // Remove tint to show custom drawable
            safeViews.btnTranslateEpubCmd.alpha = 0.55f // Inactive by default; EpubViewerManager sets 1.0f when active
            Timber.d("TranslationButtonManager: EPUB button drawable set")

            // Update Image/GIF button (in command panel)
            val imageDrawable = LanguageBadgeDrawable(context, sourceLang, targetLang, android.graphics.Color.WHITE)
            safeViews.btnTranslateImageCmd.setImageDrawable(imageDrawable)
            safeViews.btnTranslateImageCmd.imageTintList = null // Remove tint to show custom drawable
            safeViews.btnTranslateImageCmd.alpha = 0.55f // Inactive by default
            Timber.d("TranslationButtonManager: IMAGE button drawable set - drawable=$imageDrawable, tint removed")

            // Update deprecated overlay Image button
            safeViews.btnTranslateImage.setImageDrawable(imageDrawable)
            safeViews.btnTranslateImage.imageTintList = null // Remove tint to show custom drawable

            // Update Text button (via callback to TextViewerManager)
            callback.updateTextViewerTranslationButtonIcon(sourceLang, targetLang)
        }
    }

    
    /**
     * Show translation settings dialog.
     * Allows user to configure:
     * - Source/target languages
     * - Google Lens overlay style (enable/disable)
     * - Font size (AUTO, SMALL, MEDIUM, LARGE)
     * - Font family (MONOSPACE, SANS_SERIF, SERIF)
     * 
     * Note: Does nothing if ENABLE_TRANSLATION=false in BuildConfig.
     */
    fun showTranslationSettingsDialog() {
        // Guard: skip when this flavor has no translation capability.
        if (!BuildConfig.ENABLE_TRANSLATION) {
            Timber.d("TranslationButtonManager: Translation settings not available (ENABLE_TRANSLATION=false)")
            return
        }
        // S0410: the dialog itself is binding-free (TranslationSettingsDialog). The in-app player
        // still needs the saved settings applied to its active viewers/overlays, so that step is
        // passed as the onApplied hook; standalone hosts call the dialog without one.
        TranslationSettingsDialog.show(
            context = context,
            lifecycleOwner = lifecycleOwner,
            settingsRepository = settingsRepository,
        ) { newSessionSettings ->
            callback.setTranslationSessionSettings(newSessionSettings)
            callback.applyTextViewerFontSettings(newSessionSettings)
            callback.applyTranslationManagerFontSettings(newSessionSettings)
            applyFontSettingsToOverlay(newSessionSettings)
            when (callback.getCurrentFileType()) {
                MediaType.IMAGE, MediaType.GIF -> callback.translateCurrentImage()
                MediaType.PDF -> callback.forceTranslatePdf()
                MediaType.TEXT -> { /* font settings already applied above */ }
                MediaType.EPUB -> callback.applyEpubFontSettings(newSessionSettings)
                else -> { /* no translation for other types */ }
            }
        }
    }
    
    /**
     * Apply font settings to translation overlay view (Google Lens style).
     * 
     * Note: AUTO mode is handled differently for overlay - it uses default sizing algorithm.
     * Only apply non-AUTO settings to overlay.
     */
    private fun applyFontSettingsToOverlay(settings: TranslationSessionSettings) {
        if (settings.fontSize != TranslationFontSize.AUTO) {
            // TranslationOverlayView has its own font size multiplier mechanism
            // Map our session settings to overlay's internal multiplier range (0.7-1.5)
            val targetMultiplier = settings.fontSize.multiplier
            
            // Get current multiplier from overlay
            val currentMultiplier = binding.translationLensOverlay.getFontSizeMultiplier()
            
            // Calculate how many steps to adjust
            val step = 0.1f
            val diff = targetMultiplier - currentMultiplier
            val steps = (diff / step).toInt()
            
            if (steps > 0) {
                repeat(steps) {
                    binding.translationLensOverlay.increaseFontSize()
                }
            } else if (steps < 0) {
                repeat(-steps) {
                    binding.translationLensOverlay.decreaseFontSize()
                }
            }
            
            Timber.d("TranslationButtonManager: Applied font size to overlay - current=$currentMultiplier, target=$targetMultiplier, steps=$steps")
        }
        
        // Note: Font family for overlay is not implemented (TranslationOverlayView uses system default)
        Timber.d("TranslationButtonManager: Font family not applied to overlay - using system default")
    }
}
