package com.sza.fastmediasorter.domain.ocr

import android.content.Context
import android.graphics.Bitmap
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.sza.fastmediasorter.ui.player.helpers.PaddleOcrModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class PaddleOcrEngine @Inject constructor(@ApplicationContext private val context: Context, private val paddleOcrModelManager: PaddleOcrModelManager) : OfflineOcrEngine {

    private var detector: PaddlePredictor? = null
    private var classifier: PaddlePredictor? = null
    private var recognizer: PaddlePredictor? = null
    private var activeVariant: PaddleOcrModelManager.ModelVariant? = null

    override suspend fun recognizeText(bitmap: Bitmap, languageCode: String): String? {
        return recognizeTextBlocks(bitmap, languageCode)
            ?.joinToString(separator = "\n") { it.text }
            ?.takeIf { it.isNotBlank() }
    }

    override suspend fun recognizeTextBlocks(
        bitmap: Bitmap,
        languageCode: String
    ): List<OcrTextBlock>? = withContext(Dispatchers.Default) {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            Timber.w("PaddleOCR skipped: bitmap is invalid")
            return@withContext null
        }

        val modelVariant = resolveModelVariant(languageCode)
        if (!ensureInitialized(modelVariant)) {
            Timber.w("PaddleOCR skipped: model initialization failed")
            return@withContext null
        }

        runCatching {
            val startedAt = System.currentTimeMillis()
            val detectorOk = runPredictor(detector, preprocess(bitmap, MAX_DET_SIDE))
            if (!detectorOk) {
                return@runCatching emptyList()
            }

            val classifierOk = runPredictor(classifier, preprocess(bitmap, CLS_WIDTH, CLS_HEIGHT))
            val recognizerOk = runPredictor(recognizer, preprocess(bitmap, REC_WIDTH, REC_HEIGHT))
            Timber.d(
                "PaddleOCR inference sequence finished: detector=$detectorOk, classifier=$classifierOk, " +
                    "recognizer=$recognizerOk, elapsedMs=${System.currentTimeMillis() - startedAt}"
            )

            postprocess(detector, classifier, recognizer)
        }.onFailure { error ->
            Timber.e(error, "PaddleOCR recognition failed")
        }.getOrNull()
    }

    override fun release() {
        detector = null
        classifier = null
        recognizer = null
        activeVariant = null
    }

    private suspend fun ensureInitialized(modelVariant: PaddleOcrModelManager.ModelVariant): Boolean {
        if (activeVariant == modelVariant && detector != null && classifier != null && recognizer != null) {
            return true
        }

        return withContext(Dispatchers.IO) {
            if (!paddleOcrModelManager.isModelInstalled(modelVariant)) {
                val downloaded = paddleOcrModelManager.downloadModel(modelVariant)
                if (!downloaded) return@withContext false
            }

            val modelFiles = paddleOcrModelManager.getModelFiles(modelVariant)
            detector = createPredictor(modelFiles.detector.absolutePath)
            classifier = createPredictor(modelFiles.classifier.absolutePath)
            recognizer = createPredictor(modelFiles.recognizer.absolutePath)
            activeVariant = modelVariant

            detector != null && classifier != null && recognizer != null
        }
    }

    private fun createPredictor(modelPath: String): PaddlePredictor? {
        // S0923: MobileConfig()/createPaddlePredictor trigger the PaddleLite native static initializer
        // (System.loadLibrary paddle_lite_jni). If the delivered .so is not name-resolvable on this
        // device it throws UnsatisfiedLinkError - a LinkageError, not an Exception - so guard it here and
        // degrade to init-failure (null) instead of crashing the process.
        return try {
            val config = MobileConfig().apply {
                setModelFromFile(modelPath)
                setThreads(PADDLE_THREADS)
                setPowerMode(PowerMode.LITE_POWER_HIGH)
            }
            PaddlePredictor.createPaddlePredictor(config)
        } catch (e: LinkageError) {
            Timber.w(e, "PaddleOCR native library unavailable on this device")
            null
        }
    }

    private fun runPredictor(predictor: PaddlePredictor?, prepared: PreparedTensor): Boolean {
        val input = predictor?.getInput(0) ?: return false
        input.resize(longArrayOf(1L, 3L, prepared.height.toLong(), prepared.width.toLong()))
        input.setData(prepared.data)
        return predictor.run()
    }

    private fun preprocess(bitmap: Bitmap, maxSide: Int): PreparedTensor {
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return preprocess(bitmap, targetWidth, targetHeight)
    }

    private fun preprocess(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): PreparedTensor {
        val scaled = if (targetWidth == bitmap.width && targetHeight == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        }

        val pixels = IntArray(targetWidth * targetHeight)
        scaled.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        val planeSize = targetWidth * targetHeight
        val data = FloatArray(planeSize * 3)
        pixels.forEachIndexed { index, pixel ->
            val red = (pixel shr 16 and 0xFF) / 255f
            val green = (pixel shr 8 and 0xFF) / 255f
            val blue = (pixel and 0xFF) / 255f

            data[index] = (blue - MEAN_BLUE) / STD_BLUE
            data[planeSize + index] = (green - MEAN_GREEN) / STD_GREEN
            data[planeSize * 2 + index] = (red - MEAN_RED) / STD_RED
        }

        return PreparedTensor(targetWidth, targetHeight, data)
    }

    private fun postprocess(
        detector: PaddlePredictor?,
        classifier: PaddlePredictor?,
        recognizer: PaddlePredictor?
    ): List<OcrTextBlock> {
        val detectorShape = detector?.getOutput(0)?.shape()?.joinToString(prefix = "[", postfix = "]") ?: "none"
        val classifierShape = classifier?.getOutput(0)?.shape()?.joinToString(prefix = "[", postfix = "]") ?: "none"
        val recognizerShape = recognizer?.getOutput(0)?.shape()?.joinToString(prefix = "[", postfix = "]") ?: "none"
        Timber.d("PaddleOCR output shapes: det=$detectorShape, cls=$classifierShape, rec=$recognizerShape")
        return emptyList()
    }

    private fun resolveModelVariant(languageCode: String): PaddleOcrModelManager.ModelVariant {
        val normalized = languageCode.lowercase()
        return if (normalized.contains("uk") || normalized.contains("be")) {
            PaddleOcrModelManager.ModelVariant.EAST_SLAVIC
        } else {
            PaddleOcrModelManager.ModelVariant.CYRILLIC
        }
    }

    private data class PreparedTensor(
        val width: Int,
        val height: Int,
        val data: FloatArray
    )

    companion object {
        private const val MAX_DET_SIDE = 960
        private const val CLS_WIDTH = 192
        private const val CLS_HEIGHT = 48
        private const val REC_WIDTH = 320
        private const val REC_HEIGHT = 48
        private const val PADDLE_THREADS = 4
        private const val MEAN_RED = 0.485f
        private const val MEAN_GREEN = 0.456f
        private const val MEAN_BLUE = 0.406f
        private const val STD_RED = 0.229f
        private const val STD_GREEN = 0.224f
        private const val STD_BLUE = 0.225f
    }
}
