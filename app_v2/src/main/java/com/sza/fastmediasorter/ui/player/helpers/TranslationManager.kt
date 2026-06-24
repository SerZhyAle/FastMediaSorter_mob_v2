package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.sza.fastmediasorter.data.delivery.DeliveredNativeLibraryLoader
import com.sza.fastmediasorter.domain.delivery.DeliverableCapabilityRepository
import com.sza.fastmediasorter.domain.ocr.OfflineOcrEngineProvider
import com.sza.fastmediasorter.domain.translation.TranslationLanguageCodeMapper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TranslationOcrEntryPoint {
    fun offlineOcrEngineProvider(): OfflineOcrEngineProvider
    fun deliverableCapabilityRepository(): DeliverableCapabilityRepository
    fun textTranslationFacadeFactory(): TextTranslationFacadeFactory
    fun deliveredNativeLibraryLoader(): DeliveredNativeLibraryLoader
    fun statsSink(): com.sza.fastmediasorter.domain.stats.StatsSink
}

/**
 * Coordinator for translation + OCR (S0386 Phase 01).
 *
 * The ML Kit translate/language-id logic lives in [TranslationBackend] and the ML Kit/offline OCR
 * logic in [RecognitionBackend]; this class wires them, exposes them as the [translation] /
 * [recognition] facades, and keeps the existing public API delegating to them so call-sites are
 * unchanged. Language list/name/conversion helpers stay in the companion.
 */
