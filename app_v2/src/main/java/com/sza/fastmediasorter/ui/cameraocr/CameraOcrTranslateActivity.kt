package com.sza.fastmediasorter.ui.cameraocr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityCameraOcrTranslateBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.cameraocr.helpers.CameraOcrFlowManager
import com.sza.fastmediasorter.ui.cameraocr.helpers.CameraOcrStorageManager
import com.sza.fastmediasorter.ui.player.helpers.DocumentSelectionActionModeCallback
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.ui.dialog.SearchableLanguagePickerDialog
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.LanguageItem
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import com.sza.fastmediasorter.ui.player.helpers.openCalculatorForSelection
import com.sza.fastmediasorter.ui.player.helpers.openGoogleSearch
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject

/**
 * Thin UI shell for the Camera-OCR-Translate flow. Owns only view binding, click wiring and the
 * camera result launcher; all orchestration and storage live in [CameraOcrFlowManager] /
 * [CameraOcrStorageManager] (Strict Rule 3 - no business logic in the UI layer).
 */
@AndroidEntryPoint
class CameraOcrTranslateActivity :
    BaseActivity<ActivityCameraOcrTranslateBinding>(),
    CameraOcrFlowManager.Callback {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var flowManager: CameraOcrFlowManager
    private var calculatorEnabled = false

    // Crop-step language cluster state (S0354). Interface language drives the localized labels;
    // source/target codes are mirrored from settings via renderCropLanguages for the picker.
    private var cropInterfaceLang = "en"
    private var cropSourceLang = "auto"
    private var cropTargetLang = "ru"

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            flowManager.onPhotoCaptured()
        } else {
            Timber.i("CameraOcrTranslateActivity: Camera capture cancelled or failed")
            flowManager.onCaptureCancelled()
        }
    }

    override fun getViewBinding(): ActivityCameraOcrTranslateBinding {
        return ActivityCameraOcrTranslateBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val translationManager = TranslationManager(
            context = this,
            settingsRepository = settingsRepository,
            callback = object : TranslationManager.TranslationCallback {
                override fun showError(message: String) {
                    runOnUiThread {
                        Toast.makeText(this@CameraOcrTranslateActivity, message, Toast.LENGTH_LONG).show()
                    }
                }

                override fun showModelDownloadPrompt(
                    languageName: String,
                    onConfirm: () -> Unit,
                    onCancel: () -> Unit
                ) {
                    runOnUiThread {
                        AlertDialog.Builder(this@CameraOcrTranslateActivity)
                            .setTitle(R.string.download_translation_model_title)
                            .setMessage(getString(R.string.download_translation_model_message, languageName))
                            .setPositiveButton(R.string.download) { _, _ -> onConfirm() }
                            .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
                            .setOnCancelListener { onCancel() }
                            .show()
                    }
                }
            }
        )

        flowManager = CameraOcrFlowManager(
            scope = lifecycleScope,
            settingsRepository = settingsRepository,
            storageManager = CameraOcrStorageManager(applicationContext),
            translationManager = translationManager,
            callback = this
        )

        // Automatically launch camera on startup if we don't have results yet
        if (savedInstanceState == null) {
            flowManager.startCapture()
        }
    }

    override fun setupViews() {
        applySystemBarInsets()
        binding.btnSaveTxt.setOnClickListener { flowManager.exportTxt() }
        binding.btnNextPhoto.setOnClickListener { flowManager.startCapture() }
        binding.btnEmptyRetry.setOnClickListener { flowManager.startCapture() }
        binding.btnClose.setOnClickListener { finish() }
        binding.btnEmptyClose.setOnClickListener { finish() }
        binding.btnSettings.setOnClickListener { showCompactSettingsDialog() }
        binding.btnCropConfirm.setOnClickListener {
            flowManager.onCropConfirmed(
                binding.cropOverlay.getNormalizedRect(),
                binding.cropOverlay.isFrameTouched()
            )
        }
        binding.btnCropRetry.setOnClickListener { flowManager.onCropRetry() }
        binding.btnCropOcrLang.setOnClickListener {
            showLanguagePicker(
                selectedCode = cropSourceLang,
                mode = SearchableLanguagePickerDialog.Mode.SOURCE,
                interfaceLanguage = cropInterfaceLang
            ) { language -> flowManager.setCropSourceLanguage(language.code) }
        }
        binding.btnCropTargetLang.setOnClickListener {
            showLanguagePicker(
                selectedCode = cropTargetLang,
                mode = SearchableLanguagePickerDialog.Mode.TARGET,
                interfaceLanguage = cropInterfaceLang
            ) { language -> flowManager.setCropTargetLanguage(language.code) }
        }
        installResultSelectionMenu(binding.tvOriginalText)
        installResultSelectionMenu(binding.tvTranslation)
    }

    override fun observeData() {
        collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            calculatorEnabled = settings.enableCalculator
            cropInterfaceLang = settings.language
            flowManager.setOcrOnlyActive(!settings.enableTranslation || settings.cameraOcrOnly)
        }
    }

    private fun applySystemBarInsets() {
        binding.layoutResultContent.applySystemBarInsetPadding()
        binding.layoutEmptyState.applySystemBarInsetPadding()
        binding.layoutLoading.applySystemBarInsetPadding()
        binding.layoutCropState.applySystemBarInsetPadding()
    }

    // ---- CameraOcrFlowManager.Callback ----

    override fun launchCamera(intent: Intent) {
        try {
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "CameraOcrTranslateActivity: Camera launch failed")
            flowManager.onCaptureLaunchFailed()
        }
    }

    override fun showCropStep(bitmap: Bitmap) {
        Timber.d("S0338: crop preview rendered")
        binding.layoutCropState.isVisible = true
        binding.layoutResultContent.isVisible = false
        binding.layoutEmptyState.isVisible = false
        binding.layoutLoading.isVisible = false
        binding.ivCropPreview.setImageBitmap(bitmap)
        binding.cropOverlay.setImageSize(bitmap.width, bitmap.height)
        binding.cropOverlay.requestFocus()
    }

    override fun renderCropLanguages(sourceCode: String, targetCode: String, translationAvailable: Boolean) {
        cropSourceLang = sourceCode
        cropTargetLang = targetCode
        // Disable all-caps transformation: it rebuilds the text and would strip the flag ImageSpan
        // for ru/be. The compact label already upper-cases the language code, so visible text is unchanged.
        binding.btnCropOcrLang.isAllCaps = false
        binding.btnCropTargetLang.isAllCaps = false
        binding.btnCropOcrLang.text = compactLanguageLabel(binding.btnCropOcrLang, sourceCode)
        binding.btnCropOcrLang.contentDescription = cropLanguageContentDescription(
            titleRes = R.string.camera_ocr_crop_lang_ocr_desc,
            code = sourceCode
        )
        binding.ivCropLangArrow.isVisible = translationAvailable
        binding.btnCropTargetLang.isVisible = translationAvailable
        if (translationAvailable) {
            binding.btnCropTargetLang.text = compactLanguageLabel(binding.btnCropTargetLang, targetCode)
            binding.btnCropTargetLang.contentDescription = cropLanguageContentDescription(
                titleRes = R.string.camera_ocr_crop_lang_target_desc,
                code = targetCode
            )
            binding.btnCropOcrLang.nextFocusRightId = binding.btnCropTargetLang.id
            binding.btnCropConfirm.nextFocusLeftId = binding.btnCropTargetLang.id
        } else {
            binding.btnCropOcrLang.nextFocusRightId = binding.btnCropConfirm.id
            binding.btnCropConfirm.nextFocusLeftId = binding.btnCropOcrLang.id
        }
    }

    /** Compact crop-cluster label: flag plus the language code in upper case (e.g. "🌐 AUTO", "🇷🇺 RU"). */
    private fun compactLanguageLabel(view: TextView, code: String): CharSequence {
        val displayLocale = Locale.forLanguageTag(cropInterfaceLang)
        val item = TranslationLanguageCatalog.findLanguage(code, displayLocale)
        return LanguageFlagFormatter.compactLabel(view, item, code)
    }

    private fun cropLanguageContentDescription(@androidx.annotation.StringRes titleRes: Int, code: String): String {
        val displayLocale = Locale.forLanguageTag(cropInterfaceLang)
        val item = TranslationLanguageCatalog.findLanguage(code, displayLocale)
        val languageName = item?.localizedName ?: code.uppercase(Locale.ROOT)
        return "${getString(titleRes)}: $languageName"
    }

    override fun showLoading(statusRes: Int, subStatusRes: Int) {
        binding.layoutLoading.isVisible = true
        binding.tvLoadingStatus.text = if (statusRes != 0) getString(statusRes) else ""
        val sub = if (subStatusRes != 0) getString(subStatusRes) else ""
        binding.tvLoadingSub.text = sub
        binding.tvLoadingSub.isVisible = sub.isNotEmpty()
        binding.layoutResultContent.isVisible = false
        binding.layoutEmptyState.isVisible = false
        binding.layoutCropState.isVisible = false
    }

    override fun hideLoading() {
        binding.layoutLoading.isVisible = false
    }

    override fun showResults(original: String, translation: String, ocrOnly: Boolean) {
        binding.layoutResultContent.isVisible = true
        binding.layoutEmptyState.isVisible = false
        binding.layoutLoading.isVisible = false
        binding.layoutCropState.isVisible = false

        binding.tvOriginalText.text = original

        if (ocrOnly || translation.isBlank()) {
            binding.cardTranslation.isVisible = false
        } else {
            binding.cardTranslation.isVisible = true
            binding.tvTranslation.text = translation
        }
        binding.tvOriginalHeader.text = getString(R.string.camera_ocr_pane_original)
    }

    override fun showEmpty() {
        binding.layoutEmptyState.isVisible = true
        binding.layoutResultContent.isVisible = false
        binding.layoutLoading.isVisible = false
        binding.layoutCropState.isVisible = false
    }

    override fun showToast(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_LONG).show()
    }

    override fun showSaveSuccess(path: String) {
        Toast.makeText(this, getString(R.string.camera_ocr_save_success, path), Toast.LENGTH_LONG).show()
    }

    override fun finishFlow() {
        finish()
    }

    private fun showCompactSettingsDialog() {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()

            val view = LayoutInflater.from(this@CameraOcrTranslateActivity)
                .inflate(R.layout.dialog_camera_ocr_settings, null)

            // Result screen rework (S0354): OCR over the captured image is already done, so only the
            // translation target language is editable here. Changing it re-translates the existing
            // recognized text. The OCR source language is chosen at capture time on the crop step.
            val targetView = view.findViewById<TextView>(R.id.spinnerTargetLanguage)
            val cbOcrOnly = view.findViewById<CheckBox>(R.id.cbOcrOnly)

            val interfaceLang = settings.language
            var selectedTargetLang = settings.translationTargetLanguage

            fun updateTargetView() {
                applyLanguageLabel(targetView, selectedTargetLang, interfaceLang)
            }

            targetView.setOnClickListener {
                if (!targetView.isEnabled) return@setOnClickListener
                showLanguagePicker(
                    selectedCode = selectedTargetLang,
                    mode = SearchableLanguagePickerDialog.Mode.TARGET,
                    interfaceLanguage = interfaceLang
                ) { language ->
                    selectedTargetLang = language.code
                    updateTargetView()
                }
            }

            cbOcrOnly.isChecked = settings.cameraOcrOnly
            updateTargetView()
            updateTargetLanguageEnabled(targetView, settings.enableTranslation && !settings.cameraOcrOnly)
            cbOcrOnly.setOnCheckedChangeListener { _, isChecked ->
                updateTargetLanguageEnabled(targetView, settings.enableTranslation && !isChecked)
            }

            AlertDialog.Builder(this@CameraOcrTranslateActivity)
                .setTitle(R.string.settings)
                .setView(view)
                .setPositiveButton(R.string.apply) { _, _ ->
                    flowManager.applyLanguageSettings(
                        targetLang = selectedTargetLang,
                        ocrOnly = cbOcrOnly.isChecked
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showLanguagePicker(
        selectedCode: String,
        mode: SearchableLanguagePickerDialog.Mode,
        interfaceLanguage: String,
        onSelected: (LanguageItem) -> Unit
    ) {
        val tag = "${SearchableLanguagePickerDialog.TAG}_camera_${mode.name}"
        if (supportFragmentManager.findFragmentByTag(tag) != null) return
        SearchableLanguagePickerDialog.newInstance(
            selectedCode = selectedCode,
            mode = mode,
            interfaceLanguage = interfaceLanguage,
            onLanguageSelected = onSelected
        ).show(supportFragmentManager, tag)
    }

    private fun updateTargetLanguageEnabled(view: TextView, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1.0f else 0.45f
    }

    private fun applyLanguageLabel(view: TextView, code: String, interfaceLanguage: String) {
        val displayLocale = Locale.forLanguageTag(interfaceLanguage)
        val item = TranslationLanguageCatalog.findLanguage(code, displayLocale)
            ?: TranslationLanguageCatalog.findLanguage("en", displayLocale)
        if (item != null) {
            LanguageFlagFormatter.applyLabel(view, item)
            view.contentDescription =
                "${getString(R.string.translation_target_language)}: ${LanguageFlagFormatter.plainLabel(item)}"
        } else {
            val fallback = code.uppercase(Locale.ROOT)
            view.text = fallback
            view.contentDescription = "${getString(R.string.translation_target_language)}: $fallback"
        }
    }

    private fun installResultSelectionMenu(textView: TextView) {
        textView.customSelectionActionModeCallback = DocumentSelectionActionModeCallback(
            showTranslate = false,
            getSelectedText = { selectedTextFrom(textView) },
            onTranslate = { },
            onSearchGoogle = { openGoogleSearch(this, it) },
            isCalculatorAvailable = { calculatorEnabled },
            onOpenCalculator = { openCalculatorForSelection(this, it) },
        )
    }

    private fun selectedTextFrom(textView: TextView): String {
        val text = textView.text ?: return ""
        val start = textView.selectionStart.coerceAtLeast(0).coerceAtMost(text.length)
        val end = textView.selectionEnd.coerceAtLeast(0).coerceAtMost(text.length)
        return text.substring(minOf(start, end), maxOf(start, end))
    }

    override fun onDestroy() {
        flowManager.cleanup()
        super.onDestroy()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, CameraOcrTranslateActivity::class.java)
        }
    }
}
