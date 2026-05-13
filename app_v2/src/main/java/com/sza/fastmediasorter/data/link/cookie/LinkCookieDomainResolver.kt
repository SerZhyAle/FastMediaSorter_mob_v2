package com.sza.fastmediasorter.data.link.cookie

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Shared PSL-aware registrable-domain resolver for the link-download cookie path.
 *
 * Backed by OkHttp's built-in public-suffix-list implementation so that ccTLDs (co.uk,
 * com.au), PSL wildcards, and bare public suffixes are handled correctly without an
 * additional library. Returns null for IP addresses, localhost, and bare public suffixes
 * so callers can skip eTLD+1 fallback safely (S0176).
 */
internal fun registrableDomainOrNull(host: String): String? {
    if (host.isBlank()) return null
    // Build a minimal URL to access OkHttp's PSL-aware HttpUrl.topPrivateDomain().
    val url = "https://$host/".toHttpUrlOrNull() ?: return null
    return url.topPrivateDomain()
}
