package com.sza.fastmediasorter.domain.usecase.link

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LinkExtractionRegistry @Inject constructor(
    strategies: Set<@JvmSuppressWildcards UrlExtractionStrategy>,
) {

    private val orderedStrategies: List<UrlExtractionStrategy> = strategies
        .also {
            require(it.distinctBy { strategy -> strategy.id }.size == it.size) {
                "Duplicate UrlExtractionStrategy ids: ${it.map { strategy -> strategy.id }}"
            }
        }
        .sortedBy { strategy ->
            CANONICAL_ORDER.indexOf(strategy.id).let { index -> if (index < 0) Int.MAX_VALUE else index }
        }

    fun ordered(): List<UrlExtractionStrategy> = orderedStrategies

    private companion object {
        // S0174: "ytdlp" is registered only in the noLegal flavor DI module.
        // In other flavors this id matches no strategy - sort falls back to Int.MAX_VALUE, harmless.
        // S0177: native site extractors - noLegal only, benign no-op in other flavors.
        // S0303: "telegram" is the lightweight public t.me post extractor, bound only in noLegal.
        // Placed ahead of "ytdlp"/"html"/"dynamic" so it wins for t.me links; benign no-op elsewhere.
        val CANONICAL_ORDER = listOf("artstation", "deviantart", "vimeo", "dailymotion", "telegram", "ytdlp", "site", "direct", "html", "dynamic")
    }
}