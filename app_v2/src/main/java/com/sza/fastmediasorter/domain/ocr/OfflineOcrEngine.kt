package com.sza.fastmediasorter.domain.ocr

import android.graphics.Bitmap

/**
 * General offline OCR engine interface defining standard text recognition capabilities.
 */
interface OfflineOcrEngine {
    /**
     * Recognize raw text from the provided bitmap.
     * @param bitmap The image to process.
     * @param languageCode The language code (e.g. "rus", "eng").
     * @return Recognized text or null if failed.
     */
    suspend fun recognizeText(bitmap: Bitmap, languageCode: String): String?

    /**
     * Recognize and retrieve segmented text blocks with spatial coordinates and confidence.
     * @param bitmap The image to process.
     * @param languageCode The language code (e.g. "rus", "eng").
     * @return List of recognized text blocks or null if failed.
     */
    suspend fun recognizeTextBlocks(bitmap: Bitmap, languageCode: String): List<OcrTextBlock>?

    /**
     * Release resources held by the OCR engine.
     */
    fun release()
}
