package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.data.delivery.DeliveredNativeLibraryIncompatibleException
import com.sza.fastmediasorter.data.delivery.DeliveredNativeLibraryLoader
import com.sza.fastmediasorter.data.delivery.DeliveredPayloadCorruptException
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.delivery.DeliverableSet
import com.sza.fastmediasorter.domain.ocr.OcrBlockFilter
import com.sza.fastmediasorter.domain.ocr.OcrDiscardRecorder
import com.sza.fastmediasorter.domain.ocr.OcrLineGeometry
import com.sza.fastmediasorter.domain.ocr.OfflineOcrEngineProvider
import com.sza.fastmediasorter.domain.repository.SettingsRepository
import com.sza.fastmediasorter.domain.stats.StatsEvent
import com.sza.fastmediasorter.domain.stats.StatsSink
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * OCR/recognition backend split out of [TranslationManager] (S0386 Phase 01). Owns the offline
 * OCR engine via [OfflineOcrEngineProvider] (Tesseract; PaddleOCR withdrawn in S1703).
 * ML Kit Text-Recognition is removed (S0386 Phase 05).
 */
class RecognitionBackend(
    private val context: Context,
    private val callback: TranslationManager.TranslationCallback,
    private val settingsRepository: SettingsRepository,
    private val offlineOcrEngineProvider: OfflineOcrEngineProvider,
    private val translation: TextTranslationFacade,
    private val capabilityRepository: DeliverableCapabilityRepository,
    private val libraryLoader: DeliveredNativeLibraryLoader,
    private val statsSink: StatsSink,
    // S1712: the discard channel. Off by default, and while it is off this costs one flag comparison
    // per fragment and allocates nothing.
    private val discardRecorder: OcrDiscardRecorder = OcrDiscardRecorder(),
) : TextRecognitionFacade {

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

        try {
            libraryLoader.load(DeliverableSet.OCR_ENGINES)
        } catch (e: DeliveredPayloadCorruptException) {
            if (e.reason.contains("payload file missing")) {
                // S1703: a partial OCR payload now means Tesseract's own members (.so plus
                // .traineddata) are incomplete on disk. Whatever loaded before the first missing
                // file was injected already; the loader enqueued async uninstall of the partial
                // set and the re-download prompt fires on the next enable-time check via
                // DeliveryEnableInterceptor - so this operation proceeds with the loaded engine.
                Timber.i("OCR engines payload partially missing; proceeding with the loaded engine")
            } else {
                Timber.e(e, "Failed to load OCR engines native libraries")
                callback.showError(context.getString(R.string.ocr_engines_damaged))
                return null
            }
        } catch (e: DeliveredNativeLibraryIncompatibleException) {
            Timber.i("OCR engines not loadable on this device - recognition unavailable")
            return null
        } catch (e: Exception) {
            Timber.e(e, "Failed to load OCR engines native libraries")
            callback.showError(context.getString(R.string.ocr_error))
            return null
        }

        val settings = settingsRepository.getSettings().first()
        val tessLang = mlKitToTesseractLang(sourceLangCode)
        val ocrEngine = offlineOcrEngineProvider.engineFor(settings, sourceLangCode)
        Timber.d("TranslationManager.recognizeText: Trying offline OCR engine=${settings.ocrEngineType} for $sourceLangCode")
        val ocrResult = offlineOcrEngineProvider.recognizeTextWithFallback(settings, bitmap, sourceLangCode, tessLang, ocrEngine)
        if (!ocrResult.isNullOrBlank()) {
            val cleanedText = cleanOcrText(ocrResult)
            statsSink.record(StatsEvent.OcrScan)
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

        try {
            libraryLoader.load(DeliverableSet.OCR_ENGINES)
        } catch (e: DeliveredPayloadCorruptException) {
            if (e.reason.contains("payload file missing")) {
                // S1703: partial payload = incomplete Tesseract member set; proceed as above.
                Timber.i("OCR engines payload partially missing; proceeding with the loaded engine for block recognition")
            } else {
                Timber.e(e, "Failed to load OCR engines native libraries")
                callback.showError(context.getString(R.string.ocr_engines_damaged))
                return null
            }
        } catch (e: DeliveredNativeLibraryIncompatibleException) {
            Timber.i("OCR engines not loadable on this device - block recognition unavailable")
            return null
        } catch (e: Exception) {
            Timber.e(e, "Failed to load OCR engines native libraries")
            callback.showError(context.getString(R.string.ocr_error))
            return null
        }

        val settings = settingsRepository.getSettings().first()
        val tessLang = mlKitToTesseractLang(sourceLang)
        val ocrEngine = offlineOcrEngineProvider.engineFor(settings, sourceLang)
        val ocrBlocks = offlineOcrEngineProvider.recognizeTextBlocksWithFallback(settings, bitmap, sourceLang, tessLang, ocrEngine)

        if (!ocrBlocks.isNullOrEmpty()) {
            // S1712: the four thresholds live in OcrBlockFilter now, so the reason a fragment was
            // dropped survives the decision instead of collapsing into a boolean. The recorder reads that
            // same verdict - one function, two readers - and stays silent while its channel is off.
            discardRecorder.beginRun()
            val filteredBlocks = ocrBlocks.filter { block ->
                val verdict = OcrBlockFilter.evaluate(block)
                discardRecorder.record(block, verdict)
                verdict == OcrBlockFilter.Verdict.ACCEPTED
            }

            Timber.d("S1711: applying word-level line geometry to the recognised blocks")
            val translatedBlocks = mutableListOf<TranslationManager.TranslatedTextBlock>()
            for (block in filteredBlocks) {
                val translatedText = translation.translate(block.text, sourceLang, targetLang)
                if (translatedText != null) {
                    translatedBlocks.add(
                        TranslationManager.TranslatedTextBlock(
                            originalText = block.text,
                            translatedText = translatedText,
                            // S1711: the box drops the artifact words and the type size comes from the
                            // median of the real ones - both together, because fixing either alone was
                            // measured to make the overlay worse.
                            boundingBox = OcrLineGeometry.tightenedBounds(block),
                            confidence = block.confidence,
                            typeSizePx = OcrLineGeometry.typeSizePx(block)
                        )
                    )
                }
            }
            if (translatedBlocks.isNotEmpty()) {
                statsSink.record(StatsEvent.OcrScan)
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

        try {
            libraryLoader.load(DeliverableSet.OCR_ENGINES)
        } catch (e: DeliveredPayloadCorruptException) {
            if (e.reason.contains("payload file missing")) {
                // S1703: partial payload = incomplete Tesseract member set; proceed as above.
                Timber.i("OCR engines payload partially missing; proceeding with the loaded engine for word selection")
            } else {
                Timber.e(e, "Failed to load OCR engines native libraries")
                return null
            }
        } catch (e: DeliveredNativeLibraryIncompatibleException) {
            Timber.i("OCR engines not loadable on this device - selection recognition unavailable")
            return null
        } catch (e: Exception) {
            Timber.e(e, "Failed to load OCR engines native libraries")
            return null
        }

        val settings = settingsRepository.getSettings().first()
        val tessLang = "eng"
        val ocrEngine = offlineOcrEngineProvider.engineFor(settings, "en")
        val ocrBlocks = ocrEngine.recognizeTextBlocks(bitmap, tessLang) ?: return null

        val words = mutableListOf<TranslationManager.TranslatedTextBlock>()
        for (block in ocrBlocks) {
            words.add(
                TranslationManager.TranslatedTextBlock(
                    originalText = block.text,
                    translatedText = "",
                    boundingBox = OcrLineGeometry.tightenedBounds(block),
                    confidence = block.confidence,
                    typeSizePx = OcrLineGeometry.typeSizePx(block)
                )
            )
        }
        return words.ifEmpty { null }
    }

    override fun release() {
        offlineOcrEngineProvider.release()
    }
}
