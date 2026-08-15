package com.sza.fastmediasorter.ui.player.helpers

import android.graphics.Bitmap

/**
 * OCR/recognition surface split out of [TranslationManager] (S0386 Phase 01).
 *
 * Owns ML Kit text recognition plus the offline OCR engines (Tesseract/Paddle); no translation.
 * Enables delivering the OCR engines independently of the translation engine
 * (strategic spec §5.4 Set B vs Set A).
 *
 * S1667: until 2026-08-14 this type spelled "Recognition" with an extra syllable, a word English does
 * not have. The spell checker could only be kept quiet by allow-listing that spelling, which would then
 * have masked the same slip anywhere else in the tree. The type name is not persisted - no setting key,
 * no database name, no serialized field carries it - so the rename touched source only.
 */
interface TextRecognitionFacade {

    suspend fun extractTextOnly(bitmap: Bitmap, sourceLang: String = "en"): String?

    suspend fun recognizeText(bitmap: Bitmap, sourceLangCode: String = "auto"): String?

    suspend fun recognizeTextBlocksForSelection(bitmap: Bitmap): List<TranslationManager.TranslatedTextBlock>?

    fun release()
}
