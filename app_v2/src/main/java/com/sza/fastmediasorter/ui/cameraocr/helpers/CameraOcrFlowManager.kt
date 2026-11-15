package com.sza.fastmediasorter.ui.cameraocr.helpers

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.ui.cameracapture.CameraCaptureActivity
import com.sza.fastmediasorter.ui.cameracapture.model.CameraCaptureMode
import com.sza.fastmediasorter.ui.cameracapture.model.CameraScenario
import com.sza.fastmediasorter.ui.delivery.DeliveryEnableInterceptorEntryPoint
import com.sza.fastmediasorter.ui.player.helpers.TranslationManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Orchestrates the Camera-OCR-Translate flow end to end: capture preparation, photo persistence,
 * OCR/translation and `.txt` export. Owns the transient flow state (capture instant, temp file,
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
        /** Launch the prepared in-app capture Activity with the prepared [intent]. */
        fun launchCamera(intent: Intent)

        /** Show the crop step: preview [bitmap] with the draggable selection frame. */
        fun showCropStep(bitmap: Bitmap)

        /**
         * Render the crop-step language cluster: OCR [sourceCode] and translation [targetCode]
         * as flag-plus-code chips. When [translationAvailable] is false the direction arrow and
         * the target chip are hidden (only the OCR chip stays).
         */
        fun renderCropLanguages(sourceCode: String, targetCode: String, translationAvailable: Boolean)

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

    private val cropRegionManager = CropRegionManager()

    private var pendingTempFile: File? = null
    private var currentCaptureMillis: Long? = null
    private var recognizedOriginalText: String = ""
    private var translatedOutputText: String = ""
    private var ocrOnlyActive: Boolean = false
    private var orientedBitmap: Bitmap? = null

    fun setOcrOnlyActive(value: Boolean) {
        ocrOnlyActive = value
    }

    /** True when the OCR'd image is still retained and can be re-recognized after an OCR-language change. */
    fun hasRetainedSourceImage(): Boolean =
        orientedBitmap?.isRecycled == false

    private fun hasResults(): Boolean =
        recognizedOriginalText.isNotEmpty() || translatedOutputText.isNotEmpty()

    // S0386 Phase 06: camera-OCR is an OCR enable point - gate on the OCR_ENGINES set being
    // installed before launching capture; refusal closes the flow softly (no crash).
    fun startCapture() {
        val activity = callback as? FragmentActivity
        if (activity == null) {
            launchCaptureInternal()
            return
        }
        val interceptor = EntryPointAccessors.fromApplication(
            storageManager.contextForCaptureIntent().applicationContext,
            DeliveryEnableInterceptorEntryPoint::class.java
        ).deliveryEnableInterceptor()
        interceptor.requireInstalled(
            activity,
            DeliverableSet.OCR_ENGINES,
            onReady = ::launchCaptureInternal,
            onUnavailable = { callback.finishFlow() }
        )
    }

    private fun launchCaptureInternal() {
        scope.launch { prepareAndLaunchCapture() }
    }

    private suspend fun prepareAndLaunchCapture() {
        storageManager.cleanupTempFile(pendingTempFile)
        pendingTempFile = null

        val intent = if (storageManager.isCameraAvailable()) buildCaptureIntent() else null
        if (intent == null) {
            callback.showToast(R.string.camera_ocr_camera_error)
            callback.finishFlow()
        } else {
            callback.launchCamera(intent)
        }
    }

    private suspend fun buildCaptureIntent(): Intent? {
        val launchMillis = System.currentTimeMillis()
        currentCaptureMillis = launchMillis

        val tempFile = storageManager.createTempPhotoFile(launchMillis) ?: return null
        pendingTempFile = tempFile

        val uri = storageManager.buildCaptureUri(tempFile)
        return if (uri == null) {
            storageManager.cleanupTempFile(tempFile)
            pendingTempFile = null
            null
        } else {
            // In-app capture removes the OEM confirmation step before the crop screen. OCR is strictly
            // photo: pin the mode so the host never returns a video into the crop/translate flow (S0545).
            CameraCaptureActivity.createIntent(
                context = storageManager.contextForCaptureIntent(),
                outputUri = uri,
                outputPath = tempFile.absolutePath,
                mode = CameraCaptureMode.PHOTO,
                scenario = CameraScenario.OCR_TRANSLATE,
            )
        }
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

    /**
     * S1042: enter the flow from an already-captured image (e.g. a screenshot) instead of the camera.
     * Decodes [sourceFile] into the oriented working bitmap and jumps straight to the crop step,
     * reusing the identical crop -> language -> OCR/translate path. [sourceFile] is app-owned and is
     * deleted once decoded (the gallery only receives the cropped result, per S1042 owner input).
     */
    fun startWithImage(sourceFile: File) {
        callback.showLoading(R.string.camera_ocr_loading_processing, 0)
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                cropRegionManager.loadOrientedBitmap(sourceFile)
            }
            storageManager.cleanupTempFile(sourceFile)

            if (bitmap == null) {
                callback.hideLoading()
                callback.showToast(R.string.camera_ocr_camera_error)
                callback.finishFlow()
                return@launch
            }

            currentCaptureMillis = System.currentTimeMillis()
            recycleOrientedBitmap()
            orientedBitmap = bitmap
            callback.hideLoading()
            callback.showCropStep(bitmap)
            emitCropLanguages()
        }
    }

    /** Called by the Activity when the camera returned RESULT_OK. Shows the crop step. */
    fun onPhotoCaptured() {
        val tempFile = pendingTempFile ?: return
        // CAPTURE-OUTPUT rule 3: the name carries the moment the photo was taken, not the camera launch.
        currentCaptureMillis = System.currentTimeMillis()
        callback.showLoading(R.string.camera_ocr_loading_processing, 0)

        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                cropRegionManager.loadOrientedBitmap(tempFile)
            }
            // Temp capture file is no longer needed once decoded into an oriented bitmap.
            storageManager.cleanupTempFile(pendingTempFile)
            pendingTempFile = null

            if (bitmap == null) {
                callback.hideLoading()
                callback.showToast(R.string.camera_ocr_camera_error)
                return@launch
            }

            recycleOrientedBitmap()
            orientedBitmap = bitmap
            callback.hideLoading()
            callback.showCropStep(bitmap)
            emitCropLanguages()
        }
    }

    /** Reads the current OCR/translation languages and pushes them to the crop-step cluster. */
    private suspend fun emitCropLanguages() {
        val settings = settingsRepository.getSettings().first()
        // Translation availability inside this screen depends only on the global translation
        // master toggle and the per-capture OCR-only mode. The flavor capability gate is already
        // satisfied - this Activity only launches in translation-capable flavors (Rule 14: no
        // BuildConfig flavor guard in src/main).
        val translationAvailable = isTranslationAvailable(settings.enableTranslation, settings.cameraOcrOnly)
        callback.renderCropLanguages(
            sourceCode = settings.translationSourceLanguage,
            targetCode = settings.translationTargetLanguage,
            translationAvailable = translationAvailable
        )
    }

    /** Persists the chosen OCR source language to global settings and re-renders the cluster. */
    fun setCropSourceLanguage(code: String) {
        scope.launch {
            settingsRepository.updateSettings { it.copy(translationSourceLanguage = code) }
            emitCropLanguages()
        }
    }

    /** Persists the chosen translation target language to global settings and re-renders the cluster. */
    fun setCropTargetLanguage(code: String) {
        scope.launch {
            settingsRepository.updateSettings { it.copy(translationTargetLanguage = code) }
            emitCropLanguages()
        }
    }

    /**
     * Called by the Activity when the user confirms the crop step. When [frameTouched] is true the
     * selection [normalizedRect] is cropped out; otherwise the full captured frame is used. The
     * chosen image is saved to the gallery and sent to OCR/translation.
     */
    fun onCropConfirmed(normalizedRect: RectF?, frameTouched: Boolean) {
        val source = orientedBitmap ?: return
        callback.showLoading(R.string.camera_ocr_loading_processing, 0)

        scope.launch {
            val target = withContext(Dispatchers.IO) {
                if (frameTouched && normalizedRect != null) {
                    cropRegionManager.cropToNormalizedRect(source, normalizedRect)
                } else {
                    source
                }
            }
            // Drop the full-size source once a distinct cropped copy exists (memory guard).
            if (target != source) {
                source.recycle()
                orientedBitmap = target
            }

            val captureMillis = currentCaptureMillis ?: System.currentTimeMillis()
            if (!storageManager.saveBitmapToGallery(target, captureMillis)) {
                Timber.w("CameraOcrFlowManager: Image could not be saved to gallery")
            }

            runRecognition(target)
        }
    }

    /** Called by the Activity when the user taps Retry on the crop step. */
    fun onCropRetry() {
        // Leave the crop step before recycling: the preview ImageView still holds the displayed
        // bitmap, so any later draw (e.g. the close animation after a fast retry+close) would
        // crash with "Canvas: trying to use a recycled bitmap" (S3339).
        callback.showLoading(R.string.camera_ocr_loading_processing, 0)
        recycleOrientedBitmap()
        startCapture()
    }

    private suspend fun runRecognition(bitmap: Bitmap) {
        val settings = settingsRepository.getSettings().first()
        val sourceLang = settings.translationSourceLanguage
        val targetLang = settings.translationTargetLanguage
        val translationAvailable = isTranslationAvailable(settings.enableTranslation, settings.cameraOcrOnly)
        val isOcrOnly = !translationAvailable
        ocrOnlyActive = isOcrOnly

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
                // Pass the raw source code so "auto" stays auto: recognizeText detects the OCR script
                // and translate() detects the language of the recognized text and translates FROM it.
                // Mapping "auto" through languageCodeToMLKit collapses it to English (wrong source).
                val result = translationManager.recognizeAndTranslate(
                    bitmap = bitmap,
                    sourceLang = sourceLang,
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
        }
    }

    private fun recycleOrientedBitmap() {
        orientedBitmap?.let { if (!it.isRecycled) it.recycle() }
        orientedBitmap = null
    }

    fun exportTxt() {
        if (recognizedOriginalText.isEmpty()) {
            return
        }
        val captureMillis = currentCaptureMillis ?: System.currentTimeMillis()
        scope.launch {
            val path = storageManager.exportResultToTxt(
                captureMillis = captureMillis,
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

    /**
     * Persists the result-screen dialog choices (OCR source + translation target + OCR-only) as global
     * settings. When the OCR source language changed, re-runs recognition over the retained (cropped)
     * image with the new source language and re-translates - this lets the user fix a wrong source
     * language or auto-detection without re-capturing. When only the target changed, re-translates the
     * existing recognized text without a new OCR; otherwise just re-renders.
     */
    fun applyLanguageSettings(sourceLang: String, targetLang: String, ocrOnly: Boolean) {
        scope.launch {
            var previousSourceLang = sourceLang
            var translationEnabled = false
            settingsRepository.updateSettings { current ->
                previousSourceLang = current.translationSourceLanguage
                translationEnabled = current.enableTranslation
                current.copy(
                    translationSourceLanguage = sourceLang,
                    translationTargetLanguage = targetLang,
                    cameraOcrOnly = ocrOnly
                )
            }

            if (sourceLang != previousSourceLang) {
                val bitmap = orientedBitmap
                if (bitmap != null && !bitmap.isRecycled) {
                    // OCR language changed: re-run recognition over the retained image with the new
                    // source language. runRecognition reads the freshly-persisted settings and
                    // re-translates per the current target / OCR-only mode.
                    runRecognition(bitmap)
                }
                // If the retained image is gone (process death) keep the current results; the dialog
                // disables the OCR-language control in that state (hasRetainedSourceImage()).
                return@launch
            }

            val translationAvailable = isTranslationAvailable(translationEnabled, ocrOnly)
            ocrOnlyActive = !translationAvailable

            if (!translationAvailable || recognizedOriginalText.isBlank()) {
                callback.showResults(recognizedOriginalText, "", ocrOnlyActive)
                return@launch
            }

            callback.showLoading(R.string.camera_ocr_loading_saving, R.string.please_wait)
            // Pass the raw source code (e.g. "auto") so the translator auto-detects the language of
            // the already-recognized text and translates FROM it. Mapping "auto" through
            // languageCodeToMLKit would force English and mistranslate non-English captures.
            val retranslated = translationManager.translate(
                text = recognizedOriginalText,
                sourceLang = sourceLang,
                targetLang = TranslationManager.languageCodeToMLKit(targetLang)
            )
            callback.hideLoading()
            if (retranslated != null) {
                translatedOutputText = retranslated
            } else {
                callback.showToast(R.string.camera_ocr_engine_error)
            }
            callback.showResults(recognizedOriginalText, translatedOutputText, ocrOnly = false)
        }
    }

    private fun isTranslationAvailable(enableTranslation: Boolean, cameraOcrOnly: Boolean): Boolean {
        return enableTranslation && !cameraOcrOnly
    }

    fun cleanup() {
        storageManager.cleanupTempFile(pendingTempFile)
        pendingTempFile = null
        recycleOrientedBitmap()
    }

    fun saveState(outState: Bundle) {
        pendingTempFile?.absolutePath?.let { outState.putString(KEY_PENDING_TEMP_FILE, it) }
        currentCaptureMillis?.let { outState.putLong(KEY_CURRENT_CAPTURE_MILLIS, it) }
        outState.putString(KEY_RECOGNIZED_TEXT, recognizedOriginalText)
        outState.putString(KEY_TRANSLATED_TEXT, translatedOutputText)
        outState.putBoolean(KEY_OCR_ONLY_ACTIVE, ocrOnlyActive)

        val bitmap = orientedBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            val inCrop = !hasResults()
            outState.putBoolean(KEY_IN_CROP_STEP, inCrop)
            val cacheFile = File(
                storageManager.contextForCaptureIntent().cacheDir,
                "camera_ocr_retained_${System.currentTimeMillis()}.jpg"
            )
            try {
                java.io.FileOutputStream(cacheFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, RETAINED_BITMAP_JPEG_QUALITY, out)
                }
                outState.putString(KEY_WORKING_BITMAP_PATH, cacheFile.absolutePath)
            } catch (e: Exception) {
                Timber.w(e, "CameraOcrFlowManager: Failed to save working bitmap on state save")
            }
        }
    }

    fun restoreState(savedState: Bundle) {
        if (savedState.containsKey(KEY_CURRENT_CAPTURE_MILLIS)) {
            currentCaptureMillis = savedState.getLong(KEY_CURRENT_CAPTURE_MILLIS)
        }
        val pendingPath = savedState.getString(KEY_PENDING_TEMP_FILE)
        if (!pendingPath.isNullOrBlank()) {
            pendingTempFile = File(pendingPath)
        }
        recognizedOriginalText = savedState.getString(KEY_RECOGNIZED_TEXT).orEmpty()
        translatedOutputText = savedState.getString(KEY_TRANSLATED_TEXT).orEmpty()
        ocrOnlyActive = savedState.getBoolean(KEY_OCR_ONLY_ACTIVE, false)

        val workingPath = savedState.getString(KEY_WORKING_BITMAP_PATH)
        val inCropStep = savedState.getBoolean(KEY_IN_CROP_STEP, false)

        if (!workingPath.isNullOrBlank()) {
            val workingFile = File(workingPath)
            if (workingFile.exists()) {
                scope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        cropRegionManager.loadOrientedBitmap(workingFile)
                    }
                    storageManager.cleanupTempFile(workingFile)
                    if (bitmap != null) {
                        recycleOrientedBitmap()
                        orientedBitmap = bitmap
                        if (inCropStep) {
                            callback.showCropStep(bitmap)
                            emitCropLanguages()
                        }
                    }
                }
            }
        }

        if (hasResults()) {
            callback.showResults(recognizedOriginalText, translatedOutputText, ocrOnlyActive)
        }
    }

    private fun applyResults(original: String, translation: String) {
        recognizedOriginalText = original
        translatedOutputText = translation
        callback.showResults(original, translation, ocrOnlyActive)
    }

    companion object {
        private const val KEY_PENDING_TEMP_FILE = "camera_ocr_pending_temp_file"
        private const val KEY_CURRENT_CAPTURE_MILLIS = "camera_ocr_capture_millis"
        private const val KEY_RECOGNIZED_TEXT = "camera_ocr_recognized_text"
        private const val KEY_TRANSLATED_TEXT = "camera_ocr_translated_text"
        private const val KEY_OCR_ONLY_ACTIVE = "camera_ocr_only_active"
        private const val KEY_IN_CROP_STEP = "camera_ocr_in_crop_step"
        private const val KEY_WORKING_BITMAP_PATH = "camera_ocr_working_bitmap_path"
        private const val RETAINED_BITMAP_JPEG_QUALITY = 90
    }
}
