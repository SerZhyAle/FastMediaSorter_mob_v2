package com.sza.fastmediasorter.data.streams

import com.sza.fastmediasorter.domain.streams.facets.StreamFacetFolding
import java.util.Locale
import javax.inject.Inject

/**
 * Keeps catalog-origin facet identifiers stable when an older asset or a manually maintained source
 * still uses a known predecessor spelling. Category and topic keep an unknown non-blank value visible
 * for a newer catalog; language and country fold to the closed sets of STREAM-BANK 2.3 (amendment O) -
 * a language cell with nothing recognized reads `english`, a country that is not an assigned ISO 3166-1
 * alpha-2 code is blank - so a bank imported from an older publish and a rewritten one converge on the
 * same ids.
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

    private fun canonicalCategory(value: String): String = when (normalized(value)) {
        "radio", "radio (somafm)", "somafm" -> "Radio"
        "live tv", "live-tv", "television", "tv" -> "Live TV"
        "open movies", "movie", "movies", "on demand", "on-demand video", "on demand video" -> "On-demand video"
        "test", "test stream", "test streams" -> "Test streams"
        "webcam", "webcams", "cam", "cams" -> "Webcam"
        else -> value.trim()
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

    private fun canonicalLanguages(value: String): String = StreamFacetFolding.foldLanguages(value)

    private fun canonicalCountry(value: String): String = StreamFacetFolding.foldCountry(value)

    private fun normalized(value: String): String = value.trim().lowercase(Locale.ROOT)

    data class Facets(
        val category: String,
        val topic: String,
        val language: String,
        val country: String,
    )
}
