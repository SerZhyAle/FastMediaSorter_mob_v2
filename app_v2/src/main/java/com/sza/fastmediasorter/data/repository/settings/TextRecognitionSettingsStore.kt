package com.sza.fastmediasorter.data.repository.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sza.fastmediasorter.domain.model.AppSettings

/**
 * Owns persistence of the text-recognition / translation feature settings: OCR
 * (engine, fonts, PaddleOCR model), in-image translation (source/target language,
 * lens style, Google Lens), and the camera-OCR modes.
 *
 * Extracted from SettingsRepositoryImpl so this cohesive feature is a single named
 * responsibility instead of twelve keys inlined among ~150 unrelated ones. Public
 * `AppSettings` shape and every persisted key string are unchanged - behaviour is
 * preserved; only the "where these keys/defaults/mapping live" responsibility moves.
 */
object TextRecognitionSettingsStore {

    private val KEY_ENABLE_TRANSLATION = booleanPreferencesKey("enable_translation")
    private val KEY_TRANSLATION_SOURCE_LANGUAGE = stringPreferencesKey("translation_source_language")
    private val KEY_TRANSLATION_TARGET_LANGUAGE = stringPreferencesKey("translation_target_language")
    private val KEY_TRANSLATION_LENS_STYLE = booleanPreferencesKey("translation_lens_style")
    private val KEY_ENABLE_GOOGLE_LENS = booleanPreferencesKey("enable_google_lens")
    private val KEY_ENABLE_OCR = booleanPreferencesKey("enable_ocr")
    private val KEY_OCR_DEFAULT_FONT_SIZE = stringPreferencesKey("ocr_default_font_size")
    private val KEY_OCR_DEFAULT_FONT_FAMILY = stringPreferencesKey("ocr_default_font_family")
    private val KEY_OCR_ENGINE_TYPE = stringPreferencesKey("ocr_engine_type")
    private val KEY_PADDLE_OCR_MODEL = stringPreferencesKey("paddle_ocr_model")
    private val KEY_CAMERA_OCR_TRANSLATION_ENABLED = booleanPreferencesKey("camera_ocr_translation_enabled")
    private val KEY_CAMERA_OCR_ONLY = booleanPreferencesKey("camera_ocr_only")

    /** Text-recognition / translation fields read from DataStore, ready for [AppSettings]. */
    data class Values(
        val enableTranslation: Boolean,
        val translationSourceLanguage: String,
        val translationTargetLanguage: String,
        val translationLensStyle: Boolean,
        val enableGoogleLens: Boolean,
        val enableOcr: Boolean,
        val cameraOcrTranslationEnabled: Boolean,
        val cameraOcrOnly: Boolean,
        val ocrDefaultFontSize: String,
        val ocrDefaultFontFamily: String,
        val ocrEngineType: String,
        val paddleOcrModel: String,
    )

    fun read(preferences: Preferences): Values = Values(
        // S0386: default OFF - OCR/translation engines are now delivered on demand. A persisted
        // key (existing users, incl. prior default-true) is preserved; only fresh stores get false.
        enableTranslation = preferences[KEY_ENABLE_TRANSLATION] ?: false,
        translationSourceLanguage = preferences[KEY_TRANSLATION_SOURCE_LANGUAGE] ?: "auto",
        translationTargetLanguage = preferences[KEY_TRANSLATION_TARGET_LANGUAGE] ?: "ru",
        translationLensStyle = preferences[KEY_TRANSLATION_LENS_STYLE] ?: true,
        enableGoogleLens = preferences[KEY_ENABLE_GOOGLE_LENS] ?: true,
        enableOcr = preferences[KEY_ENABLE_OCR] ?: false,
        cameraOcrTranslationEnabled = preferences[KEY_CAMERA_OCR_TRANSLATION_ENABLED] ?: false,
        cameraOcrOnly = preferences[KEY_CAMERA_OCR_ONLY] ?: false,
        ocrDefaultFontSize = preferences[KEY_OCR_DEFAULT_FONT_SIZE] ?: "AUTO",
        ocrDefaultFontFamily = preferences[KEY_OCR_DEFAULT_FONT_FAMILY] ?: "DEFAULT",
        ocrEngineType = preferences[KEY_OCR_ENGINE_TYPE] ?: "TESSERACT",
        paddleOcrModel = preferences[KEY_PADDLE_OCR_MODEL] ?: "CYRILLIC",
    )

    fun write(preferences: MutablePreferences, settings: AppSettings) {
        preferences[KEY_ENABLE_TRANSLATION] = settings.enableTranslation
        preferences[KEY_TRANSLATION_SOURCE_LANGUAGE] = settings.translationSourceLanguage
        preferences[KEY_TRANSLATION_TARGET_LANGUAGE] = settings.translationTargetLanguage
        preferences[KEY_ENABLE_GOOGLE_LENS] = settings.enableGoogleLens
        preferences[KEY_TRANSLATION_LENS_STYLE] = settings.translationLensStyle
        preferences[KEY_ENABLE_OCR] = settings.enableOcr
        preferences[KEY_CAMERA_OCR_TRANSLATION_ENABLED] = settings.cameraOcrTranslationEnabled
        preferences[KEY_CAMERA_OCR_ONLY] = settings.cameraOcrOnly
        preferences[KEY_OCR_DEFAULT_FONT_SIZE] = settings.ocrDefaultFontSize
        preferences[KEY_OCR_DEFAULT_FONT_FAMILY] = settings.ocrDefaultFontFamily
        preferences[KEY_OCR_ENGINE_TYPE] = settings.ocrEngineType
        preferences[KEY_PADDLE_OCR_MODEL] = settings.paddleOcrModel
    }
}
