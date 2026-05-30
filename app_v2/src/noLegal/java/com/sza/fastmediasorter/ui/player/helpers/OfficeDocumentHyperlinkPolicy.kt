package com.sza.fastmediasorter.ui.player.helpers

import java.util.Locale

/**
 * Link / active-content policy for the embedded noLegal Office viewer (S0301 Phase 03).
 *
 * Strategic decision (§6 research item 9): ordinary hyperlinks need no confirmation, but the
 * document stays a passive surface - scripts, macros, OLE execution, and implicit remote
 * follow-up are blocked. The policy is centralized here so the viewer never scatters ad-hoc
 * URL checks across the WebView client.
 */
class OfficeDocumentHyperlinkPolicy {

    /** Schemes a user may follow as ordinary navigation (opened via the system, not in-viewer). */
    private val ordinaryLinkSchemes = setOf("http", "https", "mailto", "tel")

    /** Schemes that can execute or smuggle active content - always blocked. */
    private val activeContentSchemes = setOf(
        "javascript", "vbscript", "data", "file", "content", "jar", "intent", "ms-",
    )

    /**
     * True when [url] is an ordinary hyperlink the user may follow.
     *
     * Active-content URLs are rejected here too, so callers can treat a true result as
     * "safe to hand to the system browser".
     */
    fun allowOrdinaryLinks(url: String?): Boolean {
        val scheme = schemeOf(url) ?: return false
        if (blockActiveContent(url)) return false
        return scheme in ordinaryLinkSchemes
    }

    /**
     * True when [url] carries active content (scripts, OLE, intents, local/embedded access)
     * and must be blocked. Conservative: anything not on the ordinary allow-list is treated
     * as unsafe to execute inside the passive viewer.
     */
    fun blockActiveContent(url: String?): Boolean {
        val scheme = schemeOf(url) ?: return true
        if (activeContentSchemes.any { scheme.startsWith(it) }) return true
        return scheme !in ordinaryLinkSchemes
    }

    private fun schemeOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val idx = url.indexOf(':')
        if (idx <= 0) return null
        return url.substring(0, idx).lowercase(Locale.ROOT)
    }
}
