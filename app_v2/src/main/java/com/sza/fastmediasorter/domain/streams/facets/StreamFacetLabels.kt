package com.sza.fastmediasorter.domain.streams.facets

import java.util.Locale

/**
 * The text a reader sees for a canonical language id. Shared by the phone's pickers and the watch's facet
 * list so the two draw one label from one rule; the id itself never changes with the device, only this
 * drawing does (ADR-3 of S4133).
 *
 * The label is the interface-language name followed by the language's own name in parentheses - the form
 * the translation language list already uses, which keeps the own name ICON-EXTERNAL rule 6 asks for
 * visible and searchable - written once when the two are equal. A language the platform cannot name, and
 * an id the vocabulary does not know, keep their English text with a capital first letter.
 */
object StreamFacetLabels {

    fun languageLabel(id: String, displayLocale: Locale): String {
        val code = StreamFacetVocabulary.codeByCanonicalName[id]
        val language = code?.let(Locale::forLanguageTag)
        val localized = platformName(language, code, displayLocale)
        val own = platformName(language, code, language)
        return when {
            localized == null -> displayCase(id)
            own == null || own.equals(localized, ignoreCase = true) -> localized
            else -> "$localized ($own)"
        }
    }

    /** The platform's name of [language] drawn in [inLocale], or null when it only echoes the code back. */
    private fun platformName(language: Locale?, code: String?, inLocale: Locale?): String? {
        if (language == null || code == null || inLocale == null) return null
        return language.getDisplayLanguage(inLocale)
            .takeUnless { it.isBlank() || it.equals(code, ignoreCase = true) }
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(inLocale) else it.toString() }
    }

    private fun displayCase(name: String): String =
        name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString() }
}
