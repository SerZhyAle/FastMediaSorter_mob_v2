package com.sza.fastmediasorter.wear.domain.model

import java.util.Locale

/**
 * S2669: one curated collection as delivered in the archive's `collections.json` entry - the same
 * payload the phone reads, held here in the watch's own storage shape (no database, Gson files).
 *
 * [names] is an open locale map: the publisher guarantees `en`, and the watch picks its own locale
 * with [displayName] rather than through string resources, so a newly curated locale or collection
 * reaches the watch with the next catalog refresh and without an app release (strategic ADR-5).
 */
data class WearStreamCollection(
    val id: String,
    val sortOrder: Int,
    val names: Map<String, String>,
    /** Stream urls in the curator's order; the list order IS the order. */
    val memberUrls: List<String>
) {

    /**
     * The name this wearer reads: full locale tag, then the language-only tag, then `en` - the same
     * fall-through the phone's resolver follows, so the two surfaces never disagree on a name. The
     * raw id is the last resort rather than a blank string, because a blank chip label is untappable
     * on a round screen; the publisher's `en` guarantee keeps this rung theoretical.
     */
    fun displayName(locale: Locale): String {
        val candidates = listOf(locale.toLanguageTag(), locale.language, FALLBACK_TAG)
            .map { it.lowercase(Locale.ROOT) }
        return candidates.firstNotNullOfOrNull { tag -> names[tag]?.takeIf(String::isNotBlank) }
            ?: id
    }

    private companion object {
        const val FALLBACK_TAG = "en"
    }
}
