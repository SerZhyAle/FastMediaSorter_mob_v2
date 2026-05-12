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

        httpOnly.firstOrNull { (it.tentativeSizeBytes ?: 0L) >= ONE_MEBIBYTE }?.let { return it }

        val withKnownSize = httpOnly.filter { it.tentativeSizeBytes != null }
        if (withKnownSize.isNotEmpty()) {
            val maxSize = withKnownSize.maxOf { it.tentativeSizeBytes!! }
            return withKnownSize
                .filter { it.tentativeSizeBytes == maxSize }
                .minByOrNull { it.source.ordinal * 10_000 + httpOnly.indexOf(it) }
        }

        httpOnly.firstOrNull {
            it.source == HtmlMediaCandidate.Source.HLS_MANIFEST ||
                it.source == HtmlMediaCandidate.Source.DASH_MANIFEST
        }?.let { return it }

        return httpOnly.first()
    }

    private fun isHttpScheme(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }
}