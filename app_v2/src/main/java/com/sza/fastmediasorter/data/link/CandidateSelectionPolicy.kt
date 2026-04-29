package com.sza.fastmediasorter.data.link

/**
 * S0003 — strategic §5.1 pillar D / §6.3: selects the best candidate from a list.
 *
 * Rules (preserve input order for ties):
 * 1. Drop candidates whose scheme is not `http(s)` (filters out `data:`/`blob:`/`javascript:`).
 * 2. If any candidate has known size ≥ 1 MiB → return the **first** such candidate.
 * 3. Else if any candidate has a non-null size → return the candidate with max size,
 *    ties broken by [HtmlMediaCandidate.Source] ordinal then input order.
 * 4. Else return the first candidate by input order.
 * 5. Empty filtered list → null.
 */
object CandidateSelectionPolicy {

    private const val ONE_MEBIBYTE: Long = 1_048_576L

    fun choose(candidates: List<HtmlMediaCandidate>): HtmlMediaCandidate? {
        // Step 1: drop non-http(s). data:/blob:/javascript: never reach the network layer.
        val httpOnly = candidates.filter { isHttpScheme(it.url) }
        if (httpOnly.isEmpty()) return null

        // Step 2: first candidate with size >= 1 MiB.
        httpOnly.firstOrNull { (it.tentativeSizeBytes ?: 0L) >= ONE_MEBIBYTE }?.let { return it }

        // Step 3: largest known size.
        val withKnownSize = httpOnly.filter { it.tentativeSizeBytes != null }
        if (withKnownSize.isNotEmpty()) {
            val maxSize = withKnownSize.maxOf { it.tentativeSizeBytes!! }
            return withKnownSize
                .filter { it.tentativeSizeBytes == maxSize }
                .minByOrNull { it.source.ordinal * 10_000 + httpOnly.indexOf(it) }
        }

        // Step 4: first by input order.
        return httpOnly.first()
    }

    private fun isHttpScheme(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
    }
}
