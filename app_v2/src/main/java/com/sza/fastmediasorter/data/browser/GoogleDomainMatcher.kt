package com.sza.fastmediasorter.data.browser

import android.net.Uri

/**
 * Determines whether a URL host falls under Google's OAuth-only domain set (strategic S0200 ADR-4).
 *
 * Google domain auth flows MUST route through Chrome Custom Tabs and never through in-app WebView.
 * This matcher is the single source of truth for that decision. Adding a new Google host means
 * editing only [GOOGLE_AUTH_DOMAINS].
 *
 * Match rule: exact host or any subdomain (`*.host`) for every entry in [GOOGLE_AUTH_DOMAINS].
 */
object GoogleDomainMatcher {
    private val GOOGLE_AUTH_DOMAINS = setOf(
        "google.com",
        "accounts.google.com",
        "youtube.com",
        "music.youtube.com"
    )

    fun isGoogleAuthHost(uri: Uri?): Boolean {
        val host = uri?.host?.lowercase() ?: return false
        return GOOGLE_AUTH_DOMAINS.any { d -> host == d || host.endsWith(".$d") }
    }

    fun isGoogleAuthUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return isGoogleAuthHost(runCatching { Uri.parse(url) }.getOrNull())
    }
}
