package com.sza.fastmediasorter.domain.ocr

import android.graphics.Bitmap

/**
 * Default [OfflineOcrEngine] for flavors without a bundled offline OCR backend (photos, lite).
 *
 * These flavors do not ship the Tesseract native stack, so the recognition graph still needs a
 * non-null default engine to satisfy [OfflineOcrEngineProvider]. Recognition simply yields no text
 * here instead of failing the Hilt graph or the build.
 */
class NoOpOfflineOcrEngine : OfflineOcrEngine {

    override suspend fun recognizeText(bitmap: Bitmap, languageCode: String): String? = null

    override suspend fun recognizeTextBlocks(bitmap: Bitmap, languageCode: String): List<OcrTextBlock>? = null

    override fun release() = Unit
}
