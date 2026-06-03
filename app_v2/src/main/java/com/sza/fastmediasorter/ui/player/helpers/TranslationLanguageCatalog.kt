package com.sza.fastmediasorter.ui.player.helpers

import com.sza.fastmediasorter.domain.translation.TranslationLanguageCodeMapper
import java.util.Locale

enum class LanguageCapability {
    TRANSLATION,
    BASIC_OCR,
    QUALITY_OCR,
    NO_LEGAL_OCR
}

data class LanguageItem(
    val code: String,
    val countryCode: String?,
    val localizedName: String,
    val nativeName: String,
    val flagEmoji: String,
    val capabilities: Set<LanguageCapability>
)

object TranslationLanguageCatalog {
    private const val AUTO_DETECT_CODE = "auto"
    private const val AUTO_DETECT_LABEL = "Auto-detect"
    private const val AUTO_DETECT_FLAG = "🌐"

    private val supportedLanguageCountries = TranslationLanguageCodeMapper.supportedLanguageCountries

    val supportedCodes: Set<String> = TranslationLanguageCodeMapper.supportedCodes

    val basicOcrCodes: Set<String> = setOf(
        "af",
        "sq",
        "ca",
        "hr",
        "cs",
        "da",
        "nl",
        "en",
        "eo",
        "et",
        "fi",
        "fr",
        "gl",
        "de",
        "ht",
        "hu",
        "is",
        "id",
        "ga",
        "it",
        "lv",
        "lt",
        "ms",
        "mt",
        "no",
        "pl",
        "pt",
        "ro",
        "sk",
        "sl",
        "es",
        "sw",
        "sv",
        "tl",
        "tr",
        "vi",
        "cy"
    )

    val qualityOcrCodes: Set<String> = setOf("ru", "uk")

    val noLegalOcrCodes: Set<String> = setOf("ru", "uk", "bg", "be", "mk")

    fun buildSourceLanguageList(interfaceLang: String): List<LanguageItem> {
        val displayLocale = displayLocaleFor(interfaceLang)
        val interfaceCode = normalizeSupportedCode(interfaceLang)
        val result = mutableListOf(autoDetectItem())

        result.add(createLanguageItem(interfaceCode, displayLocale))
        if (interfaceCode != "en") {
            result.add(createLanguageItem("en", displayLocale))
        }

        result.addAll(sortedLanguageItems(displayLocale, exclude = setOf(interfaceCode, "en")))
        return result
    }

    fun buildTargetLanguageList(interfaceLang: String): List<LanguageItem> {
        val displayLocale = displayLocaleFor(interfaceLang)
        val interfaceCode = normalizeSupportedCode(interfaceLang)
        val result = mutableListOf(createLanguageItem(interfaceCode, displayLocale))

        if (interfaceCode != "en") {
            result.add(createLanguageItem("en", displayLocale))
        }

        result.addAll(sortedLanguageItems(displayLocale, exclude = setOf(interfaceCode, "en")))
        return result
    }

    fun findLanguage(code: String, displayLocale: Locale = Locale.getDefault()): LanguageItem? {
        val normalizedCode = code.lowercase(Locale.ROOT)
        if (normalizedCode == AUTO_DETECT_CODE) {
            return autoDetectItem()
        }
        if (normalizedCode !in supportedLanguageCountries) {
            return null
        }
        return createLanguageItem(normalizedCode, displayLocale)
    }

    fun formatLanguage(item: LanguageItem): String {
        val name = "${item.localizedName} (${item.nativeName})"
        return if (item.flagEmoji.isBlank()) name else "${item.flagEmoji} $name"
    }

    fun getFlagEmoji(countryCode: String?): String {
        val normalized = countryCode
            ?.uppercase(Locale.ROOT)
            ?.takeIf { it.length == 2 && it.all { char -> char in 'A'..'Z' } }
            ?: return ""

        return normalized
            .map { char -> Character.toChars(REGIONAL_INDICATOR_OFFSET + (char.code - 'A'.code)).concatToString() }
            .joinToString(separator = "")
    }

    private fun sortedLanguageItems(displayLocale: Locale, exclude: Set<String>): List<LanguageItem> {
        return supportedLanguageCountries.keys
            .filterNot { it in exclude }
            .map { createLanguageItem(it, displayLocale) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.localizedName })
    }

    private fun createLanguageItem(code: String, displayLocale: Locale): LanguageItem {
        val locale = Locale.forLanguageTag(code)
        val countryCode = supportedLanguageCountries.getValue(code)
        return LanguageItem(
            code = code,
            countryCode = countryCode,
            localizedName = locale.getDisplayLanguage(displayLocale).capitalized(displayLocale),
            nativeName = locale.getDisplayLanguage(locale).capitalized(locale),
            flagEmoji = getFlagEmoji(countryCode),
            capabilities = capabilitiesFor(code)
        )
    }

    private fun autoDetectItem(): LanguageItem {
        return LanguageItem(
            code = AUTO_DETECT_CODE,
            countryCode = null,
            localizedName = AUTO_DETECT_LABEL,
            nativeName = AUTO_DETECT_LABEL,
            flagEmoji = AUTO_DETECT_FLAG,
            capabilities = setOf(LanguageCapability.TRANSLATION)
        )
    }

    private fun capabilitiesFor(code: String): Set<LanguageCapability> {
        return buildSet {
            add(LanguageCapability.TRANSLATION)
            if (code in basicOcrCodes) add(LanguageCapability.BASIC_OCR)
            if (code in qualityOcrCodes) add(LanguageCapability.QUALITY_OCR)
            if (code in noLegalOcrCodes) add(LanguageCapability.NO_LEGAL_OCR)
        }
    }

    private fun displayLocaleFor(interfaceLang: String): Locale {
        return Locale.forLanguageTag(normalizeSupportedCode(interfaceLang))
    }

    private fun normalizeSupportedCode(code: String): String {
        val normalized = code.lowercase(Locale.ROOT)
        return if (normalized in supportedLanguageCountries) normalized else "en"
    }

    private fun String.capitalized(locale: Locale): String {
        return replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(locale) else char.toString()
        }
    }

    private const val REGIONAL_INDICATOR_OFFSET = 0x1F1E6
}
