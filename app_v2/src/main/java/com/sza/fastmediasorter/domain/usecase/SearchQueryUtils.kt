package com.sza.fastmediasorter.domain.usecase

import java.text.Normalizer

/**
 * Structured "Artist - Title" decomposition of an audio filename. [artist] is null when the name
 * could not be split into an artist part (only a title was recoverable).
 */
data class ParsedTrackName(val artist: String?, val title: String)

/**
 * Shared utility for preparing search queries from audio filenames.
 * Used by SearchAudioCoverUseCase and SearchLyricsUseCase to ensure DRY query building.
 */
object SearchQueryUtils {

    /**
     * Number of candidate results to request from each provider so the best can be selected by
     * confidence, instead of blindly taking the first result. One result set per provider call -
     * no per-track request loops (cover search performance budget).
     */
    const val COVER_CANDIDATE_LIMIT = 5

    /**
     * Minimum match confidence (0..1) required to accept a provider result as the track's cover.
     * Below this the cover is suppressed (empty state) rather than showing a likely-wrong artist.
     */
    const val COVER_MATCH_CONFIDENCE_THRESHOLD = 0.5

    // Artist agreement weighs more than title agreement: a same-titled song by a different artist
    // is the exact false-positive this scoring exists to reject.
    private const val ARTIST_WEIGHT = 0.6
    private const val TITLE_WEIGHT = 0.4

    /** Common placeholder values that should not be used as search terms */
    private val PLACEHOLDER_VALUES = setOf(
        "unknown", "unknown artist", "unknown title",
        "неизвестный", "неизвестный исполнитель", "неизвестно",
        "невідомий", "невідомий виконавець",
        "various artists", "va", "n/a", "none", "null", "undefined"
    )

    /**
     * Prepare search query from filename.
     * Removes file extension, replaces separators with spaces, removes common patterns
     * like track numbers, brackets, and parentheses.
     *
     * @param filename Audio file name (with or without extension)
     * @return Cleaned search query string
     */
    fun prepareSearchQuery(filename: String): String {
        val nameWithoutExtension = filename.substringBeforeLast('.')

        return nameWithoutExtension
            .replace(Regex("[_\\-.]"), " ")
            .replace(Regex("^\\d+\\s*[-.]?\\s*"), "") // Leading track numbers like "01 - " or "01."
            .replace(Regex("\\[.*?]"), " ")            // Content in square brackets
            .replace(Regex("\\(.*?\\)"), " ")          // Content in parentheses
            .replace(Regex("\\{.*?\\}"), " ")          // Content in curly braces
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ") // Keep only letters (any script), digits, spaces
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Check if a metadata value is a placeholder (e.g. "Unknown", "Неизвестный").
     * @return true if the value should NOT be used as a search term
     */
    fun isPlaceholderValue(value: String?): Boolean {
        if (value.isNullOrBlank()) return true
        return value.trim().lowercase() in PLACEHOLDER_VALUES
    }

    /**
     * Return the value only if it's not a placeholder, otherwise null.
     */
    fun filterPlaceholder(value: String?): String? {
        return if (isPlaceholderValue(value)) null else value?.trim()
    }

    /**
     * Strip all bracket types and their contents from a metadata string (artist, title),
     * then remove remaining special characters (keep Unicode letters, digits, spaces).
     * Used to sanitize ID3 tags and resolved metadata before building search queries.
     *
     * Examples:
     *   "Breathe (In the Air)"         → "Breathe"
     *   "Song [Live Version]"           → "Song"
     *   "Track {Remastered 2011}"       → "Track"
     *   "Pink Floyd"                    → "Pink Floyd"  (unchanged)
     */
    fun cleanForSearch(text: String): String {
        var result = text
        result = result.replace(Regex("\\s*\\([^)]*\\)"), "")  // (...)
        result = result.replace(Regex("\\s*\\[[^]]*\\]"), "")  // [...]
        result = result.replace(Regex("\\s*\\{[^}]*\\}"), "")  // {...}
        result = result.replace(Regex("\\s*<[^>]*>"), "")       // <...>
        // Remove remaining non-alphanumeric chars (keep Unicode letters/digits/spaces)
        result = result.replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        result = result.replace(Regex("\\s+"), " ").trim()
        return result
    }

    /**
     * Decompose an audio filename into an explicit artist + title pair. Handles "Artist - Title",
     * "01 - Artist - Title" (leading track number stripped) and "Artist-Title" (no surrounding
     * spaces). When no separator is present, returns the whole name as the title with a null artist.
     * Consolidates the parse previously duplicated in the lyrics path so the cover path can give the
     * artist an explicit role in the query and in confidence scoring.
     */
    fun parseArtistTitle(filename: String): ParsedTrackName {
        var name = filename.substringBeforeLast('.')
        // Strip a leading track number like "01 - ", "01.", "1 - ".
        name = name.replace(Regex("^\\d+\\s*[-.]\\s*"), "").trim()
        if (name.contains(" - ")) {
            val parts = name.split(" - ", limit = 2)
            if (parts.size == 2) {
                return ParsedTrackName(parts[0].trim().takeIf { it.isNotBlank() }, parts[1].trim())
            }
        }
        if (name.contains("-") && !name.contains(" ")) {
            val parts = name.split("-", limit = 2)
            if (parts.size == 2) {
                return ParsedTrackName(parts[0].trim().takeIf { it.isNotBlank() }, parts[1].trim())
            }
        }
        return ParsedTrackName(null, name.trim())
    }

    /**
     * Confidence (0..1) that a provider candidate matches the requested track. Artist agreement is
     * weighted above title agreement so a same-titled song by a different artist scores low. Matching
     * is token-set Jaccard over script-normalized tokens (diacritics stripped, lower-cased), which
     * tolerates word-order and extra words. [dirArtist] (artist inferred from the album folder) is an
     * additional artist signal: the better of query-artist / dir-artist agreement is used. When no
     * artist is available to verify, the score falls back to title agreement alone.
     */
    fun matchConfidence(
        queryArtist: String?,
        queryTitle: String?,
        candidateArtist: String?,
        candidateTitle: String?,
        dirArtist: String? = null,
    ): Double {
        val qArtist = matchTokens(queryArtist)
        val qDir = matchTokens(dirArtist)
        val qTitle = matchTokens(queryTitle)
        val cArtist = matchTokens(candidateArtist)
        val cTitle = matchTokens(candidateTitle)

        val titleScore = jaccard(qTitle, cTitle)
        val hasArtistQuery = qArtist.isNotEmpty() || qDir.isNotEmpty()
        if (!hasArtistQuery) return titleScore

        val artistScore = maxOf(jaccard(qArtist, cArtist), jaccard(qDir, cArtist))
        return ARTIST_WEIGHT * artistScore + TITLE_WEIGHT * titleScore
    }

    /** Script-normalized token set: NFD diacritic strip, lower-case, split on non-alphanumeric. */
    private fun matchTokens(text: String?): Set<String> {
        if (text.isNullOrBlank()) return emptySet()
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
        return normalized.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }.toSet()
    }

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersection = a.intersect(b).size.toDouble()
        val union = a.union(b).size.toDouble()
        return if (union == 0.0) 0.0 else intersection / union
    }
}
