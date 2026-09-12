package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailability
import com.sza.fastmediasorter.core.util.rethrowIfCancellation
import com.sza.fastmediasorter.databinding.ActivityPlayerUnifiedBinding
import com.sza.fastmediasorter.domain.model.MediaType
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.domain.models.TranslationSessionSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.dialog.TranslationSettingsDialog
import com.sza.fastmediasorter.utils.collectOnLifecycle
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
    private val capabilityAvailability: CapabilityAvailability,
    private val callback: TranslationButtonCallback
) {
    private val safeViews = PlayerBindingSafeViews(binding)

    private companion object {
        /** Alpha that reads as "present but not usable", matching the inactive-button alpha below. */
        const val DISABLED_BUTTON_ALPHA = 0.3f
    }

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
     * Note: Skips initialization when translation is unavailable in this build or on this device.
     */
    fun setupTranslationDefaults() {
        // Guard: skip translation setup where the capability is off, for either reason.
        if (!capabilityAvailability.isTranslationAvailable(context)) {
            Timber.d("TranslationButtonManager: translation not available - skipping defaults")
            return
        }

        lifecycleOwner.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            
            // Initialize translation session settings from AppSettings defaults
            val defaultFontSize = try {
                TranslationFontSize.valueOf(settings.ocrDefaultFontSize)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
                TranslationFontSize.AUTO
            }
            
            val defaultFontFamily = try {
                TranslationFontFamily.valueOf(settings.ocrDefaultFontFamily)
            } catch (e: Exception) {
                e.rethrowIfCancellation()
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
     * The two off states differ on purpose (S1625). A build that never shipped translation hides the
     * buttons, because the feature does not exist there and an explanation would describe nothing. A
     * device class the ML Kit licence forbids keeps them visible but disabled with the reason, so a
     * user who knows the feature exists is not left hunting for a control that silently vanished.
     */
    fun setupTranslationButtonIcons() {
        val support = capabilityAvailability.translationSupport(context)
        if (support is CapabilityAvailability.TranslationSupport.Unsupported) {
            if (support.reason == CapabilityAvailability.TranslationUnavailableReason.NOT_COMPILED_IN) {
                hideTranslationButtons()
            } else {
                showTranslationButtonsUnlicensed()
            }
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

    private fun translationButtons() = listOf(
        safeViews.btnTranslatePdfCmd,
        safeViews.btnTranslateEpubCmd,
        safeViews.btnTranslateImageCmd,
        safeViews.btnTranslateImage,
    )

    private fun hideTranslationButtons() {
        Timber.d("TranslationButtonManager: translation not compiled into this build - buttons hidden")
        translationButtons().forEach { it.isVisible = false }
    }

    /**
     * The action stays discoverable and says why it cannot run. The reason is put on the content
     * description as well as the toast, so it reaches TalkBack rather than being carried by the
     * dimmed appearance alone.
     */
    private fun showTranslationButtonsUnlicensed() {
        val reason = context.getString(R.string.translation_unavailable_device_licence)
        Timber.i("TranslationButtonManager: translation not licensed for this device class - buttons disabled")
        translationButtons().forEach { button ->
            button.isVisible = true
            // Deliberately NOT isEnabled = false: a disabled view swallows the tap, and the tap is the
            // only moment the explanation can be delivered. The button reads as unavailable through
            // the dimmed alpha and says why through its content description and the toast.
            button.alpha = DISABLED_BUTTON_ALPHA
            button.contentDescription = reason
            button.setOnClickListener { Toast.makeText(context, reason, Toast.LENGTH_LONG).show() }
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
     * Silent where the build has no translation at all; explains itself where only the device class
     * is the obstacle, since that surface was reachable a moment ago on the user's other device.
     */
    fun showTranslationSettingsDialog() {
        val support = capabilityAvailability.translationSupport(context)
        if (support is CapabilityAvailability.TranslationSupport.Unsupported) {
            if (support.reason == CapabilityAvailability.TranslationUnavailableReason.DEVICE_CLASS_NOT_LICENSED) {
                Toast.makeText(
                    context,
                    context.getString(R.string.translation_unavailable_device_licence),
                    Toast.LENGTH_LONG,
                ).show()
            }
            Timber.d("TranslationButtonManager: translation settings unavailable - %s", support.reason)
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
