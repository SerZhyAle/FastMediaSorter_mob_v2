package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap

/**
 * OCR/recognition surface split out of [TranslationManager] (S0386 Phase 01).
 *
 * Owns ML Kit text recognition plus the offline OCR engines (Tesseract/Paddle); no translation.
 * Enables delivering the OCR engines independently of the translation engine
 * (strategic spec §5.4 Set B vs Set A).
 */
interface TextRecognizationFacade {

    suspend fun extractTextOnly(bitmap: Bitmap, sourceLang: String = "en"): String?

    suspend fun recognizeText(bitmap: Bitmap, sourceLangCode: String = "auto"): String?

    suspend fun recognizeTextBlocksForSelection(bitmap: Bitmap): List<TranslationManager.TranslatedTextBlock>?

    fun release()
}
