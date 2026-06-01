package com.sza.fastmediasorter.ui.cameraocr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
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
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
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
                            .setTitle(R.string.translation_started)
                            .setMessage(getString(R.string.please_wait))
                            .setPositiveButton(R.string.ok) { _, _ -> onConfirm() }
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
        binding.btnSaveTxt.setOnClickListener { flowManager.exportTxt() }
        binding.btnNextPhoto.setOnClickListener { flowManager.startCapture() }
        binding.btnEmptyRetry.setOnClickListener { flowManager.startCapture() }
        binding.btnClose.setOnClickListener { finish() }
        binding.btnEmptyClose.setOnClickListener { finish() }
        binding.btnSettings.setOnClickListener { showCompactSettingsDialog() }
    }

    override fun observeData() {
        collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            flowManager.setOcrOnlyActive(settings.cameraOcrOnly)
        }
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

    override fun showLoading(statusRes: Int, subStatusRes: Int) {
        binding.layoutLoading.isVisible = true
        binding.tvLoadingStatus.text = if (statusRes != 0) getString(statusRes) else ""
        val sub = if (subStatusRes != 0) getString(subStatusRes) else ""
        binding.tvLoadingSub.text = sub
        binding.tvLoadingSub.isVisible = sub.isNotEmpty()
        binding.layoutResultContent.isVisible = false
        binding.layoutEmptyState.isVisible = false
    }

    override fun hideLoading() {
        binding.layoutLoading.isVisible = false
    }

    override fun showResults(original: String, translation: String, ocrOnly: Boolean) {
        binding.layoutResultContent.isVisible = true
        binding.layoutEmptyState.isVisible = false
        binding.layoutLoading.isVisible = false

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

            val spinnerSrc = view.findViewById<Spinner>(R.id.spinnerSourceLanguage)
            val spinnerTgt = view.findViewById<Spinner>(R.id.spinnerTargetLanguage)
            val cbOcrOnly = view.findViewById<CheckBox>(R.id.cbOcrOnly)

            val interfaceLang = settings.language
            val srcList = TranslationManager.buildSourceLanguageList(interfaceLang)
            val tgtList = TranslationManager.buildTargetLanguageList(interfaceLang)

            spinnerSrc.adapter = ArrayAdapter(
                this@CameraOcrTranslateActivity,
                android.R.layout.simple_spinner_item,
                srcList.map { it.first }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            spinnerTgt.adapter = ArrayAdapter(
                this@CameraOcrTranslateActivity,
                android.R.layout.simple_spinner_item,
                tgtList.map { it.first }
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            spinnerSrc.setSelection(
                srcList.indexOfFirst { it.second == settings.translationSourceLanguage }.coerceAtLeast(0)
            )
            spinnerTgt.setSelection(
                tgtList.indexOfFirst { it.second == settings.translationTargetLanguage }.coerceAtLeast(0)
            )

            cbOcrOnly.isChecked = settings.cameraOcrOnly
            spinnerTgt.isEnabled = !settings.cameraOcrOnly
            cbOcrOnly.setOnCheckedChangeListener { _, isChecked ->
                spinnerTgt.isEnabled = !isChecked
            }

            AlertDialog.Builder(this@CameraOcrTranslateActivity)
                .setTitle(R.string.settings)
                .setView(view)
                .setPositiveButton(R.string.apply) { _, _ ->
                    flowManager.applyLanguageSettings(
                        sourceLang = srcList[spinnerSrc.selectedItemPosition].second,
                        targetLang = tgtList[spinnerTgt.selectedItemPosition].second,
                        ocrOnly = cbOcrOnly.isChecked
                    )
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
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
