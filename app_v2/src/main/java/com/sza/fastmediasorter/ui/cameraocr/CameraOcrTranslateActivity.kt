package com.sza.fastmediasorter.ui.cameraocr

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.core.ui.BaseActivity
import com.sza.fastmediasorter.databinding.ActivityCameraOcrTranslateBinding
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import com.sza.fastmediasorter.utils.collectOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class CameraOcrTranslateActivity : BaseActivity<ActivityCameraOcrTranslateBinding>() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private lateinit var translationManager: TranslationManager
    private var pendingTempFile: File? = null
    private var currentPhotoTimestamp: String? = null
    private var recognizedOriginalText: String = ""
    private var translatedOutputText: String = ""
    private var isOcrOnlyActive: Boolean = false

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            processCapturedPhoto()
        } else {
            Timber.i("CameraOcrTranslateActivity: Camera capture cancelled or failed")
            cleanupTempFile()
            if (recognizedOriginalText.isEmpty() && translatedOutputText.isEmpty()) {
                finish()
            }
        }
    }

    override fun getViewBinding(): ActivityCameraOcrTranslateBinding {
        return ActivityCameraOcrTranslateBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        translationManager = TranslationManager(
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

        // Automatically launch camera on startup if we don't have results yet
        if (savedInstanceState == null) {
            launchCameraCapture()
        }
    }

    override fun setupViews() {
        binding.btnSaveTxt.setOnClickListener {
            exportResultsToTxt()
        }

        binding.btnNextPhoto.setOnClickListener {
            launchCameraCapture()
        }

        binding.btnEmptyRetry.setOnClickListener {
            launchCameraCapture()
        }

        binding.btnClose.setOnClickListener {
            finish()
        }

        binding.btnEmptyClose.setOnClickListener {
            finish()
        }

        binding.btnSettings.setOnClickListener {
            showCompactSettingsDialog()
        }
    }

    override fun observeData() {
        collectOnLifecycle(settingsRepository.getSettings()) { settings ->
            isOcrOnlyActive = settings.cameraOcrOnly
        }
    }

    private fun launchCameraCapture() {
        cleanupTempFile()

        val probeIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val handlers = packageManager.queryIntentActivities(probeIntent, 0)
        if (handlers.isEmpty()) {
            Toast.makeText(this, R.string.camera_ocr_camera_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentPhotoTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val tempFile = createTempPhotoFile(currentPhotoTimestamp!!)
        if (tempFile == null) {
            Toast.makeText(this, R.string.camera_ocr_camera_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        pendingTempFile = tempFile

        val uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", tempFile)
        } catch (e: Exception) {
            Timber.e(e, "CameraOcrTranslateActivity: FileProvider generation failed")
            Toast.makeText(this, R.string.camera_ocr_camera_error, Toast.LENGTH_LONG).show()
            tempFile.delete()
            pendingTempFile = null
            finish()
            return
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }

        try {
            cameraLauncher.launch(intent)
        } catch (e: Exception) {
            Timber.e(e, "CameraOcrTranslateActivity: Camera launch failed")
            Toast.makeText(this, R.string.camera_ocr_camera_error, Toast.LENGTH_LONG).show()
            tempFile.delete()
            pendingTempFile = null
            if (recognizedOriginalText.isEmpty() && translatedOutputText.isEmpty()) {
                finish()
            }
        }
    }

    private fun processCapturedPhoto() {
        val tempFile = pendingTempFile ?: return
        showLoadingState(true, getString(R.string.camera_ocr_loading_processing))

        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(tempFile.absolutePath)
                } catch (e: Exception) {
                    Timber.e(e, "CameraOcrTranslateActivity: Decode bitmap failed")
                    null
                }
            }

            if (bitmap == null) {
                withContext(Dispatchers.Main) {
                    showLoadingState(false)
                    Toast.makeText(
                        this@CameraOcrTranslateActivity,
                        R.string.camera_ocr_camera_error,
                        Toast.LENGTH_LONG
                    ).show()
                }
                cleanupTempFile()
                return@launch
            }

            // Save captured image to DCIM/Camera or fallback
            val savedSuccess = savePhotoToSystemGallery(tempFile, currentPhotoTimestamp!!)
            if (!savedSuccess) {
                Timber.w("CameraOcrTranslateActivity: Photo could not be saved to gallery")
            }

            // Run OCR / Translation orchestration
            val settings = settingsRepository.getSettings().first()
            val sourceLang = settings.translationSourceLanguage
            val targetLang = settings.translationTargetLanguage
            val isOcrOnly = settings.cameraOcrOnly

            try {
                if (isOcrOnly) {
                    showLoadingState(true, getString(R.string.camera_ocr_loading_saving), "")
                    val ocrText = translationManager.extractTextOnly(bitmap, sourceLang)
                    withContext(Dispatchers.Main) {
                        showLoadingState(false)
                        if (ocrText.isNullOrBlank()) {
                            showEmptyState(true)
                        } else {
                            showResults(ocrText, "")
                        }
                    }
                } else {
                    showLoadingState(true, getString(R.string.camera_ocr_loading_saving), getString(R.string.please_wait))
                    val result = translationManager.recognizeAndTranslate(
                        bitmap = bitmap,
                        sourceLang = TranslationManager.languageCodeToMLKit(sourceLang),
                        targetLang = TranslationManager.languageCodeToMLKit(targetLang)
                    )
                    withContext(Dispatchers.Main) {
                        showLoadingState(false)
                        if (result == null || result.first.isBlank()) {
                            showEmptyState(true)
                        } else {
                            showResults(result.first, result.second)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "CameraOcrTranslateActivity: OCR/Translation failed")
                withContext(Dispatchers.Main) {
                    showLoadingState(false)
                    Toast.makeText(
                        this@CameraOcrTranslateActivity,
                        R.string.camera_ocr_engine_error,
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                cleanupTempFile()
            }
        }
    }

    private fun showLoadingState(show: Boolean, status: String = "", subStatus: String = "") {
        binding.layoutLoading.isVisible = show
        if (show) {
            binding.tvLoadingStatus.text = status
            binding.tvLoadingSub.text = subStatus
            binding.tvLoadingSub.isVisible = subStatus.isNotEmpty()
            binding.layoutResultContent.isVisible = false
            binding.layoutEmptyState.isVisible = false
        }
    }

    private fun showEmptyState(show: Boolean) {
        binding.layoutEmptyState.isVisible = show
        binding.layoutResultContent.isVisible = !show
        binding.layoutLoading.isVisible = false
    }

    private fun showResults(original: String, translation: String) {
        recognizedOriginalText = original
        translatedOutputText = translation

        binding.layoutResultContent.isVisible = true
        binding.layoutEmptyState.isVisible = false
        binding.layoutLoading.isVisible = false

        binding.tvOriginalText.text = original

        if (isOcrOnlyActive || translation.isBlank()) {
            binding.cardTranslation.isVisible = false
            binding.tvOriginalHeader.text = getString(R.string.camera_ocr_pane_original)
        } else {
            binding.cardTranslation.isVisible = true
            binding.tvTranslation.text = translation
            binding.tvOriginalHeader.text = getString(R.string.camera_ocr_pane_original)
        }
    }

    private suspend fun savePhotoToSystemGallery(tempFile: File, timestamp: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val dcimDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    "Camera"
                )
                if (!dcimDir.exists()) {
                    dcimDir.mkdirs()
                }

                val targetFile = File(dcimDir, "OCR_IMG_$timestamp.jpg")
                tempFile.copyTo(targetFile, overwrite = true)

                // Trigger media scanner
                @Suppress("DEPRECATION")
                sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(targetFile)))
                Timber.i("CameraOcrTranslateActivity: Photo saved to DCIM/Camera: ${targetFile.absolutePath}")
                true
            } catch (e: Exception) {
                Timber.w(e, "CameraOcrTranslateActivity: Save to DCIM/Camera failed, trying Downloads fallback")
                try {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val targetFile = File(downloadDir, "OCR_IMG_$timestamp.jpg")
                    tempFile.copyTo(targetFile, overwrite = true)
                    @Suppress("DEPRECATION")
                    sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(targetFile)))
                    Timber.i("CameraOcrTranslateActivity: Photo saved to Downloads: ${targetFile.absolutePath}")
                    true
                } catch (ex: Exception) {
                    Timber.e(ex, "CameraOcrTranslateActivity: Save photo to gallery completely failed")
                    false
                }
            }
        }

    private fun exportResultsToTxt() {
        if (recognizedOriginalText.isEmpty()) return

        val timestamp = currentPhotoTimestamp ?: SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val txtFile = File(downloadDir, "OCR_TXT_$timestamp.txt")

                    FileOutputStream(txtFile).use { fos ->
                        val writer = fos.bufferedWriter()
                        if (isOcrOnlyActive || translatedOutputText.isEmpty()) {
                            writer.write(recognizedOriginalText)
                        } else {
                            writer.write("=== TRANSLATION ===\n")
                            writer.write(translatedOutputText)
                            writer.write("\n\n=== ORIGINAL ===\n")
                            writer.write(recognizedOriginalText)
                        }
                        writer.flush()
                    }
                    true
                } catch (e: IOException) {
                    Timber.e(e, "CameraOcrTranslateActivity: Exporting text file failed")
                    false
                }
            }

            if (success) {
                val pathStr = "Downloads/OCR_TXT_$timestamp.txt"
                Toast.makeText(
                    this@CameraOcrTranslateActivity,
                    getString(R.string.camera_ocr_save_success, pathStr),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@CameraOcrTranslateActivity,
                    R.string.camera_ocr_save_error,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showCompactSettingsDialog() {
        lifecycleScope.launch {
            val settings = settingsRepository.getSettings().first()

            val view = LayoutInflater.from(this@CameraOcrTranslateActivity)
                .inflate(R.layout.dialog_camera_ocr_settings, null)

            val spinnerSrc = view.findViewById<Spinner>(R.id.spinnerSourceLanguage)
            val spinnerTgt = view.findViewById<Spinner>(R.id.spinnerTargetLanguage)
            val cbOcrOnly = view.findViewById<CheckBox>(R.id.cbOcrOnly)

            // Setup language choices
            val interfaceLang = settings.language
            val srcList = TranslationManager.buildSourceLanguageList(interfaceLang)
            val tgtList = TranslationManager.buildTargetLanguageList(interfaceLang)

            val srcAdapter = ArrayAdapter(
                this@CameraOcrTranslateActivity,
                android.R.layout.simple_spinner_item,
                srcList.map { it.first }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinnerSrc.adapter = srcAdapter

            val tgtAdapter = ArrayAdapter(
                this@CameraOcrTranslateActivity,
                android.R.layout.simple_spinner_item,
                tgtList.map { it.first }
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            spinnerTgt.adapter = tgtAdapter

            // Select current settings
            val srcIndex = srcList.indexOfFirst { it.second == settings.translationSourceLanguage }.coerceAtLeast(0)
            spinnerSrc.setSelection(srcIndex)

            val tgtIndex = tgtList.indexOfFirst { it.second == settings.translationTargetLanguage }.coerceAtLeast(0)
            spinnerTgt.setSelection(tgtIndex)

            cbOcrOnly.isChecked = settings.cameraOcrOnly

            // Target language selection is disabled when OCR-only is active
            spinnerTgt.isEnabled = !settings.cameraOcrOnly
            cbOcrOnly.setOnCheckedChangeListener { _, isChecked ->
                spinnerTgt.isEnabled = !isChecked
            }

            AlertDialog.Builder(this@CameraOcrTranslateActivity)
                .setTitle(R.string.settings)
                .setView(view)
                .setPositiveButton(R.string.apply) { _, _ ->
                    val selectedSrc = srcList[spinnerSrc.selectedItemPosition].second
                    val selectedTgt = tgtList[spinnerTgt.selectedItemPosition].second
                    val selectedOcrOnly = cbOcrOnly.isChecked

                    lifecycleScope.launch {
                        settingsRepository.updateSettings(
                            settings.copy(
                                translationSourceLanguage = selectedSrc,
                                translationTargetLanguage = selectedTgt,
                                cameraOcrOnly = selectedOcrOnly
                            )
                        )
                        // Trigger immediate layout update with newly updated settings
                        isOcrOnlyActive = selectedOcrOnly
                        showResults(recognizedOriginalText, translatedOutputText)
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun createTempPhotoFile(timestamp: String): File? = try {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: filesDir
        File(dir, "CAP_$timestamp.jpg").also { it.createNewFile() }
    } catch (e: Exception) {
        Timber.e(e, "CameraOcrTranslateActivity: Create temp file failed")
        null
    }

    private fun cleanupTempFile() {
        pendingTempFile?.let {
            if (it.exists()) {
                it.delete()
            }
        }
        pendingTempFile = null
    }

    override fun onDestroy() {
        cleanupTempFile()
        super.onDestroy()
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, CameraOcrTranslateActivity::class.java)
        }
    }
}
