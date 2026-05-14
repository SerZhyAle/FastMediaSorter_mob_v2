package com.sza.fastmediasorter.data.link

/**
 * Selects the best candidate from a page-level media scan while preserving input
 * order for ties.
 */
object CandidateSelectionPolicy {

    private const val ONE_MEBIBYTE: Long = 1_048_576L

    fun choose(candidates: List<HtmlMediaCandidate>): HtmlMediaCandidate? {
        val httpOnly = candidates.filter { isHttpScheme(it.url) }
        if (httpOnly.isEmpty()) return null

        // S0197: when an authoritative embedded-JSON candidate is present (Threads/IG data-sjs
        // payload), restrict the winner search to that subset. Otherwise the size-first heuristic
        // can pick a larger OG-preview/avatar on the same CDN that the post page also serves.
        val pool = httpOnly.filter { it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON }
            .takeIf { it.isNotEmpty() } ?: httpOnly

        pool.firstOrNull { (it.tentativeSizeBytes ?: 0L) >= ONE_MEBIBYTE }?.let { return it }

        val withKnownSize = pool.filter { it.tentativeSizeBytes != null }
        if (withKnownSize.isNotEmpty()) {
            val maxSize = withKnownSize.maxOf { it.tentativeSizeBytes!! }
            return withKnownSize
                .filter { it.tentativeSizeBytes == maxSize }
                .minByOrNull { it.source.ordinal * 10_000 + pool.indexOf(it) }
        }

        pool.firstOrNull {
            it.source == HtmlMediaCandidate.Source.HLS_MANIFEST ||
                it.source == HtmlMediaCandidate.Source.DASH_MANIFEST
        }?.let { return it }

        return pool.first()
    }

    private fun isHttpScheme(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }
}