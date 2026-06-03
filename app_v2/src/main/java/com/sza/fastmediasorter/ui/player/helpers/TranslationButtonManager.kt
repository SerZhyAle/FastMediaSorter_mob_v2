package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.widget.TextView
import android.widget.Toast
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
import com.sza.fastmediasorter.ui.dialog.SearchableLanguagePickerDialog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

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
            // Hide all translation buttons
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
        // Guard: Skip translation settings if not supported by this flavor
        if (!BuildConfig.ENABLE_TRANSLATION) {
            Timber.d("TranslationButtonManager: Translation settings not available (ENABLE_TRANSLATION=false)")
            return
        }
        
        val dialogView = android.view.LayoutInflater.from(context).inflate(R.layout.dialog_translation_settings, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        
        // Get current settings
        lifecycleOwner.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            
            val interfaceLang = settings.language
            var selectedSourceLang = settings.translationSourceLanguage
            var selectedTargetLang = settings.translationTargetLanguage

            val sourceLanguageCodes = TranslationLanguageCatalog.buildSourceLanguageList(interfaceLang).map { it.code }.toSet()
            val targetLanguageCodes = TranslationLanguageCatalog.buildTargetLanguageList(interfaceLang).map { it.code }.toSet()

            val sourceLanguageView = dialogView.findViewById<TextView>(R.id.spinnerSourceLanguage)
            val targetLanguageView = dialogView.findViewById<TextView>(R.id.spinnerTargetLanguage)
            val btnSwapLanguages = dialogView.findViewById<android.widget.ImageButton>(R.id.btnSwapLanguages)
            val switchLensStyle = dialogView.findViewById<com.google.android.material.checkbox.MaterialCheckBox>(R.id.switchLensStyle)
            val spinnerFontSize = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerFontSize)
            val spinnerFontFamily = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerFontFamily)
            val btnOk = dialogView.findViewById<android.widget.Button>(R.id.btnOk)
            val btnCancel = dialogView.findViewById<android.widget.Button>(R.id.btnCancel)

            fun updateLanguageViews() {
                sourceLanguageView.text = formatLanguageLabel(selectedSourceLang, interfaceLang)
                targetLanguageView.text = formatLanguageLabel(selectedTargetLang, interfaceLang)
                sourceLanguageView.contentDescription =
                    "${context.getString(R.string.translation_source_language_and_ocr)}: ${sourceLanguageView.text}"
                targetLanguageView.contentDescription =
                    "${context.getString(R.string.translation_target_language)}: ${targetLanguageView.text}"
            }

            sourceLanguageView.setOnClickListener {
                showLanguagePicker(
                    selectedCode = selectedSourceLang,
                    mode = SearchableLanguagePickerDialog.Mode.SOURCE,
                    interfaceLanguage = interfaceLang
                ) { language ->
                    selectedSourceLang = language.code
                    updateLanguageViews()
                }
            }

            targetLanguageView.setOnClickListener {
                showLanguagePicker(
                    selectedCode = selectedTargetLang,
                    mode = SearchableLanguagePickerDialog.Mode.TARGET,
                    interfaceLanguage = interfaceLang
                ) { language ->
                    selectedTargetLang = language.code
                    updateLanguageViews()
                }
            }
            
            // Setup font size adapter
            val fontSizeOptions = TranslationFontSize.values()
            val fontSizeNames = fontSizeOptions.map { context.getString(it.stringResId) }.toTypedArray()
            val fontSizeAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_item, fontSizeNames)
            fontSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerFontSize.adapter = fontSizeAdapter
            
            // Setup font family adapter
            val fontFamilyOptions = TranslationFontFamily.values()
            val fontFamilyNames = fontFamilyOptions.map { context.getString(it.stringResId) }.toTypedArray()
            val fontFamilyAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_item, fontFamilyNames)
            fontFamilyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerFontFamily.adapter = fontFamilyAdapter
            
            updateLanguageViews()
            switchLensStyle.isChecked = settings.translationLensStyle
            
            // Load font settings from repository (not session) for persistence across app restarts
            val savedFontSize = try {
                TranslationFontSize.valueOf(settings.ocrDefaultFontSize)
            } catch (e: Exception) {
                TranslationFontSize.AUTO
            }
            val savedFontFamily = try {
                TranslationFontFamily.valueOf(settings.ocrDefaultFontFamily)
            } catch (e: Exception) {
                TranslationFontFamily.DEFAULT
            }
            val fontSizeIndex = fontSizeOptions.indexOf(savedFontSize)
            val fontFamilyIndex = fontFamilyOptions.indexOf(savedFontFamily)
            spinnerFontSize.setSelection(fontSizeIndex.coerceAtLeast(0))
            spinnerFontFamily.setSelection(fontFamilyIndex.coerceAtLeast(0))
            
            // Swap languages button
            btnSwapLanguages.setOnClickListener {
                if (
                    selectedSourceLang != "auto" &&
                    selectedSourceLang in targetLanguageCodes &&
                    selectedTargetLang in sourceLanguageCodes
                ) {
                    val oldSource = selectedSourceLang
                    selectedSourceLang = selectedTargetLang
                    selectedTargetLang = oldSource
                    updateLanguageViews()
                }
            }
            
            // Buttons
            btnCancel.setOnClickListener {
                dialog.dismiss()
            }
            
            btnOk.setOnClickListener {
                val newLensStyle = switchLensStyle.isChecked
                val newFontSize = fontSizeOptions[spinnerFontSize.selectedItemPosition]
                val newFontFamily = fontFamilyOptions[spinnerFontFamily.selectedItemPosition]
                
                // Save all settings to repository (including font settings for persistence)
                lifecycleOwner.lifecycleScope.launch {
                    val updatedSettings = settings.copy(
                        translationSourceLanguage = selectedSourceLang,
                        translationTargetLanguage = selectedTargetLang,
                        translationLensStyle = newLensStyle,
                        enableTranslation = true, // Ensure translation is enabled
                        ocrDefaultFontSize = newFontSize.name, // Save font size to repository
                        ocrDefaultFontFamily = newFontFamily.name // Save font family to repository
                    )
                    settingsRepository.updateSettings(updatedSettings)
                    
                    // Also update session settings for immediate use
                    val newSessionSettings = TranslationSessionSettings(
                        fontSize = newFontSize,
                        fontFamily = newFontFamily
                    )
                    callback.setTranslationSessionSettings(newSessionSettings)
                    
                    Toast.makeText(
                        context,
                        context.getString(R.string.settings_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Apply settings to all active managers (via callbacks)
                    callback.applyTextViewerFontSettings(newSessionSettings)
                    callback.applyTranslationManagerFontSettings(newSessionSettings)
                    
                    // Apply font settings to Google Lens overlay
                    applyFontSettingsToOverlay(newSessionSettings)
                    
                    // Apply translation immediately based on current content type
                    when (callback.getCurrentFileType()) {
                        MediaType.IMAGE, MediaType.GIF -> {
                            // For images: trigger translation
                            callback.translateCurrentImage()
                        }
                        MediaType.PDF -> {
                            // For PDF: force translate (will use new settings)
                            callback.forceTranslatePdf()
                        }
                        MediaType.TEXT -> {
                            // For text: apply font settings only (already applied above via applyTextViewerFontSettings)
                            // NO forced translation here - user must click translate button explicitly
                        }
                        MediaType.EPUB -> {
                            // For EPUB: apply font settings only
                            // NO forced translation here - user must click translate button explicitly
                            callback.applyEpubFontSettings(newSessionSettings)
                        }
                        else -> { /* No translation for other types */ }
                    }
                }
                
                dialog.dismiss()
            }
            
            dialog.show()
        }
    }

    private fun showLanguagePicker(
        selectedCode: String,
        mode: SearchableLanguagePickerDialog.Mode,
        interfaceLanguage: String,
        onSelected: (LanguageItem) -> Unit
    ) {
        val fragmentActivity = context as? FragmentActivity
        if (fragmentActivity == null) {
            Timber.w("TranslationButtonManager: Cannot show language picker without FragmentActivity context")
            return
        }
        val tag = "${SearchableLanguagePickerDialog.TAG}_player_${mode.name}"
        if (fragmentActivity.supportFragmentManager.findFragmentByTag(tag) != null) return
        SearchableLanguagePickerDialog.newInstance(
            selectedCode = selectedCode,
            mode = mode,
            interfaceLanguage = interfaceLanguage,
            onLanguageSelected = onSelected
        ).show(fragmentActivity.supportFragmentManager, tag)
    }

    private fun formatLanguageLabel(code: String, interfaceLanguage: String): String {
        val displayLocale = Locale.forLanguageTag(interfaceLanguage)
        val item = TranslationLanguageCatalog.findLanguage(code, displayLocale)
            ?: TranslationLanguageCatalog.findLanguage("en", displayLocale)
        return item?.let(TranslationLanguageCatalog::formatLanguage) ?: code.uppercase(Locale.ROOT)
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
            
            // Apply steps
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
