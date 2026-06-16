package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.delivery.DeliveredNativeLibraryLoader
import com.sza.fastmediasorter.data.delivery.DeliveredPayloadCorruptException
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.ocr.OfflineOcrEngineProvider
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * OCR/recognition backend split out of [TranslationManager] (S0386 Phase 01). Owns the offline
 * OCR engines via [OfflineOcrEngineProvider] (Tesseract default, PaddleOCR in noLegal).
 * ML Kit Text-Recognition is removed (S0386 Phase 05).
 */
class RecognitionBackend(
    private val context: Context,
    private val callback: TranslationManager.TranslationCallback,
    private val settingsRepository: SettingsRepository,
    private val offlineOcrEngineProvider: OfflineOcrEngineProvider,
    private val translation: TextTranslationFacade,
    private val capabilityRepository: DeliverableCapabilityRepository,
    private val libraryLoader: DeliveredNativeLibraryLoader
) : TextRecognizationFacade {

    // Languages that use Cyrillic script.
    private val cyrillicLanguages = setOf("ru", "uk", "bg", "be", "mk")

    private fun ocrEnginesInstalled(): Boolean =
        capabilityRepository.isInstalledBlocking(DeliverableSet.OCR_ENGINES)

    /** ML Kit 2-letter codes to Tesseract 3-letter codes; fallback is English. */
    private fun mlKitToTesseractLang(mlKitLang: String): String {
        return when (mlKitLang) {
            "ru" -> "rus"
            "uk" -> "ukr"
            "bg" -> "bul"
            "be" -> "bel"
            "en" -> "eng"
            "auto" -> "eng"
            else -> "eng"
        }
    }

    private fun cleanOcrText(text: String): String = TranslationTextUtils.cleanOcrText(text)

    override suspend fun extractTextOnly(
        bitmap: Bitmap,
        sourceLang: String
    ): String? {
        try {
            val extractedText = recognizeText(bitmap, sourceLang)
            if (extractedText.isNullOrBlank()) {
                Timber.d("No text detected in image")
                return null
            }
            Timber.d("Extracted text, total ${extractedText.length} characters")
            return extractedText
        } catch (e: Exception) {
            Timber.e(e, "OCR extraction error")
            callback.showError(context.getString(R.string.ocr_error))
            return null
        }
    }

    override suspend fun recognizeText(bitmap: Bitmap, sourceLangCode: String): String? {
        if (!ocrEnginesInstalled()) {
            Timber.i("OCR engines not installed - recognition unavailable")
            return null
        }

        var paddlePayloadMissing = false
        try {
            libraryLoader.load(DeliverableSet.OCR_ENGINES)
        } catch (e: DeliveredPayloadCorruptException) {
            if (e.reason.contains("payload file missing")) {
                // Tesseract .so was already injected by the loader before the first missing Paddle
                // file was detected - fall back to Tesseract for this operation. The loader already
                // enqueued async uninstall of the partial set; re-download prompt fires on the next
                // enable-time check via DeliveryEnableInterceptor.
                Timber.d("S0461: recognizeText Tesseract fallback - Paddle payload partially missing")
                Timber.i("OCR engines payload partially missing; falling back to Tesseract")
                paddlePayloadMissing = true
            } else {
                Timber.e(e, "Failed to load OCR engines native libraries")
                callback.showError(context.getString(R.string.ocr_engines_damaged))
                return null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load OCR engines native libraries")
            callback.showError(context.getString(R.string.ocr_error))
            return null
        }

        val settings = settingsRepository.getSettings().first()
        val tessLang = mlKitToTesseractLang(sourceLangCode)
        val ocrEngine = if (paddlePayloadMissing) offlineOcrEngineProvider.defaultEngine
                        else offlineOcrEngineProvider.engineFor(settings, sourceLangCode)
        Timber.d("TranslationManager.recognizeText: Trying offline OCR engine=${settings.ocrEngineType} for $sourceLangCode")
        val ocrResult = offlineOcrEngineProvider.recognizeTextWithFallback(settings, bitmap, sourceLangCode, tessLang, ocrEngine)
        if (!ocrResult.isNullOrBlank()) {
            val cleanedText = cleanOcrText(ocrResult)
            return cleanedText
        }
        return null
    }

    suspend fun recognizeAndTranslateBlocks(
        bitmap: Bitmap,
        sourceLang: String,
        targetLang: String
    ): List<TranslationManager.TranslatedTextBlock>? {
        if (!ocrEnginesInstalled()) {
            Timber.i("OCR engines not installed - recognition unavailable")
            return null
        }

        var paddlePayloadMissing = false
        try {
            libraryLoader.load(DeliverableSet.OCR_ENGINES)
        } catch (e: DeliveredPayloadCorruptException) {
            if (e.reason.contains("payload file missing")) {
                Timber.d("S0461: recognizeAndTranslateBlocks Tesseract fallback - Paddle payload partially missing")
                Timber.i("OCR engines payload partially missing; falling back to Tesseract for block recognition")
                paddlePayloadMissing = true
            } else {
                Timber.e(e, "Failed to load OCR engines native libraries")
                callback.showError(context.getString(R.string.ocr_engines_damaged))
                return null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load OCR engines native libraries")
            callback.showError(context.getString(R.string.ocr_error))
            return null
        }

        val settings = settingsRepository.getSettings().first()
        val tessLang = mlKitToTesseractLang(sourceLang)
        val ocrEngine = if (paddlePayloadMissing) offlineOcrEngineProvider.defaultEngine
                        else offlineOcrEngineProvider.engineFor(settings, sourceLang)
        val ocrBlocks = offlineOcrEngineProvider.recognizeTextBlocksWithFallback(settings, bitmap, sourceLang, tessLang, ocrEngine)

        if (!ocrBlocks.isNullOrEmpty()) {
            val filteredBlocks = ocrBlocks.filter { block ->
                if (block.confidence < 30f) return@filter false
                if (block.text.trim().length < 3) return@filter false

                val letters = block.text.count { it.isLetter() }
                val specialChars = block.text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
                val ratio = if (letters > 0) specialChars.toFloat() / letters else Float.MAX_VALUE
                if (ratio > 0.5f) return@filter false

                val boxWidth = block.boundingBox.width()
                val boxHeight = block.boundingBox.height()
                if (boxWidth < 20 || boxHeight < 10) return@filter false

                true
            }

            val translatedBlocks = mutableListOf<TranslationManager.TranslatedTextBlock>()
            for (block in filteredBlocks) {
                val translatedText = translation.translate(block.text, sourceLang, targetLang)
                if (translatedText != null) {
                    translatedBlocks.add(
                        TranslationManager.TranslatedTextBlock(
                            originalText = block.text,
                            translatedText = translatedText,
                            boundingBox = block.boundingBox,
                            confidence = block.confidence
                        )
                    )
                }
            }
            if (translatedBlocks.isNotEmpty()) {
                return translatedBlocks
            }
        }
        return null
    }

    override suspend fun recognizeTextBlocksForSelection(bitmap: Bitmap): List<TranslationManager.TranslatedTextBlock>? {
        if (!ocrEnginesInstalled()) {
            Timber.i("OCR engines not installed - selection recognition unavailable")
            return null
        }

        var paddlePayloadMissing = false
        try {
            libraryLoader.load(DeliverableSet.OCR_ENGINES)
        } catch (e: DeliveredPayloadCorruptException) {
            if (e.reason.contains("payload file missing")) {
                Timber.d("S0461: recognizeTextBlocksForSelection Tesseract fallback - Paddle payload partially missing")
                Timber.i("OCR engines payload partially missing; running Tesseract for word selection")
                paddlePayloadMissing = true
            } else {
                Timber.e(e, "Failed to load OCR engines native libraries")
                return null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load OCR engines native libraries")
            return null
        }

        val settings = settingsRepository.getSettings().first()
        val tessLang = "eng"
        val ocrEngine = if (paddlePayloadMissing) offlineOcrEngineProvider.defaultEngine
                        else offlineOcrEngineProvider.engineFor(settings, "en")
        val ocrBlocks = ocrEngine.recognizeTextBlocks(bitmap, tessLang) ?: return null

        val words = mutableListOf<TranslationManager.TranslatedTextBlock>()
        for (block in ocrBlocks) {
            words.add(
                TranslationManager.TranslatedTextBlock(
                    originalText = block.text,
                    translatedText = "",
                    boundingBox = block.boundingBox,
                    confidence = block.confidence
                )
            )
        }
        return words.ifEmpty { null }
    }

    override fun release() {
        offlineOcrEngineProvider.release()
    }
}
