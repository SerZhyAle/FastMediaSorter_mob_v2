package com.sza.fastmediasorter.ui.player.helpers

/**
 * Translation surface split out of [TranslationManager] (S0386 Phase 01).
 *
 * Owns ML Kit translate + language identification only; no OCR. Enables delivering the
 * translation engine independently of the OCR engines (strategic spec §5.4 Set A vs Set B).
 */
interface TextTranslationFacade {

    suspend fun translate(text: String, sourceLang: String = "en", targetLang: String = "ru"): String?

    suspend fun detectLanguage(text: String): String

    suspend fun getTargetLanguageCode(): String?

    fun release()
}
