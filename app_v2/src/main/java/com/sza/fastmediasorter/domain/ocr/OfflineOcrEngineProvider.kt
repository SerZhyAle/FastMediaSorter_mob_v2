package com.sza.fastmediasorter.domain.ocr

import android.graphics.Bitmap
import com.sza.fastmediasorter.domain.model.AppSettings
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineOcrEngineProvider @Inject constructor(
    private val defaultEngine: OfflineOcrEngine,
    private val contributors: Set<@JvmSuppressWildcards OcrEngineContributor>
) {

    fun engineFor(settings: AppSettings, sourceLangCode: String): OfflineOcrEngine {
        return contributors.firstOrNull { it.supports(settings, sourceLangCode) }?.engine ?: defaultEngine
    }

    suspend fun recognizeTextWithFallback(
        settings: AppSettings,
        bitmap: Bitmap,
        sourceLangCode: String,
        defaultLanguageCode: String,
        preferredEngine: OfflineOcrEngine = engineFor(settings, sourceLangCode)
    ): String? {
        val result = preferredEngine.recognizeText(
            bitmap,
            languageFor(preferredEngine, sourceLangCode, defaultLanguageCode)
        )
        if (!result.isNullOrBlank() || preferredEngine === defaultEngine) return result

        Timber.w("Selected OCR engine returned no text, falling back to default OCR")
        return defaultEngine.recognizeText(bitmap, defaultLanguageCode)
    }

    suspend fun recognizeTextBlocksWithFallback(
        settings: AppSettings,
        bitmap: Bitmap,
        sourceLangCode: String,
        defaultLanguageCode: String,
        preferredEngine: OfflineOcrEngine = engineFor(settings, sourceLangCode)
    ): List<OcrTextBlock>? {
        val result = preferredEngine.recognizeTextBlocks(
            bitmap,
            languageFor(preferredEngine, sourceLangCode, defaultLanguageCode)
        )
        if (!result.isNullOrEmpty() || preferredEngine === defaultEngine) return result

        Timber.w("Selected OCR engine returned no text blocks, falling back to default OCR")
        return defaultEngine.recognizeTextBlocks(bitmap, defaultLanguageCode)
    }

    fun release() {
        val released = mutableSetOf<Int>()
        fun releaseOnce(engine: OfflineOcrEngine) {
            if (released.add(System.identityHashCode(engine))) engine.release()
        }

        releaseOnce(defaultEngine)
        contributors.forEach { releaseOnce(it.engine) }
    }

    private fun languageFor(
        engine: OfflineOcrEngine,
        sourceLangCode: String,
        defaultLanguageCode: String
    ): String = if (engine === defaultEngine) defaultLanguageCode else sourceLangCode
}
