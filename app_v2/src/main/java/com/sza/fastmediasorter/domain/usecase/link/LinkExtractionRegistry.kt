package com.sza.fastmediasorter.domain.usecase.link

import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0003: ordered registry of [UrlExtractionStrategy] instances.
 * Canonical order: `site` first when present, then `direct`, then `html`, then
 * `dynamic`. This keeps S0117's noLegal resolver ahead of the generic strategies
 * while still letting S0140 fall through from static HTML parsing into JS-rendered
 * discovery when the first HTML pass returns NotFound.
 */
@Singleton
class LinkExtractionRegistry @Inject constructor(
    strategies: Set<@JvmSuppressWildcards UrlExtractionStrategy>,
) {

    private val orderedStrategies: List<UrlExtractionStrategy> = strategies
        .also {
            require(it.distinctBy { s -> s.id }.size == it.size) {
                "Duplicate UrlExtractionStrategy ids: ${it.map { s -> s.id }}"
            }
        }
        .sortedBy { CANONICAL_ORDER.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }

    fun ordered(): List<UrlExtractionStrategy> = orderedStrategies

    private companion object {
        val CANONICAL_ORDER = listOf("site", "direct", "html", "dynamic")
    }
}
