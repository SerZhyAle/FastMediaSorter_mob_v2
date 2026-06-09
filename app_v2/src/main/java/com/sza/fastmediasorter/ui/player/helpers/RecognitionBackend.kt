package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.ocr.OfflineOcrEngineProvider
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * OCR/recognition backend split out of [TranslationManager] (S0386 Phase 01). Owns the ML Kit
 * Latin recognizer and the offline OCR engines via [OfflineOcrEngineProvider]. Language
 * identification lives on the translation side, so the auto-detect Cyrillic-correction branch
 * calls [translation]. The combined recognize-and-translate-blocks flow stays here because its
 * offline→ML-Kit fallback is entangled with per-block translation. Behavior is identical to the
 * pre-split monolith.
 */
class RecognitionBackend(
    private val context: Context,
    private val callback: TranslationManager.TranslationCallback,
    private val settingsRepository: SettingsRepository,
    private val offlineOcrEngineProvider: OfflineOcrEngineProvider,
    private val translation: TextTranslationFacade,
    private val capabilityRepository: DeliverableCapabilityRepository
) : TextRecognizationFacade {

    // Languages that use Cyrillic script — drives Latin→Cyrillic correction after OCR.
    private val cyrillicLanguages = setOf("ru", "uk", "bg", "be", "mk")

    private fun ocrEnginesInstalled(): Boolean =
        capabilityRepository.isInstalledBlocking(DeliverableSet.OCR_ENGINES)

    // Single text recognizer - Latin script (ML Kit does not support Russian/Ukrainian).
    private var textRecognizer: TextRecognizer? = null

    /** ML Kit 2-letter codes to Tesseract 3-letter codes; Cyrillic fallback is Russian. */
    private fun mlKitToTesseractLang(mlKitLang: String): String {
        return when (mlKitLang) {
            "ru" -> "rus"
            "uk" -> "ukr"
            "bg" -> "bul"
            "be" -> "bel"
            "en" -> "eng"
            "auto" -> "rus"
            else -> "rus"
        }
    }

    private fun getTextRecognizer(): TextRecognizer {
        if (textRecognizer == null) {
            Timber.i("TranslationManager: Creating Latin text recognizer (only available option)")
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        return textRecognizer!!
    }

    private fun cleanOcrText(text: String): String = TranslationTextUtils.cleanOcrText(text)

    override suspend fun extractTextOnly(
        bitmap: Bitmap,
        sourceLang: String
    ): String? {
        try {
            // Use existing recognizeText method for OCR
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
        // Capability gate: OCR engines must be bundled or downloaded (S0386 Pillar A). The enable
        // point (Phase 06) prompts for download first; this is the defensive fallback.
        if (!ocrEnginesInstalled()) {
            Timber.i("OCR engines not installed - recognition unavailable")
            return null
        }
        // Try Tesseract for Cyrillic languages first, or if auto
        if (sourceLangCode in cyrillicLanguages || sourceLangCode == "auto") {
            val settings = settingsRepository.getSettings().first()
            val tessLang = mlKitToTesseractLang(sourceLangCode)
            val ocrEngine = offlineOcrEngineProvider.engineFor(settings, sourceLangCode)
            Timber.d("S0288: TranslationManager.recognizeText entered engine=${settings.ocrEngineType} source=$sourceLangCode")
            Timber.d("TranslationManager.recognizeText: Trying offline OCR engine=${settings.ocrEngineType} for $sourceLangCode")
            val ocrResult = offlineOcrEngineProvider.recognizeTextWithFallback(settings, bitmap, sourceLangCode, tessLang, ocrEngine)
            Timber.d("Offline OCR raw result: ${ocrResult?.take(100)} (length=${ocrResult?.length})")
            if (!ocrResult.isNullOrBlank()) {
                val cleanedText = cleanOcrText(ocrResult)
                Timber.d("Offline OCR recognition successful: ${cleanedText.length} chars (cleaned from ${ocrResult.length})")
                return cleanedText
            }
            Timber.w("Offline OCR failed or returned empty, falling back to ML Kit")
        }

        return try {
            // Note: ML Kit only supports Latin, Chinese, Japanese, Korean scripts.
            // Russian/Ukrainian are NOT supported - Latin recognizer may partially work.
            val recognizer = getTextRecognizer()
            Timber.d("TranslationManager.recognizeText: Using Latin recognizer (source=$sourceLangCode)")

            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            var text = result.text

            Timber.d("ML Kit raw result: ${text.take(100)} (length=${text.length})")

            if (text.isBlank()) {
                Timber.d("No text recognized from image")
                return null
            }

            // Post-process: convert visually similar Latin chars to Cyrillic for Russian/Ukrainian
            var conversionLang = sourceLangCode
            val shouldConvert = when (sourceLangCode) {
                in cyrillicLanguages -> {
                    true
                }
                "auto" -> {
                    val detectedLang = translation.detectLanguage(text)
                    if (detectedLang in cyrillicLanguages) {
                        conversionLang = detectedLang
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }

            if (shouldConvert) {
                val originalText = text
                text = TranslationManager.convertLatinToCyrillic(text, conversionLang)
                if (text != originalText) {
                    Timber.d("Applied Latin→Cyrillic conversion ($conversionLang): '${originalText.take(30)}...' → '${text.take(30)}...'")
                }
            }

            // Clean garbage symbols before returning
            val cleanedText = cleanOcrText(text)
            Timber.d("OCR text cleaned: ${text.length} chars → ${cleanedText.length} chars")
            cleanedText
        } catch (e: Exception) {
            Timber.e(e, "OCR error")
            callback.showError(context.getString(R.string.ocr_error))
            null
        }
    }

    /**
     * Google Lens style: extract text blocks with coordinates and translate each block via
     * [translation]. Returns translated blocks with bounding boxes for overlay rendering.
     * Kept here (not the coordinator) because the offline→ML-Kit fallback decision depends on
     * whether any block translated.
     */
    suspend fun recognizeAndTranslateBlocks(
        bitmap: Bitmap,
        sourceLang: String,
        targetLang: String
    ): List<TranslationManager.TranslatedTextBlock>? {
        if (!ocrEnginesInstalled()) {
            Timber.i("OCR engines not installed - recognition unavailable")
            return null
        }
        // Try Tesseract ONLY for Cyrillic source languages or auto-detect
        val shouldUseTesseract = sourceLang in cyrillicLanguages || sourceLang == "auto"

        if (shouldUseTesseract) {
            val settings = settingsRepository.getSettings().first()
            val tessLang = mlKitToTesseractLang(sourceLang)
            val ocrEngine = offlineOcrEngineProvider.engineFor(settings, sourceLang)
            Timber.d("S0288: TranslationManager.recognizeAndTranslateBlocks entered engine=${settings.ocrEngineType} source=$sourceLang target=$targetLang")
            Timber.d("TranslationManager.recognizeAndTranslateBlocks: Trying offline OCR engine=${settings.ocrEngineType} (source=$sourceLang, target=$targetLang)")
            val ocrBlocks = offlineOcrEngineProvider.recognizeTextBlocksWithFallback(settings, bitmap, sourceLang, tessLang, ocrEngine)

            if (!ocrBlocks.isNullOrEmpty()) {
                // Filter out low-quality blocks before translation
                val filteredBlocks = ocrBlocks.filter { block ->
                    if (block.confidence < 30f) {
                        Timber.d("Filtered block (low confidence ${block.confidence}): '${block.text.take(20)}...'")
                        return@filter false
                    }

                    if (block.text.trim().length < 3) {
                        Timber.d("Filtered block (too short): '${block.text}'")
                        return@filter false
                    }

                    val letters = block.text.count { it.isLetter() }
                    val specialChars = block.text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
                    val ratio = if (letters > 0) specialChars.toFloat() / letters else Float.MAX_VALUE
                    if (ratio > 0.5f) {
                        Timber.d("Filtered block (too many special chars, ratio=$ratio): '${block.text.take(20)}...'")
                        return@filter false
                    }

                    val boxWidth = block.boundingBox.width()
                    val boxHeight = block.boundingBox.height()
                    if (boxWidth < 20 || boxHeight < 10) {
                        Timber.d("Filtered block (box too small ${boxWidth}x${boxHeight}): '${block.text.take(20)}...'")
                        return@filter false
                    }

                    true
                }

                Timber.d("Offline OCR: ${ocrBlocks.size} raw blocks → ${filteredBlocks.size} after filtering")

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
                    Timber.d("Offline OCR block recognition successful: ${translatedBlocks.size} blocks")
                    return translatedBlocks
                }
            }
            Timber.w("Offline OCR failed or returned empty blocks, falling back to ML Kit")
        }

        return try {
            val recognizer = getTextRecognizer()
            Timber.d("TranslationManager.recognizeAndTranslateBlocks: Using Latin recognizer (source=$sourceLang)")

            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()

            if (result.textBlocks.isEmpty()) {
                Timber.d("No text blocks found in image")
                return null
            }

            val translatedBlocks = mutableListOf<TranslationManager.TranslatedTextBlock>()

            for (block in result.textBlocks) {
                var originalText = block.text
                if (originalText.isBlank()) continue

                val boundingBox = block.boundingBox ?: continue

                var conversionLang = sourceLang
                val shouldConvert = when (sourceLang) {
                    in cyrillicLanguages -> {
                        true
                    }
                    "auto" -> {
                        val detectedLang = translation.detectLanguage(originalText)
                        if (detectedLang in cyrillicLanguages) {
                            conversionLang = detectedLang
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }

                if (shouldConvert) {
                    val beforeConversion = originalText
                    originalText = TranslationManager.convertLatinToCyrillic(originalText, conversionLang)
                    if (originalText != beforeConversion) {
                        Timber.d("Block Latin→Cyrillic ($conversionLang): '${beforeConversion.take(20)}...' → '${originalText.take(20)}...'")
                    }
                }

                val translatedText = translation.translate(originalText, sourceLang, targetLang)
                if (translatedText == null) {
                    Timber.w("Translation failed for block: $originalText")
                    continue
                }

                translatedBlocks.add(
                    TranslationManager.TranslatedTextBlock(
                        originalText = originalText,
                        translatedText = translatedText,
                        boundingBox = boundingBox,
                        confidence = 1.0f // ML Kit TextBlock doesn't expose confidence directly
                    )
                )
            }

            Timber.d("Translated ${translatedBlocks.size} text blocks")
            translatedBlocks.ifEmpty { null }
        } catch (e: Exception) {
            Timber.e(e, "Error in recognizeAndTranslateBlocks")
            callback.showError(context.getString(R.string.translation_error))
            null
        }
    }

    override suspend fun recognizeTextBlocksForSelection(bitmap: Bitmap): List<TranslationManager.TranslatedTextBlock>? {
        if (!ocrEnginesInstalled()) {
            Timber.i("OCR engines not installed - selection recognition unavailable")
            return null
        }
        return try {
            val recognizer = getTextRecognizer()
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            if (result.textBlocks.isEmpty()) return null
            val words = mutableListOf<TranslationManager.TranslatedTextBlock>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val box = element.boundingBox ?: continue
                        val text = element.text
                        if (text.isBlank()) continue
                        words.add(
                            TranslationManager.TranslatedTextBlock(
                                originalText = text,
                                translatedText = "",
                                boundingBox = box,
                                confidence = 1.0f
                            )
                        )
                    }
                }
            }
            words.ifEmpty { null }
        } catch (e: Exception) {
            Timber.w(e, "recognizeTextBlocksForSelection failed")
            null
        }
    }

    override fun release() {
        textRecognizer?.close()
        textRecognizer = null

        offlineOcrEngineProvider.release()
    }
}
