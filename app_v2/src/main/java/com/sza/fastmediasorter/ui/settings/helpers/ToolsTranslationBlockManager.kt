package com.sza.fastmediasorter.ui.settings.helpers

import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.capability.CapabilityAvailabilityAccessor
import com.sza.fastmediasorter.databinding.FragmentSettingsToolsBinding
import com.sza.fastmediasorter.domain.model.AppSettings
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.common.widget.SettingsSelectionRow
import com.sza.fastmediasorter.ui.dialog.SearchableLanguagePickerDialog
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * S4622: the translation group of the Tools page - the one editor of the languages the player overlay
 * and the OCR camera both use (SETTINGS-NAVIGATION section 3 rule 2), plus the overlay style and the OCR
 * camera's text-only switch. Editing here never turns translation on: that stays the player's command.
 */
class ToolsTranslationBlockManager(
    private val fragment: Fragment,
    private val binding: FragmentSettingsToolsBinding,
    private val settingsRepository: SettingsRepository,
) {

    private val fontSizes = TranslationFontSize.values()
    private val fontFamilies = TranslationFontFamily.values()
    private var interfaceLanguage: String = DEFAULT_LANGUAGE

    /** Returns false when this build has no translation, so the page hides the whole group. */
    fun bind(): Boolean {
        if (!CapabilityAvailabilityAccessor.isTranslationAvailable(fragment.requireContext())) {
            binding.cardToolsTranslation.isVisible = false
            return false
        }
        val context = fragment.requireContext()
        binding.rowTranslationFontSize.setEntries(fontSizes.map { context.getString(it.stringResId) })
        binding.rowTranslationFontFamily.setEntries(fontFamilies.map { context.getString(it.stringResId) })
        registerPickerResult(SearchableLanguagePickerDialog.Mode.SOURCE) { code ->
            update { it.copy(translationSourceLanguage = code) }
        }
        registerPickerResult(SearchableLanguagePickerDialog.Mode.TARGET) { code ->
            update { it.copy(translationTargetLanguage = code) }
        }
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            interfaceLanguage = settings.language
            render(settings)
            bindListeners()
        }
        return true
    }

    private fun render(settings: AppSettings) {
        renderLanguages(settings)
        binding.rowTranslationLensStyle.setCheckedSilently(settings.translationLensStyle)
        val size = fontSizes.firstOrNull { it.name == settings.ocrDefaultFontSize } ?: TranslationFontSize.AUTO
        val family = fontFamilies.firstOrNull { it.name == settings.ocrDefaultFontFamily }
            ?: TranslationFontFamily.DEFAULT
        binding.rowTranslationFontSize.setSelection(fontSizes.indexOf(size).coerceAtLeast(0))
        binding.rowTranslationFontFamily.setSelection(fontFamilies.indexOf(family).coerceAtLeast(0))
        binding.rowCameraOcrOnly.setCheckedSilently(settings.cameraOcrOnly)
    }

    private fun renderLanguages(settings: AppSettings) {
        applyLanguageLabel(
            binding.rowTranslationSourceLanguage,
            settings.translationSourceLanguage,
            R.string.translation_source_language_and_ocr,
        )
        applyLanguageLabel(
            binding.rowTranslationTargetLanguage,
            settings.translationTargetLanguage,
            R.string.translation_target_language,
        )
    }

    private fun bindListeners() {
        binding.rowTranslationSourceLanguage.setOnRowClickListener {
            openPicker(SearchableLanguagePickerDialog.Mode.SOURCE)
        }
        binding.rowTranslationTargetLanguage.setOnRowClickListener {
            openPicker(SearchableLanguagePickerDialog.Mode.TARGET)
        }
        binding.btnTranslationSwapLanguages.setOnClickListener { swapLanguages() }
        binding.rowTranslationLensStyle.setOnCheckedChangeListener { checked ->
            update { it.copy(translationLensStyle = checked) }
        }
        binding.rowTranslationFontSize.setOnItemSelectedListener { index ->
            val size = fontSizes.getOrNull(index) ?: return@setOnItemSelectedListener
            update { it.copy(ocrDefaultFontSize = size.name) }
        }
        binding.rowTranslationFontFamily.setOnItemSelectedListener { index ->
            val family = fontFamilies.getOrNull(index) ?: return@setOnItemSelectedListener
            update { it.copy(ocrDefaultFontFamily = family.name) }
        }
        binding.rowCameraOcrOnly.setOnCheckedChangeListener { checked ->
            update { it.copy(cameraOcrOnly = checked) }
        }
    }

    // "auto" detects the source, so it has no target counterpart; a pair the other list lacks stays as it is.
    private fun swapLanguages() {
        val sourceCodes = TranslationLanguageCatalog.buildSourceLanguageList(interfaceLanguage).map { it.code }.toSet()
        val targetCodes = TranslationLanguageCatalog.buildTargetLanguageList(interfaceLanguage).map { it.code }.toSet()
        update { current ->
            val source = current.translationSourceLanguage
            val target = current.translationTargetLanguage
            if (source != AUTO_LANGUAGE && source in targetCodes && target in sourceCodes) {
                current.copy(translationSourceLanguage = target, translationTargetLanguage = source)
            } else {
                current
            }
        }
    }

    private fun openPicker(mode: SearchableLanguagePickerDialog.Mode) {
        val manager = fragment.childFragmentManager
        val tag = "${SearchableLanguagePickerDialog.TAG}_tools_${mode.name}"
        if (manager.findFragmentByTag(tag) != null) return
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()
            val selected = if (mode == SearchableLanguagePickerDialog.Mode.SOURCE) {
                settings.translationSourceLanguage
            } else {
                settings.translationTargetLanguage
            }
            SearchableLanguagePickerDialog.newInstance(
                selectedCode = selected,
                mode = mode,
                interfaceLanguage = interfaceLanguage,
                requestKey = requestKeyFor(mode),
            ).show(manager, tag)
        }
    }

    private fun registerPickerResult(mode: SearchableLanguagePickerDialog.Mode, onPicked: (String) -> Unit) {
        fragment.childFragmentManager.setFragmentResultListener(
            requestKeyFor(mode),
            fragment.viewLifecycleOwner,
        ) { _, bundle ->
            bundle.getString(SearchableLanguagePickerDialog.RESULT_LANGUAGE_CODE)?.let(onPicked)
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        fragment.viewLifecycleOwner.lifecycleScope.launch {
            settingsRepository.updateSettings { latest -> transform(latest) }
            renderLanguages(settingsRepository.getSettings().first())
        }
    }

    private fun applyLanguageLabel(row: SettingsSelectionRow, code: String, @StringRes titleRes: Int) {
        val displayLocale = Locale.forLanguageTag(interfaceLanguage)
        val item = TranslationLanguageCatalog.findLanguage(code, displayLocale)
            ?: TranslationLanguageCatalog.findLanguage(DEFAULT_LANGUAGE, displayLocale)
        val label = item?.let(LanguageFlagFormatter::plainLabel) ?: code.uppercase(Locale.ROOT)
        row.setValue(label)
        row.contentDescription = "${fragment.getString(titleRes)}: $label"
    }

    private fun requestKeyFor(mode: SearchableLanguagePickerDialog.Mode): String = "$REQUEST_PREFIX${mode.name}"

    private companion object {
        const val REQUEST_PREFIX = "tools_translation_language_"
        const val AUTO_LANGUAGE = "auto"
        const val DEFAULT_LANGUAGE = "en"
    }
}
