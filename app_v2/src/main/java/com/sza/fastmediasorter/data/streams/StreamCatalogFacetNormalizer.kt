package com.sza.fastmediasorter.data.streams

import java.util.Locale
import javax.inject.Inject

/**
 * Keeps catalog-origin facet identifiers stable when an older asset or a manually maintained source
 * still uses a known predecessor spelling. Unknown non-blank values stay visible for a newer catalog.
 */
class StreamCatalogFacetNormalizer @Inject constructor() {

    fun normalize(
        category: String,
        topic: String,
        language: String,
        country: String,
    ): Facets = Facets(
        category = canonicalCategory(category),
        topic = canonicalTopic(topic),
        language = canonicalLanguages(language),
        country = canonicalCountry(country),
    )

    private fun canonicalCategory(value: String): String = when (val normalized = normalized(value)) {
        "radio", "radio (somafm)" -> "Radio"
        "live tv", "live-tv", "television", "tv" -> "Live TV"
        "open movies", "movie", "movies", "on demand", "on-demand video" -> "On-demand video"
        "test stream", "test streams", "test" -> "Test streams"
        else -> normalized.ifBlank { value.trim() }
    }

    private fun canonicalTopic(value: String): String = when (normalized(value)) {
        "adult contemporary", "pop" -> "Pop"
        "christian", "religious" -> "Religious"
        "children", "kids" -> "Kids"
        "blues", "jazz & blues" -> "Jazz & Blues"
        "country", "country & folk" -> "Country & Folk"
        "movies", "movie", "open movies", "movies & series" -> "Movies & Series"
        "science", "documentary" -> "Documentary"
        else -> value.trim()
    }

    private fun canonicalLanguages(value: String): String = value
        .split(LANGUAGE_DELIMITERS)
        .map(String::trim)
        .filter(String::isNotBlank)
        .map(::canonicalLanguage)
        .distinct()
        .joinToString(separator = ",")

    private fun canonicalLanguage(value: String): String = when (normalized(value)) {
        "american english", "british english", "english uk", "engilsh" -> "english"
        "deutsch", "gernan", "gerrnan" -> "german"
        "brazilian portuguese", "portuguese brazil" -> "portuguese"
        "bahasa indonesia" -> "indonesian"
        else -> normalized(value)
    }

    private fun canonicalCountry(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length == COUNTRY_CODE_LENGTH && trimmed.all(Char::isLetter)) {
            return trimmed.uppercase(Locale.ROOT)
        }
        return COUNTRY_ALIASES[normalized(trimmed)] ?: trimmed
    }

    private fun normalized(value: String): String = value.trim().lowercase(Locale.ROOT)

    data class Facets(
        val category: String,
        val topic: String,
        val language: String,
        val country: String,
    )

    private companion object {
        const val COUNTRY_CODE_LENGTH = 2
        val LANGUAGE_DELIMITERS = Regex("[,;/|]")
        val COUNTRY_ALIASES = mapOf(
            "united states" to "US",
            "usa" to "US",
            "united kingdom" to "GB",
            "uk" to "GB",
            "germany" to "DE",
            "ukraine" to "UA",
            "russia" to "RU",
        )
    }
}
