package com.sza.fastmediasorter.domain.usecase.link

import javax.inject.Inject
import javax.inject.Singleton

/**
 * S0003: ordered registry of [UrlExtractionStrategy] instances.
 * Canonical order: `direct` first, then `html`. Future strategies append in §5.3-style
 * extension points without changing this order.
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
        val CANONICAL_ORDER = listOf("direct", "html")
    }
}
