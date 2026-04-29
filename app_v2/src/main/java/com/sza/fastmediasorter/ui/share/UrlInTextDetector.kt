package com.sza.fastmediasorter.ui.share

import android.util.Patterns
import timber.log.Timber

/**
 * S0003: extracts the first http(s) URL from a Share-sheet text payload.
 * Soft mode per strategic §6.8 — picks the first match, ignores extra tokens.
 */
object UrlInTextDetector {

    private val TRAILING_PUNCT = setOf('.', ',', ';', '!', '?', ')', ']', '}', '"', '\'', '»', '”')

    fun firstHttpUrl(input: String?): String? {
        if (input.isNullOrBlank()) return null
        val matcher = Patterns.WEB_URL.matcher(input)
        while (matcher.find()) {
            val raw = matcher.group() ?: continue
            val candidate = stripTrailingPunctuation(raw)
            val lower = candidate.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                Timber.v("UrlInTextDetector: matched url=%s", candidate)
                return candidate
            }
        }
        return null
    }

    private fun stripTrailingPunctuation(value: String): String {
        var end = value.length
        while (end > 0 && value[end - 1] in TRAILING_PUNCT) end--
        return if (end == value.length) value else value.substring(0, end)
    }
}
