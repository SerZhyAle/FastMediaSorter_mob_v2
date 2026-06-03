package com.sza.fastmediasorter.ui.player.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sza.fastmediasorter.R
import com.sza.fastmediasorter.domain.ocr.OfflineOcrEngineProvider
import com.sza.fastmediasorter.domain.translation.TranslationLanguageCodeMapper
import kotlin.coroutines.resume
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import timber.log.Timber
import com.sza.fastmediasorter.domain.model.AppSettings

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TranslationOcrEntryPoint {
    fun offlineOcrEngineProvider(): OfflineOcrEngineProvider
}

/**
 * Manages ML Kit clients for translation and OCR:
 * - Translates text from source language to target language
 * - Recognizes text from Bitmap using OCR
 * - Handles model downloads with user prompts
 * - Provides combined recognize-and-translate for PDF pages
 * - Supports Google Lens style with text blocks and coordinates
 */
class TranslationManager(
    private val context: Context,
    private val callback: TranslationCallback,
    private val settingsRepository: com.sza.fastmediasorter.domain.repository.SettingsRepository,
    private val providedOfflineOcrEngineProvider: OfflineOcrEngineProvider? = null
) {

    private val offlineOcrEngineProvider: OfflineOcrEngineProvider by lazy {
        providedOfflineOcrEngineProvider
            ?: EntryPointAccessors.fromApplication(context.applicationContext, TranslationOcrEntryPoint::class.java)
                .offlineOcrEngineProvider()
    }
    
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

    suspend fun getTargetLanguageCode(): String? {
        val settings = settingsRepository.getSettings().first()
        return Companion.languageCodeToMLKit(settings.translationTargetLanguage)
    }
    
    companion object {
        /**
         * Languages that use Cyrillic script.
         * Used to determine when to apply Latin→Cyrillic character conversion after OCR.
         * Note: Serbian (sr) is not supported by ML Kit Translate
         */
        private val CYRILLIC_LANGUAGES = setOf(
            TranslateLanguage.RUSSIAN,      // ru
            TranslateLanguage.UKRAINIAN,    // uk
            TranslateLanguage.BULGARIAN,    // bg
            TranslateLanguage.BELARUSIAN,   // be
            TranslateLanguage.MACEDONIAN    // mk
        )
        
        /**
         * Convert ML Kit language code to Tesseract language code.
         * ML Kit uses 2-letter codes (ru, en), Tesseract uses 3-letter (rus, eng).
         */
        private fun mlKitToTesseractLang(mlKitLang: String): String {
            return when (mlKitLang) {
                TranslateLanguage.RUSSIAN -> "rus"
                TranslateLanguage.UKRAINIAN -> "ukr"
                TranslateLanguage.BULGARIAN -> "bul"
                TranslateLanguage.BELARUSIAN -> "bel"
                TranslateLanguage.ENGLISH -> "eng"
                "auto" -> "rus" // Default to Russian for auto-detect on Cyrillic text
                else -> "rus" // Fallback to Russian for unknown Cyrillic
            }
        }
        
        /**
         * Convert language code from settings to ML Kit constant.
         * Supports every code exposed by the shared ML Kit translation catalog.
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
    
    private var translator: Translator? = null
    
    // Single text recognizer - Latin script (ML Kit does not support Russian/Ukrainian)
    // Note: Latin recognizer can still recognize some Cyrillic text but with lower accuracy
    private var textRecognizer: TextRecognizer? = null
    
    private val languageIdentifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(0.5f)
            .build()
    )
    private val modelManager = RemoteModelManager.getInstance()
    
    private var currentSourceLang = TranslateLanguage.ENGLISH
    private var currentTargetLang = TranslateLanguage.RUSSIAN
    
    /**
     * Get or create the TextRecognizer (Latin script).
     * Note: ML Kit Text Recognition does NOT support Russian/Ukrainian directly.
     * Only Latin, Chinese, Devanagari, Japanese, Korean scripts are available.
     */
    private fun getTextRecognizer(): TextRecognizer {
        if (textRecognizer == null) {
            Timber.i("TranslationManager: Creating Latin text recognizer (only available option)")
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        }
        return textRecognizer!!
    }
    
    /**
     * Auto-detect language of given text using ML Kit Language Identification.
     * Returns BCP-47 language code compatible with ML Kit Translate.
     * 
     * @param text Text to analyze
     * @return Detected language code (e.g., "en", "ru", "uk") or "en" as fallback
     */
    suspend fun detectLanguage(text: String): String {
        if (text.isBlank()) return TranslateLanguage.ENGLISH
        
        return try {
            val detectedLang = languageIdentifier.identifyLanguage(text).await()
            if (detectedLang == "und") { // Undetermined
                Timber.d("Language detection failed, using English as fallback")
                TranslateLanguage.ENGLISH
            } else {
                Timber.d("Detected language: $detectedLang")
                detectedLang
            }
        } catch (e: Exception) {
            Timber.e(e, "Error detecting language")
            TranslateLanguage.ENGLISH
        }
    }
    
    /**
     * Check if a direct translation path exists between two languages in ML Kit.
     * ML Kit only supports translations to/from English, not between other language pairs.
     * 
     * @param sourceLang Source language code
     * @param targetLang Target language code
     * @return True if direct translation is supported
     */
    private fun isDirectTranslationSupported(sourceLang: String, targetLang: String): Boolean {
        // Direct translation supported only if one of the languages is English
        return sourceLang == TranslateLanguage.ENGLISH || 
               targetLang == TranslateLanguage.ENGLISH ||
               sourceLang == targetLang
    }
    
    /**
     * Extract text from image using OCR without translation
     * Used for OCR feature where user just wants to copy text from image
     * 
     * @param bitmap Image to extract text from
     * @param sourceLang Language code for OCR engine selection
     * @return Recognized text or null if no text found
     */
    suspend fun extractTextOnly(
        bitmap: Bitmap,
        sourceLang: String = TranslateLanguage.ENGLISH
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
    
    /**
     * Translate text from source language to target language.
     * Downloads model if not available on device (~30MB).
     * 
     * IMPORTANT: ML Kit only supports translations to/from English.
     * For other language pairs (e.g., Ukrainian→Russian), uses two-step translation via English.
     * 
     * @param text Text to translate
     * @param sourceLang Source language code (default: English). Use "auto" for auto-detection.
     * @param targetLang Target language code (default: Russian)
     * @return Translated text or null on error
     */
    suspend fun translate(
        text: String, 
        sourceLang: String = TranslateLanguage.ENGLISH,
        targetLang: String = TranslateLanguage.RUSSIAN
    ): String? {
        if (text.isBlank()) return null
        
        Timber.d("translate() called: sourceLang='$sourceLang', targetLang='$targetLang', textLength=${text.length}")
        
        // Auto-detect source language if requested
        val actualSourceLang = if (sourceLang == "auto") {
            val detected = detectLanguage(text)
            Timber.d("Auto-detection: sourceLang='auto' → detected='$detected'")
            detected
        } else {
            Timber.d("Using provided source language: '$sourceLang'")
            sourceLang
        }
        
        // If source and target are the same, return original text
        if (actualSourceLang == targetLang) {
            Timber.d("Source and target languages are identical, skipping translation")
            return text
        }
        
        try {
            // Check if direct translation is supported
            if (!isDirectTranslationSupported(actualSourceLang, targetLang)) {
                Timber.d("Direct translation $actualSourceLang→$targetLang not supported. Using two-step via English.")
                
                // Step 1: source → English
                val intermediateText = translateDirect(text, actualSourceLang, TranslateLanguage.ENGLISH)
                if (intermediateText == null) {
                    Timber.e("Intermediate translation ($actualSourceLang→en) failed")
                    return null
                }
                
                Timber.d("Intermediate translation successful: ${text.take(50)}... → ${intermediateText.take(50)}...")
                
                // Step 2: English → target
                val finalText = translateDirect(intermediateText, TranslateLanguage.ENGLISH, targetLang)
                if (finalText == null) {
                    Timber.e("Final translation (en→$targetLang) failed")
                    return null
                }
                
                Timber.d("Two-step translation completed: $actualSourceLang→en→$targetLang")
                return finalText
            } else {
                // Direct translation supported
                return translateDirect(text, actualSourceLang, targetLang)
            }
        } catch (e: Exception) {
            Timber.e(e, "Translation error")
            callback.showError(context.getString(R.string.translation_error))
            return null
        }
    }
    
    /**
     * Perform direct translation between two languages using a single translator.
     * Internal method called by translate() for each translation step.
     * 
     * @param text Text to translate
     * @param sourceLang Source language code
     * @param targetLang Target language code
     * @return Translated text or null on error
     */
    private suspend fun translateDirect(
        text: String,
        sourceLang: String,
        targetLang: String
    ): String? {
        if (text.isBlank()) return null
        
        try {
            // Reinitialize translator if language pair changed
            Timber.d("translateDirect: translator=${translator != null}, current=($currentSourceLang→$currentTargetLang), requested=($sourceLang→$targetLang)")
            
            if (translator == null || sourceLang != currentSourceLang || targetLang != currentTargetLang) {
                translator?.close()
                
                Timber.d("Reinitializing translator (was: $currentSourceLang→$currentTargetLang, now: $sourceLang→$targetLang)")
                
                currentSourceLang = sourceLang
                currentTargetLang = targetLang
                
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang)
                    .setTargetLanguage(targetLang)
                    .build()
                
                translator = Translation.getClient(options)
                
                // Check if model is downloaded, prompt user if not
                val targetModel = TranslateRemoteModel.Builder(targetLang).build()
                val isModelDownloaded = modelManager.isModelDownloaded(targetModel).await()
                
                if (!isModelDownloaded) {
                    // Wait (race-free) for the user to confirm the download in the prompt dialog.
                    if (!awaitModelDownloadConfirmation(targetLang)) {
                        Timber.d("Translation model download declined by user")
                        return null
                    }

                    // Download model and wait for completion (no WiFi-only restriction)
                    Timber.d("Starting translation model download: $targetLang")
                    val conditions = DownloadConditions.Builder().build()
                    translator?.downloadModelIfNeeded(conditions)?.await()
                    Timber.i("Translation model download completed: $targetLang")
                }
                
                // Always ensure model is ready before use (even if isModelDownloaded=true, model might be loading)
                Timber.d("Ensuring translation model is ready: $targetLang")
                val ensureConditions = DownloadConditions.Builder().build()
                translator?.downloadModelIfNeeded(ensureConditions)?.await()
                Timber.d("Translation model ready for use: $targetLang")
            }
            
            return translator?.translate(text)?.await()
        } catch (e: Exception) {
            Timber.e(e, "Direct translation error: $sourceLang→$targetLang (Fix with AI)")
            
            // Check if model is corrupted - delete and redownload automatically
            if (e.message?.contains("model files not found", ignoreCase = true) == true ||
                e.message?.contains("downloadModelIfNeeded", ignoreCase = true) == true) {
                Timber.w("Translation model appears corrupted, deleting and re-downloading: $targetLang")
                try {
                    val targetModel = TranslateRemoteModel.Builder(targetLang).build()
                    modelManager.deleteDownloadedModel(targetModel).await()
                    Timber.i("Deleted corrupted translation model: $targetLang")

                    // Prompt for re-download and wait (race-free) for the user's decision.
                    if (!awaitModelDownloadConfirmation(targetLang)) {
                        Timber.d("Translation model re-download declined by user")
                        return null
                    }

                    // Re-download model (no WiFi-only restriction)
                    Timber.d("Starting translation model re-download: $targetLang")
                    val conditions = DownloadConditions.Builder().build()
                    translator?.downloadModelIfNeeded(conditions)?.await()
                    Timber.i("Translation model re-download completed: $targetLang")
                    
                    // Retry translation after re-download
                    return translator?.translate(text)?.await()
                } catch (deleteEx: Exception) {
                    Timber.e(deleteEx, "Failed to recover corrupted translation model")
                }
            }
            
            return null
        }
    }

    /**
     * Suspend until the user confirms or declines downloading a translation model.
     *
     * Replaces the previous fixed-polling wait: that approach read plain local flags
     * mutated on the UI thread from a coroutine thread without a memory barrier, so a
     * user tap on "OK" was frequently not observed and the wait expired on its arbitrary
     * 30s timeout without ever starting the download. A continuation resumes reliably from
     * any thread, and cancelling the enclosing scope (e.g. activity destroyed) cancels it
     * cleanly instead of leaking a 30s poll loop.
     *
     * @return true if the user confirmed the download, false if declined/cancelled.
     */
    private suspend fun awaitModelDownloadConfirmation(targetLang: String): Boolean =
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val languageName = getLanguageName(targetLang)
            callback.showModelDownloadPrompt(
                languageName,
                onConfirm = { if (cont.isActive) cont.resume(true) },
                onCancel = { if (cont.isActive) cont.resume(false) }
            )
        }

    /**
     * Clean OCR text from garbage symbols and pseudographics.
     * Removes box-drawing characters, control characters, and other visual noise.
     * 
     * @param text Raw OCR text
     * @return Cleaned text with only meaningful content
     */
    private fun cleanOcrText(text: String): String = TranslationTextUtils.cleanOcrText(text)
    
    /**
     * Recognize text from Bitmap using OCR.
     * 
     * @param bitmap Image to extract text from
     * @param sourceLangCode Source language code (determines which OCR script to use)
     * @return Recognized text or null if no text found
     */
    suspend fun recognizeText(bitmap: Bitmap, sourceLangCode: String = "auto"): String? {
        // Try Tesseract for Cyrillic languages first, or if auto
        if (sourceLangCode in CYRILLIC_LANGUAGES || sourceLangCode == "auto") {
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
            // This fixes OCR errors when Latin recognizer is used on Cyrillic text
            var conversionLang = sourceLangCode
            val shouldConvert = when (sourceLangCode) {
                in CYRILLIC_LANGUAGES -> {
                    // Explicit Cyrillic language source - always convert
                    true
                }
                "auto" -> {
                    // Auto-detect: check if text is actually Cyrillic
                    val detectedLang = detectLanguage(text)
                    if (detectedLang in CYRILLIC_LANGUAGES) {
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
                text = convertLatinToCyrillic(text, conversionLang)
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
     * Combined OCR + Translation: Extract text from Bitmap and translate it.
     * 
     * @param bitmap PDF page or image to process
     * @param sourceLang Source language (determines OCR script)
     * @param targetLang Target language (default: Russian)
     * @return Pair of (recognizedText, translatedText) or null on error
     */
    suspend fun recognizeAndTranslate(
        bitmap: Bitmap,
        sourceLang: String = TranslateLanguage.ENGLISH,
        targetLang: String = TranslateLanguage.RUSSIAN
    ): Pair<String, String>? {
        val recognizedText = recognizeText(bitmap, sourceLang) ?: return null
        val translatedText = translate(recognizedText, sourceLang, targetLang) ?: return null
        return Pair(recognizedText, translatedText)
    }
    
    /**
     * Google Lens style: Extract text blocks with coordinates and translate each block.
     * Returns list of translated blocks with their bounding boxes for overlay rendering.
     * 
     * @param bitmap PDF page or image to process
     * @param sourceLang Source language (default: auto-detect)
     * @param targetLang Target language (default: Russian)
     * @return List of TranslatedTextBlock or null on error
     */
    suspend fun recognizeAndTranslateBlocks(
        bitmap: Bitmap,
        sourceLang: String = TranslateLanguage.ENGLISH,
        targetLang: String = TranslateLanguage.RUSSIAN
    ): List<TranslatedTextBlock>? {
        // Try Tesseract ONLY for Cyrillic source languages or auto-detect
        // Do NOT use Tesseract for known non-Cyrillic languages (like English)
        val shouldUseTesseract = sourceLang in CYRILLIC_LANGUAGES || sourceLang == "auto"

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
                    // 1. Minimum confidence threshold
                    if (block.confidence < 30f) {
                        Timber.d("Filtered block (low confidence ${block.confidence}): '${block.text.take(20)}...'")
                        return@filter false
                    }
                    
                    // 2. Minimum text length (ignore single chars and very short text)
                    if (block.text.trim().length < 3) {
                        Timber.d("Filtered block (too short): '${block.text}'")
                        return@filter false
                    }
                    
                    // 3. Filter blocks with too many special characters (likely OCR noise)
                    val letters = block.text.count { it.isLetter() }
                    val specialChars = block.text.count { !it.isLetterOrDigit() && !it.isWhitespace() }
                    val ratio = if (letters > 0) specialChars.toFloat() / letters else Float.MAX_VALUE
                    if (ratio > 0.5f) { // More than 50% special chars compared to letters
                        Timber.d("Filtered block (too many special chars, ratio=$ratio): '${block.text.take(20)}...'")
                        return@filter false
                    }
                    
                    // 4. Minimum bounding box size (ignore tiny artifacts)
                    val boxWidth = block.boundingBox.width()
                    val boxHeight = block.boundingBox.height()
                    if (boxWidth < 20 || boxHeight < 10) {
                        Timber.d("Filtered block (box too small ${boxWidth}x${boxHeight}): '${block.text.take(20)}...'")
                        return@filter false
                    }
                    
                    true
                }
                
                Timber.d("Offline OCR: ${ocrBlocks.size} raw blocks → ${filteredBlocks.size} after filtering")
                
                val translatedBlocks = mutableListOf<TranslatedTextBlock>()
                for (block in filteredBlocks) {
                    val translatedText = translate(block.text, sourceLang, targetLang)
                    if (translatedText != null) {
                        translatedBlocks.add(
                            TranslatedTextBlock(
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
            
            val translatedBlocks = mutableListOf<TranslatedTextBlock>()
            
            // Process each text block
            for (block in result.textBlocks) {
                var originalText = block.text
                if (originalText.isBlank()) continue
                
                val boundingBox = block.boundingBox ?: continue
                
                // Post-process: convert visually similar Latin chars to Cyrillic for Russian/Ukrainian
                // This fixes OCR errors when Latin recognizer is used on Cyrillic text
                var conversionLang = sourceLang
                val shouldConvert = when (sourceLang) {
                    in CYRILLIC_LANGUAGES -> {
                        // Explicit Cyrillic language source - always convert
                        true
                    }
                    "auto" -> {
                        // Auto-detect: check if text is actually Cyrillic
                        val detectedLang = detectLanguage(originalText)
                        if (detectedLang in CYRILLIC_LANGUAGES) {
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
                    originalText = convertLatinToCyrillic(originalText, conversionLang)
                    if (originalText != beforeConversion) {
                        Timber.d("Block Latin→Cyrillic ($conversionLang): '${beforeConversion.take(20)}...' → '${originalText.take(20)}...'")
                    }
                }
                
                // Translate this block's text
                val translatedText = translate(originalText, sourceLang, targetLang)
                if (translatedText == null) {
                    // If translation fails, skip this block
                    Timber.w("Translation failed for block: $originalText")
                    continue
                }
                
                translatedBlocks.add(
                    TranslatedTextBlock(
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
    
    /**
     * Recognize word-level text boxes for in-place selection mapping (no translation).
     * Returns each recognized word as a [TranslatedTextBlock] carrying its original text and
     * pixel bounding box (in bitmap coordinates); [TranslatedTextBlock.translatedText] is empty.
     * Used by the PDF text-selection overlay to pre-select the word under a long-press point.
     */
    suspend fun recognizeTextBlocksForSelection(bitmap: Bitmap): List<TranslatedTextBlock>? {
        return try {
            val recognizer = getTextRecognizer()
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            if (result.textBlocks.isEmpty()) return null
            val words = mutableListOf<TranslatedTextBlock>()
            for (block in result.textBlocks) {
                for (line in block.lines) {
                    for (element in line.elements) {
                        val box = element.boundingBox ?: continue
                        val text = element.text
                        if (text.isBlank()) continue
                        words.add(
                            TranslatedTextBlock(
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

    private fun getLanguageName(langCode: String): String =
        TranslationTextUtils.getLanguageName(langCode)
    
    /**
     * Release resources
     */
    fun release() {
        translator?.close()
        translator = null
        
        textRecognizer?.close()
        textRecognizer = null

        offlineOcrEngineProvider.release()
    }
}
