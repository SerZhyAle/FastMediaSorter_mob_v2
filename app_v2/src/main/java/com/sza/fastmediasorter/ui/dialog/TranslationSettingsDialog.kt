package com.sza.fastmediasorter.ui.dialog

import android.content.Context
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.BuildConfig
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.models.TranslationFontFamily
import com.sza.fastmediasorter.domain.models.TranslationFontSize
import com.sza.fastmediasorter.domain.models.TranslationSessionSettings
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.delivery.DeliveryEnableInterceptorEntryPoint
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.LanguageItem
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale

/**
 * Binding-free translation/OCR settings dialog (S0410, extracted from `TranslationButtonManager` so
 * any host can open it). Lets the user pick source/target language, the Lens overlay style, and
 * font size/family; confirming persists the settings and enables translation. The host-specific
 * "apply the new settings to the active viewers/overlays" step is supplied via [onApplied] - the
 * in-app player passes its apply block; the standalone host omits it and re-reads settings on next use.
 */
object TranslationSettingsDialog {

    fun show(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        settingsRepository: SettingsRepository,
        onApplied: ((TranslationSessionSettings) -> Unit)? = null,
    ) {
        if (!BuildConfig.ENABLE_TRANSLATION) {
            Timber.d("TranslationSettingsDialog: not available (ENABLE_TRANSLATION=false)")
            return
        }

        val dialogView = android.view.LayoutInflater.from(context)
            .inflate(R.layout.dialog_translation_settings, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(context)
            .setView(dialogView)
            .create()

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
                applyLanguageLabel(
                    context, sourceLanguageView, selectedSourceLang, interfaceLang,
                    R.string.translation_source_language_and_ocr
                )
                applyLanguageLabel(
                    context, targetLanguageView, selectedTargetLang, interfaceLang,
                    R.string.translation_target_language
                )
            }

            sourceLanguageView.setOnClickListener {
                showLanguagePicker(
                    context,
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
                    context,
                    selectedCode = selectedTargetLang,
                    mode = SearchableLanguagePickerDialog.Mode.TARGET,
                    interfaceLanguage = interfaceLang
                ) { language ->
                    selectedTargetLang = language.code
                    updateLanguageViews()
                }
            }

            val fontSizeOptions = TranslationFontSize.values()
            val fontSizeNames = fontSizeOptions.map { context.getString(it.stringResId) }.toTypedArray()
            val fontSizeAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_item, fontSizeNames)
            fontSizeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerFontSize.adapter = fontSizeAdapter

            val fontFamilyOptions = TranslationFontFamily.values()
            val fontFamilyNames = fontFamilyOptions.map { context.getString(it.stringResId) }.toTypedArray()
            val fontFamilyAdapter = android.widget.ArrayAdapter(context, android.R.layout.simple_spinner_item, fontFamilyNames)
            fontFamilyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerFontFamily.adapter = fontFamilyAdapter

            updateLanguageViews()
            switchLensStyle.isChecked = settings.translationLensStyle

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

            btnCancel.setOnClickListener { dialog.dismiss() }

            btnOk.setOnClickListener {
                val newLensStyle = switchLensStyle.isChecked
                val newFontSize = fontSizeOptions[spinnerFontSize.selectedItemPosition]
                val newFontFamily = fontFamilyOptions[spinnerFontFamily.selectedItemPosition]

                // Confirming these settings turns translation ON, so gate on the TRANSLATION
                // deliverable being installed; on refusal close without enabling.
                val applyAndPersist = {
                    lifecycleOwner.lifecycleScope.launch {
                        val updatedSettings = settings.copy(
                            translationSourceLanguage = selectedSourceLang,
                            translationTargetLanguage = selectedTargetLang,
                            translationLensStyle = newLensStyle,
                            enableTranslation = true,
                            ocrDefaultFontSize = newFontSize.name,
                            ocrDefaultFontFamily = newFontFamily.name
                        )
                        settingsRepository.updateSettings(updatedSettings)

                        val newSessionSettings = TranslationSessionSettings(
                            fontSize = newFontSize,
                            fontFamily = newFontFamily
                        )
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_saved),
                            Toast.LENGTH_SHORT
                        ).show()
                        // Host applies the new settings to its active viewers/overlays (in-app only).
                        onApplied?.invoke(newSessionSettings)
                    }
                    // Dismiss synchronously (matches the in-app behaviour); the save runs in the
                    // launched coroutine above. Keeping this as the lambda's last statement makes
                    // applyAndPersist a () -> Unit, the type requireInstalled's onReady expects.
                    dialog.dismiss()
                }

                val gateActivity = context as? FragmentActivity
                if (gateActivity != null) {
                    EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        DeliveryEnableInterceptorEntryPoint::class.java
                    ).deliveryEnableInterceptor().requireInstalled(
                        gateActivity,
                        DeliverableSet.TRANSLATION,
                        onReady = applyAndPersist,
                        onUnavailable = { dialog.dismiss() }
                    )
                } else {
                    applyAndPersist()
                }
            }

            dialog.show()
        }
    }

    private fun showLanguagePicker(
        context: Context,
        selectedCode: String,
        mode: SearchableLanguagePickerDialog.Mode,
        interfaceLanguage: String,
        onSelected: (LanguageItem) -> Unit
    ) {
        val fragmentActivity = context as? FragmentActivity
        if (fragmentActivity == null) {
            Timber.w("TranslationSettingsDialog: cannot show language picker without FragmentActivity context")
            return
        }
        val tag = "${SearchableLanguagePickerDialog.TAG}_settings_${mode.name}"
        if (fragmentActivity.supportFragmentManager.findFragmentByTag(tag) != null) return
        SearchableLanguagePickerDialog.newInstance(
            selectedCode = selectedCode,
            mode = mode,
            interfaceLanguage = interfaceLanguage,
            onLanguageSelected = onSelected
        ).show(fragmentActivity.supportFragmentManager, tag)
    }

    private fun applyLanguageLabel(
        context: Context,
        view: TextView,
        code: String,
        interfaceLanguage: String,
        @androidx.annotation.StringRes titleRes: Int
    ) {
        val displayLocale = Locale.forLanguageTag(interfaceLanguage)
        val item = TranslationLanguageCatalog.findLanguage(code, displayLocale)
            ?: TranslationLanguageCatalog.findLanguage("en", displayLocale)
        if (item != null) {
            LanguageFlagFormatter.applyLabel(view, item)
            view.contentDescription =
                "${context.getString(titleRes)}: ${LanguageFlagFormatter.plainLabel(item)}"
        } else {
            val fallback = code.uppercase(Locale.ROOT)
            view.text = fallback
            view.contentDescription = "${context.getString(titleRes)}: $fallback"
        }
    }
}