class TranslationManager(
    private val context: Context,
    private val callback: TranslationCallback,
    private val settingsRepository: com.sza.fastmediasorter.domain.repository.SettingsRepository,
    private val providedOfflineOcrEngineProvider: OfflineOcrEngineProvider? = null
) {

    private val deliveryEntryPoint by lazy {
        EntryPointAccessors.fromApplication(context.applicationContext, TranslationOcrEntryPoint::class.java)
    }

    private val offlineOcrEngineProvider: OfflineOcrEngineProvider by lazy {
        providedOfflineOcrEngineProvider ?: deliveryEntryPoint.offlineOcrEngineProvider()
    }

    private val capabilityRepository: DeliverableCapabilityRepository by lazy {
        deliveryEntryPoint.deliverableCapabilityRepository()
    }

    private val translationBackend: TextTranslationFacade by lazy {
        deliveryEntryPoint.textTranslationFacadeFactory().create(callback)
    }

    private val recognitionBackend: RecognitionBackend by lazy {
        RecognitionBackend(
            context,
            callback,
            settingsRepository,
            offlineOcrEngineProvider,
            translationBackend,
            capabilityRepository,
            deliveryEntryPoint.deliveredNativeLibraryLoader(),
            deliveryEntryPoint.statsSink()
        )
    }

    /** Translation surface (ML Kit translate + language-id). */
    val translation: TextTranslationFacade get() = translationBackend

    /** OCR surface (ML Kit text recognition + offline engines). */
    val recognition: TextRecognizationFacade get() = recognitionBackend

    /**
     * Data class representing a text block with position and translation
     */
    data class TranslatedTextBlock(
        val originalText: String,
        val translatedText: String,
        val boundingBox: Rect,
        val confidence: Float
    )

    /**
     * Apply font settings from session configuration (stub for now)
     * Note: Google Lens overlay font settings are applied via TranslationOverlayView
     */
    @Suppress("UNUSED_PARAMETER")
    fun applyFontSettings(settings: com.sza.fastmediasorter.domain.models.TranslationSessionSettings) {
        // Font settings for Google Lens style are handled in TranslationOverlayView
        // This method exists for consistency with TextViewerManager API
        Timber.d("TranslationManager.applyFontSettings called (delegated to overlay views)")
    }

    suspend fun getTargetLanguageCode(): String? = translationBackend.getTargetLanguageCode()

    companion object {
        /**
         * Convert ML Kit language code to Tesseract language code.
         * ML Kit uses 2-letter codes (ru, en), Tesseract uses 3-letter (rus, eng).
         */
        fun languageCodeToMLKit(code: String): String =
            TranslationLanguageCodeMapper.languageCodeToMLKit(code)

        /**
         * Get language name in English (for source language list)
         */
        fun getEnglishLanguageName(code: String): String {
            return when (code.lowercase()) {
                "auto" -> "Auto-detect"
                "en" -> "English"
                "ar" -> "Arabic"
                "bn" -> "Bengali"
                "bg" -> "Bulgarian"
                "be" -> "Belarusian"
                "zh" -> "Chinese"
                "nl" -> "Dutch"
                "fr" -> "French"
                "de" -> "German"
                "el" -> "Greek"
                "hi" -> "Hindi"
                "id" -> "Indonesian"
                "it" -> "Italian"
                "ja" -> "Japanese"
                "ko" -> "Korean"
                "mk" -> "Macedonian"
                "mt" -> "Maltese"
                "fa" -> "Persian"
                "pl" -> "Polish"
                "pt" -> "Portuguese"
                "ru" -> "Russian"
                "es" -> "Spanish"
                "th" -> "Thai"
                "tr" -> "Turkish"
                "uk" -> "Ukrainian"
                "vi" -> "Vietnamese"
                else -> code.uppercase()
            }
        }

        /**
         * Get language name in native script (for target language list)
         */
        fun getNativeLanguageName(code: String): String {
            return when (code.lowercase()) {
                "en" -> "English"
                "ar" -> "العربية"
                "bn" -> "বাংলা"
                "bg" -> "Български"
                "be" -> "Беларуская"
                "zh" -> "中文"
                "nl" -> "Nederlands"
                "fr" -> "Français"
                "de" -> "Deutsch"
                "el" -> "Ελληνικά"
                "hi" -> "हिन्दी"
                "id" -> "Bahasa Indonesia"
                "it" -> "Italiano"
                "ja" -> "日本語"
                "ko" -> "한국어"
                "mk" -> "Македонски"
                "mt" -> "Malti"
                "fa" -> "فارسی"
                "pl" -> "Polski"
                "pt" -> "Português"
                "ru" -> "Русский"
                "es" -> "Español"
                "th" -> "ไทย"
                "tr" -> "Türkçe"
                "uk" -> "Українська"
                "vi" -> "Tiếng Việt"
                else -> code.uppercase()
            }
        }

        /**
         * Build source language list dynamically based on UI language.
         * Order: Auto, UI Language, English (if not UI), Others alphabetically
         */
        fun buildSourceLanguageList(interfaceLang: String): List<Pair<String, String>> {
            val allLanguages = listOf(
                "ar", "bn", "bg", "be", "zh", "nl", "en", "fr", "de", "el", "hi",
                "id", "it", "ja", "ko", "mk", "mt", "fa", "pl", "pt", "ru", "es", "th", "tr", "uk", "vi"
            )

            val result = mutableListOf<Pair<String, String>>()

            // 1. Auto-detect
            result.add(getEnglishLanguageName("auto") to "auto")

            // 2. Current interface language
            result.add(getEnglishLanguageName(interfaceLang) to interfaceLang)

            // 3. English (if not interface language)
            if (interfaceLang != "en") {
                result.add("English" to "en")
            }

            // 4. All others alphabetically by English name
            allLanguages
                .filter { it != interfaceLang && it != "en" }
                .sortedBy { getEnglishLanguageName(it) }
                .forEach { code ->
                    result.add(getEnglishLanguageName(code) to code)
                }

            return result
        }

        /**
         * Build target language list dynamically based on UI language.
         * Order: UI Language, English (if not UI), Others alphabetically with native names
         */
        fun buildTargetLanguageList(interfaceLang: String): List<Pair<String, String>> {
            val allLanguages = listOf(
                "ar", "bn", "bg", "be", "zh", "nl", "en", "fr", "de", "el", "hi",
                "id", "it", "ja", "ko", "mk", "mt", "fa", "pl", "pt", "ru", "es", "th", "tr", "uk", "vi"
            )

            val result = mutableListOf<Pair<String, String>>()

            // 1. Current interface language with native name
            result.add(getNativeLanguageName(interfaceLang) to interfaceLang)

            // 2. English (if not interface language)
            if (interfaceLang != "en") {
                result.add("English" to "en")
            }

            // 3. All others alphabetically by English name, displayed with native names
            allLanguages
                .filter { it != interfaceLang && it != "en" }
                .sortedBy { getEnglishLanguageName(it) }
                .forEach { code ->
                    result.add(getNativeLanguageName(code) to code)
                }

            return result
        }

        /**
         * Map of visually similar Latin characters to their Cyrillic equivalents.
         * Used to fix OCR misrecognition when ML Kit's Latin recognizer is used on Cyrillic text.
         *
         * Based on common homoglyphs and OCR error patterns.
         */
        private val latinToCyrillicMapCommon = mapOf(
            // Lowercase
            'a' to 'а', // U+0430
            'c' to 'с', // U+0441
            'e' to 'е', // U+0435
            'o' to 'о', // U+043E
            'p' to 'р', // U+0440
            'x' to 'х', // U+0445
            'y' to 'у', // U+0443
            'k' to 'к', // U+043A
            'm' to 'м', // U+043C
            // Contextual/Font-dependent (Italic/Handwritten)
            'u' to 'и', // Italic u looks like и
            'r' to 'г', // r looks like г
            'n' to 'п', // Italic n looks like п
            'b' to 'ь', // b looks like ь (soft sign)

            // Uppercase
            'A' to 'А', // U+0410
            'B' to 'В', // U+0412
            'C' to 'С', // U+0421
            'E' to 'Е', // U+0415
            'H' to 'Н', // U+041D (Latin H -> Cyrillic En)
            'K' to 'К', // U+041A
            'M' to 'М', // U+041C
            'O' to 'О', // U+041E
            'P' to 'Р', // U+0420 (Latin P -> Cyrillic Er)
            'T' to 'Т', // U+0422
            'X' to 'Х', // U+0425
            'Y' to 'У', // U+0423

            // Digits/Symbols -> Cyrillic
            '3' to 'З', // Digit 3 -> Ze
            '4' to 'Ч', // Digit 4 -> Che
            '6' to 'б', // Digit 6 -> be
            '0' to 'О', // Digit 0 -> O
            'W' to 'Ш', // W -> Sha
            'w' to 'ш'  // w -> sha
        )

        // Ukrainian specific additions/overrides
        private val latinToCyrillicMapUk = mapOf(
            'i' to 'і', // Latin i -> Cyrillic i (Ukrainian)
            'I' to 'І'  // Latin I -> Cyrillic I (Ukrainian)
        )

        /**
         * Convert visually similar Latin characters to Cyrillic.
         * Used to fix OCR errors when Latin recognizer is applied to Russian/Ukrainian text.
         *
         * Only converts characters that are already present in the map.
         * Preserves spacing, punctuation, and actual Latin letters that don't have Cyrillic lookalikes.
         *
         * @param text Text with mixed Latin/Cyrillic characters
         * @param languageCode Source language code (e.g. "ru", "uk") to apply specific rules
         * @return Text with Latin lookalikes converted to Cyrillic
         */
        fun convertLatinToCyrillic(text: String, languageCode: String = "ru"): String {
            // 1. Handle multi-character sequences first (OCR segmentation errors)
            var result = text
                .replace("III", "Ш") // III -> Ш
                .replace("LL1", "Ш") // LL1 -> Ш
                .replace("rn", "м") // r + n -> м
                .replace("nn", "п") // n + n -> п (sometimes)

            // Language specific multi-char replacements
            if (languageCode == "ru" || languageCode == "be") {
                result = result
                    .replace("bl", "ы")
                    .replace("bI", "ы") // b + capital I
                    .replace("6l", "ы") // 6 + l
                    .replace("6I", "ы") // 6 + capital I
            } else if (languageCode == "uk") {
                // Ukrainian specific multi-char
                result = result
                    .replace("ji", "ї")
                    .replace("ii", "ї") // sometimes ii is recognized for ї
                    .replace("yi", "ї")
                    .replace("ye", "є")
            }

            // 2. Handle single characters
            // Merge common map with language specific map
            val map = if (languageCode == "uk") {
                latinToCyrillicMapCommon + latinToCyrillicMapUk
            } else {
                latinToCyrillicMapCommon
            }

            return result.map { char ->
                map[char] ?: char
            }.joinToString("")
        }
    }

    interface TranslationCallback {
        fun showError(message: String)
        fun showModelDownloadPrompt(
            languageName: String,
            onConfirm: () -> Unit,
            onCancel: () -> Unit
        )
    }

    suspend fun detectLanguage(text: String): String = translationBackend.detectLanguage(text)

    /**
     * Extract text from image using OCR without translation.
     */
    suspend fun extractTextOnly(
        bitmap: Bitmap,
        sourceLang: String = "en"
    ): String? = recognitionBackend.extractTextOnly(bitmap, sourceLang)

    /**
     * Translate text from source language to target language.
     */
    suspend fun translate(
        text: String,
        sourceLang: String = "en",
        targetLang: String = "ru"
    ): String? = translationBackend.translate(text, sourceLang, targetLang)

    /**
     * Recognize text from Bitmap using OCR.
     */
    suspend fun recognizeText(bitmap: Bitmap, sourceLangCode: String = "auto"): String? =
        recognitionBackend.recognizeText(bitmap, sourceLangCode)

    /**
     * Combined OCR + Translation: extract text from Bitmap and translate it.
     */
    suspend fun recognizeAndTranslate(
        bitmap: Bitmap,
        sourceLang: String = "en",
        targetLang: String = "ru"
    ): Pair<String, String>? {
        val recognizedText = recognitionBackend.recognizeText(bitmap, sourceLang) ?: return null
        val translatedText = translationBackend.translate(recognizedText, sourceLang, targetLang) ?: return null
        return Pair(recognizedText, translatedText)
    }

    /**
     * Google Lens style: extract text blocks with coordinates and translate each block.
     */
    suspend fun recognizeAndTranslateBlocks(
        bitmap: Bitmap,
        sourceLang: String = "en",
        targetLang: String = "ru"
    ): List<TranslatedTextBlock>? =
        recognitionBackend.recognizeAndTranslateBlocks(bitmap, sourceLang, targetLang)

    /**
     * Recognize word-level text boxes for in-place selection mapping (no translation).
     */
    suspend fun recognizeTextBlocksForSelection(bitmap: Bitmap): List<TranslatedTextBlock>? =
        recognitionBackend.recognizeTextBlocksForSelection(bitmap)

    /**
     * Release resources
     */
    fun release() {
        recognitionBackend.release()
        translationBackend.release()
    }
}
