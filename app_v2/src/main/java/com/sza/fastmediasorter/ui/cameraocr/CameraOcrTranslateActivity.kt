package com.sza.fastmediasorter.ui.cameraocr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityCameraOcrTranslateBinding
import com.sza.fastmediasorter.ui.cameraocr.helpers.CameraOcrFlowManager
import com.sza.fastmediasorter.ui.cameraocr.helpers.CameraOcrFlowManagerFactory
import com.sza.fastmediasorter.ui.cameraocr.helpers.CameraOcrStorageManager
import com.sza.fastmediasorter.ui.common.help.HelpOverlayEntryManager
import com.sza.fastmediasorter.ui.dialog.SearchableLanguagePickerDialog
import com.sza.fastmediasorter.ui.player.helpers.DocumentSelectionActionModeCallback
import com.sza.fastmediasorter.ui.player.helpers.LanguageFlagFormatter
import com.sza.fastmediasorter.ui.player.helpers.TranslationLanguageCatalog
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.ui.player.helpers.openCalculatorForSelection
import com.sza.fastmediasorter.ui.player.helpers.openGoogleSearch
import com.sza.fastmediasorter.ui.settings.SettingsActivity
import com.sza.fastmediasorter.ui.settings.SettingsActivity.Companion.SECTION_TRANSLATION
import com.sza.fastmediasorter.util.showBoundToHost
import com.sza.fastmediasorter.utils.applySystemBarInsetPadding
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
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
    lateinit var flowManagerFactory: CameraOcrFlowManagerFactory

    private lateinit var flowManager: CameraOcrFlowManager
    private var calculatorEnabled = false
    private var helpEntry: HelpOverlayEntryManager? = null

    // Crop-step language cluster state (S0354). Interface language drives the localized labels;
    // source/target codes are mirrored from settings via renderCropLanguages for the picker.
    private var cropInterfaceLang = "en"
    private var cropSourceLang = "auto"
    private var cropTargetLang = "ru"

    // S4622: the settings as they stood when the Tools settings page was opened; compared on return.
    private var settingsReturnSnapshot: CameraOcrFlowManager.LanguageSnapshot? = null

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

        val translationCallback = object : TranslationManager.TranslationCallback {
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
                    MaterialAlertDialogBuilder(this@CameraOcrTranslateActivity)
                        .setTitle(R.string.download_translation_model_title)
                        .setMessage(getString(R.string.download_translation_model_message, languageName))
                        .setPositiveButton(R.string.download) { _, _ -> onConfirm() }
                        .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
                        .setOnCancelListener { onCancel() }
                        .showBoundToHost(this@CameraOcrTranslateActivity)
                }
            }
        }

        flowManager = flowManagerFactory.create(
            context = this,
            scope = lifecycleScope,
            storageManager = flowManagerFactory.createStorageManager(applicationContext),
            translationCallback = translationCallback,
            callback = this,
        )

        // Automatically start on first creation: from an existing image (S1042 - e.g. a screenshot
        // routed here by the gesture) when EXTRA_SOURCE_IMAGE_PATH is set, otherwise the camera.
        if (savedInstanceState != null) {
            flowManager.restoreState(savedInstanceState)
        } else {
            val sourcePath = intent?.getStringExtra(EXTRA_SOURCE_IMAGE_PATH)
            if (!sourcePath.isNullOrBlank()) {
                flowManager.startWithImage(java.io.File(sourcePath))
            } else {
                flowManager.startCapture()
            }
        }
    }

    override fun setupViews() {
        applySystemBarInsets()
        helpEntry = HelpOverlayEntryManager(
            this, binding.root.parent as ViewGroup, javaClass.simpleName, null,
            reserveSpaceFor = binding.root,
        )
        binding.btnSaveTxt.setOnClickListener { flowManager.exportTxt() }
        binding.btnNextPhoto.setOnClickListener { flowManager.startCapture() }
        binding.btnEmptyRetry.setOnClickListener { flowManager.startCapture() }
        binding.btnClose.setOnClickListener { finish() }
        binding.btnEmptyClose.setOnClickListener { finish() }
        binding.btnSettings.setOnClickListener { openToolsSettings() }
        binding.btnCropConfirm.setOnClickListener {
            flowManager.onCropConfirmed(
                binding.cropOverlay.getNormalizedRect(),
                binding.cropOverlay.isFrameTouched()
            )
        }
        binding.btnCropRetry.setOnClickListener { flowManager.onCropRetry() }
        // S1214: registered here rather than inside the click handlers, so a picker restored after a
        // recreation still finds its listener; the click only opens the dialog.
        listenForLanguagePick(REQ_CROP_SOURCE_LANGUAGE) { code -> flowManager.setCropSourceLanguage(code) }
        listenForLanguagePick(REQ_CROP_TARGET_LANGUAGE) { code -> flowManager.setCropTargetLanguage(code) }
        binding.btnCropOcrLang.setOnClickListener {
            showLanguagePicker(
                requestKey = REQ_CROP_SOURCE_LANGUAGE,
                selectedCode = cropSourceLang,
                mode = SearchableLanguagePickerDialog.Mode.SOURCE,
                interfaceLanguage = cropInterfaceLang
            )
        }
        binding.btnCropTargetLang.setOnClickListener {
            showLanguagePicker(
                requestKey = REQ_CROP_TARGET_LANGUAGE,
                selectedCode = cropTargetLang,
                mode = SearchableLanguagePickerDialog.Mode.TARGET,
                interfaceLanguage = cropInterfaceLang
            )
        }
        installResultSelectionMenu(binding.tvOriginalText)
        installResultSelectionMenu(binding.tvTranslation)
    }

    override fun observeData() {
        collectOnLifecycle(appSettings) { settings ->
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
        binding.btnCropOcrLang.text = compactLanguageLabel(sourceCode)
        binding.btnCropOcrLang.contentDescription = cropLanguageContentDescription(
            titleRes = R.string.camera_ocr_crop_lang_ocr_desc,
            code = sourceCode
        )
        binding.ivCropLangArrow.isVisible = translationAvailable
        binding.btnCropTargetLang.isVisible = translationAvailable
        if (translationAvailable) {
            binding.btnCropTargetLang.text = compactLanguageLabel(targetCode)
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

    /** Compact crop-cluster label: the language code in upper case (e.g. "🌐 AUTO", "RU"). */
    private fun compactLanguageLabel(code: String): CharSequence {
        val displayLocale = Locale.forLanguageTag(cropInterfaceLang)
        val item = TranslationLanguageCatalog.findLanguage(code, displayLocale)
        return LanguageFlagFormatter.compactLabel(item, code)
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

    // S4622: the OCR languages and OCR-only are a block of the Tools settings page, the one editor they
    // share with the player (SETTINGS-NAVIGATION section 3 rule 2); the result is redone on return.
    private fun openToolsSettings() {
        lifecycleScope.launch {
            settingsReturnSnapshot = flowManager.currentLanguageSnapshot()
            startActivity(SettingsActivity.openToolsSectionIntent(this@CameraOcrTranslateActivity, SECTION_TRANSLATION))
        }
    }

    override fun onResumeWithViews() {
        super.onResumeWithViews()
        settingsReturnSnapshot?.let { before ->
            settingsReturnSnapshot = null
            flowManager.reapplyAfterSettingsReturn(before)
        }
    }

    private fun showLanguagePicker(
        requestKey: String,
        selectedCode: String,
        mode: SearchableLanguagePickerDialog.Mode,
        interfaceLanguage: String
    ) {
        val tag = "${SearchableLanguagePickerDialog.TAG}_camera_${mode.name}"
        if (supportFragmentManager.findFragmentByTag(tag) != null) return
        SearchableLanguagePickerDialog.newInstance(
            selectedCode = selectedCode,
            mode = mode,
            interfaceLanguage = interfaceLanguage,
            requestKey = requestKey
        ).show(supportFragmentManager, tag)
    }

    private fun listenForLanguagePick(requestKey: String, onPicked: (String) -> Unit) {
        supportFragmentManager.setFragmentResultListener(requestKey, this) { _, bundle ->
            bundle.getString(SearchableLanguagePickerDialog.RESULT_LANGUAGE_CODE)?.let(onPicked)
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        flowManager.saveState(outState)
    }

    override fun onDestroy() {
        helpEntry?.release()
        helpEntry = null
        if (isFinishing) {
            flowManager.cleanup()
        }
        super.onDestroy()
    }

    companion object {
        /** S1042: absolute path of an app-owned source image (e.g. staged screenshot) to OCR instead of capturing. */
        const val EXTRA_SOURCE_IMAGE_PATH = "source_image_path"

        // S1214: the crop step's two language pickers share this Activity's FragmentManager, so each
        // needs its own result key.
        private const val REQ_CROP_SOURCE_LANGUAGE = "camera_ocr_crop_source_language"
        private const val REQ_CROP_TARGET_LANGUAGE = "camera_ocr_crop_target_language"

        fun createIntent(context: Context): Intent {
            return Intent(context, CameraOcrTranslateActivity::class.java)
        }

        /** S1042: open the OCR/translate flow over an existing image at [sourceImagePath] (skips the camera). */
        fun createIntent(context: Context, sourceImagePath: String): Intent {
            return Intent(context, CameraOcrTranslateActivity::class.java)
                .putExtra(EXTRA_SOURCE_IMAGE_PATH, sourceImagePath)
        }
    }
}
