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

        val pool = authoritativePool(httpOnly)

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

    fun batchPool(candidates: List<HtmlMediaCandidate>): List<HtmlMediaCandidate> {
        val httpOnly = candidates.filter { isHttpScheme(it.url) }
        if (httpOnly.isEmpty()) return emptyList()
        return authoritativePool(httpOnly)
    }

    // S0224: batch notifications derive their visible total from batch.items.size. When
    // data-sjs provides authoritative slide URLs, keep batch selection on that subset so the
    // total matches the number of files we will actually save instead of mixed preview noise.
    private fun authoritativePool(candidates: List<HtmlMediaCandidate>): List<HtmlMediaCandidate> {
        return candidates.filter { it.source == HtmlMediaCandidate.Source.EMBEDDED_JSON }
            .takeIf { it.isNotEmpty() } ?: candidates
    }

    private fun isHttpScheme(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }
}