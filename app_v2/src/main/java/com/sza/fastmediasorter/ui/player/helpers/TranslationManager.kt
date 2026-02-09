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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import timber.log.Timber
import com.sza.fastmediasorter.domain.model.AppSettings

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
    private val settingsRepository: com.sza.fastmediasorter.domain.repository.SettingsRepository
) {
    
    private val tesseractManager = TesseractManager(context)
    
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
        @Suppress("UNUSED_VARIABLE")
        val settings = settingsRepository.getSettings().first()
        // COMPILE FIX: Temporarily hardcoded due to build issue
        // return Companion.languageCodeToMLKit(settings.translationTargetLanguage)
        return Companion.languageCodeToMLKit("ru")
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
         * Supports major world languages by native speakers population + Maltese
         */
        fun languageCodeToMLKit(code: String): String {
            return when (code.lowercase()) {
                "en" -> TranslateLanguage.ENGLISH
                "ru" -> TranslateLanguage.RUSSIAN
                "uk" -> TranslateLanguage.UKRAINIAN
                "es" -> TranslateLanguage.SPANISH
                "fr" -> TranslateLanguage.FRENCH
                "de" -> TranslateLanguage.GERMAN
                "zh" -> TranslateLanguage.CHINESE
                "ja" -> TranslateLanguage.JAPANESE
                "ko" -> TranslateLanguage.KOREAN
                "ar" -> TranslateLanguage.ARABIC
                "pt" -> TranslateLanguage.PORTUGUESE
                "bn" -> TranslateLanguage.BENGALI
                "hi" -> TranslateLanguage.HINDI
                "it" -> TranslateLanguage.ITALIAN
                "tr" -> TranslateLanguage.TURKISH
                "pl" -> TranslateLanguage.POLISH
                "nl" -> TranslateLanguage.DUTCH
                "th" -> TranslateLanguage.THAI
                "vi" -> TranslateLanguage.VIETNAMESE
                "id" -> TranslateLanguage.INDONESIAN
                "fa" -> TranslateLanguage.PERSIAN
                "el" -> TranslateLanguage.GREEK
                "mt" -> TranslateLanguage.MALTESE
                else -> TranslateLanguage.ENGLISH // Fallback
            }
        }
        
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
            callback.showError("OCR error: ${e.message}")
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
            callback.showError("Translation failed: ${e.message}")
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
                    val languageName = getLanguageName(targetLang)
                    var downloadConfirmed = false
                    var downloadCancelled = false
                    
                    // Show prompt on main thread
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        callback.showModelDownloadPrompt(
                            languageName,
                            onConfirm = { downloadConfirmed = true },
                            onCancel = { downloadCancelled = true }
                        )
                    }
                    
                    // Wait for user decision (simple polling, replace with suspendCancellableCoroutine for production)
                    var waitTime = 0
                    while (!downloadConfirmed && !downloadCancelled && waitTime < 30000) {
                        kotlinx.coroutines.delay(100)
                        waitTime += 100
                    }
                    
                    if (downloadCancelled || waitTime >= 30000) {
                        Timber.d("Translation model download cancelled by user or timeout")
                        return null
                    }
                    
                    // Download model and wait for completion
                    Timber.d("Starting translation model download: $targetLang")
                    val conditions = DownloadConditions.Builder()
                        .requireWifi()
                        .build()
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
                    
                    // Show message to user and prompt for re-download
                    val languageName = getLanguageName(targetLang)
                    var downloadConfirmed = false
                    var downloadCancelled = false
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        callback.showModelDownloadPrompt(
                            languageName,
                            onConfirm = { downloadConfirmed = true },
                            onCancel = { downloadCancelled = true }
                        )
                    }
                    
                    // Wait for user decision
                    var waitTime = 0
                    while (!downloadConfirmed && !downloadCancelled && waitTime < 30000) {
                        kotlinx.coroutines.delay(100)
                        waitTime += 100
                    }
                    
                    if (downloadCancelled || waitTime >= 30000) {
                        Timber.d("Translation model re-download cancelled by user or timeout")
                        return null
                    }
                    
                    // Re-download model
                    Timber.d("Starting translation model re-download: $targetLang")
                    val conditions = DownloadConditions.Builder()
                        .requireWifi()
                        .build()
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
     * Clean OCR text from garbage symbols and pseudographics.
     * Removes box-drawing characters, control characters, and other visual noise.
     * 
     * @param text Raw OCR text
     * @return Cleaned text with only meaningful content
     */
    private fun cleanOcrText(text: String): String {
        return text
            // Remove box-drawing characters (U+2500-U+257F)
            .replace(Regex("[\u2500-\u257F]"), "")
            // Remove block elements (U+2580-U+259F)
            .replace(Regex("[\u2580-\u259F]"), "")
            // Remove geometric shapes (U+25A0-U+25FF)
            .replace(Regex("[\u25A0-\u25FF]"), "")
            // Remove miscellaneous symbols (arrows, etc.) (U+2190-U+21FF)
            .replace(Regex("[\u2190-\u21FF]"), "")
            // Remove mathematical operators (U+2200-U+22FF)
            .replace(Regex("[\u2200-\u22FF]"), "")
            // Remove miscellaneous technical symbols (U+2300-U+23FF)
            .replace(Regex("[\u2300-\u23FF]"), "")
            // Remove control characters except newlines and tabs
            .replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]"), "")
            // Remove excessive whitespace (multiple spaces → single space)
            .replace(Regex(" {2,}"), " ")
            // Remove empty lines (multiple newlines → max 2)
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
    
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
            val tessLang = mlKitToTesseractLang(sourceLangCode)
            Timber.d("TranslationManager.recognizeText: Trying Tesseract for $sourceLangCode (tess=$tessLang)")
            val tesseractResult = tesseractManager.recognizeText(bitmap, tessLang)
            Timber.d("Tesseract raw result: ${tesseractResult?.take(100)} (length=${tesseractResult?.length})")
            if (!tesseractResult.isNullOrBlank()) {
                val cleanedText = cleanOcrText(tesseractResult)
                Timber.d("Tesseract recognition successful: ${cleanedText.length} chars (cleaned from ${tesseractResult.length})")
                return cleanedText
            }
            Timber.w("Tesseract failed or returned empty, falling back to ML Kit")
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
            callback.showError("Text recognition failed: ${e.message}")
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
            val tessLang = mlKitToTesseractLang(sourceLang)
            Timber.d("TranslationManager.recognizeAndTranslateBlocks: Trying Tesseract (source=$sourceLang, tess=$tessLang, target=$targetLang)")
            val tesseractBlocks = tesseractManager.recognizeTextBlocks(bitmap, tessLang)
            
            if (!tesseractBlocks.isNullOrEmpty()) {
                // Filter out low-quality blocks before translation
                val filteredBlocks = tesseractBlocks.filter { block ->
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
                
                Timber.d("Tesseract: ${tesseractBlocks.size} raw blocks → ${filteredBlocks.size} after filtering")
                
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
                    Timber.d("Tesseract block recognition successful: ${translatedBlocks.size} blocks")
                    return translatedBlocks
                }
            }
            Timber.w("Tesseract failed or returned empty blocks, falling back to ML Kit")
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
            callback.showError("Translation failed: ${e.message}")
            null
        }
    }
    
    /**
     * Get human-readable language name from ML Kit language code
     */
    private fun getLanguageName(langCode: String): String {
        return when (langCode) {
            TranslateLanguage.ENGLISH -> "English"
            TranslateLanguage.CHINESE -> "Chinese"
            TranslateLanguage.SPANISH -> "Spanish"
            TranslateLanguage.HINDI -> "Hindi"
            TranslateLanguage.ARABIC -> "Arabic"
            TranslateLanguage.BENGALI -> "Bengali"
            TranslateLanguage.PORTUGUESE -> "Portuguese"
            TranslateLanguage.RUSSIAN -> "Russian"
            TranslateLanguage.JAPANESE -> "Japanese"
            TranslateLanguage.TURKISH -> "Turkish"
            TranslateLanguage.KOREAN -> "Korean"
            TranslateLanguage.FRENCH -> "French"
            TranslateLanguage.GERMAN -> "German"
            TranslateLanguage.VIETNAMESE -> "Vietnamese"
            TranslateLanguage.ITALIAN -> "Italian"
            TranslateLanguage.POLISH -> "Polish"
            TranslateLanguage.UKRAINIAN -> "Ukrainian"
            TranslateLanguage.THAI -> "Thai"
            TranslateLanguage.PERSIAN -> "Persian"
            TranslateLanguage.DUTCH -> "Dutch"
            TranslateLanguage.GREEK -> "Greek"
            TranslateLanguage.INDONESIAN -> "Indonesian"
            TranslateLanguage.MALTESE -> "Maltese"
            else -> "target language"
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        translator?.close()
        translator = null
        
        textRecognizer?.close()
        textRecognizer = null

        tesseractManager.release()
    }
}
