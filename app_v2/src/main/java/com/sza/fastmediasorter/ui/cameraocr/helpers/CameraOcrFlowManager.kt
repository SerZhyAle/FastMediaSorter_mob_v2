package com.sza.fastmediasorter.ui.cameraocr.helpers

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.annotation.StringRes
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orchestrates the Camera-OCR-Translate flow end to end: capture preparation, photo persistence,
 * OCR/translation and `.txt` export. Owns the transient flow state (current timestamp, temp file,
 * recognized/translated text, OCR-only mode) and drives the UI exclusively through [Callback].
 *
 * The Activity keeps only view binding, click wiring and the [androidx.activity.result] launcher;
 * all business logic lives here so the UI layer stays logic-free (Strict Rule 3).
 */
class CameraOcrFlowManager(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val storageManager: CameraOcrStorageManager,
    private val translationManager: TranslationManager,
    private val callback: Callback
) {

    /** UI surface the flow renders onto. Implemented by the Activity. */
    interface Callback {
        /** Launch the system camera with the prepared capture [intent]. */
        fun launchCamera(intent: Intent)

        /** Show the loading state. A `0` resource id means "no text". */
        fun showLoading(@StringRes statusRes: Int, @StringRes subStatusRes: Int)

        fun hideLoading()

        /** Render the result surface: translation on top (unless [ocrOnly]) and original below. */
        fun showResults(original: String, translation: String, ocrOnly: Boolean)

        fun showEmpty()

        fun showToast(@StringRes messageRes: Int)

        fun showSaveSuccess(path: String)

        /** Terminate the flow (no usable result, fatal capture error). */
        fun finishFlow()
    }

    private var pendingTempFile: File? = null
    private var currentTimestamp: String? = null
    private var recognizedOriginalText: String = ""
    private var translatedOutputText: String = ""
    private var ocrOnlyActive: Boolean = false

    fun setOcrOnlyActive(value: Boolean) {
        ocrOnlyActive = value
    }

    private fun hasResults(): Boolean =
        recognizedOriginalText.isNotEmpty() || translatedOutputText.isNotEmpty()

    fun startCapture() {
        storageManager.cleanupTempFile(pendingTempFile)
        pendingTempFile = null

        if (!storageManager.isCameraAvailable()) {
            callback.showToast(R.string.camera_ocr_camera_error)
            callback.finishFlow()
            return
        }

        val timestamp = newTimestamp()
        currentTimestamp = timestamp

        val tempFile = storageManager.createTempPhotoFile(timestamp)
        if (tempFile == null) {
            callback.showToast(R.string.camera_ocr_camera_error)
            callback.finishFlow()
            return
        }
        pendingTempFile = tempFile

        val uri = storageManager.buildCaptureUri(tempFile)
        if (uri == null) {
            storageManager.cleanupTempFile(tempFile)
            pendingTempFile = null
            callback.showToast(R.string.camera_ocr_camera_error)
            callback.finishFlow()
            return
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
        }
        callback.launchCamera(intent)
    }

    /** Called by the Activity when [launchCamera] threw. */
    fun onCaptureLaunchFailed() {
        storageManager.cleanupTempFile(pendingTempFile)
        pendingTempFile = null
        callback.showToast(R.string.camera_ocr_camera_error)
        if (!hasResults()) {
            callback.finishFlow()
        }
    }

    /** Called by the Activity when the camera returned without RESULT_OK. */
    fun onCaptureCancelled() {
        storageManager.cleanupTempFile(pendingTempFile)
        pendingTempFile = null
        if (!hasResults()) {
            callback.finishFlow()
        }
    }

    /** Called by the Activity when the camera returned RESULT_OK. */
    fun onPhotoCaptured() {
        val tempFile = pendingTempFile ?: return
        callback.showLoading(R.string.camera_ocr_loading_processing, 0)

        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                try {
                    BitmapFactory.decodeFile(tempFile.absolutePath)
                } catch (e: Exception) {
                    Timber.e(e, "CameraOcrFlowManager: Decode bitmap failed")
                    null
                }
            }

            if (bitmap == null) {
                callback.hideLoading()
                callback.showToast(R.string.camera_ocr_camera_error)
                storageManager.cleanupTempFile(pendingTempFile)
                pendingTempFile = null
                return@launch
            }

            val timestamp = currentTimestamp ?: newTimestamp()
            if (!storageManager.savePhotoToGallery(tempFile, timestamp)) {
                Timber.w("CameraOcrFlowManager: Photo could not be saved to gallery")
            }

            val settings = settingsRepository.getSettings().first()
            val sourceLang = settings.translationSourceLanguage
            val targetLang = settings.translationTargetLanguage
            val isOcrOnly = settings.cameraOcrOnly

            try {
                if (isOcrOnly) {
                    callback.showLoading(R.string.camera_ocr_loading_saving, 0)
                    val ocrText = translationManager.extractTextOnly(bitmap, sourceLang)
                    if (ocrText.isNullOrBlank()) {
                        callback.showEmpty()
                    } else {
                        applyResults(ocrText, "")
                    }
                } else {
                    callback.showLoading(R.string.camera_ocr_loading_saving, R.string.please_wait)
                    val result = translationManager.recognizeAndTranslate(
                        bitmap = bitmap,
                        sourceLang = TranslationManager.languageCodeToMLKit(sourceLang),
                        targetLang = TranslationManager.languageCodeToMLKit(targetLang)
                    )
                    if (result == null || result.first.isBlank()) {
                        callback.showEmpty()
                    } else {
                        applyResults(result.first, result.second)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "CameraOcrFlowManager: OCR/Translation failed")
                callback.hideLoading()
                callback.showToast(R.string.camera_ocr_engine_error)
            } finally {
                storageManager.cleanupTempFile(pendingTempFile)
                pendingTempFile = null
            }
        }
    }

    fun exportTxt() {
        if (recognizedOriginalText.isEmpty()) {
            return
        }
        val timestamp = currentTimestamp ?: newTimestamp()
        scope.launch {
            val path = storageManager.exportResultToTxt(
                timestamp = timestamp,
                originalText = recognizedOriginalText,
                translationText = translatedOutputText,
                ocrOnly = ocrOnlyActive
            )
            if (path != null) {
                callback.showSaveSuccess(path)
            } else {
                callback.showToast(R.string.camera_ocr_save_error)
            }
        }
    }

    /** Persists the dialog choices as global settings and re-renders the current result. */
    fun applyLanguageSettings(sourceLang: String, targetLang: String, ocrOnly: Boolean) {
        scope.launch {
            val current = settingsRepository.getSettings().first()
            settingsRepository.updateSettings(
                current.copy(
                    translationSourceLanguage = sourceLang,
                    translationTargetLanguage = targetLang,
                    cameraOcrOnly = ocrOnly
                )
            )
            ocrOnlyActive = ocrOnly
            callback.showResults(recognizedOriginalText, translatedOutputText, ocrOnly)
        }
    }

    fun cleanup() {
        storageManager.cleanupTempFile(pendingTempFile)
        pendingTempFile = null
    }

    private fun applyResults(original: String, translation: String) {
        recognizedOriginalText = original
        translatedOutputText = translation
        callback.showResults(original, translation, ocrOnlyActive)
    }

    private fun newTimestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
