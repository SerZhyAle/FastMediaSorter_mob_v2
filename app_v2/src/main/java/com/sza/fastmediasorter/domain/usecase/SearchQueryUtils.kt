package com.sza.fastmediasorter.domain.usecase

/**
 * Shared utility for preparing search queries from audio filenames.
 * Used by SearchAudioCoverUseCase and SearchLyricsUseCase to ensure DRY query building.
 */
object SearchQueryUtils {

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
}
