package com.sza.fastmediasorter.domain.translation

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

/**
 * Shared ML Kit translation language mapping used by domain prewarm and player UI code.
 */
object TranslationLanguageCodeMapper {
    val supportedLanguageCountries: Map<String, String> = linkedMapOf(
        "af" to "ZA",
        "sq" to "AL",
        "ar" to "SA",
        "be" to "BY",
        "bn" to "BD",
        "bg" to "BG",
        "ca" to "ES",
        "zh" to "CN",
        "hr" to "HR",
        "cs" to "CZ",
        "da" to "DK",
        "nl" to "NL",
        "en" to "GB",
        "eo" to "UN",
        "et" to "EE",
        "fi" to "FI",
        "fr" to "FR",
        "gl" to "ES",
        "ka" to "GE",
        "de" to "DE",
        "el" to "GR",
        "gu" to "IN",
        "ht" to "HT",
        "he" to "IL",
        "hi" to "IN",
        "hu" to "HU",
        "is" to "IS",
        "id" to "ID",
        "ga" to "IE",
        "it" to "IT",
        "ja" to "JP",
        "kn" to "IN",
        "ko" to "KR",
        "lv" to "LV",
        "lt" to "LT",
        "mk" to "MK",
        "ms" to "MY",
        "mt" to "MT",
        "mr" to "IN",
        "no" to "NO",
        "fa" to "IR",
        "pl" to "PL",
        "pt" to "PT",
        "ro" to "RO",
        "ru" to "RU",
        "sk" to "SK",
        "sl" to "SI",
        "es" to "ES",
        "sw" to "TZ",
        "sv" to "SE",
        "tl" to "PH",
        "ta" to "IN",
        "te" to "IN",
        "th" to "TH",
        "tr" to "TR",
        "uk" to "UA",
        "ur" to "PK",
        "vi" to "VN",
        "cy" to "GB"
    )

    val supportedCodes: Set<String> = supportedLanguageCountries.keys

    fun languageCodeToMLKit(code: String): String {
        val normalized = code.lowercase(Locale.ROOT)
        return when (normalized) {
            "auto" -> TranslateLanguage.ENGLISH
            in supportedCodes -> normalized
            else -> TranslateLanguage.ENGLISH
        }
    }
}
