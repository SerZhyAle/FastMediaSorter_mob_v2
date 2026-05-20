package com.sza.fastmediasorter.ui.share

import android.util.Patterns
import timber.log.Timber

/**
 * S0003 / S0140: extracts one or more http(s) URLs from a Share-sheet text payload.
 * Soft mode keeps the input order, strips trailing punctuation, and de-duplicates
 * repeated links so a copied album/series block can reuse the batch coordinator.
 */
object UrlInTextDetector {

    private val TRAILING_PUNCT = setOf('.', ',', ';', '!', '?', ')', ']', '}', '"', '\'', '»', '”')

    fun firstHttpUrl(input: String?): String? = httpUrls(input).firstOrNull()

    fun httpUrls(input: String?): List<String> {
        if (input.isNullOrBlank()) return emptyList()
        Timber.d("S0140: pillar-U multi-URL share-text parser entry")
        val matcher = Patterns.WEB_URL.matcher(input)
        val urls = linkedSetOf<String>()
        while (matcher.find()) {
            val raw = matcher.group() ?: continue
            val candidate = stripTrailingPunctuation(raw)
            val lower = candidate.lowercase()
            if (lower.startsWith("http://") || lower.startsWith("https://")) {
                urls += candidate
            }
        }
        if (urls.isNotEmpty()) {
            Timber.v("UrlInTextDetector: matched %d url(s)", urls.size)
        }
        return urls.toList()
    }

    private fun stripTrailingPunctuation(value: String): String {
        var end = value.length
        while (end > 0 && value[end - 1] in TRAILING_PUNCT) end--
        return if (end == value.length) value else value.substring(0, end)
    }
}
